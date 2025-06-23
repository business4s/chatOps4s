package models

trait Action:
  val channelId: String

case class AcceptDeclineAction(
    channelId: String,
    message: String
) extends Action

case class FormAction(
   channelId: String,
   inputs: Seq[Input]
) extends Action
