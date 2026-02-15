package chatops4s.slack.api

import io.circe.{Codec, Decoder, Encoder, Json}
import chatops4s.slack.api.blocks.given
import chatops4s.slack.api.blocks.ViewType

object socket {

  private type Block = chatops4s.slack.api.blocks.Block
  private type TextObject = chatops4s.slack.api.blocks.TextObject

  enum EnvelopeType {
    case Interactive, SlashCommands, EventsApi
    case Unknown(value: String)
  }
  object EnvelopeType {
    private val mapping = Map(
      "interactive" -> Interactive,
      "slash_commands" -> SlashCommands,
      "events_api" -> EventsApi,
    )
    private val reverse = mapping.map(_.swap)
    given Encoder[EnvelopeType] = Encoder[String].contramap {
      case Unknown(s) => s
      case other      => reverse(other)
    }
    given Decoder[EnvelopeType] = Decoder[String].map(s => mapping.getOrElse(s, Unknown(s)))
  }

  case class Envelope(
      envelope_id: String,
      `type`: EnvelopeType,
      payload: Option[Json] = None,
      accepts_response_payload: Option[Boolean] = None,
      retry_attempt: Option[Int] = None,
      retry_reason: Option[String] = None,
  ) derives Codec.AsObject

  case class Ack(
      envelope_id: String,
      payload: Option[Json] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/interaction-payloads/block_actions-payload
  case class InteractionPayload(
      `type`: String,
      trigger_id: String,
      user: User,
      api_app_id: String,
      token: String,
      container: Container,
      actions: List[Action],
      team: Option[Team] = None,
      enterprise: Option[Json] = None,
      channel: Option[Channel] = None,
      message: Option[Message] = None,
      view: Option[ViewPayload] = None,
      state: Option[Json] = None,
      response_url: Option[String] = None,
      hash: Option[String] = None,
  ) derives Codec.AsObject

  case class User(
      id: UserId,
      username: Option[String] = None,
      name: Option[String] = None,
      team_id: Option[TeamId] = None,
  ) derives Codec.AsObject

  case class Team(
      id: TeamId,
      domain: Option[String] = None,
  ) derives Codec.AsObject

  case class Channel(
      id: ChannelId,
      name: Option[String] = None,
  ) derives Codec.AsObject

  case class Container(
      `type`: Option[String] = None,
      message_ts: Option[Timestamp] = None,
      channel_id: Option[ChannelId] = None,
      is_ephemeral: Option[Boolean] = None,
      view_id: Option[String] = None,
      attachment_id: Option[Int] = None,
      is_app_unfurl: Option[Boolean] = None,
  ) derives Codec.AsObject

  case class Action(
      action_id: String,
      block_id: String,
      `type`: String,
      action_ts: Timestamp,
      value: Option[String] = None,
      text: Option[TextObject] = None,
      selected_option: Option[SelectedOption] = None,
      selected_options: Option[List[SelectedOption]] = None,
      selected_date: Option[String] = None,
      selected_time: Option[String] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/interactivity/implementing-slash-commands
  case class SlashCommandPayload(
      command: String,
      text: String,
      user_id: UserId,
      channel_id: ChannelId,
      response_url: String,
      trigger_id: String,
      team_id: TeamId,
      team_domain: String,
      channel_name: String,
      api_app_id: String,
      token: Option[String] = None,
      user_name: Option[String] = None,
      enterprise_id: Option[String] = None,
      enterprise_name: Option[String] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/interaction-payloads/view-interactions-payload
  case class ViewSubmissionPayload(
      `type`: String,
      user: User,
      view: ViewPayload,
      api_app_id: String,
      team: Option[Team] = None,
      enterprise: Option[Json] = None,
      hash: Option[String] = None,
      response_urls: Option[List[Json]] = None,
      token: Option[String] = None,
  ) derives Codec.AsObject

  case class ViewPayload(
      id: String,
      `type`: Option[ViewType] = None,
      callback_id: Option[String] = None,
      state: Option[ViewState] = None,
      hash: Option[String] = None,
      title: Option[TextObject] = None,
      blocks: Option[List[Block]] = None,
      private_metadata: Option[String] = None,
  ) derives Codec.AsObject

  case class ViewState(
      values: Map[String, Map[String, ViewStateValue]],
  ) derives Codec.AsObject

  // https://docs.slack.dev/reference/interaction-payloads/view-interactions-payload
  // https://github.com/slackapi/java-slack-sdk/blob/main/slack-api-model/src/main/java/com/slack/api/model/view/ViewState.java
  case class ViewStateValue(
      `type`: Option[String] = None,
      value: Option[String] = None,
      selected_date: Option[String] = None,
      selected_time: Option[String] = None,
      selected_date_time: Option[Int] = None,
      selected_option: Option[SelectedOption] = None,
      selected_options: Option[List[SelectedOption]] = None,
      selected_conversation: Option[String] = None,
      selected_conversations: Option[List[String]] = None,
      selected_channel: Option[String] = None,
      selected_channels: Option[List[String]] = None,
      selected_user: Option[UserId] = None,
      selected_users: Option[List[UserId]] = None,
      timezone: Option[String] = None,
      rich_text_value: Option[Json] = None,
      files: Option[List[Json]] = None,
  ) derives Codec.AsObject

  case class SelectedOption(
      text: Option[TextObject] = None,
      value: String,
  ) derives Codec.AsObject

  // https://docs.slack.dev/interactivity/handling-user-interaction
  case class CommandResponsePayload(
      response_type: ResponseType,
      text: String,
      blocks: Option[List[Block]] = None,
      replace_original: Option[Boolean] = None,
      delete_original: Option[Boolean] = None,
  ) derives Codec.AsObject
}
