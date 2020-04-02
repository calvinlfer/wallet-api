package com.calvin.walletapi.repositories
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import zio.Task

object SlickFeeRepository {
  def make(config: DatabaseConfig[JdbcProfile]): FeeRepository =
    new SlickFeeRepository(config)
}

private class SlickFeeRepository(config: DatabaseConfig[JdbcProfile]) extends FeeRepository {
  import config._
  import config.profile.api._

  override def resume: Task[Option[Long]] = {
    val query = sql"SELECT sequence_number FROM processor".as[Long].headOption

    Task.fromFuture(_ => db.run(query))
  }

  override def save(f: FeeUpdate): Task[Unit] = {
    val update = sql"""
    INSERT INTO processor AS existing (sequence_number, amount, calculation_name)
    VALUES (${f.sequenceNumber}, ${f.feeAmount}, 'fee')
    ON CONFLICT (calculation_name) 
    DO UPDATE 
    SET amount = EXCLUDED.amount + existing.amount, sequence_number = EXCLUDED.sequence_number
    WHERE EXCLUDED.sequence_number > existing.sequence_number
    """.asUpdate

    Task.fromFuture(_ => db.run(update)).unit
  }

  override def amount: Task[FeeAmount] = {
    val query = sql"SELECT amount FROM processor WHERE calculation_name='fee'".as[Long].headOption

    Task
      .fromFuture(_ => db.run(query))
      .map(_.fold(ifEmpty = 0L)(identity))
      .map(FeeAmount)
  }
}
