package dev.zaidel.serialization

import akka.util.ByteString
import cats.Id
import dev.zaidel.models.Message
import io.circe.syntax._

class MessageSerializer extends Serializer[Id, Message, ByteString] {
  override def serialize(from: Message): ByteString =
    ByteString(from.asJson.toString())
}
