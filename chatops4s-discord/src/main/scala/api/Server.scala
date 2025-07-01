package api

import cats.effect.{ExitCode, IO, IOApp}
import io.circe.Json
import models.{DiscordResponse, InteractionContext}
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
import utilities.EnvLoader
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.model.StatusCode
import io.circe.parser.*
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object Server extends IOApp:
  private val discordInbound = new DiscordInbound()

  private sealed trait ErrorInfo
  private case class BadRequest(what: String) extends ErrorInfo
  private case class Unauthorized() extends ErrorInfo

  private def verifySignature(
   publicKey: String,
   signature: String,
   timestamp: String,
   body: String
 ): Boolean = {
    val publicKeyBytes = Hex.decode(publicKey.strip())
    val signatureBytes = Hex.decode(signature.strip())
    println("ran")
    val message = (timestamp.strip() + body.strip()).getBytes("UTF-8")
    val verifier = new Ed25519Signer()
    verifier.init(false, new Ed25519PublicKeyParameters(publicKeyBytes, 0))
    verifier.update(message, 0, message.length)
    verifier.verifySignature(signatureBytes)
  }



  private val logic: (String, String, Json) => IO[Either[ErrorInfo, DiscordResponse]] = (signature: String, timestamp: String, json: Json) => {
    val maybeContext = for {
      customId <- json.hcursor.downField("data").get[String]("custom_id").toOption
      userId <- json.hcursor.downField("member").downField("user").get[String]("id").toOption
      channelId <- json.hcursor.get[String]("channel_id").toOption
      messageId <- json.hcursor.downField("message").get[String]("id").toOption
      _type     <- json.hcursor.get[Int]("type").toOption
    } yield (customId, _type, InteractionContext(userId, channelId, messageId))
    maybeContext match {
      case Some((id, _type, ctx)) =>
        if (_type == 1) {
          IO.println("ping")
          IO.pure(Right(DiscordResponse(`type` = 1)))
        } else {
          discordInbound.handlers.get(id) match {
            case Some(f) => f(ctx).map(Right(_)).as(Right(DiscordResponse(`type` = 1)))
            case None => IO.pure(Right(DiscordResponse(`type` = 1)))
          }
        }
      case None => IO.pure(Left(BadRequest(what = "Invalid Type")))
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

  private val swaggerRoutes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      SwaggerInterpreter().fromEndpoints[IO](
        List(interactionEndpoint),
        "Discord Interaction API",
        "1.0"
      )
    )

  override def run(args: List[String]): IO[ExitCode] = {
    val routes: HttpRoutes[IO] =
      Http4sServerInterpreter[IO]()
        .toRoutes(interactionEndpoint.serverLogic {
          case (signature, timestamp, json) =>
            val body = json
            IO.println("getting public key")
            val discordPublicKey = EnvLoader.get("DISCORD_BOT_PUBLIC_KEY")
            if (!verifySignature(discordPublicKey, signature, timestamp, body)) {
              IO.pure(Left(Unauthorized()))
            } else {
              parse(json) match {
                case Right(body) => logic(signature, timestamp, body)
                case Left(err) => IO.pure(Left(BadRequest(what = "Parsing error")))
              }
            }
        })

    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router(
        "/" -> routes,
        "/" -> swaggerRoutes
      ).orNotFound)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
