package chatops4s.slack

import cats.effect.IO
import cats.implicits.*
import chatops4s.slack.models.*
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.circe.*

class SlackClient(config: SlackConfig, backend: Backend[IO]) {

  private val baseUrl = "https://slack.com/api"

  def postMessage(request: SlackPostMessageRequest): IO[SlackPostMessageResponse] = {
    val req = basicRequest
      .post(uri"$baseUrl/chat.postMessage")
      .header("Authorization", s"Bearer ${config.botToken}")
      .header("Content-Type", "application/json")
      .body(request.asJson.noSpaces)
      .response(asJson[SlackPostMessageResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(slackResponse) => IO.pure(slackResponse)
        case Left(error)          => IO.raiseError(new RuntimeException(s"Failed to send message: $error"))
      }
    }
  }

  def postMessageToThread(channelId: String, threadTs: String, text: String): IO[SlackPostMessageResponse] = {
    val request = SlackPostMessageRequest(
      channel = channelId,
      text = text,
      thread_ts = Some(threadTs),
    )
    postMessage(request)
  }
}