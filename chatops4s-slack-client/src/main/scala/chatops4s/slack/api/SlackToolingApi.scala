package chatops4s.slack.api

import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.monad.MonadError
import sttp.monad.syntax.*

class SlackToolingApi[F[_]](backend: Backend[F], token: SlackRefreshToken) {

  private given MonadError[F] = backend.monad
  private val baseUrl          = "https://slack.com/api"

  object tooling {
    object tokens {

      // https://docs.slack.dev/reference/methods/tooling.tokens.rotate
      def rotate(): F[SlackResponse[chatops4s.slack.api.tooling.tokens.RotateResponse]] =
        post("tooling.tokens.rotate", chatops4s.slack.api.tooling.tokens.RotateRequest(refresh_token = token))
    }
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
