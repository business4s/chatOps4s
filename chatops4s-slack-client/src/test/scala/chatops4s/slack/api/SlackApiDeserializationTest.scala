package chatops4s.slack.api

import io.circe.Decoder
import io.circe.parser.decode
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source
import scala.util.Using

class SlackApiDeserializationTest extends AnyFreeSpec with Matchers {

  private def loadFixture(name: String): Option[String] =
    Option(getClass.getResourceAsStream(s"/responses/$name")).map { is =>
      Using.resource(Source.fromInputStream(is))(_.mkString)
    }

  private def parseOk[T: Decoder](name: String)(checks: T => Unit): Unit = {
    val json = loadFixture(name)
    assume(json.isDefined, s"Fixture $name not found — run ResponseCollector first")
    val result = decode[SlackResponse[T]](json.get)
    result match {
      case Right(SlackResponse.Ok(value)) => checks(value)
      case Right(SlackResponse.Err(err))  => fail(s"Expected Ok but got Err($err)")
      case Left(err)                      => fail(s"Failed to decode: $err")
    }
  }

  private def parseOkInline[T: Decoder](json: String)(checks: T => Unit): Unit = {
    val result = decode[SlackResponse[T]](json)
    result match {
      case Right(SlackResponse.Ok(value)) => checks(value)
      case Right(SlackResponse.Err(err))  => fail(s"Expected Ok but got Err($err)")
      case Left(err)                      => fail(s"Failed to decode: $err")
    }
  }

  "Deserialization of real Slack responses" - {

    "chat.postMessage" in {
      parseOk[chat.PostMessageResponse]("chat.postMessage.json") { r =>
        r.channel.value should not be empty
        r.ts.value should not be empty
      }
    }

    "chat.update" in {
      parseOk[chat.UpdateResponse]("chat.update.json") { r =>
        r.channel.value should not be empty
        r.ts.value should not be empty
      }
    }

    "chat.delete" in {
      parseOk[chat.DeleteResponse]("chat.delete.json") { r =>
        r.channel.value should not be empty
        r.ts.value should not be empty
      }
    }

    "chat.postEphemeral" in {
      parseOk[chat.PostEphemeralResponse]("chat.postEphemeral.json") { r =>
        r.message_ts.value should not be empty
      }
    }

    "reactions.add" in {
      parseOk[reactions.AddResponse]("reactions.add.json") { _ => /* AddResponse is empty */ }
    }

    "reactions.remove" in {
      parseOk[reactions.RemoveResponse]("reactions.remove.json") { _ => /* RemoveResponse is empty */ }
    }

    "views.open" in {
      parseOk[views.OpenResponse]("views.open.json") { r =>
        r.view shouldBe defined
        r.view.get.hcursor.get[String]("id").toOption shouldBe Some("VMHU10V25")
      }
    }

    "apps.connections.open" in {
      parseOk[apps.ConnectionsOpenResponse]("apps.connections.open.json") { r =>
        r.url should startWith("wss://")
      }
    }

    "error response parses as Err" in {
      val json = loadFixture("error.json")
      assume(json.isDefined, "Fixture error.json not found — run ResponseCollector first")
      val result = decode[SlackResponse[chat.DeleteResponse]](json.get)
      result match {
        case Right(SlackResponse.Err(error)) =>
          error should not be empty
        case Right(SlackResponse.Ok(_)) =>
          fail("Expected Err but got Ok")
        case Left(err) =>
          fail(s"Failed to decode: $err")
      }
    }

    "okOrThrow works on success" in {
      val json = loadFixture("chat.postMessage.json")
      assume(json.isDefined, "Fixture chat.postMessage.json not found — run ResponseCollector first")
      val response = decode[SlackResponse[chat.PostMessageResponse]](json.get).toOption.get
      val value = response.okOrThrow
      value.channel.value should not be empty
      value.ts.value should not be empty
    }

    "okOrThrow throws SlackApiError on error" in {
      val json = loadFixture("error.json")
      assume(json.isDefined, "Fixture error.json not found — run ResponseCollector first")
      val response = decode[SlackResponse[chat.DeleteResponse]](json.get).toOption.get
      val ex = intercept[SlackApiError] {
        response.okOrThrow
      }
      ex.error should not be empty
    }
  }

