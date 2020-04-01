package com.calvin.walletapi.dtos.requests

import io.circe.Decoder

object Withdraw {
  implicit val decoder: Decoder[Withdraw] =
    Decoder
      .forProduct1("amount")(Withdraw.apply)
      .emap(d =>
        Either.cond(
          test = d.amount > 0,
          right = d,
          left = "Withdraw amount must be positive and greater than 0"
        )
      )
}
case class Withdraw(amount: Long)
