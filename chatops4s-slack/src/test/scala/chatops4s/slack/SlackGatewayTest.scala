package chatops4s.slack

import cats.effect.IO
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
  ): (SlackGatewayImpl[IO], Unit) =
    createGatewayWith(backend)(_ => IO.unit)

  private def createGatewayWith[A](
      backend: sttp.client4.testing.BackendStub[IO],
  )(setup: SlackSetup[IO] => IO[A]): (SlackGatewayImpl[IO], A) = {
    val (gw, a) = SlackGateway
      .create[IO, A]("test-token", "test-secret", backend)(setup)
      .allocated
      .unsafeRunSync()
      ._1
    (gw.asInstanceOf[SlackGatewayImpl[IO]], a)
  }

  "SlackGateway" - {

    "send" - {
      "should send a simple message" in {
        val backend = MockBackend.withPostMessage(okResponse.asJson.noSpaces)
        val (gateway, _) = createGateway(backend)

        val result = gateway.send("C123", "Hello World").unsafeRunSync()

        result shouldBe MessageId("C123", "1234567890.123")
      }

      "should send a message with buttons" in {
        val backend = MockBackend.withPostMessage(okResponse.asJson.noSpaces)
        val (gateway, (approve, reject)) = createGatewayWith(backend) { setup =>
          for {
            a <- setup.onButton((_, _) => IO.unit)
            r <- setup.onButton((_, _) => IO.unit)
          } yield (a, r)
        }

        val result = gateway.send("C123", "Deploy?", Seq(
          Button("Approve", approve),
          Button("Reject", reject),
        )).unsafeRunSync()

        result shouldBe MessageId("C123", "1234567890.123")
      }

      "should handle API errors" in {
        val errorResponse = SlackModels.PostMessageResponse(ok = false, error = Some("invalid_auth"))
        val backend = MockBackend.withPostMessage(errorResponse.asJson.noSpaces)
        val (gateway, _) = createGateway(backend)

        val ex = intercept[RuntimeException] {
          gateway.send("C123", "Test").unsafeRunSync()
        }
        ex.getMessage should include("invalid_auth")
      }

      "should handle missing timestamp" in {
        val noTsResponse = SlackModels.PostMessageResponse(ok = true, ts = None)
        val backend = MockBackend.withPostMessage(noTsResponse.asJson.noSpaces)
        val (gateway, _) = createGateway(backend)

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
        val (gateway, _) = createGateway(backend)

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
        val (gateway, btn) = createGatewayWith(backend) { setup =>
          setup.onButton((_, _) => IO.unit)
        }

        val result = gateway.reply(
          MessageId("C123", "1234567890.123"),
          "Confirm?",
          Seq(Button("OK", btn)),
        ).unsafeRunSync()

        result shouldBe MessageId("C123", "1234567891.456")
      }
    }

    "onButton" - {
      "should generate unique button IDs" in {
        val backend = MockBackend.create()
        val (_, ids) = createGatewayWith(backend) { setup =>
          (1 to 10).toList.traverse(_ => setup.onButton((_, _) => IO.unit))
        }

        ids.map(_.value).toSet.size shouldBe 10
      }
    }

    "interaction handling" - {
      "should dispatch button click to registered handler" in {
        val backend = MockBackend.create()
        var captured: Option[ButtonClick] = None

        val (gateway, btnId) = createGatewayWith(backend) { setup =>
          setup.onButton { (click, _) =>
            IO { captured = Some(click) }
          }
        }

        val payload = interactionPayload(btnId.value)
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        captured shouldBe defined
        captured.get.userId shouldBe "U123"
        captured.get.messageId shouldBe MessageId("C123", "1234567890.123")
        captured.get.buttonId shouldBe btnId
      }

      "should ignore unknown action IDs" in {
        val backend = MockBackend.create()
        var called = false

        val (gateway, _) = createGatewayWith(backend) { setup =>
          setup.onButton { (_, _) => IO { called = true } }
        }

        val payload = interactionPayload("unknown_action_id")
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        called shouldBe false
      }

      "should dispatch multiple actions in one payload" in {
        val backend = MockBackend.create()
        var count = 0

        val (gateway, (btn1, btn2)) = createGatewayWith(backend) { setup =>
          for {
            b1 <- setup.onButton { (_, _) => IO { count += 1 } }
            b2 <- setup.onButton { (_, _) => IO { count += 10 } }
          } yield (b1, b2)
        }

        val payload = SlackModels.InteractionPayload(
          `type` = "block_actions",
          user = SlackModels.User("U123"),
          channel = SlackModels.Channel("C123"),
          container = SlackModels.Container(Some("1234567890.123")),
          actions = Some(List(
            SlackModels.Action(btn1.value, Some(btn1.value)),
            SlackModels.Action(btn2.value, Some(btn2.value)),
          )),
        )
        gateway.handleInteractionPayload(payload).unsafeRunSync()

        count shouldBe 11
      }
    }
  }

  private def interactionPayload(actionId: String): SlackModels.InteractionPayload =
    SlackModels.InteractionPayload(
      `type` = "block_actions",
      user = SlackModels.User("U123"),
      channel = SlackModels.Channel("C123"),
      container = SlackModels.Container(Some("1234567890.123")),
      actions = Some(List(
        SlackModels.Action(actionId, Some(actionId)),
      )),
    )
}
