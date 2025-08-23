package chatops4s.slack

import cats.effect.Sync
import chatops4s.slack.models.*
import sttp.client4.*
import sttp.client4.circe.*
import com.typesafe.scalalogging.StrictLogging

class SlackClient[F[_]: Sync](config: SlackConfig, backend: Backend[F]) extends StrictLogging {

  private val baseUrl = "https://slack.com/api"

  def postMessage(request: SlackPostMessageRequest): F[SlackPostMessageResponse] = {
    val req = basicRequest
      .post(uri"$baseUrl/chat.postMessage")
      .header("Authorization", s"Bearer ${config.botToken}")
      .body(asJson(request))
      .response(asJson[SlackPostMessageResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(slackResponse) =>
          if (slackResponse.ok) {
            Sync[F].delay(logger.debug(s"Message sent successfully: ${slackResponse.ts}")) *>
              Sync[F].pure(slackResponse)
          } else {
            val errorMsg = s"Slack API error: ${slackResponse.error.getOrElse("Unknown error")}"
            Sync[F].delay(logger.error(errorMsg)) *>
              Sync[F].raiseError(new RuntimeException(errorMsg))
          }
        case Left(error)          =>
          val errorMsg = s"Failed to send message: $error"
          Sync[F].delay(logger.error(errorMsg)) *>
            Sync[F].raiseError(new RuntimeException(errorMsg))
      }
    }
  }

  def postMessageToThread(channelId: String, threadTs: String, text: String): F[SlackPostMessageResponse] = {
    Sync[F].delay(logger.debug(s"Sending thread message to channel $channelId, thread $threadTs")) *>
      {
        val request = SlackPostMessageRequest(
          channel = channelId,
          text = text,
          thread_ts = Some(threadTs),
        )
        postMessage(request)
      }
  }
}