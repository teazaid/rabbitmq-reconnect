package dev.zaidel.serialization

trait Deserializer[Context[_], From, To] {
  def deserialize(from: From): Context[To]
}
