package api

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import io.circe.Json
import models.{Button, DiscordResponse, InteractionContext, Message, MessageResponse}
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Hex
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.model.StatusCode
import io.circe.parser.*
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import utilities.EnvLoader

object Server extends IOApp:
  private sealed trait ErrorInfo
  private case class BadRequest(what: String) extends ErrorInfo
  private case class Unauthorized() extends ErrorInfo
  private val discordInbound = new DiscordInbound()

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

  private val logic: (String, String, Json) => IO[Either[ErrorInfo, DiscordResponse]] = (_, _, json) => {
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
            println(discordInbound.handlers)
            discordInbound.handlers.get(id) match {
              case Some(handler) =>
                handler(ctx).map(_ => Right(DiscordResponse(`type` = 6))) // ACK with update
              case None =>
                IO.pure(Right(DiscordResponse(`type` = 6)))
            }
          case _ =>
            IO.pure(Left(BadRequest("Missing interaction fields")))
        }
      case None => IO.pure(Left(BadRequest("Missing type field")))
    }
  }

  private val interactionEndpoint = endpoint.post
    .in("api" / "interactions")
    .in(header[String]("X-Signature-Ed25519"))
    .in(header[String]("X-Signature-Timestamp"))
    .in(stringBody)
    .errorOut(
      oneOf(
        oneOfVariant(StatusCode.Unauthorized, jsonBody[Unauthorized].description("Unauthorized")),
        oneOfVariant(StatusCode.BadRequest, jsonBody[BadRequest].description("Bad Request"))
      )
    )
    .out(jsonBody[DiscordResponse])


  private val sendEndpoint = endpoint.get
    .in("send")
    .out(jsonBody[MessageResponse])


  private val sendRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      sendEndpoint.serverLogicSuccess[IO](_ => {
        val discordOutbound = new DiscordOutbound(
          token = EnvLoader.get("DISCORD_BOT_TOKEN"),
          url = EnvLoader.get("DISCORD_BOT_URL"),
          applicationId = EnvLoader.get("DISCORD_BOT_APPLICATION_ID")
        )
        val acceptButton = discordInbound.registerAction((ctx) =>
          discordOutbound.sendToChannel(
            ctx.channelId,
            Message(
              text = "You pressed accept!"
            )
          ).flatMap { response =>
            IO.println(s"Accepted. Sent message ${response.messageId}")
          }
        )
        val declineButton = discordInbound.registerAction((ctx) =>
          discordOutbound.sendToChannel(
            ctx.channelId,
            Message(
              text = "You pressed decline!"
            )
          ).flatMap { response =>
            IO.println(s"Declined. Sent message ${response.messageId}")
          }
        )
        val message = Message(
          text = "Deploy to production?",
          interactions = Seq(
            acceptButton.render("Accept"),
            declineButton.render("Decline")
          )
        )
        val response = discordOutbound.sendToChannel("1381992880834351184", message)
        response
      })
    )


  private val swaggerRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      SwaggerInterpreter().fromEndpoints[IO](
        List(interactionEndpoint, sendEndpoint),
        "Discord Interaction API",
        "1.0"
      )
    )

  override def run(args: List[String]): IO[ExitCode] = {
    EnvLoader.loadEnv()

    val routes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]()
        .toRoutes(interactionEndpoint.serverLogic[IO] {
          case (signature, timestamp, json) =>
            val discordPublicKey = "cec2f053ddcba6bb67570ac176afc730df3325a729ccb32edbed9dbe4d1741ca"
            if (!verifySignature(discordPublicKey, signature, timestamp, json)) {
              IO.pure(Left(Unauthorized()))
            } else {
              parse(json) match {
                case Right(json) => logic(signature, timestamp, json)
                case Left(err) => IO.pure(Left(BadRequest(what = s"Parsing error: ${err.message}")))
              }
            }
        })

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router(
        "/" -> (routes <+> swaggerRoutes <+> sendRoutes) // Combine all routes here
      ).orNotFound)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
