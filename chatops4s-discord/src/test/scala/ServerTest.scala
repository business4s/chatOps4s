import api.{DiscordInbound, Server}
import cats.effect.{IO, Resource}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.util.encoders.Hex
import io.circe.Json
import models.InteractionType
import cats.effect.unsafe.implicits.global
import org.http4s.ember.server.EmberServerBuilder
import sttp.client4.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import com.comcast.ip4s.{Host, Port}
import org.http4s.HttpRoutes
import sttp.client4.circe.asJson
import sttp.client4.httpclient.cats.HttpClientCatsBackend

class ServerTest extends AnyFreeSpec with Matchers {
  private val privateKey   = new Ed25519PrivateKeyParameters(new java.security.SecureRandom())
  private val publicKey    = privateKey.generatePublicKey()
  private val publicKeyHex = Hex.toHexString(publicKey.getEncoded)
  private val discordInbound = new DiscordInbound()
  private val server         = new Server(publicKeyHex, discordInbound)

  private def withServer[A](port: Int)(test: org.http4s.Uri => IO[A]) = {
    val routes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(List(server.interactionRoute))
    val serverResource = EmberServerBuilder.default[IO]
      .withHost(Host.fromString("127.0.0.1").get)
      .withPort(Port.fromInt(port).get)
      .withHttpApp(routes.orNotFound)
      .build

    serverResource.use(_ => test(org.http4s.Uri.unsafeFromString(s"http://127.0.0.1:$port"))).unsafeRunSync()
  }

  private def sign(privateKey: Ed25519PrivateKeyParameters, timestamp: String, body: String) = {
    val signer = new org.bouncycastle.crypto.signers.Ed25519Signer()
    signer.init(true, privateKey)
    val msg = (timestamp + body).getBytes("UTF-8")
    signer.update(msg, 0, msg.length)
    Hex.toHexString(signer.generateSignature())
  }

  "Server interactions" - {
    "responds with PING for valid type 1 interaction" in {
      // arrange
      withServer(5000) { baseUri =>
        val body = """{"type":1}"""
        val timestamp = "12345"
        val sig = sign(privateKey, timestamp, body)
        // act
        HttpClientCatsBackend.resource[IO]().use { backend =>
          val request = basicRequest
            .post(uri"$baseUri/api/interactions")
            .header("X-Signature-Ed25519", sig)
            .header("X-Signature-Timestamp", timestamp)
            .body(body)
            .response(asJson[Json])

          for {
            response <- request.send(backend)
            _ <- backend.close()
          } yield {
            // assert
            val json = response.body.getOrElse(fail("No JSON response"))
            json.hcursor.downField("type").as[Int].getOrElse(null) shouldBe InteractionType.Ping.value
          }
        }
      }
    }

    "returns error for invalid signature" in {
      withServer(5001) { baseUri =>
        // arrange
        val body = """{"type":1}"""
        val timestamp = "12345"
        val otherPrivateKey = new Ed25519PrivateKeyParameters(new java.security.SecureRandom())
        val sig = Hex.toHexString(otherPrivateKey.generatePublicKey().getEncoded)
        // act
        HttpClientCatsBackend.resource[IO]().use { backend =>
          val request = basicRequest
            .post(uri"$baseUri/api/interactions")
            .header("X-Signature-Ed25519", sig)
            .header("X-Signature-Timestamp", timestamp)
            .body(body)
            .response(asStringAlways)

          for {
            response <- request.send(backend)
            _ <- backend.close()
          } yield {
            // assert
            response.code shouldBe sttp.model.StatusCode.Unauthorized
          }
        }
      }
    }

    "returns error for invalid payload type" in {
      withServer(5002) { baseUri =>
        // arrange
        val body = """{"type":999}"""
        val timestamp = "12345"
        val sig = sign(privateKey, timestamp, body)
        // act
        HttpClientCatsBackend.resource[IO]().use { backend =>
          val request = basicRequest
            .post(uri"$baseUri/api/interactions")
            .header("X-Signature-Ed25519", sig)
            .header("X-Signature-Timestamp", timestamp)
            .body(body)
            .response(asJson[Json])

          for {
            response <- request.send(backend)
            _ <- backend.close()
          } yield {
            // assert
            response.code shouldBe sttp.model.StatusCode.BadRequest
          }
        }
      }
    }
  }
}
