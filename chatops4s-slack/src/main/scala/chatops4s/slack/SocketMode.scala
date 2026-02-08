package chatops4s.slack

import cats.effect.kernel.{Async, Temporal}
import cats.syntax.all.*
import chatops4s.slack.api.SlackApi
import io.circe.parser
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.ws.async.*

import scala.concurrent.duration.*

import SlackModels.*

private[slack] object SocketMode {

  def runLoop[F[_]: Async](
      appToken: String,
      backend: WebSocketBackend[F],
      onInteraction: InteractionPayload => F[Unit],
      onSlashCommand: SlashCommandPayload => F[Unit],
  ): F[Unit] = {
    val loop: F[Unit] = for {
      url <- openSocketUrl(appToken, backend)
      _   <- connectAndHandle(url, backend, onInteraction, onSlashCommand)
    } yield ()

    loop.handleErrorWith { _ =>
      Temporal[F].sleep(2.seconds) >> runLoop(appToken, backend, onInteraction, onSlashCommand)
    }
  }

  private def openSocketUrl[F[_]: Async](
      appToken: String,
      backend: Backend[F],
  ): F[String] = {
    SlackApi.apps.connectionsOpen(backend, appToken).map(_.okOrThrow.url)
  }

  private def connectAndHandle[F[_]: Async](
      url: String,
      backend: WebSocketBackend[F],
      onInteraction: InteractionPayload => F[Unit],
      onSlashCommand: SlashCommandPayload => F[Unit],
  ): F[Unit] = {
    basicRequest
      .get(uri"$url")
      .response(asWebSocket[F, Unit] { ws =>
        def loop: F[Unit] =
          ws.receiveText().flatMap { text =>
            parser.decode[SocketEnvelope](text) match {
              case Right(envelope) =>
                val ack = ws.sendText(SocketAck(envelope.envelope_id).asJson.noSpaces)
                val dispatch = envelope.`type` match {
                  case "interactive" =>
                    envelope.payload match {
                      case Some(json) =>
                        json.as[InteractionPayload] match {
                          case Right(payload) => onInteraction(payload).attempt.void
                          case Left(_)        => Async[F].unit
                        }
                      case None => Async[F].unit
                    }
                  case "slash_commands" =>
                    envelope.payload match {
                      case Some(json) =>
                        json.as[SlashCommandPayload] match {
                          case Right(payload) => onSlashCommand(payload).attempt.void
                          case Left(_)        => Async[F].unit
                        }
                      case None => Async[F].unit
                    }
                  case _ => Async[F].unit
                }
                ack >> dispatch >> loop
              case Left(_) => loop
            }
          }
        loop
      })
      .send(backend)
      .void
  }
}
