package chatops4s.slack

import chatops4s.slack.api.{ResponseType, SlackApi, Timestamp, TriggerId, UserId, chat, reactions, users, views}
import chatops4s.slack.api.socket.CommandResponsePayload
import chatops4s.slack.api.blocks.{Block, View}
import io.circe.syntax.*
import sttp.client4.*
import sttp.monad.syntax.*
import chatops4s.slack.monadSyntax.*

private[slack] class SlackClient[F[_]](token: String, backend: Backend[F]) {

  private given sttp.monad.MonadError[F] = backend.monad

  private val api = new SlackApi[F](backend, token)

  def postMessage(channel: String, text: String, blocks: Option[List[Block]], threadTs: Option[Timestamp]): F[MessageId] = {
    val request = chat.PostMessageRequest(
      channel = channel,
      text = text,
      blocks = blocks,
      thread_ts = threadTs,
    )

    api.chat.postMessage(request).map { resp =>
      val r = resp.okOrThrow
      MessageId(r.channel, r.ts)
    }
  }

  def respondToCommand(responseUrl: String, text: String, responseType: ResponseType): F[Unit] = {
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

  def postEphemeral(channel: String, userId: UserId, text: String): F[Unit] =
    api.chat.postEphemeral(chat.PostEphemeralRequest(channel = channel, user = userId, text = text))
      .map(_.okOrThrow).void

  def openView(triggerId: TriggerId, view: View): F[Unit] =
    api.views.open(views.OpenRequest(trigger_id = triggerId, view = view))
      .map(_.okOrThrow).void

  def getUserInfo(userId: UserId): F[users.UserInfo] =
    api.users.info(users.InfoRequest(user = userId)).map(_.okOrThrow.user)

  def updateMessage(messageId: MessageId, text: String, blocks: Option[List[Block]]): F[MessageId] = {
    val request = chat.UpdateRequest(
      channel = messageId.channel,
      ts = messageId.ts,
      text = Some(text),
      blocks = blocks,
    )

    api.chat.update(request).map { resp =>
      resp.okOrThrow
      messageId
    }
  }
}
