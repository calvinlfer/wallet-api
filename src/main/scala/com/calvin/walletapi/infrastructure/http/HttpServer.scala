package com.calvin.walletapi.infrastructure.http

import akka.{ actor, Done }
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.util.{ Failure, Success }
import scala.concurrent.duration._

object HttpServer {
  def start(system: ActorSystem[_], routes: Route, port: Int): Unit = {
    import akka.actor.typed.scaladsl.adapter._
    implicit val classicSystem: actor.ActorSystem = system.toClassic
    val shutdown                                  = CoordinatedShutdown(classicSystem)
    import system.executionContext

    Http().bindAndHandle(routes, "localhost", port).onComplete {
      case Failure(exception) =>
        system.log.error("Failed to start HTTP server, shutting down", exception)
        system.terminate()

      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(
          "HTTP Server online at http://{}:{}/",
          address.getHostString,
          address.getPort
        )

        shutdown.addTask(
          CoordinatedShutdown.PhaseServiceRequestsDone,
          "http-walletserver-graceful-terminate"
        ) { () =>
          binding.terminate(10.seconds).map { _ =>
            system.log.info(
              "HTTP Server http://{}:{}/ graceful shutdown completed",
              address.getHostString,
              address.getPort
            )
            Done
          }
        }
    }
  }
}
