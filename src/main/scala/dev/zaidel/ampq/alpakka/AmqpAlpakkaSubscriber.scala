package dev.zaidel.ampq.alpakka

import akka.actor.ActorSystem
import akka.stream.alpakka.amqp.scaladsl.AmqpSource
import akka.stream.alpakka.amqp.{AmqpConnectionProvider, NamedQueueSourceSettings, QueueDeclaration}
import akka.stream.scaladsl.{RestartSource, Sink}
import akka.stream.{Materializer, SharedKillSwitch}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import dev.zaidel.ampq.{Consumer, Subscriber}
import dev.zaidel.models.SubscriberConfig
import dev.zaidel.serialization.Deserializer

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try

class AmqpAlpakkaSubscriber[Entity](subscriberConfig: SubscriberConfig,
                                    amqpConnectionProvider: AmqpConnectionProvider,
                                    queueDeclaration: QueueDeclaration,
                                    killSwitch: SharedKillSwitch,
                                    val consumer: Consumer[Future, Entity],
                                    val entityDeserializer: Deserializer[Try, ByteString, Entity])
                                   (implicit system: ActorSystem, mat: Materializer)
  extends Subscriber[Future, ByteString, Entity] with LazyLogging {

  override val queueName: String = queueDeclaration.name

  private implicit val ex = system.dispatcher

  override def subscribe(): Future[Unit] =
    RestartSource.withBackoff(5.seconds, 10.seconds, 0.3, 20) { () =>
      AmqpSource.atMostOnceSource(
        NamedQueueSourceSettings(amqpConnectionProvider, queueName)
          .withDeclaration(queueDeclaration),
        bufferSize = subscriberConfig.bufferSize)

        .mapAsync(subscriberConfig.parallelism) { message =>
          for {
            entity <- Future.fromTry(entityDeserializer.deserialize(message.bytes))
            _ <- consumer.onNext(entity)
          } yield ()
        }
    }
      .via(killSwitch.flow)
      .runWith(Sink.ignore).map(_ => ())

  override def unsubscribe(): Future[Unit] =
    Future.successful(killSwitch.shutdown())
}