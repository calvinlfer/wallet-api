package com.calvin.walletapi

import com.calvin.walletapi.infrastructure.Guardian
import akka.actor.typed.scaladsl.adapter._

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
