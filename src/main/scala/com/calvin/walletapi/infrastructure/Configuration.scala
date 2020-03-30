package com.calvin.walletapi.infrastructure

import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration.FiniteDuration

object Configuration {
  def load: Result[Configuration] = ConfigSource.default.load[Configuration]
}

case class HttpConfig(port: Int)
case class WalletServiceConfig(timeout: FiniteDuration, historyLimit: Int)
case class SlickConfig(db: DatabaseConfig)
case class DatabaseConfig(url: String, user: String, password: String)
case class Configuration(http: HttpConfig, walletService: WalletServiceConfig, slick: SlickConfig)
