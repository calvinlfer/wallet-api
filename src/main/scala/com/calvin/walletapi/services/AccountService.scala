package com.calvin.walletapi.services

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.calvin.walletapi.actors.Account._
import com.calvin.walletapi.services.Response._
import com.calvin.walletapi.services.ErrorResponse._
import zio.Task

sealed trait ErrorResponse
object ErrorResponse {
  type AccountNotCreated = AccountNotCreated.type
  type AlreadyCreated    = AlreadyCreated.type

  case object AlreadyCreated    extends ErrorResponse
  case object AccountNotCreated extends ErrorResponse
}

sealed trait Response
object Response {
  type SuccessfulCreation = SuccessfulCreation.type

  case object SuccessfulCreation                extends Response
  case class SuccessfulDeposit(amount: Long)    extends Response
  case class SuccessfulWithdrawal(amount: Long) extends Response
  case class History(events: List[Event])       extends Response
}

trait AccountService {
  def create(accountId: AccountId): Task[Either[AlreadyCreated, SuccessfulCreation]]

  def deposit(
    accountId: AccountId,
    amount: Long
  ): Task[Either[AccountNotCreated, SuccessfulDeposit]]

  def withdraw(
    accountId: AccountId,
    amount: Long
  ): Task[Either[ErrorResponse, SuccessfulWithdrawal]]

  def immediateHistory(accountId: AccountId): Task[Either[AccountNotCreated, History]]

  def allHistory(accountId: AccountId): Task[Either[AccountNotCreated, Source[Event, NotUsed]]]
}
