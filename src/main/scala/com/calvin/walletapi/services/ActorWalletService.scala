package com.calvin.walletapi.services
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityRef }
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.typed.PersistenceId
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.calvin.walletapi.actors.Wallet
import com.calvin.walletapi.actors.Wallet._
import com.calvin.walletapi.domain.WalletId
import com.calvin.walletapi.services.Error._
import com.calvin.walletapi.services.Response._
import zio.Task

import scala.concurrent.duration.FiniteDuration

object ActorWalletService {
  def create(system: ActorSystem[_], timeoutDuration: FiniteDuration, historyLimit: Int): WalletService = {
    val sharding = ClusterSharding(system)
    sharding.init(
      Entity(Wallet.TypeKey)(entityCtx =>
        Wallet
          .create(historyLimit)(
            walletId = WalletId(entityCtx.entityId),
            persistenceId = PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId)
          )
      )
    )

    val readJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    val refForId: WalletId => EntityRef[Command[Reply]] =
      aId => sharding.entityRefFor(Wallet.TypeKey, aId.id)

    val queryForId: WalletId => Source[Event, NotUsed] =
      aId =>
        readJournal
          .currentEventsByPersistenceId(aId.id, 0L, Long.MaxValue)
          .map(_.event)
          .collectType[Event]

    implicit val timeout: Timeout = Timeout(timeoutDuration)

    new ActorWalletService(refForId, queryForId)
  }
}

private class ActorWalletService(
  refForId: WalletId => EntityRef[Command[Reply]],
  eventsForId: WalletId => Source[Event, NotUsed]
)(implicit timeout: Timeout)
    extends WalletService {
  override def create(walletId: WalletId): Task[Either[AlreadyCreated, SuccessfulCreation]] = {
    val ref      = refForId(walletId)
    val openTask = Task.fromFuture(_ => ref ? Command.Open)
    openTask.flatMap {
      case Reply.Opened(already) =>
        val response =
          if (already) Left(AlreadyCreated)
          else Right(SuccessfulCreation)
        Task(response)

      case invalidReply =>
        Task.dieMessage(s"Expected the wallet to be created or already created but got $invalidReply")
    }
  }

  override def deposit(walletId: WalletId, amount: Long): Task[Either[WalletNotCreated, SuccessfulDeposit]] = {
    val ref         = refForId(walletId)
    val depositTask = Task.fromFuture(_ => ref ? Command.Deposit(amount))
    depositTask.flatMap {
      case Reply.DoesNotExist =>
        Task(Left(WalletNotCreated))

      case Reply.SuccessfulDeposit =>
        Task(Right(SuccessfulDeposit(amount)))

      case invalidReply =>
        Task.dieMessage(s"Expected the wallet to be created or a successful deposit but got $invalidReply")
    }
  }

  override def withdraw(walletId: WalletId, amount: Long): Task[Either[Error, SuccessfulWithdrawal]] = {
    val ref          = refForId(walletId)
    val withdrawTask = Task.fromFuture(_ => ref ? Command.Withdraw(amount))
    withdrawTask.flatMap {
      case Reply.DoesNotExist =>
        Task(Left(WalletNotCreated))

      case Reply.SuccessfulWithdrawal =>
        Task(Right(SuccessfulWithdrawal(amount)))

      case invalidReply =>
        Task.dieMessage(s"Expected the wallet to be created or a successful deposit but got $invalidReply")
    }
  }

  override def immediateHistory(walletId: WalletId): Task[Either[WalletNotCreated, History]] = {
    val ref         = refForId(walletId)
    val historyTask = Task.fromFuture(_ => ref ? Command.ViewImmediateHistory)
    historyTask.flatMap {
      case Reply.DoesNotExist =>
        Task(Left(WalletNotCreated))

      case Reply.ImmediateHistory(events) =>
        Task(Right(History(events)))

      case invalidReply =>
        Task.dieMessage(s"Expected the wallet to be created or a successful deposit but got $invalidReply")
    }
  }

  override def allHistory(walletId: WalletId): Task[Either[WalletNotCreated, Source[Wallet.Event, NotUsed]]] =
    immediateHistory(walletId).map {
      case Left(value) => Left(value)
      case Right(_)    => Right(eventsForId(walletId))
    }

  override def balance(walletId: WalletId): Task[Either[WalletNotCreated, Balance]] = {
    val ref         = refForId(walletId)
    val balanceTask = Task.fromFuture(_ => ref ? Command.ViewBalance)
    balanceTask.flatMap {
      case Reply.DoesNotExist =>
        Task(Left(WalletNotCreated))

      case Reply.CurrentBalance(amount) =>
        Task(Right(Balance(amount)))

      case invalidReply =>
        Task.dieMessage(s"Expected the wallet to be created or the balance but got $invalidReply")
    }
  }
}
