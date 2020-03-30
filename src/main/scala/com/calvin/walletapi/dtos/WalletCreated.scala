package com.calvin.walletapi.dtos

import com.calvin.walletapi.domain.WalletId
import io.circe.Encoder

object WalletCreated {
  implicit val encoder: Encoder[WalletCreated] =
    Encoder.forProduct1("id")(_.id)
}
case class WalletCreated(id: WalletId)
