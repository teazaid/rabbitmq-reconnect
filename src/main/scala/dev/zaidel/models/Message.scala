package dev.zaidel.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

final case class Message(messageId: Long)

object Message {
  implicit val MessageDecoder: Decoder[Message] = deriveDecoder[Message]
  implicit val MessageEncoder: Encoder[Message] = deriveEncoder[Message]
}