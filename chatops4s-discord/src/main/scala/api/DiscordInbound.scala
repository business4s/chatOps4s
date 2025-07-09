package api

import cats.effect.{ExitCode, IO}
import cats.effect.unsafe.IORuntime
import io.circe.Json
import models.{Button, ButtonInteraction, InboundGateway, InteractionContext}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.json.circe.*
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

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
