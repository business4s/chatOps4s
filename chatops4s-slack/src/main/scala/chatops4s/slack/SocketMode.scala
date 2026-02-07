package chatops4s.slack

import cats.effect.kernel.{Async, Temporal}
import cats.syntax.all.*
import io.circe.parser
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.ws.async.*

import scala.concurrent.duration.*

import SlackModels.*

private[slack] object SocketMode {

  def runLoop[F[_]: Async](
      appToken: String,
      backend: WebSocketBackend[F],
      onInteraction: InteractionPayload => F[Unit],
  ): F[Unit] = {
    val loop: F[Unit] = for {
      url <- openSocketUrl(appToken, backend)
      _   <- connectAndHandle(url, backend, onInteraction)
    } yield ()

    loop.handleErrorWith { _ =>
      Temporal[F].sleep(2.seconds) >> runLoop(appToken, backend, onInteraction)
    }
  }

  private def openSocketUrl[F[_]: Async](
      appToken: String,
      backend: Backend[F],
  ): F[String] = {
    val req = basicRequest
      .post(uri"https://slack.com/api/apps.connections.open")
      .header("Authorization", s"Bearer $appToken")
      .contentType("application/x-www-form-urlencoded")
      .response(asJson[ConnectionsOpenResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(r) if r.ok => r.url match {
          case Some(url) => Async[F].pure(url)
          case None      => Async[F].raiseError(SlackApiException("no_url", List("No URL in connections.open response")))
        }
        case Right(r) =>
          Async[F].raiseError(SlackApiException(r.error.getOrElse("unknown"), List("connections.open failed")))
        case Left(err) =>
          Async[F].raiseError(SlackApiException("parse_error", List(s"Failed to parse connections.open response: $err")))
      }
    }
  }

  private def connectAndHandle[F[_]: Async](
      url: String,
      backend: WebSocketBackend[F],
      onInteraction: InteractionPayload => F[Unit],
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
