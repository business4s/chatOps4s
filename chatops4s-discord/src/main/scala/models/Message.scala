package models

import interactions.Button

case class Message(
  text: String,
  interactions: Seq[Button] = Seq()
)