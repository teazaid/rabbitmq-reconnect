package dev.zaidel.ampq

trait Consumer[Context[_], Entity] {
  def onNext(entity: Entity): Context[Unit]
}

