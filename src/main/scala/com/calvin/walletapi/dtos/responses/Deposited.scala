package com.calvin.walletapi.dtos.responses

import com.calvin.walletapi.domain.WalletId
import io.circe.Encoder

object Deposited {
  implicit val encoder: Encoder[Deposited] =
    Encoder.forProduct3("id", "amountDeposited", "feeAppliedToBalance")(d =>
      (d.id, d.amountDeposited, d.feeAppliedToBalance)
    )
}
case class Deposited(id: WalletId, amountDeposited: Long, feeAppliedToBalance: Long)
