package chatops4s.slack

import chatops4s.{Button, Message, MessageResponse, OutboundGateway}
import chatops4s.slack.models.*
import com.typesafe.scalalogging.StrictLogging
import scala.collection.mutable
import sttp.monad.MonadError

class SlackOutboundGateway[F[_]](
    slackClient: SlackClient[F],
    private val messageStore: mutable.Map[String, String], // messageId -> channelId mapping
    implicit val monad: MonadError[F],
) extends OutboundGateway[F]
    with StrictLogging {

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

    monad.flatMap(slackClient.postMessage(slackRequest)) { response =>
      response.ts match {
        case Some(timestamp) =>
          val messageId = s"$channelId-$timestamp"
          messageStore += (timestamp -> channelId)
          logger.debug(s"Message stored with ID: $messageId")
          monad.unit(MessageResponse(messageId))
        case None            =>
          val errorMsg = "No message timestamp returned"
          logger.error(errorMsg)
          monad.error(new RuntimeException(errorMsg))
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
  def create[F[_]](slackClient: SlackClient[F])(implicit monad: MonadError[F]): F[SlackOutboundGateway[F]] = {
    val messageStore = mutable.Map.empty[String, String]
    monad.unit(new SlackOutboundGateway[F](slackClient, messageStore, monad))
  }
}
