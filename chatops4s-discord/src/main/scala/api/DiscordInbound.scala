package api

import cats.effect.{ExitCode, IO}
import cats.effect.unsafe.IORuntime
import io.circe.Json
import models.{Button, ButtonInteraction, InboundGateway, InteractionContext}

import scala.collection.concurrent.TrieMap

class DiscordInbound extends InboundGateway {
  val handlers: TrieMap[String, InteractionContext => IO[Unit]] = TrieMap.empty[String, InteractionContext => IO[Unit]]

  override def registerAction(handler: InteractionContext => IO[Unit]): ButtonInteraction = {
    val id = java.util.UUID.randomUUID().toString.take(n = 8)
    handlers += id -> handler
    
    class ButtonInteractionImpl extends ButtonInteraction {
      override def render(label: String): Button = {
        Button(
          label = label,
          value = id
        )
      }
    }
    new ButtonInteractionImpl()
  }
}
