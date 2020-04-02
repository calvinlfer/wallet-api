package com.calvin.walletapi.dtos.responses

import io.circe.Encoder

object ErrorResponse {
  implicit val encoder: Encoder[ErrorResponse] =
    Encoder.forProduct1("message")(_.message)

  val noWallet: ErrorResponse            = ErrorResponse("No wallet with that ID exists")
  val walletAlreadyExists: ErrorResponse = ErrorResponse("Wallet with that ID already exists")
  val unexpected: ErrorResponse          = ErrorResponse("Unexpected error")
  val withdrawalTooSmall: ErrorResponse  = ErrorResponse("Your withdrawal amount is too small")
  val depositTooSmall: ErrorResponse     = ErrorResponse("Your deposit amount is too small")
  val unavailable: ErrorResponse         = ErrorResponse("Please try again later")
  val withdrewTooMuch: ErrorResponse     = ErrorResponse("Not enough balance to withdraw specified amount")
}
case class ErrorResponse(message: String)
