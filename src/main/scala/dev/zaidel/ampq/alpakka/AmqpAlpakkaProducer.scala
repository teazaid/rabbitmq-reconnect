package dev.zaidel.ampq.alpakka

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.alpakka.amqp.scaladsl.AmqpSink
import akka.stream.alpakka.amqp.{AmqpConnectionProvider, AmqpWriteSettings, QueueDeclaration, WriteMessage}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import cats.Id
import com.rabbitmq.client.AMQP.BasicProperties
import com.typesafe.scalalogging.LazyLogging
import dev.zaidel.ampq.Producer
import dev.zaidel.serialization.Serializer
import scala.concurrent.{ExecutionContext, Future}

class AmqpAlpakkaProducer[Entity](queueDeclaration: QueueDeclaration,
                                  amqpConnectionProvider: AmqpConnectionProvider,
                                  serializer: Serializer[Id, Entity, ByteString]
                                 )(implicit system: ActorSystem,
                                   mat: Materializer,
                                   ex: ExecutionContext) extends Producer[Future, Entity] with LazyLogging {

  private val amqpSink: Sink[WriteMessage, Future[Done]] =
    AmqpSink(
      AmqpWriteSettings(amqpConnectionProvider)
        .withDeclaration(queueDeclaration)
        .withRoutingKey(queueDeclaration.name)
    )

  override def put(entity: Entity): Future[Unit] = {
    val propBuilder = new BasicProperties.Builder
    propBuilder.deliveryMode(2)
    val payload = serializer.serialize(entity)
    val messageToSend = WriteMessage(payload).withProperties(propBuilder.build())
    Source.single(messageToSend).runWith(amqpSink).map(_ => ())
  }
}