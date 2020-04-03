package com.calvin.walletapi.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import Fees._
import org.scalacheck.{ Gen, Prop }
import org.scalatestplus.scalacheck.Checkers
import org.scalacheck.Prop._

import scala.jdk.CollectionConverters._

trait Laws extends Matchers {
  val tier1Gen: Gen[Long] = Gen.chooseNum[Long](100L, 100000L)
  val tier2Gen: Gen[Long] = Gen.chooseNum[Long](100001L, 200000L)
  val tier3Gen: Gen[Long] = Gen.chooseNum[Long](200001L, 400000L)
  val tier4Gen: Gen[Long] = Gen.chooseNum[Long](400001L, Long.MaxValue)

  val lowerFeesAtHigherTiers: Prop = forAll(Gen.sequence(List(tier1Gen, tier2Gen, tier3Gen, tier4Gen))) {
    lowToHighBalanceTiers =>
      val depositAmount = 1000L
      lowToHighBalanceTiers.asScala
        .map(balance => calculateFee(FeeType.Deposit)(balance, depositAmount))
        .map(_.fee)
        .reduce { (lowerTierFee, higherTierFee) =>
          lowerTierFee should be > higherTierFee
          higherTierFee
        }
      true
  }
}

class FeesSpec extends AnyFlatSpec with Checkers with Laws {
  check(lowerFeesAtHigherTiers :| "Fees are lower at higher tiers")
}
