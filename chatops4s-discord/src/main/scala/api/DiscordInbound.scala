package api

import api.Server.getClass
import cats.effect.IO
import com.typesafe.scalalogging.Logger
import models.{Button, ButtonInteraction, InboundGateway, InteractionContext}

import scala.collection.concurrent.TrieMap

class DiscordInbound(verbose: Boolean = false) extends InboundGateway {
  private final val logger = Logger(getClass.getName)
  val handlers: TrieMap[String, InteractionContext => IO[Unit]] = TrieMap.empty[String, InteractionContext => IO[Unit]]

  override def registerAction(handler: InteractionContext => IO[Unit]): ButtonInteraction = {
    if (verbose) logger.info("Registered action")
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
