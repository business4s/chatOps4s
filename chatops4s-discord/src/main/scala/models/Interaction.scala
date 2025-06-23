package models

import enums.AcceptDeclineCustomId
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class InteractionRequest(
  `type`: Int,
  token: Option[String] = null,
  id: String,
  data: Option[InteractionRequestData] = null
)
object InteractionRequest {
  implicit val decoder: Decoder[InteractionRequest] = deriveDecoder
  implicit val encoder: Encoder[InteractionRequest] = deriveEncoder
}

case class InteractionRequestData(
  custom_id: Option[String],
  component_type: Option[Int]
)
object InteractionRequestData {
  implicit val decoder: Decoder[InteractionRequestData] = deriveDecoder
  implicit val encoder: Encoder[InteractionRequestData] = deriveEncoder
}

trait Interaction:
  def `type`: Int
  def ephemeral: Boolean
  def content(incoming: InteractionRequest): String

case class SlashInteraction(
  message: String,
  ephemeral: Boolean
) extends Interaction:
  override val `type`: Int = 4
  override def content(incoming: InteractionRequest): String = message

object SlashInteraction {
  implicit val decoder: Decoder[SlashInteraction] = deriveDecoder
  implicit val encoder: Encoder[SlashInteraction] = deriveEncoder
}

case class Interactions(
  acceptDeclineInteraction: Option[AcceptDeclineInteraction] = None,
  slashInteraction: Option[SlashInteraction] = None,
  formInteraction: Option[FormInteraction] = None
)

case class AcceptDeclineInteraction(
   decliningMessage: String,
   acceptingMessage: String,
   ephemeral: Boolean
) extends Interaction:
  override val `type`: Int = 4
  override def content(incoming: InteractionRequest): String =
    incoming.data.get.custom_id match
      case Some(AcceptDeclineCustomId.Accept.value) => acceptingMessage
      case Some(AcceptDeclineCustomId.Decline.value) => decliningMessage
      case _ => "Could not recognize decision. Please try again."

object AcceptDeclineInteraction {
  implicit val decoder: Decoder[AcceptDeclineInteraction] = deriveDecoder
  implicit val encoder: Encoder[AcceptDeclineInteraction] = deriveEncoder
}

case class FormInteraction(
  message: String,
  ephemeral: Boolean
) extends Interaction:
  override val `type`: Int = 4
  override def content(incoming: InteractionRequest): String = message

object FormInteraction {
  implicit val decoder: Decoder[FormInteraction] = deriveDecoder
  implicit val encoder: Encoder[FormInteraction] = deriveEncoder
}

case class InteractionResponse(
  `type`: Int,
  data: Option[InteractionResponseData] = None
)
object InteractionResponse {
  implicit val decoder: Decoder[InteractionResponse] = deriveDecoder
  implicit val encoder: Encoder[InteractionResponse] = deriveEncoder
}

case class InteractionResponseData(
  content: String,
  flags: Int = 64
)
object InteractionResponseData {
  implicit val decoder: Decoder[InteractionResponseData] = deriveDecoder
  implicit val encoder: Encoder[InteractionResponseData] = deriveEncoder
}
