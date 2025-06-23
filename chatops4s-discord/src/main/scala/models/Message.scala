package models

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class MessageResponse(
  content: String,
  components: Seq[ActionRow]
)
object MessageResponse {
  implicit val decoder: Decoder[MessageResponse] = deriveDecoder
  implicit val encoder: Encoder[MessageResponse] = deriveEncoder
}

case class ActionRow(
  `type`: Int,
  components: Seq[Button]
)
object ActionRow {
  implicit val decoder: Decoder[ActionRow] = deriveDecoder
  implicit val encoder: Encoder[ActionRow] = deriveEncoder
}

case class Button(
 `type`: Int,
 style: Int,
 label: String,
 custom_id: String,
)
object Button {
  implicit val decoder: Decoder[Button] = deriveDecoder
  implicit val encoder: Encoder[Button] = deriveEncoder
}