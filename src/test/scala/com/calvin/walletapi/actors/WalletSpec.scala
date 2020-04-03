package com.calvin.walletapi.actors

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.Behavior
import akka.persistence.typed.PersistenceId
import com.calvin.walletapi.actors.Wallet.{ Command, Reply }
import com.calvin.walletapi.domain.WalletId
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class WalletSpec extends ScalaTestWithActorTestKit(s"""
  akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
  akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
  akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"  
  """) with AnyFlatSpecLike with Matchers {

  def exampleWallet(id: String): Behavior[Command[Reply]] =
    Wallet.create(10)(WalletId(id), PersistenceId("Wallet", id))

  "Wallet" should "not exist when messaged for the first time" in {
    val probe = createTestProbe[Reply]
    val ref   = spawn(exampleWallet("example"))
    ref ! Command.ViewBalance(probe.ref)
    probe.expectMessage(Reply.DoesNotExist)
  }

  it should "have a balance of 0 when an account is opened" in {
    val probe = createTestProbe[Reply]
    val ref   = spawn(exampleWallet("example"))
    ref ! Command.Open(probe.ref)
    probe.expectMessage(Reply.Opened(false))

    ref ! Command.ViewBalance(probe.ref)
    probe.expectMessage(Reply.CurrentBalance(0L))
  }

  it should "have the fee applied to your deposit and withdrawal" in {
    val probe = createTestProbe[Reply]
    val ref   = spawn(exampleWallet("example"))
    ref ! Command.Deposit(Wallet.MinTransactionAmount * 2)(probe.ref)
    val depositResult = probe.expectMessageType[Reply.SuccessfulDeposit]
    depositResult.fee should be > 0L

    ref ! Command.Withdraw(Wallet.MinTransactionAmount)(probe.ref)
    val withdrawResult = probe.expectMessageType[Reply.SuccessfulWithdrawal]
    withdrawResult.fee should be > 0L
  }

  it should "prevent you from trying to deposit or withdraw less than the minimum amount" in {
    val probe = createTestProbe[Reply]
    val ref   = spawn(exampleWallet("example"))
    // NOTE: We use the same account so we don't need to open it again

    ref ! Command.Deposit(Wallet.MinTransactionAmount - 1)(probe.ref)
    probe.expectMessage(Reply.InsufficientDepositAmount)

    ref ! Command.Withdraw(Wallet.MinTransactionAmount - 1)(probe.ref)
    probe.expectMessage(Reply.InsufficientWithdrawalAmount)
  }

  it should "prevent you from trying to withdraw more than what you have" in {
    val probe = createTestProbe[Reply]
    val ref   = spawn(exampleWallet("example"))
    // NOTE: We use the same account so we don't need to open it again

    ref ! Command.Deposit(Wallet.MinTransactionAmount)(probe.ref)
    probe.expectMessageType[Reply.SuccessfulDeposit]

    ref ! Command.Withdraw(Wallet.MinTransactionAmount * 1000)(probe.ref)
    probe.expectMessage(Reply.InsufficientFunds)
  }
}
