package com.calvin.walletapi.dtos

import io.circe.Encoder

object ErrorResponse {
  implicit val encoder: Encoder[ErrorResponse] =
    Encoder.forProduct1("message")(_.message)
}
case class ErrorResponse(message: String)
