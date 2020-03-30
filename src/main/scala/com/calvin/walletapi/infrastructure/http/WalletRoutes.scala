package com.calvin.walletapi.infrastructure.http

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.calvin.walletapi.domain.WalletId
import com.calvin.walletapi.dtos._
import com.calvin.walletapi.services.WalletService
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

  val routes: Route = createWallet ~ getBalance ~ deposit

  private val WalletSegment = Segment.flatMap(rawId => WalletId.validate(rawId).toOption)

  private def createWallet: Route =
    post {
      pathPrefix("wallets") {
        pathEndOrSingleSlash {
          entity(as[CreateWallet]) { createWallet =>
            completeTask(walletService.create(createWallet.id))("create-wallet")(
              fail = _ => BadRequest -> ErrorResponse("Wallet with that ID already exists")
            )(pass = _ => Created -> WalletCreated(createWallet.id))
          }
        }
      }
    }

  private def getBalance: Route =
    get {
      pathPrefix("wallets" / WalletSegment / "balance") { walletId =>
        completeTask(walletService.balance(walletId))("get-balance")(
          fail = _ => BadRequest -> ErrorResponse("No wallet with that ID exists")
        )(pass = b => OK -> Balance(walletId, b.amount))
      }
    }

  private def deposit: Route =
    post {
      pathPrefix("wallets" / WalletSegment / "deposit")(walletId =>
        entity(as[Deposit]) { d =>
          completeTask(walletService.deposit(walletId, d.amount))("deposit")(
            fail = _ => BadRequest -> ErrorResponse("No wallet with that ID exists")
          )(pass = b => OK -> Deposited(walletId, b.amount))
        }
      )
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
}
