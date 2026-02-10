package chatops4s.slack

case class MessageId(channel: String, ts: String)

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
    channelId: String,
    text: String,
)

sealed trait CommandResponse
object CommandResponse {
  case class Ephemeral(text: String) extends CommandResponse
  case class InChannel(text: String) extends CommandResponse
  case object Silent                 extends CommandResponse
}
