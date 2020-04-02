package com.calvin.walletapi.actors

import java.time.ZonedDateTime
import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, RetentionCriteria }
import com.calvin.walletapi.actors.Wallet.Event.FeeSubtracted
import com.calvin.walletapi.domain.Fees.{ FeeInfo, FeeType }
import com.calvin.walletapi.domain.{ Fees, WalletId }
import com.calvin.walletapi.infrastructure.serialization.CborSerializable

import scala.collection.immutable.Queue

object Wallet {
  sealed trait Command[R <: Reply] {
    def replyTo: ActorRef[R]
  }
  object Command {
    final case class Open(replyTo: ActorRef[Reply])                                extends Command[Reply]
    final case class Deposit(amount: Long)(val replyTo: ActorRef[Reply])           extends Command[Reply]
    final case class Withdraw(amount: Long)(val replyTo: ActorRef[Reply])          extends Command[Reply]
    final case class ViewDepositFee(amount: Long)(val replyTo: ActorRef[Reply])    extends Command[Reply]
    final case class ViewWithdrawalFee(amount: Long)(val replyTo: ActorRef[Reply]) extends Command[Reply]
    final case class ViewBalance(replyTo: ActorRef[Reply])                         extends Command[Reply]
    final case class ViewImmediateHistory(replyTo: ActorRef[Reply])                extends Command[Reply]
  }

  sealed trait Event extends CborSerializable
  object Event {
    final case class Opened(date: ZonedDateTime)                    extends Event
    final case class Deposited(amount: Long, id: String, fee: Long) extends Event
    final case class Withdrawn(amount: Long, id: String, fee: Long) extends Event
    final case class FeeSubtracted(percentage: Double, feeAmount: Long, feeType: FeeType, relationId: String)
        extends Event
  }

  sealed trait Reply
  object Reply {
    final case object DoesNotExist                               extends Reply
    final case class Opened(already: Boolean)                    extends Reply
    final case object InsufficientFunds                          extends Reply
    final case object InsufficientDepositAmount                  extends Reply
    final case object InsufficientWithdrawalAmount               extends Reply
    final case object SuccessfulDeposit                          extends Reply
    final case object SuccessfulWithdrawal                       extends Reply
    final case class CurrentBalance(amount: Long)                extends Reply
    final case class FeeBreakdown(percentage: Double, fee: Long) extends Reply
    final case class ImmediateHistory(events: List[Event])       extends Reply
  }

  sealed trait State
  object State {
    final case object Uninitialized                               extends State
    final case class Active(balance: Long, history: Queue[Event]) extends State
  }

  // 1 dollar or 100 cents is the minimum amount we will allow so the fee structure works nicely
  private val MinTransactionAmount = 100L

  private val commandHandler: (State, Command[Reply]) => Effect[Event, State] = { (state, command) =>
    state match {
      case State.Uninitialized =>
        command match {
          case Command.Open(replyTo) =>
            Effect
              .persist(Event.Opened(ZonedDateTime.now()))
              .thenReply(replyTo)(_ => Reply.Opened(false))

          case otherEvent =>
            Effect.reply(replyTo = otherEvent.replyTo)(Reply.DoesNotExist)
        }

      case State.Active(balance, history) =>
        command match {
          case Command.Open(replyTo) =>
            Effect.reply(replyTo)(Reply.Opened(true))

          // NOTE: Deposits and successful Withdrawals should support having an idempotency key
          // like Stripe does in order to avoid duplicate transactions
          case c @ Command.Deposit(amount) if amount >= MinTransactionAmount =>
            val FeeInfo(percent, fee, amountMinusFee) = Fees.calculateFee(FeeType.Deposit)(balance, amount)
            val depositId                             = generateId()

            Effect
              .persist(
                Event.Deposited(amountMinusFee, depositId, fee),
                FeeSubtracted(percent, fee, FeeType.Deposit, depositId)
              )
              .thenReply(c.replyTo)(_ => Reply.SuccessfulDeposit)

          case c @ Command.Deposit(_) =>
            Effect.reply(c.replyTo)(Reply.InsufficientDepositAmount)

          case c @ Command.Withdraw(amount) if balance >= amount && amount >= MinTransactionAmount =>
            val FeeInfo(percent, fee, amountMinusFee) = Fees.calculateFee(FeeType.Withdraw)(balance, amount)
            val withdrawalId                          = generateId()

            Effect
              .persist(
                Event.Withdrawn(amountMinusFee, withdrawalId, fee),
                Event.FeeSubtracted(percent, fee, FeeType.Withdraw, withdrawalId)
              )
              .thenReply(c.replyTo)(_ => Reply.SuccessfulWithdrawal)

          case c @ Command.Withdraw(amount) if amount < MinTransactionAmount =>
            Effect.reply(c.replyTo)(Reply.InsufficientWithdrawalAmount)

          case c @ Command.Withdraw(_) =>
            Effect.reply(c.replyTo)(Reply.InsufficientFunds)

          case Command.ViewBalance(replyTo) =>
            Effect.reply(replyTo)(Reply.CurrentBalance(balance))

          case c @ Command.ViewDepositFee(amount) =>
            val FeeInfo(percent, fee, _) = Fees.calculateFee(FeeType.Deposit)(balance, amount)
            Effect.reply(c.replyTo)(Reply.FeeBreakdown(percent, fee))

          case c @ Command.ViewWithdrawalFee(amount) =>
            val FeeInfo(percent, fee, _) = Fees.calculateFee(FeeType.Withdraw)(balance, amount)
            Effect.reply(c.replyTo)(Reply.FeeBreakdown(percent, fee))

          case Command.ViewImmediateHistory(replyTo) =>
            Effect.reply(replyTo)(Reply.ImmediateHistory(history.toList))
        }
    }
  }

  private def eventHandler(historyLimit: Int): (State, Event) => State = { (state, event) =>
    state match {
      case State.Uninitialized =>
        event match {
          case o: Event.Opened =>
            State.Active(0L, Queue(o))

          case _ =>
            state
        }

      case s @ State.Active(balance, history) =>
        event match {
          case _: Event.Opened =>
            s

          case _: Event.FeeSubtracted =>
            s

          case d @ Event.Deposited(amount, _, _) =>
            s.copy(balance = balance + amount, history = keepLatest(historyLimit)(history.enqueue(d)))

          case w @ Event.Withdrawn(amount, _, _) =>
            s.copy(balance = balance - amount, history = keepLatest(historyLimit)(history.enqueue(w)))
        }
    }
  }

  private def generateId(): String = UUID.randomUUID().toString

  private def keepLatest[A](limit: Int)(queue: Queue[A]): Queue[A] =
    queue.takeRight(limit)

  private def eventTagger: Event => Set[String] = {
    case _: Event.Opened    => Set("open")
    case _: Event.Deposited => Set("deposit")
    case _: Event.Withdrawn => Set("withdraw")
    case _: FeeSubtracted   => Set("fee")
  }

  val TypeKey: EntityTypeKey[Command[Reply]] = EntityTypeKey[Command[Reply]]("Wallet")

  def create(historyLimit: Int)(walletId: WalletId, persistenceId: PersistenceId): Behavior[Command[Reply]] =
    Behaviors.setup { ctx =>
      ctx.log.info(s"Starting Wallet ${walletId.id}")
      EventSourcedBehavior(persistenceId, State.Uninitialized, commandHandler, eventHandler(historyLimit))
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 10, keepNSnapshots = 2))
        .withTagger(eventTagger)
    }
}
