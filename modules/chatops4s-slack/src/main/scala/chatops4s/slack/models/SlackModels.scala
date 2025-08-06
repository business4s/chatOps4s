package chatops4s.slack.models

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.*
import pureconfig.ConfigReader

case class SlackConfig(
    botToken: String,
    signingSecret: String,
    port: Int = 3000,
) derives ConfigReader

case class SlackPostMessageRequest(
    channel: String,
    text: String,
    blocks: Option[List[SlackBlock]] = None,
    thread_ts: Option[String] = None,
)

object SlackPostMessageRequest {
  given Codec[SlackPostMessageRequest] = deriveCodec[SlackPostMessageRequest]
}

case class SlackBlock(
    `type`: String,
    text: Option[SlackText] = None,
    elements: Option[List[SlackBlockElement]] = None,
    accessory: Option[SlackBlockElement] = None,
)

object SlackBlock {
  given Codec[SlackBlock] = deriveCodec[SlackBlock]
}

case class SlackText(
    `type`: String,
    text: String,
)

object SlackText {
  given Codec[SlackText] = deriveCodec[SlackText]
}

case class SlackBlockElement(
    `type`: String,
    text: Option[SlackText] = None,
    action_id: Option[String] = None,
    value: Option[String] = None,
    style: Option[String] = None,
)

object SlackBlockElement {
  given Codec[SlackBlockElement] = deriveCodec[SlackBlockElement]
}

case class SlackPostMessageResponse(
    ok: Boolean,
    channel: Option[String] = None,
    ts: Option[String] = None,
    message: Option[SlackMessage] = None,
    error: Option[String] = None,
)

object SlackPostMessageResponse {
  given Codec[SlackPostMessageResponse] = deriveCodec[SlackPostMessageResponse]
}

case class SlackMessage(
    text: String,
    user: Option[String] = None,
    ts: String,
    thread_ts: Option[String] = None,
)

object SlackMessage {
  given Codec[SlackMessage] = deriveCodec[SlackMessage]
}

case class SlackInteractionPayload(
    `type`: String,
    user: SlackUser,
    container: SlackContainer,
    trigger_id: String,
    team: SlackTeam,
    channel: SlackChannel,
    message: Option[SlackMessage] = None,
    actions: Option[List[SlackAction]] = None,
    response_url: Option[String] = None,
)

object SlackInteractionPayload {
  given Codec[SlackInteractionPayload] = deriveCodec[SlackInteractionPayload]
}

case class SlackUser(
    id: String,
    name: String,
)

object SlackUser {
  given Codec[SlackUser] = deriveCodec[SlackUser]
}

case class SlackTeam(
    id: String,
    domain: String,
)

object SlackTeam {
  given Codec[SlackTeam] = deriveCodec[SlackTeam]
}

case class SlackChannel(
    id: String,
    name: String,
)

object SlackChannel {
  given Codec[SlackChannel] = deriveCodec[SlackChannel]
}

case class SlackContainer(
    `type`: String,
    message_ts: Option[String] = None,
)

object SlackContainer {
  given Codec[SlackContainer] = deriveCodec[SlackContainer]
}

case class SlackAction(
    action_id: String,
    block_id: Option[String] = None,
    text: SlackText,
    value: Option[String] = None,
    `type`: String,
    action_ts: String,
)

object SlackAction {
  given Codec[SlackAction] = deriveCodec[SlackAction]
}
