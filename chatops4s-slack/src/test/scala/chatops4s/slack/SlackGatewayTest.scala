package chatops4s.slack

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.traverse.*
import io.circe.syntax.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SlackGatewayTest extends AnyFreeSpec with Matchers {

  private val okResponse = SlackModels.PostMessageResponse(
    ok = true,
    channel = Some("C123"),
    ts = Some("1234567890.123"),
  )

  private def createGateway(
      backend: sttp.client4.testing.BackendStub[IO],
  ): SlackGatewayImpl[IO] = {
    val handlersRef = Ref.of[IO, Map[String, ErasedHandler[IO]]](Map.empty).unsafeRunSync()
    new SlackGatewayImpl[IO]("test-token", backend, handlersRef, listen = IO.never)
  }

  "SlackGateway" - {

    "send" - {
      "should send a simple message" in {
        val backend = MockBackend.withPostMessage(okResponse.asJson.noSpaces)
        val gateway = createGateway(backend)

        val result = gateway.send("C123", "Hello World").unsafeRunSync()

        result shouldBe MessageId("C123", "1234567890.123")
      }

      "should send a message with buttons" in {
        val backend = MockBackend.withPostMessage(okResponse.asJson.noSpaces)
        val gateway = createGateway(backend)
        val approve = gateway.onButton[String]((_, _) => IO.unit).unsafeRunSync()
        val reject = gateway.onButton[String]((_, _) => IO.unit).unsafeRunSync()

        val result = gateway.send("C123", "Deploy?", Seq(
          Button("Approve", approve, approve.value),
          Button("Reject", reject, reject.value),
        )).unsafeRunSync()

        result shouldBe MessageId("C123", "1234567890.123")
      }

      "should handle API errors" in {
        val errorResponse = SlackModels.PostMessageResponse(ok = false, error = Some("invalid_auth"))
        val backend = MockBackend.withPostMessage(errorResponse.asJson.noSpaces)
        val gateway = createGateway(backend)

        val ex = intercept[RuntimeException] {
          gateway.send("C123", "Test").unsafeRunSync()
        }
        ex.getMessage should include("invalid_auth")
      }

      "should handle missing timestamp" in {
        val noTsResponse = SlackModels.PostMessageResponse(ok = true, ts = None)
        val backend = MockBackend.withPostMessage(noTsResponse.asJson.noSpaces)
        val gateway = createGateway(backend)

        assertThrows[RuntimeException] {
          gateway.send("C123", "Test").unsafeRunSync()
        }
      }
    }

    "reply" - {
      "should reply in thread" in {
        val threadResponse = SlackModels.PostMessageResponse(
          ok = true,
          channel = Some("C123"),
          ts = Some("1234567891.456"),
        )
        val backend = MockBackend.withPostMessage(threadResponse.asJson.noSpaces)
        val gateway = createGateway(backend)

        val result = gateway.reply(MessageId("C123", "1234567890.123"), "Thread reply").unsafeRunSync()

        result shouldBe MessageId("C123", "1234567891.456")
      }

      "should reply in thread with buttons" in {
        val threadResponse = SlackModels.PostMessageResponse(
          ok = true,
          channel = Some("C123"),
          ts = Some("1234567891.456"),
        )
        val backend = MockBackend.withPostMessage(threadResponse.asJson.noSpaces)
        val gateway = createGateway(backend)
        val btn = gateway.onButton[String]((_, _) => IO.unit).unsafeRunSync()

        val result = gateway.reply(
          MessageId("C123", "1234567890.123"),
          "Confirm?",
          Seq(Button("OK", btn, btn.value)),
        ).unsafeRunSync()

        result shouldBe MessageId("C123", "1234567891.456")
      }
    }

    "onButton" - {
      "should generate unique button IDs" in {
        val backend = MockBackend.create()
        val gateway = createGateway(backend)

        val ids = (1 to 10).toList.traverse(_ => gateway.onButton[String]((_, _) => IO.unit)).unsafeRunSync()

        ids.map(_.value).toSet.size shouldBe 10
      }
    }

    "interaction handling" - {
      "should dispatch button click to registered handler" in {
        val backend = MockBackend.create()
        var captured: Option[ButtonClick[String]] = None

        val gateway = createGateway(backend)
        val btnId = gateway.onButton[String] { (click, _) =>
          IO { captured = Some(click) }
        }.unsafeRunSync()

        val payload = interactionPayload(btnId.value, "my-value")
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.userId shouldBe "U123"
        captured.get.messageId shouldBe MessageId("C123", "1234567890.123")
        captured.get.value shouldBe "my-value"
      }

      "should ignore unknown action IDs" in {
        val backend = MockBackend.create()
        var called = false

        val gateway = createGateway(backend)
        gateway.onButton[String] { (_, _) => IO { called = true } }.unsafeRunSync()

        val payload = interactionPayload("unknown_action_id", "v")
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        called shouldBe false
      }

      "should dispatch multiple actions in one payload" in {
        val backend = MockBackend.create()
        var count = 0

        val gateway = createGateway(backend)
        val btn1 = gateway.onButton[String] { (_, _) => IO { count += 1 } }.unsafeRunSync()
        val btn2 = gateway.onButton[String] { (_, _) => IO { count += 10 } }.unsafeRunSync()

        val payload = SlackModels.InteractionPayload(
          `type` = "block_actions",
          user = SlackModels.User("U123"),
          channel = SlackModels.Channel("C123"),
          container = SlackModels.Container(Some("1234567890.123")),
          actions = Some(List(
            SlackModels.Action(s"${btn1.value}:v1", Some("v1")),
            SlackModels.Action(s"${btn2.value}:v2", Some("v2")),
          )),
        )
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        count shouldBe 11
      }
    }
  }

  private def interactionPayload(handlerId: String, value: String): SlackModels.InteractionPayload =
    SlackModels.InteractionPayload(
      `type` = "block_actions",
      user = SlackModels.User("U123"),
      channel = SlackModels.Channel("C123"),
      container = SlackModels.Container(Some("1234567890.123")),
      actions = Some(List(
        SlackModels.Action(s"$handlerId:$value", Some(value)),
      )),
    )
}
