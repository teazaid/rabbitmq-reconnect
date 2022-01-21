package dev.zaidel.serialization

import akka.util.ByteString
import cats.MonadError
import dev.zaidel.models.Message
import io.circe.parser.parse

class MessageDeserializer[F[_]]()(implicit me: MonadError[F, Throwable]) extends Deserializer[F, ByteString, Message] {
  override def deserialize(from: ByteString): F[Message] =
    me.fromEither(for {
      json <- parse(from.utf8String)
      message <- json.as[Message]
    } yield message)
}
