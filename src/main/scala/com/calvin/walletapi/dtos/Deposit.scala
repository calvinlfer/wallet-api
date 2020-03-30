package com.calvin.walletapi.dtos

import io.circe.Decoder

object Deposit {
  implicit val decoder: Decoder[Deposit] =
    Decoder
      .forProduct1("amount")(Deposit.apply)
      .emap(d =>
        Either.cond(
          test = d.amount > 0,
          right = d,
          left = "Deposit amount must be positive and greater than 0"
        )
      )
}
case class Deposit(amount: Long)
