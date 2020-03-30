package com.calvin.walletapi.actors

import java.time.ZonedDateTime

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

import scala.collection.immutable.Queue

object Account {
  case class AccountId(id: String) extends AnyVal

  sealed trait Command[R <: Reply] extends CborSerializable {
    def replyTo: ActorRef[R]
  }
  object Command {
    final case class Open(replyTo: ActorRef[Reply])                       extends Command[Reply]
    final case class Deposit(amount: Long)(val replyTo: ActorRef[Reply])  extends Command[Reply]
    final case class Withdraw(amount: Long)(val replyTo: ActorRef[Reply]) extends Command[Reply]
    final case class ViewBalance(replyTo: ActorRef[Reply])                extends Command[Reply]
    final case class ViewImmediateHistory(replyTo: ActorRef[Reply])       extends Command[Reply]
  }

  sealed trait Event extends CborSerializable
  object Event {
    final case class Opened(date: ZonedDateTime) extends Event
    final case class Deposited(amount: Long)     extends Event
    final case class Withdrawn(amount: Long)     extends Event
  }

  sealed trait Reply extends CborSerializable
  object Reply {
    final case object DoesNotExist                         extends Reply
    final case class Opened(already: Boolean)              extends Reply
    final case object InsufficientFunds                    extends Reply
    final case object SuccessfulDeposit                    extends Reply
    final case object SuccessfulWithdrawal                 extends Reply
    final case class CurrentBalance(amount: Long)          extends Reply
    final case class ImmediateHistory(events: List[Event]) extends Reply
  }

  sealed trait State
  object State {
    final case object Uninitialized                               extends State
    final case class Active(balance: Long, history: Queue[Event]) extends State
  }

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
          case c @ Command.Deposit(amount) =>
            Effect
              .persist(Event.Deposited(amount))
              .thenReply(c.replyTo)(_ => Reply.SuccessfulDeposit)

          case c @ Command.Withdraw(amount) if balance >= amount =>
            Effect
              .persist(Event.Withdrawn(amount))
              .thenReply(c.replyTo)(_ => Reply.SuccessfulWithdrawal)

          case c @ Command.Withdraw(_) =>
            Effect.reply(c.replyTo)(Reply.InsufficientFunds)

          case Command.ViewBalance(replyTo) =>
            Effect.reply(replyTo)(Reply.CurrentBalance(balance))

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

          case d @ Event.Deposited(amount) =>
            s.copy(balance = balance + amount, history = keepLatest(historyLimit)(history.enqueue(d)))

          case w @ Event.Withdrawn(amount) =>
            s.copy(balance = balance - amount, history = keepLatest(historyLimit)(history.enqueue(w)))
        }
    }
  }

  private def keepLatest[A](limit: Int)(queue: Queue[A]): Queue[A] =
    queue.takeRight(limit)

  val TypeKey: EntityTypeKey[Command[Reply]] = EntityTypeKey[Command[Reply]]("Account")

  def create(historyLimit: Int)(accountId: AccountId, persistenceId: PersistenceId): Behavior[Command[Reply]] =
    Behaviors.setup { ctx =>
      ctx.log.info(s"Starting Account ${accountId.id}")
      EventSourcedBehavior(persistenceId, State.Uninitialized, commandHandler, eventHandler(historyLimit))
    }
}
