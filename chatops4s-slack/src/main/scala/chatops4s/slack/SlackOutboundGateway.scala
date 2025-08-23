package chatops4s.slack

import cats.effect.{Sync, Ref}
import chatops4s.{Button, Message, MessageResponse, OutboundGateway}
import chatops4s.slack.models.*
import com.typesafe.scalalogging.StrictLogging

class SlackOutboundGateway[F[_]: Sync](
                                        slackClient: SlackClient[F],
                                        messageStore: Ref[F, Map[String, String]], // messageId -> channelId mapping
                                      ) extends OutboundGateway[F] with StrictLogging {

  override def sendToChannel(channelId: String, message: Message): F[MessageResponse] = {
    Sync[F].delay(logger.info(s"Sending message to channel: $channelId")) *>
      sendMessage(channelId, message, threadTs = None)
  }

  override def sendToThread(messageId: String, message: Message): F[MessageResponse] = {
    val (channelId, threadTs) = extractChannelAndTimestamp(messageId)
    Sync[F].delay(logger.info(s"Sending thread message to channel: $channelId, thread: $threadTs")) *>
      sendMessage(channelId, message, threadTs = Some(threadTs))
  }

  private def sendMessage(channelId: String, message: Message, threadTs: Option[String]): F[MessageResponse] = {
    val slackRequest = convertToSlackRequest(channelId, message, threadTs)

    slackClient.postMessage(slackRequest).flatMap { response =>
      response.ts match {
        case Some(timestamp) =>
          for {
            _        <- messageStore.update(_ + (timestamp -> channelId))
            messageId = s"$channelId-$timestamp"
            _        <- Sync[F].delay(logger.debug(s"Message stored with ID: $messageId"))
          } yield MessageResponse(messageId)
        case None            =>
          val errorMsg = "No message timestamp returned"
          Sync[F].delay(logger.error(errorMsg)) *>
            Sync[F].raiseError(new RuntimeException(errorMsg))
      }
    }
  }

  private def convertToSlackRequest(
                                     channelId: String,
                                     message: Message,
                                     threadTs: Option[String],
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
  def create[F[_]: Sync](slackClient: SlackClient[F]): F[SlackOutboundGateway[F]] = {
    Ref
      .of[F, Map[String, String]](Map.empty)
      .map(new SlackOutboundGateway[F](slackClient, _))
  }
}