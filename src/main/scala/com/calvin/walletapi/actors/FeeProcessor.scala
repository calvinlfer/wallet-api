package com.calvin.walletapi.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, SupervisorStrategy }
import akka.cluster.typed.SingletonActor
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{ EventEnvelope, PersistenceQuery, Sequence }
import akka.stream.scaladsl.{ Keep, RunnableGraph, Sink }
import akka.stream.{ KillSwitch, KillSwitches, Materializer, UniqueKillSwitch }
import com.calvin.walletapi.actors.Wallet.Event.FeeSubtracted
import com.calvin.walletapi.repositories.{ FeeRepository, FeeUpdate, SlickFeeRepository }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import zio.Task

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * The FreeProcessor is responsible for populating the processor table: 'fee' row
 * This uses a persistence query to read from the journal whose events are tagged with 'fee'
 */
object FeeProcessor {
  private[FeeProcessor] sealed trait Protocol
  private[FeeProcessor] object Protocol {
    case object SearchingForOffset extends Protocol
    case class Begin(offset: Long) extends Protocol
    case object BeginStop          extends Protocol
    case object FinishedStop       extends Protocol
  }

  private def updateGraph(
    update: FeeUpdate => Task[Unit],
    offset: Long,
    journal: JdbcReadJournal,
    runtime: zio.Runtime[Any]
  ): RunnableGraph[UniqueKillSwitch] =
    journal
      .eventsByTag("fee", offset)
      .collect {
        case EventEnvelope(Sequence(offset), _, _, f: FeeSubtracted) =>
          (offset, f)
      }
      .map { case (o, e) => FeeUpdate(e.feeAmount, o) }
      .map(update)
      .mapAsync(1)(runtime.unsafeRunToFuture)
      .viaMat(KillSwitches.single)(Keep.right)
      .to(Sink.ignore)

  private def start(runtime: zio.Runtime[Any]): Behavior[Protocol] = Behaviors.setup { ctx =>
    implicit val ec: ExecutionContext = ctx.executionContext
    implicit val mat: Materializer    = Materializer(ctx)

    val dbConfig            = DatabaseConfig.forConfig[JdbcProfile]("slick")
    val repo: FeeRepository = SlickFeeRepository.make(dbConfig)
    val journal             = PersistenceQuery(ctx.system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    ctx.self ! Protocol.SearchingForOffset

    Behaviors.receiveMessage {
      case Protocol.SearchingForOffset =>
        ctx.log.debug("Retrieving offset for fee query")
        runtime
          .unsafeRunToFuture(repo.resume)
          .map(_.fold(0L)(identity))
          .foreach(ctx.self ! Protocol.Begin(_))
        Behaviors.same

      case Protocol.Begin(offset) =>
        ctx.log.debug("Offset found, beginning fee query")
        val switch = updateGraph(repo.save, offset, journal, runtime).run()
        updating(switch, dbConfig)

      case Protocol.BeginStop =>
        ctx.log.debug("Stop message retrieved before fee query has begun")
        dbConfig.db.shutdown.foreach(_ => ctx.self ! Protocol.FinishedStop)
        stopping

      case Protocol.FinishedStop =>
        ctx.log.warn("Fee query has received FinishedStop prematurely")
        Behaviors.ignore
    }
  }

  private def updating(k: KillSwitch, dbConfig: DatabaseConfig[JdbcProfile]): Behavior[Protocol] = Behaviors.setup {
    ctx =>
      implicit val ec: ExecutionContext = ctx.executionContext

      Behaviors.receiveMessage {
        case Protocol.SearchingForOffset =>
          Behaviors.ignore

        case Protocol.Begin(_) =>
          Behaviors.ignore

        case Protocol.BeginStop =>
          ctx.log.info("Fee Query was asked to shut down")
          k.shutdown()
          dbConfig.db.shutdown.foreach(_ => ctx.self ! Protocol.FinishedStop)
          stopping

        case Protocol.FinishedStop =>
          Behaviors.ignore
      }
  }

  private def stopping: Behavior[Protocol] = Behaviors.receiveMessage {
    case Protocol.SearchingForOffset =>
      Behaviors.ignore

    case Protocol.Begin(_) =>
      Behaviors.ignore

    case Protocol.BeginStop =>
      Behaviors.ignore

    case Protocol.FinishedStop =>
      Behaviors.stopped
  }

  def apply(runtime: zio.Runtime[Any]): SingletonActor[Protocol] =
    SingletonActor(
      Behaviors
        .supervise(start(runtime))
        .onFailure[Exception](
          SupervisorStrategy.restartWithBackoff(minBackoff = 1.second, maxBackoff = 30.seconds, randomFactor = 0.1)
        ),
      "GlobalFeeProcessor"
    ).withStopMessage(Protocol.BeginStop)

}
