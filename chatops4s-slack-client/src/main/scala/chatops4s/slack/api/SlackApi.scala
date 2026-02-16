package chatops4s.slack.api

import chatops4s.slack.api.apps.ConnectionsOpenResponse
import chatops4s.slack.api.chat.{
  DeleteRequest,
  DeleteResponse,
  PostEphemeralRequest,
  PostEphemeralResponse,
  PostMessageRequest,
  PostMessageResponse,
  UpdateRequest,
  UpdateResponse,
}
import chatops4s.slack.api.reactions.{AddRequest, AddResponse, RemoveRequest, RemoveResponse}
import chatops4s.slack.api.users.{InfoRequest as UsersInfoRequest, InfoResponse as UsersInfoResponse}
import chatops4s.slack.api.views.{OpenRequest, OpenResponse}
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.ws.async.*
import sttp.monad.MonadError
import sttp.monad.syntax.*

class SlackApi[F[_]](backend: Backend[F], token: SlackBotToken) {

  private given sttp.monad.MonadError[F] = backend.monad
  private val baseUrl                    = "https://slack.com/api"

  object chat {

    // https://docs.slack.dev/reference/methods/chat.postMessage
    def postMessage(req: PostMessageRequest): F[SlackResponse[PostMessageResponse]] = post("chat.postMessage", req)

    // https://docs.slack.dev/reference/methods/chat.update
    def update(req: UpdateRequest): F[SlackResponse[UpdateResponse]] = post("chat.update", req)

    // https://docs.slack.dev/reference/methods/chat.delete
    def delete(req: DeleteRequest): F[SlackResponse[DeleteResponse]] = post("chat.delete", req)

    // https://docs.slack.dev/reference/methods/chat.postEphemeral
    def postEphemeral(req: PostEphemeralRequest): F[SlackResponse[PostEphemeralResponse]] = post("chat.postEphemeral", req)
  }

  object reactions {

    // https://docs.slack.dev/reference/methods/reactions.add
    def add(req: AddRequest): F[SlackResponse[AddResponse]] = post("reactions.add", req)

    // https://docs.slack.dev/reference/methods/reactions.remove
    def remove(req: RemoveRequest): F[SlackResponse[RemoveResponse]] = post("reactions.remove", req)
  }

  object views {

    // https://docs.slack.dev/reference/methods/views.open
    def open(req: OpenRequest): F[SlackResponse[OpenResponse]] = post("views.open", req)
  }

  object users {

    // https://docs.slack.dev/reference/methods/users.info
    def info(req: UsersInfoRequest): F[SlackResponse[UsersInfoResponse]] =
      get("users.info", Map("user" -> req.user.value))
  }

  private def get[Res: io.circe.Decoder](method: String, params: Map[String, String]): F[SlackResponse[Res]] =
    backend
      .send(
        basicRequest
          .get(uri"$baseUrl/$method?$params")
          .header("Authorization", s"Bearer ${token.value}")
          .response(asJsonAlways[SlackResponse[Res]]),
      )
      .map(_.body)
      .map {
        case Right(res) => res
        case Left(err)  => throw SlackApiError("deserialization_error", List(s"$method: $err"))
      }

  private def post[Req: io.circe.Encoder, Res: io.circe.Decoder](method: String, req: Req): F[SlackResponse[Res]] =
    backend
      .send(
        basicRequest
          .post(uri"$baseUrl/$method")
          .header("Authorization", s"Bearer ${token.value}")
          .contentType("application/json")
          .body(req.asJson.deepDropNullValues.noSpaces)
          .response(asJsonAlways[SlackResponse[Res]]),
      )
      .map(_.body)
      .map {
        case Right(res) => res
        case Left(err)  => throw SlackApiError("deserialization_error", List(s"$method: $err"))
      }
}

object SlackApi {

  object apps {
    // https://docs.slack.dev/reference/methods/apps.connections.open
    def connectionsOpen[F[_]](backend: Backend[F], appToken: SlackAppToken): F[SlackResponse[ConnectionsOpenResponse]] = {
      given sttp.monad.MonadError[F] = backend.monad
      backend
        .send(
          basicRequest
            .post(uri"https://slack.com/api/apps.connections.open")
            .header("Authorization", s"Bearer ${appToken.value}")
            .contentType("application/x-www-form-urlencoded")
            .response(asJsonAlways[SlackResponse[ConnectionsOpenResponse]]),
        )
        .map(_.body)
        .map {
          case Right(res) => res
          case Left(err)  => throw SlackApiError("deserialization_error", List(s"apps.connections.open: $err"))
        }
    }

    // https://docs.slack.dev/apis/events-api/using-socket-mode
    def connectToSocket[F[_]](url: String, backend: WebSocketBackend[F])(
        handler: socket.Envelope => F[Unit],
    ): F[socket.DisconnectReason] = {
      given monad: MonadError[F] = backend.monad
      basicRequest
        .get(uri"$url")
        .response(asWebSocketOrFail[F, socket.DisconnectReason] { ws =>
          def loop: F[socket.DisconnectReason] =
            ws.receiveText().flatMap { text =>
              io.circe.parser.decode[socket.Envelope](text) match {
                case Right(envelope) =>
                  val ack = ws.sendText(socket.Ack(envelope.envelope_id).asJson.noSpaces)
                  ack.flatMap(_ => handler(envelope)).flatMap(_ => loop)
                case Left(_) =>
                  io.circe.parser.decode[socket.Disconnect](text) match {
                    case Right(disconnect) => monad.unit(disconnect.reason)
                    case Left(_)           => loop // Malformed frames skipped to avoid killing the connection
                  }
              }
            }
          loop
        })
        .send(backend)
        .map(_.body)
    }
  }
}
