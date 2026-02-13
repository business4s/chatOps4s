package chatops4s.slack

import chatops4s.slack.api.ChannelId

case class MessageId(channel: ChannelId, ts: String)

case class TriggerId(value: String)

case class ButtonId[T <: String](value: String) {
  def toButton(label: String, value: T): Button = Button(label, this, value)
}

case class Button private (label: String, actionId: String, value: String)

object Button {
  def apply[T <: String](label: String, id: ButtonId[T], value: T): Button =
    new Button(label, id.value, value)
}

case class ButtonClick[T <: String](
    userId: String,
    messageId: MessageId,
    value: T,
    triggerId: TriggerId,
    threadId: Option[MessageId] = None,
)

trait CommandParser[T] {
  def parse(text: String): Either[String, T]
}

object CommandParser {
  given CommandParser[String] with {
    def parse(text: String): Either[String, String] = Right(text)
  }
}

case class Command[T](
    args: T,
    userId: String,
    channelId: ChannelId,
    text: String,
    triggerId: TriggerId,
)

sealed trait CommandResponse
object CommandResponse {
  case class Ephemeral(text: String) extends CommandResponse
  case class InChannel(text: String) extends CommandResponse
  case object Silent extends CommandResponse
}
