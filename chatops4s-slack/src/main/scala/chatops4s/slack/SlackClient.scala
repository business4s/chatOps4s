package chatops4s.slack

import chatops4s.slack.models.*
import sttp.client4.*
import sttp.client4.circe.*
import com.typesafe.scalalogging.StrictLogging

class SlackClient[F[_]](config: SlackConfig, backend: Backend[F]) extends StrictLogging {

  private val baseUrl                                  = "https://slack.com/api"
  implicit private val monad: sttp.monad.MonadError[F] = backend.monad

  def postMessage(request: SlackPostMessageRequest): F[SlackPostMessageResponse] = {
    val req = basicRequest
      .post(uri"$baseUrl/chat.postMessage")
      .header("Authorization", s"Bearer ${config.botToken}")
      .body(asJson(request))
      .response(asJson[SlackPostMessageResponse])

    monad.flatMap(backend.send(req)) { response =>
      response.body match {
        case Right(slackResponse) =>
          if (slackResponse.ok) {
            logger.debug(s"Message sent successfully: ${slackResponse.ts}")
            monad.unit(slackResponse)
          } else {
            val errorMsg = s"Slack API error: ${slackResponse.error.getOrElse("Unknown error")}"
            logger.error(errorMsg)
            monad.error(new RuntimeException(errorMsg))
          }
        case Left(error)          =>
          val errorMsg = s"Failed to send message: $error"
          logger.error(errorMsg)
          monad.error(new RuntimeException(errorMsg))
      }
    }
  }

  def postMessageToThread(channelId: String, threadTs: String, text: String): F[SlackPostMessageResponse] = {
    logger.debug(s"Sending thread message to channel $channelId, thread $threadTs")
    val request = SlackPostMessageRequest(
      channel = channelId,
      text = text,
      thread_ts = Some(threadTs),
    )
    postMessage(request)
  }
}
