package com.calvin.walletapi.services
import akka.NotUsed
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity }
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.typed.PersistenceId
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.calvin.walletapi.actors.Wallet
import com.calvin.walletapi.actors.Wallet._
import com.calvin.walletapi.domain.Fees.FeeType
import com.calvin.walletapi.domain.WalletId
import com.calvin.walletapi.services.Error._
import com.calvin.walletapi.services.Response._
import zio.Task

import scala.concurrent.duration.FiniteDuration

object ActorWalletService {
  def make(system: ActorSystem[_], timeoutDuration: FiniteDuration, historyLimit: Int): WalletService = {
    implicit val timeout: Timeout = Timeout(timeoutDuration)
    val sharding                  = ClusterSharding(system)

    sharding.init(
      Entity(Wallet.TypeKey)(entityCtx =>
        Wallet
          .create(historyLimit)(
            walletId = WalletId(entityCtx.entityId),
            persistenceId = PersistenceId(entityCtx.entityTypeKey.name, entityCtx.entityId)
          )
      )
    )

    val queryForId: WalletId => Source[Event, NotUsed] = {
      val readJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

      { walletId =>
        readJournal
          .currentEventsByPersistenceId(s"${Wallet.TypeKey.name}|${walletId.id}", 0L, Long.MaxValue)
          .map(_.event)
          .collectType[Event]
      }
    }

    val routeMessage: (WalletId, ActorRef[Reply] => Command[Reply]) => Task[Reply] = { (w, c) =>
      val ref = sharding.entityRefFor(Wallet.TypeKey, w.id)
      Task.fromFuture(_ => ref ? c)
    }

    new ActorWalletService(routeMessage, queryForId)
  }
}

private class ActorWalletService(
  routeMessage: (WalletId, ActorRef[Reply] => Command[Reply]) => Task[Reply],
  eventsForId: WalletId => Source[Event, NotUsed]
) extends WalletService {

  override def create(walletId: WalletId): Task[Either[AlreadyCreated, SuccessfulCreation]] =
    routeMessage(walletId, Command.Open).flatMap(createReply)

  override def deposit(walletId: WalletId, amount: Long): Task[Either[Error, SuccessfulDeposit]] =
    routeMessage(walletId, Command.Deposit(amount)).flatMap(depositReply(amount))

  override def withdraw(walletId: WalletId, amount: Long): Task[Either[Error, SuccessfulWithdrawal]] =
    routeMessage(walletId, Command.Withdraw(amount)).flatMap(withdrawReply(amount))

  override def immediateHistory(walletId: WalletId): Task[Either[WalletNotCreated, History]] =
    routeMessage(walletId, Command.ViewImmediateHistory).flatMap(immediateHistoryReply)

  override def allHistory(walletId: WalletId): Task[Either[WalletNotCreated, Source[Wallet.Event, NotUsed]]] =
    balance(walletId).map {
      case Left(value) => Left(value)
      case Right(_)    => Right(eventsForId(walletId))
    }

  override def balance(walletId: WalletId): Task[Either[WalletNotCreated, Balance]] =
    routeMessage(walletId, Command.ViewBalance).flatMap(balanceReply)

  override def queryDepositFee(walletId: WalletId, amount: Long): Task[Either[WalletNotCreated, FeeReport]] =
    queryFee(FeeType.Deposit)(walletId, amount)

  override def queryWithdrawFee(walletId: WalletId, amount: Long): Task[Either[WalletNotCreated, FeeReport]] =
    queryFee(FeeType.Withdraw)(walletId, amount)

  private def createReply(r: Reply): Task[Either[AlreadyCreated, SuccessfulCreation]] = r match {
    case Reply.Opened(already) =>
      val response =
        if (already) Left(AlreadyCreated)
        else Right(SuccessfulCreation)
      Task(response)

    case invalidReply =>
      Task.dieMessage(s"Expected the wallet to be created or already created but got $invalidReply")
  }

  private def depositReply(amount: Long)(r: Reply): Task[Either[Error, SuccessfulDeposit]] = r match {
    case Reply.DoesNotExist =>
      Task(Left(WalletNotCreated))

    case Reply.InsufficientDepositAmount =>
      Task(Left(InsufficientAmount))

    case Reply.SuccessfulDeposit(fee) =>
      Task(Right(SuccessfulDeposit(amount, fee)))

    case invalidReply =>
      Task.dieMessage(s"Expected the wallet to be created or a successful deposit but got $invalidReply")
  }

  private def withdrawReply(amount: Long)(r: Reply): Task[Either[Error, SuccessfulWithdrawal]] = r match {
    case Reply.DoesNotExist =>
      Task(Left(WalletNotCreated))

    case Reply.InsufficientWithdrawalAmount =>
      Task(Left(InsufficientAmount))

    case Reply.InsufficientFunds =>
      Task(Left(WithdrawOverBalance))

    case Reply.SuccessfulWithdrawal(fee) =>
      Task(Right(SuccessfulWithdrawal(amount, fee)))

    case invalidReply =>
      Task.dieMessage(s"Expected the wallet to be created or a successful withdrawal but got $invalidReply")
  }

  private def immediateHistoryReply(r: Reply): Task[Either[WalletNotCreated, History]] = r match {
    case Reply.DoesNotExist =>
      Task(Left(WalletNotCreated))

    case Reply.ImmediateHistory(events) =>
      Task(Right(History(events)))

    case invalidReply =>
      Task.dieMessage(s"Expected the wallet to be created or a successful deposit but got $invalidReply")
  }

  private def balanceReply(r: Reply): Task[Either[WalletNotCreated, Balance]] = r match {
    case Reply.DoesNotExist =>
      Task(Left(WalletNotCreated))

    case Reply.CurrentBalance(amount) =>
      Task(Right(Balance(amount)))

    case invalidReply =>
      Task.dieMessage(s"Expected the wallet to be created or the balance but got $invalidReply")
  }

  private def queryFee(
    query: FeeType
  )(walletId: WalletId, amount: Long): Task[Either[WalletNotCreated, FeeReport]] = {
    val command = query match {
      case FeeType.Withdraw => Command.ViewWithdrawalFee(amount)(_)
      case FeeType.Deposit  => Command.ViewDepositFee(amount)(_)
    }

    routeMessage(walletId, command).flatMap(queryFeeReply)
  }

  private def queryFeeReply(r: Reply): Task[Either[WalletNotCreated, FeeReport]] = r match {
    case Reply.DoesNotExist =>
      Task(Left(WalletNotCreated))

    case Reply.FeeBreakdown(percentage, fee) =>
      Task(Right(FeeReport(percentage, fee)))

    case invalidReply =>
      Task.dieMessage(s"Expected the wallet to be created or the fee breakdown but got $invalidReply")
  }
}
