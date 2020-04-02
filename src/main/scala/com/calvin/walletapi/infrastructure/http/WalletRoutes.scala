package com.calvin.walletapi.infrastructure.http

import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import com.calvin.walletapi.domain.WalletId
import com.calvin.walletapi.dtos.requests._
import com.calvin.walletapi.dtos.responses._
import com.calvin.walletapi.services.{ Error, WalletService }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import org.slf4j.Logger
import zio.Task

import scala.util.{ Failure, Success }

object WalletRoutes {
  def create(walletService: WalletService, log: Logger, runtime: zio.Runtime[Any]): Route =
    new WalletRoutes(walletService, log, runtime).routes
}

private class WalletRoutes(walletService: WalletService, log: Logger, runtime: zio.Runtime[Any])
    extends FailFastCirceSupport {

  val routes: Route = pathPrefix("wallets")(createWallet ~ balance ~ deposit ~ withdraw ~ history)

  private val WalletSegment = Segment.flatMap(rawId => WalletId.validate(rawId).toOption)

  implicit val ess: EntityStreamingSupport = EntityStreamingSupport.json()

  private def createWallet: Route =
    post {
      pathEndOrSingleSlash {
        entity(as[CreateWallet]) { createWallet =>
          completeTask(walletService.create(createWallet.id))("create-wallet")(
            fail = _ => BadRequest -> ErrorResponse("Wallet with that ID already exists")
          )(pass = _ => Created -> WalletCreated(createWallet.id))
        }
      }
    }

  private def balance: Route =
    get {
      pathPrefix(WalletSegment / "balance") { walletId =>
        completeTask(walletService.balance(walletId))("get-balance")(
          fail = _ => BadRequest -> ErrorResponse("No wallet with that ID exists")
        )(pass = b => OK -> Balance(walletId, b.amount))
      }
    }

  private def deposit: Route =
    post {
      pathPrefix(WalletSegment)(walletId =>
        entity(as[Deposit]) { d =>
          path("deposit") {
            completeTask(walletService.deposit(walletId, d.amount))("deposit")(
              fail = {
                case Error.WalletNotCreated =>
                  BadRequest -> ErrorResponse("No wallet with that ID exists")
                case Error.InsufficientAmount =>
                  BadRequest -> ErrorResponse("Your deposit amount is too small")
                case other =>
                  log.error(s"Deposits should never result in $other")
                  BadRequest -> ErrorResponse("Unexpected error")
              }
            )(pass = b => OK -> Deposited(walletId, b.amount))
          } ~
            path("depositFee") {
              completeTask(walletService.queryDepositFee(walletId, d.amount))("depositFee")(
                fail = _ => BadRequest -> ErrorResponse("No wallet with that ID exists")
              )(pass = b => OK -> FeeBreakdown(b.percentage, b.fee))
            }
        }
      )
    }

  private def withdraw: Route =
    post {
      pathPrefix(WalletSegment)(walletId =>
        entity(as[Withdraw]) { d =>
          path("withdraw") {
            completeTask(walletService.withdraw(walletId, d.amount))("withdraw")(
              fail = {
                case Error.WalletNotCreated =>
                  BadRequest -> ErrorResponse("No wallet with that ID exists")

                case Error.InsufficientAmount =>
                  BadRequest -> ErrorResponse("Your withdrawal amount is too small")

                case Error.WithdrawOverBalance =>
                  BadRequest -> ErrorResponse("Not enough balance to withdraw specified amount")

                case other =>
                  log.error(s"Deposits should never result in $other")
                  BadRequest -> ErrorResponse("Unexpected error")
              }
            )(pass = b => OK -> Deposited(walletId, b.amount))
          } ~
            path("withdrawFee") {
              completeTask(walletService.queryWithdrawFee(walletId, d.amount))("withdrawFee")(
                fail = _ => BadRequest -> ErrorResponse("No wallet with that ID exists")
              )(pass = b => OK -> FeeBreakdown(b.percentage, b.fee))
            }
        }
      )
    }

  private def history: Route = {
    import io.circe.generic.auto._

    get {
      pathPrefix(WalletSegment / "history") { w =>
        path("immediate") {
          completeTask(walletService.immediateHistory(w))("immediate-history")(fail =
            _ => BadRequest -> ErrorResponse("No wallet with that ID exists")
          )(pass = history => OK -> history)
        } ~ path("all") {
          completeTaskSource(walletService.allHistory(w))("all-history")(fail =
            _ => BadRequest -> ErrorResponse("No wallet with that ID exists")
          )
        }
      }
    }
  }

  private def completeTask[A, A1: Encoder, B, B1: Encoder](
    task: Task[Either[A, B]]
  )(requestName: String)(fail: A => (StatusCode, A1))(pass: B => (StatusCode, B1)): Route = {
    val runningTask = runtime.unsafeRunToFuture(task)
    onComplete(runningTask) {
      case Failure(exception) =>
        log.error(s"Failed to execute $requestName", exception)
        complete(ServiceUnavailable -> ErrorResponse("Please try again later"))

      case Success(Left(a)) =>
        complete(fail(a))

      case Success(Right(b)) =>
        complete(pass(b))
    }
  }

  private def completeTaskSource[A, A1: Encoder, B: Encoder](
    task: Task[Either[A, Source[B, _]]]
  )(requestName: String)(fail: A => (StatusCode, A1)): Route = {
    val runningTask = runtime.unsafeRunToFuture(task)
    onComplete(runningTask) {
      case Failure(exception) =>
        log.error(s"Failed to execute $requestName", exception)
        complete(ServiceUnavailable -> ErrorResponse("Please try again later"))

      case Success(Left(a)) =>
        complete(fail(a))

      case Success(Right(b)) =>
        complete(b)
    }
  }
}
