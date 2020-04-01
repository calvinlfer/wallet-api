package com.calvin.walletapi.domain

import com.fasterxml.jackson.core.{ JsonGenerator, JsonParser }
import com.fasterxml.jackson.databind.{ DeserializationContext, SerializerProvider }
import com.fasterxml.jackson.databind.annotation.{ JsonDeserialize, JsonSerialize }
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import scala.collection.SortedMap

object Fees {
  @JsonSerialize(using = classOf[FeeTypeJsonSerializer])
  @JsonDeserialize(using = classOf[FeeTypeJsonDeserializer])
  sealed trait FeeType
  object FeeType {
    case object Withdraw extends FeeType
    case object Deposit  extends FeeType
  }

  case class FeeInfo(percentage: Double, fee: Long, amountMinusFee: Long)

  def calculateFee(feeType: FeeType)(existingBalance: Long, amountAdded: Long): FeeInfo = {
    val structure  = feeStructure(feeType)
    val percentage = structure.rangeFrom(existingBalance).head._2
    val fee        = math.floor(amountAdded * percentage).toLong
    FeeInfo(percentage, fee, amountAdded - fee)
  }

  private def feeStructure(feeType: FeeType): FeeStructure =
    feeType match {
      case FeeType.Withdraw => withdrawalFeeTiers
      case FeeType.Deposit  => depositFeeTiers
    }

  type FeeStructure = SortedMap[Long, Double]

  /**
   * Deposit fee structure
   * - keys correspond to the existing balance
   * - values correspond to the fee rate
   * <= 100000 cents gives you a 10% fee structure
   * <= 200000 cents gives you a 5% fee structure
   * ...
   * finally having a balance over 400000 cents gives you a 1% fee structure
   *
   * NOTE: You can make this more dynamic by retrieving this from a database or
   * storing this information in an event sourced actor that is allowed to change
   */
  private val depositFeeTiers = SortedMap(
    100000L       -> 0.10,
    200000L       -> 0.05,
    400000L       -> 0.02,
    Long.MaxValue -> 0.01
  )

  /**
   * Withdrawal fee structure
   * - keys correspond to the existing balance
   * - values correspond to the fee rate
   */
  private val withdrawalFeeTiers = SortedMap(
    100000L       -> 0.12,
    200000L       -> 0.06,
    400000L       -> 0.03,
    Long.MaxValue -> 0.01
  )

  class FeeTypeJsonSerializer extends StdSerializer[FeeType](classOf[FeeType]) {
    override def serialize(value: FeeType, gen: JsonGenerator, provider: SerializerProvider): Unit = {
      val string = value match {
        case FeeType.Withdraw => "w"
        case FeeType.Deposit  => "d"
      }
      gen.writeString(string)
    }
  }

  class FeeTypeJsonDeserializer extends StdDeserializer[FeeType](classOf[FeeType]) {
    override def deserialize(p: JsonParser, ctxt: DeserializationContext): FeeType =
      p.getText match {
        case "w" => FeeType.Withdraw
        case "d" => FeeType.Deposit
      }
  }
}
