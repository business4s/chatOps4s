package chatops4s.slack.api

import sttp.client4.*
import sttp.client4.circe.*
import sttp.monad.MonadError
import sttp.monad.syntax.*

import java.util.Base64

object SlackOAuth {

  private val baseUrl = "https://slack.com/api"

  /** Exchange an OAuth authorization code for an access token.
    *
    * Uses HTTP Basic Auth with `client_id:client_secret` as recommended by Slack,
    * and posts `code` and `redirect_uri` as form-urlencoded body.
    *
    * @see [[https://docs.slack.dev/reference/methods/oauth.v2.access]]
    */
  def exchangeCode[F[_]](backend: Backend[F], req: oauth.AccessRequest): F[SlackResponse[oauth.AccessResponse]] = {
    given MonadError[F] = backend.monad

    val credentials = Base64.getEncoder.encodeToString(s"${req.client_id}:${req.client_secret}".getBytes("UTF-8"))
    val formParams  = Map("code" -> req.code) ++ req.redirect_uri.map("redirect_uri" -> _)

    backend
      .send(
        basicRequest
          .post(uri"$baseUrl/oauth.v2.access")
          .header("Authorization", s"Basic $credentials")
          .body(formParams)
          .response(asJsonOrFail[SlackResponse[oauth.AccessResponse]]),
      )
      .map(_.body)
  }
}
