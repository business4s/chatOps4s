package api

import io.circe.*
import io.circe.syntax.*
import com.typesafe.scalalogging.StrictLogging
import models.*
import sttp.client4.circe.*
import sttp.client4.*
import sttp.monad.MonadError
import sttp.monad.syntax.*

class DiscordOutbound[F[_]](token: String, url: String, backend: Backend[F]) extends OutboundGateway[F], StrictLogging {
  final private val rootUrl       = "https://discord.com/api/v10"
  final private val versionNumber = 1.0
  given m: MonadError[F]          = backend.monad

  private def baseRequest = basicRequest
    .header("Authorization", s"Bot $token")
    .header("User-Agent", s"DiscordBot ($url, $versionNumber)")
    .header("Content-Type", "application/json")

  override def sendToChannel(channelId: String, message: Message): F[MessageResponse] = {
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
      .response(asJsonOrFail[MessageResponse])

    for {
      _    <- m.eval(logger.info(s"Sending message to channel $channelId: $message"))
      resp <- request.send(backend)
      _    <- m.eval(logger.info("Message sent to Discord"))
    } yield resp.body

  }

  override def replyToMessage(channelId: String, messageId: String, message: Message): F[MessageResponse] = {
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
      .response(asJsonOrFail[MessageResponse])

    for {
      _    <- m.eval(logger.info(s"Replying to message $messageId in channel $channelId: $message"))
      resp <- request.send(backend)
      _    <- m.eval(logger.info("Reply sent to Discord"))
    } yield resp.body
  }

  override def sendToThread(channelId: String, threadName: String, message: Message): F[MessageResponse] = {
    val createThreadJson = Json.obj(
      "name" := threadName,
    )

    val createThreadRequest = baseRequest
      .post(uri"$rootUrl/channels/$channelId/threads")
      .body(asJson(createThreadJson))
      .response(asJsonOrFail[ThreadResponse])

    for {
      threadResp <- createThreadRequest.send(backend)
      threadId    = threadResp.body.id
      _          <- m.eval(logger.info(s"Created thread $threadName with id ${threadId}"))
      msgResp    <- sendToChannel(threadId, message)
    } yield msgResp

  }
}
