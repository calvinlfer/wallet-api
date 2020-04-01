package com.calvin.walletapi.dtos.requests

import com.calvin.walletapi.domain.WalletId
import io.circe.Decoder

object CreateWallet {
  implicit val createWalletDecoder: Decoder[CreateWallet] =
    Decoder.forProduct1("id")(CreateWallet.apply)
}

case class CreateWallet(id: WalletId)
