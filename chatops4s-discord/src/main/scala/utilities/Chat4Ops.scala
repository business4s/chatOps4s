package utilities

import enums.InteractionType.{AcceptDecline, Form, Slash, Ping}
import models.*

import scala.collection.parallel.CollectionConverters.*

object Chat4Ops {
  def executeActions(actions: Seq[Action]): Boolean = {
    actions.par.map(action => this.executeAction(action)).forall(_ == true)
  }

  def executeAction(action: Action): Boolean = {
    action match {
      case AcceptDeclineAction(channelId, message) =>
        DiscordBot.sendAcceptDeclineMessage(
          channelId = channelId,
          content = message
        )
        true
      case FormAction(inputs, channelId) =>
        println(s"handle form with channelId $channelId")
        true
      case _ => false
    }
  }

  def executeRegistration(registration: Registration): Boolean = {
    registration match {
      case slashRegistration: SlashRegistration =>
        DiscordBot.sendSlashRegistration(slashRegistration)
        true
      case _ => false
    }
  }

  def executeInteraction(interactionRequest: InteractionRequest, interactions: Interactions): Option[InteractionResponse] = {
    interactionRequest.`type` match {
      case Ping.value =>
        Some(InteractionResponse(
          `type` = 1
        ))
      case AcceptDecline.value if interactions.acceptDeclineInteraction.isDefined =>
        Some(DiscordBot.sendInteraction(
          incoming = interactionRequest,
          interaction = interactions.acceptDeclineInteraction.get,
        ))
      case Slash.value if interactions.slashInteraction.isDefined =>
        Some(DiscordBot.sendInteraction(
          incoming = interactionRequest,
          interaction = interactions.slashInteraction.get,
        ))
      case Form.value if interactions.formInteraction.isDefined =>
        Some(DiscordBot.sendInteraction(
          incoming = interactionRequest,
          interaction = interactions.formInteraction.get
        ))
      case _ => null
    }
  }
}
