package models

import io.circe.generic.auto._

case class Message(
    text: String,
    interactions: Seq[Button] = Seq(),
)
