package com.calvin.walletapi.infrastructure

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.calvin.walletapi.infrastructure.http.{ WalletRoutes, WalletServer }
import com.calvin.walletapi.services.ActorWalletService

object Guardian {
  def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    val config = Configuration.load.getOrElse(sys.error("Failed to load configuration"))
    FlywayMigrations.runMigrations(config.slick.db)
    val runtime = zio.Runtime.default
    val walletService =
      ActorWalletService.create(ctx.system, config.walletService.timeout, config.walletService.historyLimit)
    val routes = WalletRoutes.create(walletService, ctx.log, runtime)

    WalletServer.start(ctx.system, routes, config.http.port)
    Behaviors.empty
  }
}
