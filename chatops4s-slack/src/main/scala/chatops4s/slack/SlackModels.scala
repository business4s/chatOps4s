package chatops4s.slack

import io.circe.{Codec, Json}

private[slack] object SlackModels {

  case class Block(
      `type`: String,
      text: Option[TextObject] = None,
      elements: Option[List[BlockElement]] = None,
  ) derives Codec.AsObject

  case class TextObject(
      `type`: String,
      text: String,
  ) derives Codec.AsObject

  case class BlockElement(
      `type`: String,
      text: Option[TextObject] = None,
      action_id: Option[String] = None,
      value: Option[String] = None,
  ) derives Codec.AsObject

  case class InteractionPayload(
      `type`: String,
      user: User,
      channel: Channel,
      container: Container,
      message: Option[InteractionMessage] = None,
      actions: Option[List[Action]] = None,
  ) derives Codec.AsObject

  case class User(id: String) derives Codec.AsObject
  case class Channel(id: String) derives Codec.AsObject
  case class Container(message_ts: Option[String] = None) derives Codec.AsObject
  case class InteractionMessage(thread_ts: Option[String] = None) derives Codec.AsObject
  case class Action(action_id: String, value: Option[String] = None) derives Codec.AsObject

  case class SocketEnvelope(
      envelope_id: String,
      `type`: String,
      payload: Option[Json] = None,
  ) derives Codec.AsObject

  case class SocketAck(
      envelope_id: String,
  ) derives Codec.AsObject

  case class SlashCommandPayload(
      command: String,
      text: String,
      user_id: String,
      channel_id: String,
      response_url: String,
  ) derives Codec.AsObject

  case class CommandResponsePayload(
      response_type: String,
      text: String,
  ) derives Codec.AsObject
}
