package com.calvin.walletapi.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.ClusterSingleton
import com.calvin.walletapi.infrastructure.http.{ WalletRoutes, WalletServer }
import com.calvin.walletapi.infrastructure.{ Configuration, FlywayMigrations }
import com.calvin.walletapi.services.ActorWalletService

object Guardian {
  def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    val config  = Configuration.load.getOrElse(sys.error("Failed to load configuration"))
    val runtime = zio.Runtime.default

    // run migrations
    FlywayMigrations.runMigrations(config.slick.db)

    // wallet
    val walletService =
      ActorWalletService.create(ctx.system, config.walletService.timeout, config.walletService.historyLimit)

    // http
    if (config.http.enabled) {
      val routes = WalletRoutes.create(walletService, ctx.log, runtime)
      WalletServer.start(ctx.system, routes, config.http.port)
    } else ()

    // fee persistence query
    ClusterSingleton(ctx.system).init(FeeProcessor(runtime))

    Behaviors.empty
  }
}
