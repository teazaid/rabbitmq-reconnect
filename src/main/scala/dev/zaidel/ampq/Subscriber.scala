package dev.zaidel.ampq

import dev.zaidel.serialization.Deserializer

import scala.util.Try

trait Subscriber[Context[_], RawEntity, Entity] {
  val consumer: Consumer[Context, Entity]
  val entityDeserializer: Deserializer[Try, RawEntity, Entity]
  val queueName: String

  def subscribe(): Context[Unit]

  def unsubscribe(): Context[Unit]
}
