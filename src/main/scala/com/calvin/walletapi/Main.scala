package com.calvin.walletapi

import akka.actor.typed.scaladsl.adapter._
import com.calvin.walletapi.actors.Guardian

object Main {
  def main(args: Array[String]): Unit = {
    // This is done for the sake of the SBR
    val classic = akka.actor.ActorSystem("WalletAPI")
    classic.spawn[Nothing](Guardian(), "Guardian")
  }

  /**
 * // Without the SBR
 * def main(args: Array[String]): Unit =
 * ActorSystem[Nothing](Guardian(), "WalletAPI")
 */
}
