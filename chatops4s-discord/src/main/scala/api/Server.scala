package api

import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import io.circe.*
import io.circe.parser.*
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import models.{DiscordResponse, InteractionContext, InteractionType, RequestInteractionData}
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Hex
import sttp.model.StatusCode
import sttp.tapir.server.ServerEndpoint

class Server(discordPublicKey: String, discordInbound: DiscordInbound) extends StrictLogging {
  sealed trait ErrorInfo
  private case class BadRequest(what: String) extends ErrorInfo
  private case class Unauthorized()           extends ErrorInfo

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

  val interactionRoute: ServerEndpoint.Full[Unit, Unit, (String, String, String), ErrorInfo, DiscordResponse, Any, IO] =
    interactionEndpoint.serverLogic { case (signature, timestamp, body) =>
      logic(signature, timestamp, body)
    }

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

  private def logic(signature: String, timestamp: String, body: String): IO[Either[ErrorInfo, DiscordResponse]] = {
    if (!verifySignature(discordPublicKey, signature, timestamp, body)) {
      logger.info("Failed to authorize signature of request from Discord")
      IO.pure(Left(Unauthorized()))
    } else {
      parse(body) match {
        case Right(json) => processRequest(json).map(_.left.map(BadRequest.apply))
        case Left(err)   =>
          logger.info("Failed to parse body sent from Discord")
          IO.pure(Left(BadRequest(s"Parsing error: ${err.message}")))
      }
    }
  }

  private def processRequest(json: Json): IO[Either[String, DiscordResponse]] = {
    json.as[RequestInteractionData] match {
      case Left(err) =>
        logger.info(s"Failed to decode interaction JSON: ${err.getMessage}")
        IO.pure(Left("Invalid interaction payload"))
      case Right(interaction) =>
        interaction.`type` match {
          case 1 => IO.pure(Right(DiscordResponse(`type` = InteractionType.Ping.value))) // PING
          case _ =>
            (for {
              data      <- interaction.data.toRight("Missing data")
              customId   = data.custom_id
              member    <- interaction.member.toRight("Missing member")
              userId     = member.user.id
              channelId <- interaction.channel_id.toRight("Missing channel_id")
              message   <- interaction.message.toRight("Missing message")
              messageId  = message.id
            } yield (customId, userId, channelId, messageId)) match {
              case Right((id, uid, cid, mid)) =>
                val ctx = InteractionContext(uid, cid, mid)
                discordInbound.handlers.get(id) match {
                  case Some(handler) =>
                    handler(ctx).map(_ => Right(DiscordResponse(`type` = InteractionType.DeferredMessageUpdate.value)))
                  case None =>
                    IO.pure(Right(DiscordResponse(`type` = InteractionType.DeferredMessageUpdate.value)))
                }
              case Left(missing) =>
                logger.info(s"Missing properties to handle interaction: $missing")
                IO.pure(Left(missing))
            }
        }
    }
  }
}
