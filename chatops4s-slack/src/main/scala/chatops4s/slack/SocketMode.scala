package chatops4s.slack

import chatops4s.slack.api.SlackApi
import io.circe.parser
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.ws.async.*
import sttp.monad.MonadError
import sttp.monad.syntax.*
import chatops4s.slack.monadSyntax.*

import SlackModels.*

private[slack] object SocketMode {

  def runLoop[F[_]](
      appToken: String,
      backend: WebSocketBackend[F],
      onInteraction: InteractionPayload => F[Unit],
      onSlashCommand: SlashCommandPayload => F[Unit],
  ): F[Unit] = {
    given monad: MonadError[F] = backend.monad

    val loop: F[Unit] = for {
      url <- openSocketUrl(appToken, backend)
      _   <- connectAndHandle(url, backend, onInteraction, onSlashCommand)
    } yield ()

    loop.handleError { case _ =>
      monad.blocking(Thread.sleep(2000)) >> runLoop(appToken, backend, onInteraction, onSlashCommand)
    }
  }

  private def openSocketUrl[F[_]](
      appToken: String,
      backend: Backend[F],
  ): F[String] = {
    given sttp.monad.MonadError[F] = backend.monad
    SlackApi.apps.connectionsOpen(backend, appToken).map(_.okOrThrow.url)
  }

  private def connectAndHandle[F[_]](
      url: String,
      backend: WebSocketBackend[F],
      onInteraction: InteractionPayload => F[Unit],
      onSlashCommand: SlashCommandPayload => F[Unit],
  ): F[Unit] = {
    given monad: MonadError[F] = backend.monad
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
                          case Right(payload) => onInteraction(payload).map(_ => ()).handleError { case _ => monad.unit(()) }
                          case Left(_)        => monad.unit(())
                        }
                      case None => monad.unit(())
                    }
                  case "slash_commands" =>
                    envelope.payload match {
                      case Some(json) =>
                        json.as[SlashCommandPayload] match {
                          case Right(payload) => onSlashCommand(payload).map(_ => ()).handleError { case _ => monad.unit(()) }
                          case Left(_)        => monad.unit(())
                        }
                      case None => monad.unit(())
                    }
                  case _ => monad.unit(())
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
