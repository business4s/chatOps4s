import api.DiscordOutbound
import io.circe.syntax.*
import models.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.shared.Identity

class DiscordOutboundTest extends AnyFreeSpec with Matchers {
  private val token   = "dummy-token"
  private val baseUrl = "http://test-url"

  "DiscordOutbound" - {
    "should POST a message with text only in sendToChannel" in {
      // arrange
      val backendStub =
        SyncBackendStub
          .whenRequestMatches(_.uri.toString.contains("/channels/123/messages"))
          .thenRespondAdjust(
            MessageResponse(id = "msg1").asJson.noSpaces,
          )
      val outbound    = new DiscordOutbound[Identity](token, baseUrl, backendStub)
      val message     = Message(text = "hello", interactions = List.empty)
      // act
      val response    = outbound.sendToChannel("123", message)
      // assert
      response shouldBe MessageResponse("msg1")
    }

    "should POST a message with buttons in sendToChannel" in {
      // arrange
      val backendStub =
        SyncBackendStub
          .whenRequestMatches(_.uri.toString.contains("/channels/456/messages"))
          .thenRespondAdjust(
            MessageResponse(id = "msg2").asJson.noSpaces,
          )
      val outbound    = new DiscordOutbound[Identity](token, baseUrl, backendStub)
      val button      = Button(label = "Click", value = "btn1")
      val message     = Message(text = "click", interactions = List(button))
      // act
      val response    = outbound.sendToChannel("456", message)
      // assert
      response shouldBe MessageResponse("msg2")
    }

    "should create a thread then send a message inside it in sendToThread" in {
      // arrange
      val backendStub =
        SyncBackendStub
          .whenRequestMatches(_.uri.toString.contains("/channels/789/threads"))
          .thenRespondAdjust(
            ThreadResponse(id = "thread123").asJson.noSpaces,
          )
          .whenRequestMatches(_.uri.toString.contains("/channels/thread123/messages"))
          .thenRespondAdjust(
            MessageResponse(id = "msg3").asJson.noSpaces,
          )
      val outbound    = new DiscordOutbound[Identity](token, baseUrl, backendStub)
      val message     = Message(text = "inside thread", interactions = Nil)
      // act
      val response    = outbound.sendToThread("789", "topic-thread", message)
      // assert
      response shouldBe MessageResponse("msg3")
    }
  }
}
