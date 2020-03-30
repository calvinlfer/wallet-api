package com.calvin.walletapi.dtos

import com.calvin.walletapi.domain.WalletId
import io.circe.Encoder

object Deposited {
  implicit val encoder: Encoder[Deposited] =
    Encoder.forProduct2("id", "amountDeposited")(d => (d.id, d.amountDeposited))
}
case class Deposited(id: WalletId, amountDeposited: Long)
