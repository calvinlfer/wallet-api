package com.calvin.walletapi.dtos.responses

import com.calvin.walletapi.domain.WalletId
import io.circe.Encoder

object Withdrawn {
  implicit val encoder: Encoder[Withdrawn] =
    Encoder.forProduct3("id", "amountWithdrawn", "feeAppliedToBalance")(w =>
      (w.id, w.amountWithdrawn, w.feeAppliedToBalance)
    )
}
case class Withdrawn(id: WalletId, amountWithdrawn: Long, feeAppliedToBalance: Long)
