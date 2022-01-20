package dev.zaidel.ampq.alpakka

import akka.Done
import akka.actor.ActorSystem
import akka.stream.alpakka.amqp.{AmqpCredentials, AmqpDetailsConnectionProvider, QueueDeclaration}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{KillSwitches, Materializer}
import cats.implicits._
import dev.zaidel.ampq.Consumer
import dev.zaidel.models.{Message, SubscriberConfig}
import dev.zaidel.serialization.{MessageDeserializer, MessageSerializer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.testcontainers.containers.RabbitMQContainer

import java.util.Collections
import java.util.concurrent.atomic.LongAdder
import scala.concurrent.{Future, Promise}
import scala.util.Try

class AmqpAlpakkaSubscriberIT extends AsyncWordSpec with BeforeAndAfterAll with Matchers {
  private val deserializer = new MessageDeserializer[Try]()
  private val serializer = new MessageSerializer()
  private val amqpCredentials = AmqpCredentials("guest", "guest")
  private val queueName = "message-queue"

  private val amountOfMessagesToSend = 100L

  private val queueDeclaration = QueueDeclaration(queueName)
    .withDurable(true)
    .withAutoDelete(false)

  private val subscriberConfig = SubscriberConfig(10, 1)

  private implicit val actorSystem = ActorSystem("testing")
  private implicit val materializer = Materializer

  private val sharedKillSwitch = KillSwitches.shared("message-consumer-kill-switch")

  private val container = new RabbitMQContainer("rabbitmq:3.9.11-management-alpine") {
    def addFixedPort(hostPort: Int, containerPort: Int): Unit =
      super.addFixedExposedPort(hostPort, containerPort)
  }

  private val AMQP_PORT = 5672

  override protected def beforeAll(): Unit = {
    container
      .withQueue(queueName, false, true, Collections.emptyMap())
      .withVhost("/")
      .withCreateContainerCmdModifier(cmd => cmd.withHostName("testing-rabbit"))
      .withFileSystemBind("./data", "/var/lib/rabbitmq/")

    container.addFixedPort(AMQP_PORT, AMQP_PORT)

    container.start()
  }

  override protected def afterAll(): Unit = {
    actorSystem.terminate()
    container.stop()
  }

  s"produce ${amountOfMessagesToSend} messages with subscriber restart" in {
    // Promise which which will completed when the last published message is consumed
    val processedLastMessage = Promise[Unit]
    val consumer = new Consumer[Future, Message] {
      val consumedMessageCount = new LongAdder()

      override def onNext(entity: Message): Future[Unit] = {
        consumedMessageCount.increment()
        info(s"Consuming message number: ${consumedMessageCount.longValue()}")

        if (amqpContainerReadyForRestart()) {
          restartRabbitMQContainer()
        }

        if (processingTheLastMessage(entity)) {
          processedLastMessage.success()
          sharedKillSwitch.shutdown()
        }

        Future.unit
      }

      private def amqpContainerReadyForRestart(): Boolean = consumedMessageCount.longValue() == 11

      private def restartRabbitMQContainer(): Unit = {
        info("Restarting RabbitMQ container:")
        // block current thread with waitFor,
        // otherwise there is a chance that to process all the messages before container is restarted.
        Runtime.getRuntime.exec(s"docker restart ${container.getContainerId}").waitFor()
        ()
      }

      private def processingTheLastMessage(entity: Message): Boolean = {
        entity.messageId == amountOfMessagesToSend
      }
    }

    val connectionProvider = AmqpDetailsConnectionProvider("localhost", container.getAmqpPort)
      .withCredentials(amqpCredentials)

    val amqpAlpakkaProducer = new AmqpAlpakkaProducer[Message](queueDeclaration, connectionProvider, serializer)

    val amqpSubscriber = new AmqpAlpakkaSubscriber[Message](subscriberConfig,
      connectionProvider,
      queueDeclaration,
      sharedKillSwitch,
      consumer,
      deserializer)

    for {
      _ <- populateQueue(amqpAlpakkaProducer)
      _ = info("Queue was populated")
      _ <- amqpSubscriber.subscribe()
      _ <- processedLastMessage.future
    } yield succeed
  }

  private def populateQueue(amqpAlpakkaProducer: AmqpAlpakkaProducer[Message]): Future[Done] =
    Source.fromIterator(() => List.range(1, amountOfMessagesToSend + 1).iterator)
      .map(Message(_))
      .mapAsync(1)(amqpAlpakkaProducer.put)
      .runWith(Sink.ignore)
}
