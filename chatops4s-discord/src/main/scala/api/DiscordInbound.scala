package api

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import models.{Button, ButtonInteraction, InboundGateway, InteractionContext}

import scala.collection.concurrent.TrieMap

class DiscordInbound extends InboundGateway, StrictLogging {
  val handlers: TrieMap[String, InteractionContext => IO[Unit]] = TrieMap.empty[String, InteractionContext => IO[Unit]]

  override def registerAction(handler: InteractionContext => IO[Unit]): ButtonInteraction = {
    logger.info("Registered action")
    val id = java.util.UUID.randomUUID().toString.take(n = 8)
    handlers += id -> handler
    (label: String) => {
      Button(
        label = label,
        value = id
      )
    }
  }
}