  // Examples based on Slack API documentation
  "Deserialization of Slack doc examples" - {

    // https://docs.slack.dev/reference/methods/chat.postMessage
    "chat.postMessage" in {
      parseOkInline[chat.PostMessageResponse](
        """{
          |  "ok": true,
          |  "channel": "C123456",
          |  "ts": "1503435956.000247",
          |  "message": {
          |    "text": "Here's a message for you",
          |    "username": "ecto1",
          |    "bot_id": "B19LU7CSY",
          |    "attachments": [],
          |    "type": "message",
          |    "subtype": "bot_message",
          |    "ts": "1503435956.000247"
          |  }
          |}""".stripMargin
      ) { r =>
        r.channel shouldBe ChannelId("C123456")
        r.ts shouldBe Timestamp("1503435956.000247")
        r.message shouldBe defined
      }
    }

    // https://docs.slack.dev/reference/methods/chat.update
    "chat.update" in {
      parseOkInline[chat.UpdateResponse](
        """{
          |  "ok": true,
          |  "channel": "C123456",
          |  "ts": "1401383885.000061",
          |  "text": "Updated text you carefully authored",
          |  "message": {
          |    "text": "Updated text you carefully authored",
          |    "user": "U34567"
          |  }
          |}""".stripMargin
      ) { r =>
        r.channel shouldBe ChannelId("C123456")
        r.ts shouldBe Timestamp("1401383885.000061")
        r.text shouldBe Some("Updated text you carefully authored")
        r.message shouldBe defined
      }
    }

    // https://docs.slack.dev/reference/methods/chat.delete
    "chat.delete" in {
      parseOkInline[chat.DeleteResponse](
        """{
          |  "ok": true,
          |  "channel": "C123456",
          |  "ts": "1401383885.000061"
          |}""".stripMargin
      ) { r =>
        r.channel shouldBe ChannelId("C123456")
        r.ts shouldBe Timestamp("1401383885.000061")
      }
    }

    // https://docs.slack.dev/reference/methods/chat.postEphemeral
    "chat.postEphemeral" in {
      parseOkInline[chat.PostEphemeralResponse](
        """{
          |  "ok": true,
          |  "message_ts": "1502210682.580145"
          |}""".stripMargin
      ) { r =>
        r.message_ts shouldBe Timestamp("1502210682.580145")
      }
    }

    // https://docs.slack.dev/reference/methods/reactions.add
    "reactions.add" in {
      parseOkInline[reactions.AddResponse]("""{"ok": true}""") { _ => /* empty response */ }
    }

    // https://docs.slack.dev/reference/methods/reactions.remove
    "reactions.remove" in {
      parseOkInline[reactions.RemoveResponse]("""{"ok": true}""") { _ => /* empty response */ }
    }

    // https://docs.slack.dev/reference/methods/views.open
    "views.open" in {
      parseOkInline[views.OpenResponse](
        """{
          |  "ok": true,
          |  "view": {
          |    "id": "VMHU10V25",
          |    "team_id": "T8N4K1JN",
          |    "type": "modal",
          |    "bot_id": "BA0G7HC02",
          |    "app_id": "A21SDS90",
          |    "title": {
          |      "type": "plain_text",
          |      "text": "Quite a plain modal"
          |    },
          |    "submit": {
          |      "type": "plain_text",
          |      "text": "Create"
          |    },
          |    "close": {
          |      "type": "plain_text",
          |      "text": "Cancel"
          |    },
          |    "blocks": [
          |      {
          |        "type": "input",
          |        "block_id": "a_block_id",
          |        "label": {
          |          "type": "plain_text",
          |          "text": "A simple label",
          |          "emoji": true
          |        },
          |        "optional": false,
          |        "dispatch_action": false,
          |        "element": {
          |          "type": "plain_text_input",
          |          "action_id": "an_action_id",
          |          "dispatch_action_config": {
          |            "trigger_actions_on": ["on_enter_pressed"]
          |          }
          |        }
          |      }
          |    ],
          |    "private_metadata": "",
          |    "callback_id": "view_identifier_12",
          |    "state": {
          |      "values": {}
          |    },
          |    "hash": "156772938.1827394",
          |    "clear_on_close": false,
          |    "notify_on_close": false,
          |    "root_view_id": "VMHU10V25",
          |    "external_id": "",
          |    "app_installed_team_id": "T8N4K1JN"
          |  }
          |}""".stripMargin
      ) { r =>
        r.view shouldBe defined
        r.view.get.hcursor.get[String]("id").toOption shouldBe Some("VMHU10V25")
        r.view.get.hcursor.get[String]("type").toOption shouldBe Some("modal")
      }
    }

    // https://docs.slack.dev/reference/methods/apps.connections.open
    "apps.connections.open" in {
      parseOkInline[apps.ConnectionsOpenResponse](
        """{
          |  "ok": true,
          |  "url": "wss://wss-primary.slack.com/link/?ticket=example-ticket&app_id=A123456"
          |}""".stripMargin
      ) { r =>
        r.url should startWith("wss://")
      }
    }
  }
}
