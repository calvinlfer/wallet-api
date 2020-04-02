package com.calvin.walletapi.repositories

import zio.Task

case class FeeUpdate(feeAmount: Long, sequenceNumber: Long)
case class FeeAmount(amount: Long)

trait FeeRepository {
  def resume: Task[Option[Long]]

  def save(f: FeeUpdate): Task[Unit]

  def amount: Task[FeeAmount]
}
