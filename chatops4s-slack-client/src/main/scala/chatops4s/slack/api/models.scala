package chatops4s.slack.api

import io.circe.{Codec, Decoder, Encoder, Json}

case class ChannelId(value: String)
object ChannelId {
  given Encoder[ChannelId] = Encoder[String].contramap(_.value)
  given Decoder[ChannelId] = Decoder[String].map(ChannelId(_))
}

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
      blocks: Option[List[Json]] = None,
      attachments: Option[List[Json]] = None,
      thread_ts: Option[String] = None,
      unfurl_links: Option[Boolean] = None,
      unfurl_media: Option[Boolean] = None,
      mrkdwn: Option[Boolean] = None,
      metadata: Option[Json] = None,
      reply_broadcast: Option[Boolean] = None,
      parse: Option[String] = None,
      icon_emoji: Option[String] = None,
      icon_url: Option[String] = None,
      username: Option[String] = None,
  ) derives Codec.AsObject

  case class PostMessageResponse(
      channel: ChannelId,
      ts: String,
      message: Option[Json] = None,
      response_metadata: Option[ResponseMetadata] = None,
  ) derives Codec.AsObject

  case class UpdateRequest(
      channel: ChannelId,
      ts: String,
      text: Option[String] = None,
      blocks: Option[List[Json]] = None,
      attachments: Option[List[Json]] = None,
      reply_broadcast: Option[Boolean] = None,
      metadata: Option[Json] = None,
      parse: Option[String] = None,
      file_ids: Option[List[String]] = None,
  ) derives Codec.AsObject

  case class UpdateResponse(
      channel: ChannelId,
      ts: String,
      text: Option[String] = None,
      message: Option[Json] = None,
  ) derives Codec.AsObject

  case class DeleteRequest(
      channel: ChannelId,
      ts: String,
  ) derives Codec.AsObject

  case class DeleteResponse(
      channel: ChannelId,
      ts: String,
  ) derives Codec.AsObject

  case class PostEphemeralRequest(
      channel: String,
      user: String,
      text: String,
      blocks: Option[List[Json]] = None,
      attachments: Option[List[Json]] = None,
      thread_ts: Option[String] = None,
      parse: Option[String] = None,
      icon_emoji: Option[String] = None,
      icon_url: Option[String] = None,
      username: Option[String] = None,
  ) derives Codec.AsObject

  case class PostEphemeralResponse(
      message_ts: String,
  ) derives Codec.AsObject
}

case class ResponseMetadata(
    messages: Option[List[String]] = None,
) derives Codec.AsObject

object reactions {

  case class AddRequest(
      channel: ChannelId,
      timestamp: String,
      name: String,
  ) derives Codec.AsObject

  case class AddResponse() derives Codec.AsObject

  case class RemoveRequest(
      channel: ChannelId,
      timestamp: String,
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
      view: Json,
  ) derives Codec.AsObject

  case class OpenResponse(
      view: Option[Json] = None,
  ) derives Codec.AsObject
}
