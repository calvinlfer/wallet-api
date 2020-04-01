package com.calvin.walletapi.dtos.responses

import io.circe.Encoder

object FeeBreakdown {
  implicit val encoder: Encoder[FeeBreakdown] =
    Encoder.forProduct2("percentage", "fee")(b => (b.percentage, b.fee))
}
case class FeeBreakdown(percentage: Double, fee: Long)
