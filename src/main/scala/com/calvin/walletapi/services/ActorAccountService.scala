package com.calvin.walletapi.services
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityRef }
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.typed.PersistenceId
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.calvin.walletapi.actors.Account
import com.calvin.walletapi.actors.Account._
import com.calvin.walletapi.services.ErrorResponse._
import com.calvin.walletapi.services.Response._
import zio.Task

import scala.concurrent.duration.FiniteDuration

object ActorAccountService {
  def create(system: ActorSystem[_], timeoutDuration: FiniteDuration, historyLimit: Int): AccountService = {
    val sharding = ClusterSharding(system)
    sharding.init(
      Entity(Account.TypeKey)(entityCtx =>
        Account
          .create(historyLimit)(
            accountId = AccountId(entityCtx.entityId),
            persistenceId = PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId)
          )
      )
    )

    val readJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    val refForId: AccountId => EntityRef[Command[Reply]] =
      aId => sharding.entityRefFor(Account.TypeKey, aId.id)

    val queryForId: AccountId => Source[Event, NotUsed] =
      aId =>
        readJournal
          .currentEventsByPersistenceId(aId.id, 0L, Long.MaxValue)
          .map(_.event)
          .collectType[Event]

    implicit val timeout: Timeout = Timeout(timeoutDuration)

    new ActorAccountService(refForId, queryForId)
  }
}

private class ActorAccountService(
  refForId: AccountId => EntityRef[Command[Reply]],
  eventsForId: AccountId => Source[Event, NotUsed]
)(implicit timeout: Timeout)
    extends AccountService {
  override def create(accountId: AccountId): Task[Either[AlreadyCreated, SuccessfulCreation]] = {
    val ref      = refForId(accountId)
    val openTask = Task.fromFuture(_ => ref ? Command.Open)
    openTask.flatMap {
      case Reply.Opened(already) =>
        val response =
          if (already) Left(AlreadyCreated)
          else Right(SuccessfulCreation)
        Task(response)

      case invalidReply =>
        Task.dieMessage(s"Expected the account to be created or already created but got $invalidReply")
    }
  }

  override def deposit(accountId: AccountId, amount: Long): Task[Either[AccountNotCreated, SuccessfulDeposit]] = {
    val ref         = refForId(accountId)
    val depositTask = Task.fromFuture(_ => ref ? Command.Deposit(amount))
    depositTask.flatMap {
      case Reply.DoesNotExist =>
        Task(Left(AccountNotCreated))

      case Reply.SuccessfulDeposit =>
        Task(Right(SuccessfulDeposit(amount)))

      case invalidReply =>
        Task.dieMessage(s"Expected the account to be created or a successful deposit but got $invalidReply")
    }
  }

  override def withdraw(accountId: AccountId, amount: Long): Task[Either[ErrorResponse, SuccessfulWithdrawal]] = {
    val ref          = refForId(accountId)
    val withdrawTask = Task.fromFuture(_ => ref ? Command.Withdraw(amount))
    withdrawTask.flatMap {
      case Reply.DoesNotExist =>
        Task(Left(AccountNotCreated))

      case Reply.SuccessfulWithdrawal =>
        Task(Right(SuccessfulWithdrawal(amount)))

      case invalidReply =>
        Task.dieMessage(s"Expected the account to be created or a successful deposit but got $invalidReply")
    }
  }

  override def immediateHistory(accountId: AccountId): Task[Either[AccountNotCreated, History]] = {
    val ref         = refForId(accountId)
    val historyTask = Task.fromFuture(_ => ref ? Command.ViewImmediateHistory)
    historyTask.flatMap {
      case Reply.DoesNotExist =>
        Task(Left(AccountNotCreated))

      case Reply.ImmediateHistory(events) =>
        Task(Right(History(events)))

      case invalidReply =>
        Task.dieMessage(s"Expected the account to be created or a successful deposit but got $invalidReply")
    }
  }

  override def allHistory(accountId: AccountId): Task[Either[AccountNotCreated, Source[Account.Event, NotUsed]]] =
    immediateHistory(accountId).map {
      case Left(value) => Left(value)
      case Right(_)    => Right(eventsForId(accountId))
    }
}
