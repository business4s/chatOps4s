package chatops4s.slack

import chatops4s.slack.api.{SlackApi, chat, reactions, views}
import chatops4s.slack.api.socket.CommandResponsePayload
import chatops4s.slack.api.blocks.Block
import io.circe.syntax.*
import sttp.client4.*
import sttp.monad.syntax.*
import chatops4s.slack.monadSyntax.*

private[slack] class SlackClient[F[_]](token: String, backend: Backend[F]) {

  private given sttp.monad.MonadError[F] = backend.monad

  private val api = new SlackApi[F](backend, token)

  def postMessage(channel: String, text: String, blocks: Option[List[Block]], threadTs: Option[String]): F[MessageId] = {
    val request = chat.PostMessageRequest(
      channel = channel,
      text = text,
      blocks = blocks.map(_.map(_.asJson.deepDropNullValues)),
      thread_ts = threadTs,
    )

    api.chat.postMessage(request).map { resp =>
      val r = resp.okOrThrow
      MessageId(r.channel, r.ts)
    }
  }

  def respondToCommand(responseUrl: String, text: String, responseType: String): F[Unit] = {
    val body = CommandResponsePayload(response_type = responseType, text = text)

    val req = basicRequest
      .post(uri"$responseUrl")
      .contentType("application/json")
      .body(body.asJson.deepDropNullValues.noSpaces)

    backend.send(req).void
  }

  def deleteMessage(messageId: MessageId): F[Unit] =
    api.chat.delete(chat.DeleteRequest(channel = messageId.channel, ts = messageId.ts))
      .map(_.okOrThrow).void

  def addReaction(messageId: MessageId, emoji: String): F[Unit] =
    api.reactions.add(reactions.AddRequest(channel = messageId.channel, timestamp = messageId.ts, name = emoji))
      .map(_.okOrThrow).void

  def removeReaction(messageId: MessageId, emoji: String): F[Unit] =
    api.reactions.remove(reactions.RemoveRequest(channel = messageId.channel, timestamp = messageId.ts, name = emoji))
      .map(_.okOrThrow).void

  def postEphemeral(channel: String, userId: String, text: String): F[Unit] =
    api.chat.postEphemeral(chat.PostEphemeralRequest(channel = channel, user = userId, text = text))
      .map(_.okOrThrow).void

  def openView(triggerId: String, view: io.circe.Json): F[Unit] =
    api.views.open(views.OpenRequest(trigger_id = triggerId, view = view))
      .map(_.okOrThrow).void

  def updateMessage(messageId: MessageId, text: String, blocks: Option[List[Block]]): F[MessageId] = {
    val request = chat.UpdateRequest(
      channel = messageId.channel,
      ts = messageId.ts,
      text = Some(text),
      blocks = blocks.map(_.map(_.asJson.deepDropNullValues)), // TODO why its json and not our block abstraction?
    )

    api.chat.update(request).map { resp =>
      resp.okOrThrow
      messageId
    }
  }
}
