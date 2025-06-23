package models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}


trait Registration:
  def `type`: Int

case class SlashRegistration(
   name: String,
   description: String
) extends Registration:
  override val `type`: Int = 4
object SlashRegistration {
  implicit val decoder: Decoder[SlashRegistration] = deriveDecoder
  implicit val encoder: Encoder[SlashRegistration] = deriveEncoder
}
