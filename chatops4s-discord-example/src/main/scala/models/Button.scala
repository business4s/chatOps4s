package models

import io.circe.generic.auto._

case class Button(
   label: String,
   value: String
)