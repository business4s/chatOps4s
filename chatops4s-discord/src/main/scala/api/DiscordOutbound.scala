package api

import io.circe.*
import io.circe.syntax.*
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import models.*
import sttp.client4.circe.*
import sttp.client4.*

// TODO Parametrize with F[_] and remove cats dependency
class DiscordOutbound(token: String, url: String, backend: Backend[IO]) extends OutboundGateway, StrictLogging {
  final private val rootUrl       = "https://discord.com/api/v10"
  final private val versionNumber = 1.0

  private def baseRequest = basicRequest
    .header("Authorization", s"Bot $token")
    .header("User-Agent", s"DiscordBot ($url, $versionNumber)")
    .header("Content-Type", "application/json")

  override def sendToChannel(channelId: String, message: Message): IO[MessageResponse] = {
    // TODO logging should be inside IO
    logger.info(s"Sending message to channel $channelId: $message")
    val json = if (message.interactions.nonEmpty) {
      Json.obj(
        "content"    := message.text,
        "components" := Json.arr(
          Json.obj(
            "type"       := ContentType.ActionRow.value,
            "components" := message.interactions.map { b =>
              Json.obj(
                "type"      := ContentType.Button.value,
                "style"     := ButtonStyle.Primary.value,
                "label"     := b.label,
                "custom_id" := b.value,
              )
            },
          ),
        ),
      )
    } else {
      Json.obj(
        "content" := message.text,
      )
    }

    val request = baseRequest
      .post(uri"$rootUrl/channels/$channelId/messages")
      .body(json.noSpaces)
      .response(asJson[Json])

    request.send(backend).flatMap { response =>
      response.body match {
        case Right(json) =>
          val messageId = json.hcursor.get[String]("id").getOrElse("")
          logger.info("Message sent to Discord")
          IO.pure(MessageResponse(messageId = messageId))
        case Left(error) =>
          logger.info(s"Failed to send message: $error")
          IO.raiseError(new RuntimeException(s"Failed to send message: $error"))
      }
    }
  }

  override def replyToMessage(channelId: String, messageId: String, message: Message): IO[MessageResponse] = {
    // TODO logging should be inside IO
    logger.info(s"Replying to message $messageId in channel $channelId: $message")

    val baseJson = Json.obj(
      "content"           := message.text,
      "message_reference" := Json.obj(
        "message_id"         := messageId,
        "channel_id"         := channelId,
        "fail_if_not_exists" := false,
      ),
    )

    val json =
      if (message.interactions.nonEmpty) {
        baseJson.deepMerge(
          Json.obj(
            "components" := Json.arr(
              Json.obj(
                "type"       := ContentType.ActionRow.value,
                "components" := message.interactions.map { b =>
                  Json.obj(
                    "type"      := ContentType.Button.value,
                    "style"     := ButtonStyle.Primary.value,
                    "label"     := b.label,
                    "custom_id" := b.value,
                  )
                },
              ),
            ),
          ),
        )
      } else baseJson

    val request = baseRequest
      .post(uri"$rootUrl/channels/$channelId/messages")
      .body(json.noSpaces)
      .response(asJson[Json])

    request.send(backend).flatMap { response =>
      response.body match {
        case Right(json) =>
          val newMessageId = json.hcursor.get[String]("id").getOrElse("")
          logger.info("Reply sent to Discord")
          IO.pure(MessageResponse(messageId = newMessageId))
        case Left(error) =>
          logger.error(s"Failed to send reply: $error")
          IO.raiseError(new RuntimeException(s"Failed to send reply: $error"))
      }
    }
  }

  override def sendToThread(channelId: String, threadName: String, message: Message): IO[MessageResponse] = {
    val createThreadJson = Json.obj(
      "name" := threadName,
    )

    val createThreadRequest = baseRequest
      .post(uri"$rootUrl/channels/$channelId/threads")
      .body(asJson(createThreadJson))
      .response(asJson[Json])

    createThreadRequest.send(backend).flatMap { response =>
      response.body match {
        case Right(json) =>
          val cursor = json.hcursor
          cursor.get[String]("id") match {
            case Right(threadId) =>
              logger.info(s"Created thread $threadName with id $threadId")
              sendToChannel(threadId, message)
            case Left(err)       =>
              IO.raiseError(new RuntimeException(s"Could not extract 'id': $err"))
          }
        case Left(error) =>
          IO.raiseError(new RuntimeException(s"Request failed: $error"))
      }
    }
  }
}
