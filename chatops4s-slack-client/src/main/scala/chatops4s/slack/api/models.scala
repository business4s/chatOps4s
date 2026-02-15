package chatops4s.slack.api

import io.circe.{Codec, Decoder, Encoder, Json}

private type Block = chatops4s.slack.api.blocks.Block
private type View = chatops4s.slack.api.blocks.View
private type ViewType = chatops4s.slack.api.blocks.ViewType

opaque type ChannelId = String
object ChannelId {
  def apply(value: String): ChannelId = value
  extension (x: ChannelId) def value: String = x
  given Encoder[ChannelId] = Encoder[String]
  given Decoder[ChannelId] = Decoder[String]
}

opaque type UserId = String
object UserId {
  def apply(value: String): UserId = value
  extension (x: UserId) def value: String = x
  given Encoder[UserId] = Encoder[String]
  given Decoder[UserId] = Decoder[String]
}

opaque type TeamId = String
object TeamId {
  def apply(value: String): TeamId = value
  extension (x: TeamId) def value: String = x
  given Encoder[TeamId] = Encoder[String]
  given Decoder[TeamId] = Decoder[String]
}

opaque type ConversationId = String
object ConversationId {
  def apply(value: String): ConversationId = value
  extension (x: ConversationId) def value: String = x
  given Encoder[ConversationId] = Encoder[String]
  given Decoder[ConversationId] = Decoder[String]
}

opaque type Timestamp = String
object Timestamp {
  def apply(value: String): Timestamp = value
  extension (x: Timestamp) def value: String = x
  given Encoder[Timestamp] = Encoder[String]
  given Decoder[Timestamp] = Decoder[String]
}

opaque type Email = String
object Email {
  def apply(value: String): Email = value
  extension (x: Email) def value: String = x
  given Encoder[Email] = Encoder[String]
  given Decoder[Email] = Decoder[String]
}

opaque type TriggerId = String
object TriggerId {
  def apply(value: String): TriggerId = value
  extension (x: TriggerId) def value: String = x
  given Encoder[TriggerId] = Encoder[String]
  given Decoder[TriggerId] = Decoder[String]
}

enum ParseMode {
  case Full, Raw
}
object ParseMode {
  private val mapping = Map("full" -> Full, "none" -> Raw)
  private val reverse = mapping.map(_.swap)
  given Encoder[ParseMode] = Encoder[String].contramap(reverse)
  given Decoder[ParseMode] = Decoder[String].emap(s => mapping.get(s).toRight(s"Unknown parse mode: $s"))
}

enum ResponseType {
  case InChannel, Ephemeral
}
object ResponseType {
  private val mapping = Map("in_channel" -> InChannel, "ephemeral" -> Ephemeral)
  private val reverse = mapping.map(_.swap)
  given Encoder[ResponseType] = Encoder[String].contramap(reverse)
  given Decoder[ResponseType] = Decoder[String].emap(s => mapping.get(s).toRight(s"Unknown response type: $s"))
}

// https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/Message.java
case class Message(
    `type`: Option[String] = None,
    subtype: Option[String] = None,
    team: Option[TeamId] = None,
    channel: Option[ChannelId] = None,
    user: Option[UserId] = None,
    username: Option[String] = None,
    text: Option[String] = None,
    blocks: Option[List[Block]] = None,
    attachments: Option[List[Json]] = None,
    ts: Option[Timestamp] = None,
    thread_ts: Option[Timestamp] = None,
    app_id: Option[String] = None,
    bot_id: Option[String] = None,
    bot_profile: Option[Json] = None,
    display_as_bot: Option[Boolean] = None,
    icons: Option[Json] = None,
    file: Option[Json] = None,
    files: Option[List[Json]] = None,
    upload: Option[Boolean] = None,
    parent_user_id: Option[UserId] = None,
    client_msg_id: Option[String] = None,
    edited: Option[Json] = None,
    unfurl_links: Option[Boolean] = None,
    unfurl_media: Option[Boolean] = None,
    is_thread_broadcast: Option[Boolean] = None,
    is_locked: Option[Boolean] = None,
    reply_count: Option[Int] = None,
    reply_users: Option[List[UserId]] = None,
    reply_users_count: Option[Int] = None,
    latest_reply: Option[Timestamp] = None,
    subscribed: Option[Boolean] = None,
    hidden: Option[Boolean] = None,
    is_starred: Option[Boolean] = None,
    pinned_to: Option[List[String]] = None,
    reactions: Option[List[Json]] = None,
    metadata: Option[Json] = None,
    room: Option[Json] = None,
) derives Codec.AsObject

