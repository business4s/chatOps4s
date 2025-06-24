package models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class MessageResponse(
  messageId: String
)
object MessageResponse {
  implicit val decoder: Decoder[MessageResponse] = deriveDecoder
  implicit val encoder: Encoder[MessageResponse] = deriveEncoder
}