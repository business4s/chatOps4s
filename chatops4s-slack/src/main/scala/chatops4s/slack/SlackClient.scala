package chatops4s.slack

import chatops4s.slack.models.*
import sttp.client4.*
import sttp.client4.circe.*
import com.typesafe.scalalogging.StrictLogging

trait Monad[F[_]] {
  def pure[A](value: A): F[A]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def raiseError[A](error: Throwable): F[A]
}

object Monad {
  def apply[F[_]](implicit M: Monad[F]): Monad[F] = M
}

class SlackClient[F[_]: Monad](config: SlackConfig, backend: Backend[F]) extends StrictLogging {

  private val baseUrl = "https://slack.com/api"

  def postMessage(request: SlackPostMessageRequest): F[SlackPostMessageResponse] = {
    val req = basicRequest
      .post(uri"$baseUrl/chat.postMessage")
      .header("Authorization", s"Bearer ${config.botToken}")
      .body(asJson(request))
      .response(asJson[SlackPostMessageResponse])

    Monad[F].flatMap(backend.send(req)) { response =>
      response.body match {
        case Right(slackResponse) =>
          if (slackResponse.ok) {
            // Log debug message directly since we can't lift IO operations in generic F[_]
            logger.debug(s"Message sent successfully: ${slackResponse.ts}")
            Monad[F].pure(slackResponse)
          } else {
            val errorMsg = s"Slack API error: ${slackResponse.error.getOrElse("Unknown error")}"
            logger.error(errorMsg)
            Monad[F].raiseError(new RuntimeException(errorMsg))
          }
        case Left(error)          =>
          val errorMsg = s"Failed to send message: $error"
          logger.error(errorMsg)
          Monad[F].raiseError(new RuntimeException(errorMsg))
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