sealed trait SlackResponse[+T] {
  def okOrThrow: T
}

object SlackResponse {
  case class Ok[+T](value: T) extends SlackResponse[T] {
    def okOrThrow: T = value
  }
  case class Err(error: String) extends SlackResponse[Nothing] {
    def okOrThrow: Nothing = throw SlackApiError(error)
  }

  given [T: Decoder]: Decoder[SlackResponse[T]] = Decoder.instance { cursor =>
    cursor.get[Boolean]("ok").flatMap {
      case true  => cursor.as[T].map(Ok(_))
      case false => cursor.getOrElse[String]("error")("unknown").map(Err(_))
    }
  }
}

object chat {

  case class PostMessageRequest(
      channel: String,
      text: String,
      blocks: Option[List[Block]] = None,
      attachments: Option[List[Json]] = None,
      thread_ts: Option[Timestamp] = None,
      unfurl_links: Option[Boolean] = None,
      unfurl_media: Option[Boolean] = None,
      mrkdwn: Option[Boolean] = None,
      metadata: Option[Json] = None,
      reply_broadcast: Option[Boolean] = None,
      parse: Option[ParseMode] = None,
      icon_emoji: Option[String] = None,
      icon_url: Option[String] = None,
      username: Option[String] = None,
  ) derives Codec.AsObject

  case class PostMessageResponse(
      channel: ChannelId,
      ts: Timestamp,
      message: Option[Message] = None,
      response_metadata: Option[ResponseMetadata] = None,
  ) derives Codec.AsObject

  case class UpdateRequest(
      channel: ChannelId,
      ts: Timestamp,
      text: Option[String] = None,
      blocks: Option[List[Block]] = None,
      attachments: Option[List[Json]] = None,
      reply_broadcast: Option[Boolean] = None,
      metadata: Option[Json] = None,
      parse: Option[ParseMode] = None,
      file_ids: Option[List[String]] = None,
  ) derives Codec.AsObject

  case class UpdateResponse(
      channel: ChannelId,
      ts: Timestamp,
      text: Option[String] = None,
      message: Option[Json] = None,
  ) derives Codec.AsObject

  case class DeleteRequest(
      channel: ChannelId,
      ts: Timestamp,
  ) derives Codec.AsObject

  case class DeleteResponse(
      channel: ChannelId,
      ts: Timestamp,
  ) derives Codec.AsObject

  case class PostEphemeralRequest(
      channel: String,
      user: UserId,
      text: String,
      blocks: Option[List[Block]] = None,
      attachments: Option[List[Json]] = None,
      thread_ts: Option[Timestamp] = None,
      parse: Option[ParseMode] = None,
      icon_emoji: Option[String] = None,
      icon_url: Option[String] = None,
      username: Option[String] = None,
  ) derives Codec.AsObject

  case class PostEphemeralResponse(
      message_ts: Timestamp,
  ) derives Codec.AsObject
}

case class ResponseMetadata(
    messages: Option[List[String]] = None,
) derives Codec.AsObject

object reactions {

  case class AddRequest(
      channel: ChannelId,
      timestamp: Timestamp,
      name: String,
  ) derives Codec.AsObject

  case class AddResponse() derives Codec.AsObject

  case class RemoveRequest(
      channel: ChannelId,
      timestamp: Timestamp,
      name: String,
  ) derives Codec.AsObject

  case class RemoveResponse() derives Codec.AsObject
}

object apps {

  case class ConnectionsOpenResponse(
      url: String,
  ) derives Codec.AsObject
}

object views {

  case class OpenRequest(
      trigger_id: String,
      view: View,
  ) derives Codec.AsObject

  case class OpenResponse(
      view: Option[Json] = None,
  ) derives Codec.AsObject
}

object users {

  // https://docs.slack.dev/reference/methods/users.info
  case class InfoRequest(user: UserId) derives Codec.AsObject

  case class UserProfile(
      email: Option[Email] = None,
      display_name: Option[String] = None,
      real_name: Option[String] = None,
  ) derives Codec.AsObject

  case class UserInfo(
      id: UserId,
      name: Option[String] = None,
      real_name: Option[String] = None,
      profile: Option[UserProfile] = None,
      is_bot: Option[Boolean] = None,
      tz: Option[String] = None,
  ) derives Codec.AsObject

  case class InfoResponse(user: UserInfo) derives Codec.AsObject
}
