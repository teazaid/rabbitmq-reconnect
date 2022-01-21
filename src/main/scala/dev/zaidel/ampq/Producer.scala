package dev.zaidel.ampq

trait Producer[Context[_], Entity] {
  def put(entity: Entity): Context[Unit]
}
