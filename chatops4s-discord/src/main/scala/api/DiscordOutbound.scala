package api

import io.circe.*
import io.circe.syntax.*
import cats.effect.IO
import models.*
import sttp.client4.circe.asJson
import sttp.client4._
import sttp.client4.httpclient.cats.HttpClientCatsBackend

class DiscordOutbound(token: String) extends OutboundGateway {
  private val baseUrl = "https://discord.com/api/v10"

  override def sendToChannel(channelId: String, message: Message): IO[MessageResponse] = {
    val json = Json.obj(
      "content" := message.text,
      "components" := List(Json.arr(
        message.interactions.map { b =>
          Json.obj(
            "type" := 2,
            "style" := 1,
            "label" := b.label,
            "custom_id" := b.value
          )
        }: _*
      ))
    )

    val request = basicRequest
      .post(uri"https://discord.com/api/v10/channels/$channelId/messages")
      .auth.bearer(token)
      .body(json.noSpaces)
      .response(asJson[Json])

    HttpClientCatsBackend.resource[IO]().use { backend =>
      request.send(backend).flatMap { response =>
        response.body match {
          case Right(json) =>
            val msgId = json.hcursor.get[String]("id").getOrElse("")
            IO.pure(MessageResponse(msgId))
          case Left(error) =>
            IO.raiseError(new RuntimeException(s"Failed to send message: $error"))
        }
      }
    }
  }

  override def sendToThread(messageId: String, message: Message): IO[MessageResponse] = {
    IO.raiseError(new NotImplementedError("Not yet implemented"))
  }
}
