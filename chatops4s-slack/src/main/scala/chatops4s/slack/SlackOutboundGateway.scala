package chatops4s.slack

import chatops4s.{Button, Message, MessageResponse, OutboundGateway}
import chatops4s.slack.models.*
import com.typesafe.scalalogging.StrictLogging

class SlackOutboundGateway[F[_]: Monad](
    slackClient: SlackClient[F],
    messageStore: Ref[F, Map[String, String]], // messageId -> channelId mapping
) extends OutboundGateway[F]
    with StrictLogging {

  private val M = Monad[F]

  override def sendToChannel(channelId: String, message: Message): F[MessageResponse] = {
    logger.info(s"Sending message to channel: $channelId")
    sendMessage(channelId, message, threadTs = None)
  }

  override def sendToThread(messageId: String, message: Message): F[MessageResponse] = {
    val (channelId, threadTs) = extractChannelAndTimestamp(messageId)
    logger.info(s"Sending thread message to channel: $channelId, thread: $threadTs")
    sendMessage(channelId, message, threadTs = Some(threadTs))
  }

  private def sendMessage(channelId: String, message: Message, threadTs: Option[String]): F[MessageResponse] = {
    val slackRequest = convertToSlackRequest(channelId, message, threadTs)

    M.flatMap(slackClient.postMessage(slackRequest)) { response =>
      response.ts match {
        case Some(timestamp) =>
          M.flatMap(messageStore.update(_ + (timestamp -> channelId))) { _ =>
            val messageId = s"$channelId-$timestamp"
            logger.debug(s"Message stored with ID: $messageId")
            M.pure(MessageResponse(messageId))
          }
        case None            =>
          val errorMsg = "No message timestamp returned"
          logger.error(errorMsg)
          M.raiseError(new RuntimeException(errorMsg))
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
  def create[F[_]: Monad](slackClient: SlackClient[F]): F[SlackOutboundGateway[F]] = {
    Monad[F].flatMap(Ref.of[F, Map[String, String]](Map.empty)) { ref =>
      Monad[F].pure(new SlackOutboundGateway[F](slackClient, ref))
    }
  }
}
