package com.calvin.walletapi

import akka.actor.typed.ActorSystem
import com.calvin.walletapi.infrastructure.Guardian

object Main {
  def main(args: Array[String]): Unit =
    ActorSystem[Nothing](Guardian(), "WalletAPI")
}
