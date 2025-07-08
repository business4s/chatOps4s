package chatops4s.slack

import cats.effect.IO
import chatops4s.{Button, Message, MessageResponse, OutboundGateway}
import chatops4s.slack.models.*

class SlackOutboundGateway(slackClient: SlackClient) extends OutboundGateway {

  override def sendToChannel(channelId: String, message: Message): IO[MessageResponse] = {
    val slackRequest = convertToSlackRequest(channelId, message)

    slackClient.postMessage(slackRequest).flatMap { response =>
      if (response.ok) {
        response.ts match {
          case Some(timestamp) => IO.pure(MessageResponse(timestamp))
          case None => IO.raiseError(new RuntimeException("No message timestamp returned"))
        }
      } else {
        IO.raiseError(new RuntimeException(s"Slack API error: ${response.error.getOrElse("Unknown error")}"))
      }
    }
  }

  override def sendToThread(messageId: String, message: Message): IO[MessageResponse] = {
    // For thread replies,actually i need a way to store message ID i will do that here itself(for developer only)
    val channelId = extractChannelFromMessageId(messageId)
    val slackRequest = convertToSlackRequest(channelId, message, Some(messageId))

    slackClient.postMessage(slackRequest).flatMap { response =>
      if (response.ok) {
        response.ts match {
          case Some(timestamp) => IO.pure(MessageResponse(timestamp))
          case None => IO.raiseError(new RuntimeException("No message timestamp returned"))
        }
      } else {
        IO.raiseError(new RuntimeException(s"Slack API error: ${response.error.getOrElse("Unknown error")}"))
      }
    }
  }

  private def convertToSlackRequest(
                                     channelId: String,
                                     message: Message,
                                     threadTs: Option[String] = None
                                   ): SlackPostMessageRequest = {
    val blocks = if (message.interactions.nonEmpty) {
      Some(List(
        SlackBlock(
          `type` = "section",
          text = Some(SlackText(`type` = "mrkdwn", text = message.text))
        ),
        SlackBlock(
          `type` = "actions",
          elements = Some(message.interactions.map(convertButtonToSlackElement).toList)
        )
      ))
    } else None

    SlackPostMessageRequest(
      channel = channelId,
      text = message.text,
      blocks = blocks,
      thread_ts = threadTs
    )
  }

  private def convertButtonToSlackElement(button: Button): SlackBlockElement = {
    SlackBlockElement(
      `type` = "button",
      text = Some(SlackText(`type` = "plain_text", text = button.label)),
      action_id = Some(button.value),
      value = Some(button.value),
      style = Some("primary")
    )
  }

  private def extractChannelFromMessageId(messageId: String): String = {
    
    messageId.split("-").headOption.getOrElse(messageId)
  }
}