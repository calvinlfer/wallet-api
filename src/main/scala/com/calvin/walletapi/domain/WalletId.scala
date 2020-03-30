package com.calvin.walletapi.domain

import io.circe.{ Decoder, Encoder }

object WalletId {
  implicit val decoder: Decoder[WalletId] =
    Decoder[String].emap(validate)

  implicit val encoder: Encoder[WalletId] =
    Encoder[String].contramap(_.id)

  def validate(rawId: String): Either[String, WalletId] =
    if (rawId.forall(_.isLetterOrDigit)) Right(WalletId(rawId))
    else Left("Wallet ID must be alpha-numeric")
}
case class WalletId private (id: String) extends AnyVal
