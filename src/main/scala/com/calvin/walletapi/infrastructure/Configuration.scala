package com.calvin.walletapi.infrastructure

import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration.FiniteDuration

object Configuration {
  def load: Result[Configuration] = ConfigSource.default.load[Configuration]
}

case class HttpConfig(port: Int, enabled: Boolean)
case class WalletServiceConfig(timeout: FiniteDuration, historyLimit: Int)
case class SlickConfig(db: DbConfig)
case class DbConfig(url: String, user: String, password: String)
case class Configuration(http: HttpConfig, walletService: WalletServiceConfig, slick: SlickConfig)
