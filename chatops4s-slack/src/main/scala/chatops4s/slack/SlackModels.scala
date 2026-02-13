package chatops4s.slack

import io.circe.{Codec, Json}

private[slack] object SlackModels {

  case class Block(
      `type`: String,
      text: Option[TextObject] = None,
      elements: Option[List[BlockElement]] = None,
      block_id: Option[String] = None,
      element: Option[BlockElement] = None,
      label: Option[TextObject] = None,
      optional: Option[Boolean] = None,
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
      is_decimal_allowed: Option[Boolean] = None,
      options: Option[List[BlockOption]] = None,
      initial_value: Option[String] = None,
  ) derives Codec.AsObject

  case class BlockOption(
      text: TextObject,
      value: String,
  ) derives Codec.AsObject

  case class InteractionPayload(
      `type`: String,
      user: User,
      channel: Channel,
      container: Container,
      message: Option[InteractionMessage] = None,
      actions: Option[List[Action]] = None,
      trigger_id: String,
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
      trigger_id: String,
  ) derives Codec.AsObject

  case class CommandResponsePayload(
      response_type: String,
      text: String,
  ) derives Codec.AsObject

  // View submission types
  case class ViewSubmissionPayload(
      `type`: String,
      user: User,
      view: ViewPayload,
  ) derives Codec.AsObject

  case class ViewPayload(
      id: String,
      callback_id: Option[String] = None,
      state: Option[ViewState] = None,
  ) derives Codec.AsObject

  case class ViewState(
      values: Map[String, Map[String, Json]],
  ) derives Codec.AsObject

  // View model for opening modals
  case class View(
      `type`: String,
      callback_id: String,
      title: TextObject,
      submit: Option[TextObject] = None,
      blocks: List[Block],
  ) derives Codec.AsObject
}
