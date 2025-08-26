import api.{DiscordInbound, Server}
import cats.effect.IO
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.util.encoders.Hex
import io.circe.parser.*
import models.{DiscordResponse, InteractionType}
import cats.effect.unsafe.implicits.global

class ServerTest extends AnyFreeSpec with Matchers {
  private def sign(privateKey: Ed25519PrivateKeyParameters, timestamp: String, body: String): String = {
    val signer = new org.bouncycastle.crypto.signers.Ed25519Signer()
    signer.init(true, privateKey)
    val msg = (timestamp + body).getBytes("UTF-8")
    signer.update(msg, 0, msg.length)
    Hex.toHexString(signer.generateSignature())
  }

  private val privateKey = new Ed25519PrivateKeyParameters(new java.security.SecureRandom())
  private val publicKey  = privateKey.generatePublicKey()
  private val publicKeyHex = Hex.toHexString(publicKey.getEncoded)

  private val discordInbound = new DiscordInbound() // you can mock handlers if needed
  private val server = new Server(publicKeyHex, discordInbound)

  "Server.verifySignature" - {
    "accepts a valid signature" in {
      val body = """{"type":1}"""
      val timestamp = "12345"
      val sig = sign(privateKey, timestamp, body)

      val method = classOf[Server].getDeclaredMethod("verifySignature", classOf[String], classOf[String], classOf[String], classOf[String])
      method.setAccessible(true)
      val result = method.invoke(server, publicKeyHex, sig, timestamp, body).asInstanceOf[Boolean]

      result shouldBe true
    }

    "rejects an invalid signature" in {
      val body = """{"type":1}"""
      val timestamp = "12345"
      val sig = "deadbeef"

      val method = classOf[Server].getDeclaredMethod("verifySignature", classOf[String], classOf[String], classOf[String], classOf[String])
      method.setAccessible(true)
      val result = method.invoke(server, publicKeyHex, sig, timestamp, body).asInstanceOf[Boolean]

      result shouldBe false
    }
  }

  "Server.processRequest" - {
    "responds with PING to type 1 interaction" in {
      val json = parse("""{"type":1}""").getOrElse(fail("Failed to parse JSON"))
      val method = classOf[Server].getDeclaredMethod("processRequest", classOf[io.circe.Json])
      method.setAccessible(true)

      val resultIO = method.invoke(server, json).asInstanceOf[IO[Either[String, DiscordResponse]]]
      val result = resultIO.unsafeRunSync()

      result shouldBe Right(DiscordResponse(InteractionType.Ping.value))
    }

    "returns Left on invalid payload" in {
      val json = parse("""{"type":999,"data":{}}""").getOrElse(fail("Failed to parse JSON"))
      val method = classOf[Server].getDeclaredMethod("processRequest", classOf[io.circe.Json])
      method.setAccessible(true)

      val resultIO = method.invoke(server, json).asInstanceOf[IO[Either[String, DiscordResponse]]]
      val result = resultIO.unsafeRunSync()

      result.isLeft shouldBe true
    }
  }
}
