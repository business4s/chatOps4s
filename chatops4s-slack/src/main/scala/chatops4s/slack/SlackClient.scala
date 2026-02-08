package chatops4s.slack

import cats.effect.kernel.Async
import cats.syntax.all.*
import io.circe.syntax.*
import sttp.client4.*
import sttp.client4.circe.*

import SlackModels.*

private[slack] class SlackClient[F[_]: Async](token: String, backend: Backend[F]) {

  private val baseUrl = "https://slack.com/api"

  def postMessage(channel: String, text: String, blocks: Option[List[Block]], threadTs: Option[String]): F[MessageId] = {
    val request = PostMessageRequest(
      channel = channel,
      text = text,
      blocks = blocks,
      thread_ts = threadTs,
    )

    val req = basicRequest
      .post(uri"$baseUrl/chat.postMessage")
      .header("Authorization", s"Bearer $token")
      .contentType("application/json")
      .body(request.asJson.deepDropNullValues.noSpaces)
      .response(asJson[PostMessageResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(slackResp) if slackResp.ok =>
          slackResp.ts match {
            case Some(ts0) => Async[F].pure(MessageId(channel, ts0))
            case None      => Async[F].raiseError(SlackApiException("no_timestamp", List("No timestamp in response")))
          }
        case Right(slackResp) =>
          val details = slackResp.response_metadata.flatMap(_.messages).getOrElse(Nil)
          Async[F].raiseError(SlackApiException(slackResp.error.getOrElse("unknown"), details))
        case Left(err) =>
          Async[F].raiseError(SlackApiException("parse_error", List(s"Failed to parse response: $err")))
      }
    }
  }

  def respondToCommand(responseUrl: String, text: String, responseType: String): F[Unit] = {
    val body = CommandResponsePayload(response_type = responseType, text = text)

    val req = basicRequest
      .post(uri"$responseUrl")
      .contentType("application/json")
      .body(body.asJson.noSpaces)

    backend.send(req).void
  }

  def updateMessage(messageId: MessageId, text: String, blocks: Option[List[Block]]): F[MessageId] = {
    val request = UpdateMessageRequest(
      channel = messageId.channel,
      ts = messageId.ts,
      text = text,
      blocks = blocks,
    )

    val req = basicRequest
      .post(uri"$baseUrl/chat.update")
      .header("Authorization", s"Bearer $token")
      .contentType("application/json")
      .body(request.asJson.deepDropNullValues.noSpaces)
      .response(asJson[PostMessageResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(slackResp) if slackResp.ok =>
          Async[F].pure(messageId)
        case Right(slackResp) =>
          val details = slackResp.response_metadata.flatMap(_.messages).getOrElse(Nil)
          Async[F].raiseError(SlackApiException(slackResp.error.getOrElse("unknown"), details))
        case Left(err) =>
          Async[F].raiseError(SlackApiException("parse_error", List(s"Failed to parse response: $err")))
      }
    }
  }
}
