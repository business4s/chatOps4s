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

  def deleteMessage(messageId: MessageId): F[Unit] = {
    val request = DeleteMessageRequest(channel = messageId.channel, ts = messageId.ts)
    sendOkRequest(uri"$baseUrl/chat.delete", request.asJson)
  }

  def addReaction(messageId: MessageId, emoji: String): F[Unit] = {
    val request = ReactionRequest(channel = messageId.channel, timestamp = messageId.ts, name = emoji)
    sendOkRequest(uri"$baseUrl/reactions.add", request.asJson)
  }

  def removeReaction(messageId: MessageId, emoji: String): F[Unit] = {
    val request = ReactionRequest(channel = messageId.channel, timestamp = messageId.ts, name = emoji)
    sendOkRequest(uri"$baseUrl/reactions.remove", request.asJson)
  }

  def postEphemeral(channel: String, userId: String, text: String): F[Unit] = {
    val request = PostEphemeralRequest(channel = channel, user = userId, text = text)
    sendOkRequest(uri"$baseUrl/chat.postEphemeral", request.asJson)
  }

  private def sendOkRequest(url: sttp.model.Uri, body: io.circe.Json): F[Unit] = {
    val req = basicRequest
      .post(url)
      .header("Authorization", s"Bearer $token")
      .contentType("application/json")
      .body(body.noSpaces)
      .response(asJson[OkResponse])

    backend.send(req).flatMap { response =>
      response.body match {
        case Right(r) if r.ok => Async[F].unit
        case Right(r) =>
          Async[F].raiseError(SlackApiException(r.error.getOrElse("unknown")))
        case Left(err) =>
          Async[F].raiseError(SlackApiException("parse_error", List(s"Failed to parse response: $err")))
      }
    }
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
