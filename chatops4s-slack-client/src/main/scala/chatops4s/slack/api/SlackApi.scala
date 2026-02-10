package chatops4s.slack.api

import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.monad.syntax.*

class SlackApi[F[_]](backend: Backend[F], token: String) {

  private given sttp.monad.MonadError[F] = backend.monad
  private val baseUrl                    = "https://slack.com/api"

  object chat {

    def postMessage(req: chatops4s.slack.api.chat.PostMessageRequest): F[SlackResponse[chatops4s.slack.api.chat.PostMessageResponse]] =
      post("chat.postMessage", req)

    def update(req: chatops4s.slack.api.chat.UpdateRequest): F[SlackResponse[chatops4s.slack.api.chat.UpdateResponse]] =
      post("chat.update", req)

    def delete(req: chatops4s.slack.api.chat.DeleteRequest): F[SlackResponse[chatops4s.slack.api.chat.DeleteResponse]] =
      post("chat.delete", req)

    def postEphemeral(req: chatops4s.slack.api.chat.PostEphemeralRequest): F[SlackResponse[chatops4s.slack.api.chat.PostEphemeralResponse]] =
      post("chat.postEphemeral", req)
  }

  object reactions {

    def add(req: chatops4s.slack.api.reactions.AddRequest): F[SlackResponse[chatops4s.slack.api.reactions.AddResponse]] =
      post("reactions.add", req)

    def remove(req: chatops4s.slack.api.reactions.RemoveRequest): F[SlackResponse[chatops4s.slack.api.reactions.RemoveResponse]] =
      post("reactions.remove", req)
  }

  private def post[Req: io.circe.Encoder, Res: io.circe.Decoder](method: String, req: Req): F[SlackResponse[Res]] =
    backend
      .send(
        basicRequest
          .post(uri"$baseUrl/$method")
          .header("Authorization", s"Bearer $token")
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
    def connectionsOpen[F[_]](backend: Backend[F], appToken: String): F[SlackResponse[chatops4s.slack.api.apps.ConnectionsOpenResponse]] = {
      given sttp.monad.MonadError[F] = backend.monad
      backend
        .send(
          basicRequest
            .post(uri"https://slack.com/api/apps.connections.open")
            .header("Authorization", s"Bearer $appToken")
            .contentType("application/x-www-form-urlencoded")
            .response(asJsonAlways[SlackResponse[chatops4s.slack.api.apps.ConnectionsOpenResponse]]),
        )
        .map(_.body)
        .map {
          case Right(res) => res
          case Left(err)  => throw SlackApiError("deserialization_error", List(s"apps.connections.open: $err"))
        }
    }
  }
}
