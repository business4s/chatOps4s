package chatops4s.slack

import chatops4s.slack.api.SlackApi
import io.circe.{Decoder, Json, parser}
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
      onViewSubmission: ViewSubmissionPayload => F[Unit],
      retryDelay: Option[F[Unit]] = None,
  ): F[Unit] = {
    given monad: MonadError[F] = backend.monad
    val delay = retryDelay.getOrElse(monad.blocking(Thread.sleep(2000)))

    val loop: F[Unit] = for {
      url <- openSocketUrl(appToken, backend)
      _   <- connectAndHandle(url, backend, onInteraction, onSlashCommand, onViewSubmission)
    } yield ()

    loop.handleError { case _ =>
      delay >> runLoop(appToken, backend, onInteraction, onSlashCommand, onViewSubmission, Some(delay))
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
      onViewSubmission: ViewSubmissionPayload => F[Unit],
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
                        val payloadType = json.hcursor.downField("type").as[String].getOrElse("")
                        if (payloadType == "view_submission") dispatchPayload(json, onViewSubmission)
                        else dispatchPayload(json, onInteraction)
                      case None => monad.unit(())
                    }
                  case "slash_commands" =>
                    envelope.payload match {
                      case Some(json) => dispatchPayload(json, onSlashCommand)
                      case None       => monad.unit(())
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

  private def dispatchPayload[F[_], A: Decoder](json: Json, handler: A => F[Unit])(using monad: MonadError[F]): F[Unit] =
    json.as[A] match {
      case Right(a) => handler(a).handleError { case e =>
        monad.blocking(System.err.println(s"[chatops4s] Handler error: ${e.getMessage}"))
      }
      case Left(_)  => monad.unit(())
    }
}
