package models

import enums.InputType

case class Input(
  label: String,
  required: Boolean,
  `type`: InputType
)
