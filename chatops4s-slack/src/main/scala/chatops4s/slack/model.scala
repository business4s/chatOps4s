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
)

case class SlackApiException(error: String, details: List[String] = Nil)
    extends RuntimeException(s"Slack API error: $error${if (details.nonEmpty) s". ${details.mkString("; ")}" else ""}")
