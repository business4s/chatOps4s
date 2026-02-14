package chatops4s.slack.api

import io.circe.{Codec, Json}

object socket {

  case class Envelope(
      envelope_id: String,
      `type`: String,
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
      message: Option[Json] = None,
      view: Option[Json] = None,
      state: Option[Json] = None,
      response_url: Option[String] = None,
      hash: Option[String] = None,
  ) derives Codec.AsObject

  case class User(
      id: String,
      username: Option[String] = None,
      name: Option[String] = None,
      team_id: Option[String] = None,
  ) derives Codec.AsObject

  case class Team(
      id: String,
      domain: Option[String] = None,
  ) derives Codec.AsObject

  case class Channel(
      id: String,
      name: Option[String] = None,
  ) derives Codec.AsObject

  case class Container(
      `type`: Option[String] = None,
      message_ts: Option[String] = None,
      channel_id: Option[String] = None,
      is_ephemeral: Option[Boolean] = None,
      view_id: Option[String] = None,
      attachment_id: Option[Int] = None,
      is_app_unfurl: Option[Boolean] = None,
  ) derives Codec.AsObject

  case class Action(
      action_id: String,
      block_id: String,
      `type`: String,
      action_ts: String,
      value: Option[String] = None,
      text: Option[Json] = None,
      selected_option: Option[Json] = None,
      selected_options: Option[List[Json]] = None,
      selected_date: Option[String] = None,
      selected_time: Option[String] = None,
  ) derives Codec.AsObject

  // https://docs.slack.dev/interactivity/implementing-slash-commands
  case class SlashCommandPayload(
      command: String,
      text: String,
      user_id: String,
      channel_id: String,
      response_url: String,
      trigger_id: String,
      team_id: String,
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
      `type`: Option[String] = None,
      callback_id: Option[String] = None,
      state: Option[ViewState] = None,
      hash: Option[String] = None,
      title: Option[Json] = None,
      blocks: Option[List[Json]] = None,
      private_metadata: Option[String] = None,
  ) derives Codec.AsObject

  case class ViewState(
      values: Map[String, Map[String, Json]],
  ) derives Codec.AsObject

  // https://docs.slack.dev/interactivity/handling-user-interaction
  case class CommandResponsePayload(
      response_type: String,
      text: String,
      blocks: Option[List[Json]] = None,
      replace_original: Option[Boolean] = None,
      delete_original: Option[Boolean] = None,
  ) derives Codec.AsObject
}
