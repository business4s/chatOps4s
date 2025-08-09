package models

case class Message(
    text: String,
    interactions: Seq[Button] = Seq(),
)
