package com.calvin.walletapi.dtos

import com.calvin.walletapi.domain.WalletId
import io.circe.Encoder

object Balance {
  implicit val encoder: Encoder[Balance] =
    Encoder.forProduct2("id", "amount")(b => (b.id, b.amount))
}
case class Balance(id: WalletId, amount: Long)
