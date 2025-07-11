package chatops4s.slack.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class SlackConfig(
                        botToken: String,
                        signingSecret: String,
                        port: Int = 3000
                      )

// Slack API request models
case class SlackPostMessageRequest(
                                    channel: String,
                                    text: String,
                                    blocks: Option[List[SlackBlock]] = None,
                                    thread_ts: Option[String] = None
                                  )

case class SlackBlock(
                       `type`: String,
                       text: Option[SlackText] = None,
                       elements: Option[List[SlackBlockElement]] = None,
                       accessory: Option[SlackBlockElement] = None
                     )

case class SlackText(
                      `type`: String,
                      text: String
                    )

case class SlackBlockElement(
                              `type`: String,
                              text: Option[SlackText] = None,
                              action_id: Option[String] = None,
                              value: Option[String] = None,
                              style: Option[String] = None
                            )

// Slack API response models
case class SlackPostMessageResponse(
                                     ok: Boolean,
                                     channel: Option[String] = None,
                                     ts: Option[String] = None,
                                     message: Option[SlackMessage] = None,
                                     error: Option[String] = None
                                   )

case class SlackMessage(
                         text: String,
                         user: Option[String] = None,
                         ts: String,
                         thread_ts: Option[String] = None
                       )

// Slack interaction payload models
case class SlackInteractionPayload(
                                    `type`: String,
                                    user: SlackUser,
                                    container: SlackContainer,
                                    trigger_id: String,
                                    team: SlackTeam,
                                    channel: SlackChannel,
                                    message: Option[SlackMessage] = None,
                                    actions: Option[List[SlackAction]] = None,
                                    response_url: Option[String] = None
                                  )

case class SlackUser(
                      id: String,
                      name: String
                    )

case class SlackTeam(
                      id: String,
                      domain: String
                    )

case class SlackChannel(
                         id: String,
                         name: String
                       )

case class SlackContainer(
                           `type`: String,
                           message_ts: Option[String] = None
                         )

case class SlackAction(
                        action_id: String,
                        block_id: Option[String] = None,
                        text: SlackText,
                        value: Option[String] = None,
                        `type`: String,
                        action_ts: String
                      )

// JSON codecs
object SlackModels {
  implicit val slackTextEncoder: Encoder[SlackText] = deriveEncoder
  implicit val slackTextDecoder: Decoder[SlackText] = deriveDecoder

  implicit val slackBlockElementEncoder: Encoder[SlackBlockElement] = deriveEncoder
  implicit val slackBlockElementDecoder: Decoder[SlackBlockElement] = deriveDecoder

  implicit val slackBlockEncoder: Encoder[SlackBlock] = deriveEncoder
  implicit val slackBlockDecoder: Decoder[SlackBlock] = deriveDecoder

  implicit val slackPostMessageRequestEncoder: Encoder[SlackPostMessageRequest] = deriveEncoder
  implicit val slackPostMessageRequestDecoder: Decoder[SlackPostMessageRequest] = deriveDecoder

  implicit val slackMessageEncoder: Encoder[SlackMessage] = deriveEncoder
  implicit val slackMessageDecoder: Decoder[SlackMessage] = deriveDecoder

  implicit val slackPostMessageResponseEncoder: Encoder[SlackPostMessageResponse] = deriveEncoder
  implicit val slackPostMessageResponseDecoder: Decoder[SlackPostMessageResponse] = deriveDecoder

  implicit val slackUserEncoder: Encoder[SlackUser] = deriveEncoder
  implicit val slackUserDecoder: Decoder[SlackUser] = deriveDecoder

  implicit val slackTeamEncoder: Encoder[SlackTeam] = deriveEncoder
  implicit val slackTeamDecoder: Decoder[SlackTeam] = deriveDecoder

  implicit val slackChannelEncoder: Encoder[SlackChannel] = deriveEncoder
  implicit val slackChannelDecoder: Decoder[SlackChannel] = deriveDecoder

  implicit val slackContainerEncoder: Encoder[SlackContainer] = deriveEncoder
  implicit val slackContainerDecoder: Decoder[SlackContainer] = deriveDecoder

  implicit val slackActionEncoder: Encoder[SlackAction] = deriveEncoder
  implicit val slackActionDecoder: Decoder[SlackAction] = deriveDecoder

  implicit val slackInteractionPayloadEncoder: Encoder[SlackInteractionPayload] = deriveEncoder
  implicit val slackInteractionPayloadDecoder: Decoder[SlackInteractionPayload] = deriveDecoder
}