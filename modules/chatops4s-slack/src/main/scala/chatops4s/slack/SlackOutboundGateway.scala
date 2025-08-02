package chatops4s.slack

import cats.effect.{IO, Ref}
import chatops4s.{Button, Message, MessageResponse, OutboundGateway}
import chatops4s.slack.models.*

class SlackOutboundGateway(
    slackClient: SlackClient,
    messageStore: Ref[IO, Map[String, String]], // messageId -> channelId mapping
) extends OutboundGateway {

  override def sendToChannel(channelId: String, message: Message): IO[MessageResponse] = {
    val slackRequest = convertToSlackRequest(channelId, message)

    slackClient.postMessage(slackRequest).flatMap { response =>
      if (response.ok) {
        response.ts match {
          case Some(timestamp) =>
            for {
              _        <- messageStore.update(_ + (timestamp -> channelId))
              messageId = s"$channelId-$timestamp" // Store channel info in messageId
            } yield MessageResponse(messageId)
          case None            => IO.raiseError(new RuntimeException("No message timestamp returned"))
        }
      } else {
        IO.raiseError(new RuntimeException(s"Slack API error: ${response.error.getOrElse("Unknown error")}"))
      }
    }
  }

  override def sendToThread(messageId: String, message: Message): IO[MessageResponse] = {
    val (channelId, threadTs) = extractChannelAndTimestamp(messageId)
    val slackRequest          = convertToSlackRequest(channelId, message, Some(threadTs))

    slackClient.postMessage(slackRequest).flatMap { response =>
      if (response.ok) {
        response.ts match {
          case Some(timestamp) =>
            for {
              _           <- messageStore.update(_ + (timestamp -> channelId))
              newMessageId = s"$channelId-$timestamp"
            } yield MessageResponse(newMessageId)
          case None            => IO.raiseError(new RuntimeException("No message timestamp returned"))
        }
      } else {
        IO.raiseError(new RuntimeException(s"Slack API error: ${response.error.getOrElse("Unknown error")}"))
      }
    }
  }

  private def convertToSlackRequest(
      channelId: String,
      message: Message,
      threadTs: Option[String] = None,
  ): SlackPostMessageRequest = {
    val blocks = if (message.interactions.nonEmpty) {
      Some(
        List(
          SlackBlock(
            `type` = "section",
            text = Some(SlackText(`type` = "mrkdwn", text = message.text)),
          ),
          SlackBlock(
            `type` = "actions",
            elements = Some(message.interactions.map(convertButtonToSlackElement).toList),
          ),
        ),
      )
    } else None

    SlackPostMessageRequest(
      channel = channelId,
      text = message.text,
      blocks = blocks,
      thread_ts = threadTs,
    )
  }

  private def convertButtonToSlackElement(button: Button): SlackBlockElement = {
    SlackBlockElement(
      `type` = "button",
      text = Some(SlackText(`type` = "plain_text", text = button.label)),
      action_id = Some(button.value),
      value = Some(button.value),
      style = Some("primary"),
    )
  }

  private def extractChannelAndTimestamp(messageId: String): (String, String) = {
    messageId.split("-", 2) match {
      case Array(channelId, timestamp) => (channelId, timestamp)
      case _                           => throw new IllegalArgumentException(s"Invalid message ID format: $messageId")
    }
  }
}

object SlackOutboundGateway {
  def create(slackClient: SlackClient): IO[SlackOutboundGateway] = {
    Ref
      .of[IO, Map[String, String]](Map.empty)
      .map(new SlackOutboundGateway(slackClient, _))
  }
}
