package api

import cats.effect.IO
import io.circe.Json
import io.circe.parser._
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._
import io.circe.generic.auto._
import models.{DiscordResponse, InteractionContext}
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Hex
import sttp.model.StatusCode

object Server {
  sealed trait ErrorInfo
  private case class BadRequest(what: String) extends ErrorInfo
  private case class Unauthorized() extends ErrorInfo
  private val discordInbound = new DiscordInbound()
  val interactionEndpoint: Endpoint[Unit, (String, String, String), ErrorInfo, DiscordResponse, Any] =
    endpoint.post
      .in("api" / "interactions")
      .in(header[String]("X-Signature-Ed25519"))
      .in(header[String]("X-Signature-Timestamp"))
      .in(stringBody)
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized].description("Unauthorized")),
          oneOfVariant(StatusCode.BadRequest, jsonBody[BadRequest].description("Bad Request"))
        )
      )
      .out(jsonBody[DiscordResponse])

  private def verifySignature(
     publicKey: String,
     signature: String,
     timestamp: String,
     body: String
   ): Boolean = {
    val publicKeyBytes = Hex.decode(publicKey.strip())
    val signatureBytes = Hex.decode(signature.strip())
    val message = (timestamp.strip() + body.strip()).getBytes("UTF-8")
    val verifier = new Ed25519Signer()
    verifier.init(false, new Ed25519PublicKeyParameters(publicKeyBytes, 0))
    verifier.update(message, 0, message.length)
    verifier.verifySignature(signatureBytes)
  }

  def logic(discordPublicKey: String): ((String, String, String)) => IO[Either[ErrorInfo, DiscordResponse]] = {
    case (signature, timestamp, body) =>
      if (!verifySignature(discordPublicKey, signature, timestamp, body)) {
        IO.pure(Left(Unauthorized()))
      } else {
        parse(body) match {
          case Right(json) => processRequest(json)
          case Left(err)   => IO.pure(Left(BadRequest(s"Parsing error: ${err.message}")))
        }
      }
  }

  private def processRequest(json: Json) = {
    val cursor = json.hcursor
    val _type = cursor.get[Int]("type").toOption

    _type match {
      case Some(1) => IO.pure(Right(DiscordResponse(`type` = 1))) // PING
      case Some(_) =>
        val customId = cursor.downField("data").get[String]("custom_id").toOption
        val userId = cursor.downField("member").downField("user").get[String]("id").toOption
        val channelId = cursor.get[String]("channel_id").toOption
        val messageId = cursor.downField("message").get[String]("id").toOption
        (customId, userId, channelId, messageId) match {
          case (Some(id), Some(uid), Some(cid), Some(mid)) =>
            val ctx = InteractionContext(uid, cid, mid)
            discordInbound.handlers.get(id) match {
              case Some(handler) =>
                handler(ctx).map(_ => Right(DiscordResponse(`type` = 6)))
              case None =>
                IO.pure(Right(DiscordResponse(`type` = 6)))
            }
          case _ =>
            IO.pure(Left(BadRequest("Missing interaction fields")))
        }
      case None => IO.pure(Left(BadRequest("Missing type field")))
    }
  }
}
