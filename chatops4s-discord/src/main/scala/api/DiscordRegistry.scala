package api

import cats.effect.IO
import models.InteractionContext
import models.Message
import models.MessageResponse

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class DiscordRegistry {
  val interactions: TrieMap[String, (Message, (String, Message) => IO[MessageResponse])] = TrieMap.empty

  def registerInteraction(message: Message, sender: (String, Message) => IO[MessageResponse]): String = {
    val id = java.util.UUID.randomUUID().toString.take(n = 8)
    interactions += id -> (message, sender)
    id
  }

  def getInteraction(id: String): (Message, (String, Message) => IO[MessageResponse]) = {
    val maybeInteraction = interactions.get(id)
    maybeInteraction match {
      case Some(interaction) => interaction
      case None => throw RuntimeException(s"Interaction with id $id not found")
    }
  }

  def removeInteraction(id: String): Boolean = {
    val maybeInteraction = interactions.get(id)
    maybeInteraction match {
      case Some(interaction) =>
        interactions.remove(id)
        true
      case None => false
    }
  }
}
