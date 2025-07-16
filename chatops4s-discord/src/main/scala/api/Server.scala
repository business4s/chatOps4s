package api

import cats.effect.IO
import com.typesafe.scalalogging.{Logger, StrictLogging}
import enums.InteractionType
import io.circe.Json
import io.circe.parser.*
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import models.{DiscordResponse, InteractionContext}
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Hex
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint.Full

class Server(discordPublicKey: String) extends StrictLogging {
  sealed trait ErrorInfo
  private case class BadRequest(what: String) extends ErrorInfo
  private case class Unauthorized()           extends ErrorInfo
  private val discordInbound = new DiscordInbound()

  private val interactionEndpoint =
    endpoint.post
      .in("api" / "interactions")
      .in(header[String]("X-Signature-Ed25519"))
      .in(header[String]("X-Signature-Timestamp"))
      .in(stringBody)
      .errorOut(
        oneOf[ErrorInfo](
          oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized].description("Unauthorized")),
          oneOfVariant(StatusCode.BadRequest, jsonBody[BadRequest].description("Bad Request")),
        ),
      )
      .out(jsonBody[DiscordResponse])

  val interactionRoute: Full[Unit, Unit, (String, String, String), ErrorInfo, DiscordResponse, Any, IO] = interactionEndpoint.serverLogic(logic())

  private def verifySignature(
      publicKey: String,
      signature: String,
      timestamp: String,
      body: String,
  ): Boolean = {
    val publicKeyBytes = Hex.decode(publicKey.strip())
    val signatureBytes = Hex.decode(signature.strip())
    val message        = (timestamp.strip() + body.strip()).getBytes("UTF-8")
    val verifier       = new Ed25519Signer()
    verifier.init(false, new Ed25519PublicKeyParameters(publicKeyBytes, 0))
    verifier.update(message, 0, message.length)
    verifier.verifySignature(signatureBytes)
  }

  private def logic(): ((String, String, String)) => IO[Either[ErrorInfo, DiscordResponse]] = { case (signature, timestamp, body) =>
    if (!verifySignature(discordPublicKey, signature, timestamp, body)) {
      logger.info("Failed to authorize signature of request from Discord")
      IO.pure(Left(Unauthorized()))
    } else {
      parse(body) match {
        case Right(json) => processRequest(json)
        case Left(err)   =>
          logger.info("Failed to parse body send from Discord")
          IO.pure(Left(BadRequest(s"Parsing error: ${err.message}")))
      }
    }
  }

  private def processRequest(json: Json) = {
    val cursor = json.hcursor
    val _type  = cursor.get[Int]("type").toOption

    _type match {
      case Some(1) => IO.pure(Right(DiscordResponse(`type` = InteractionType.Ping.value))) // PING
      case Some(_) =>
        val customId  = cursor.downField("data").get[String]("custom_id").toOption
        val userId    = cursor.downField("member").downField("user").get[String]("id").toOption
        val channelId = cursor.get[String]("channel_id").toOption
        val messageId = cursor.downField("message").get[String]("id").toOption
        (customId, userId, channelId, messageId) match {
          case (Some(id), Some(uid), Some(cid), Some(mid)) =>
            val ctx = InteractionContext(uid, cid, mid)
            discordInbound.handlers.get(id) match {
              case Some(handler) =>
                handler(ctx).map(_ => Right(DiscordResponse(`type` = InteractionType.DeferredMessageUpdate.value)))
              case None          =>
                IO.pure(Right(DiscordResponse(`type` = InteractionType.DeferredMessageUpdate.value)))
            }
          case _                                           =>
            logger.info("Missing properties to handle interaction")
            IO.pure(Left(BadRequest("Missing interaction fields")))
        }
      case None    =>
        logger.info("Missing the type property to handle interaction")
        IO.pure(Left(BadRequest("Missing type property")))
    }
  }
}
