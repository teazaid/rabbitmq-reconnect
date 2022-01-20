package dev.zaidel.serialization

trait Serializer[Context[_], From, To] {
  def serialize(from: From): Context[To]
}
