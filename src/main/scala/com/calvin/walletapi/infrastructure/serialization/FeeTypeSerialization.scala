package com.calvin.walletapi.infrastructure.serialization

import com.calvin.walletapi.domain.Fees
import com.calvin.walletapi.domain.Fees.FeeType
import com.fasterxml.jackson.core.{ JsonGenerator, JsonParser }
import com.fasterxml.jackson.databind.{ DeserializationContext, SerializerProvider }
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer

object FeeTypeSerialization {
  // FeeType events are persisted so we use Jackson to do this
  class FeeTypeJsonSerializer extends StdSerializer[Fees.FeeType](classOf[Fees.FeeType]) {
    override def serialize(value: Fees.FeeType, gen: JsonGenerator, provider: SerializerProvider): Unit = {
      val string = value match {
        case FeeType.Withdraw => "w"
        case FeeType.Deposit  => "d"
      }
      gen.writeString(string)
    }
  }

  class FeeTypeJsonDeserializer extends StdDeserializer[Fees.FeeType](classOf[Fees.FeeType]) {
    override def deserialize(p: JsonParser, ctxt: DeserializationContext): Fees.FeeType =
      p.getText match {
        case "w" => FeeType.Withdraw
        case "d" => FeeType.Deposit
      }
  }
}
