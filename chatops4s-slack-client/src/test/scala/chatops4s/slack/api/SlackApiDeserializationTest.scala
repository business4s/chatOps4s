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

  "Deserialization of real Slack responses" - {

    "chat.postMessage" in {
      parseOk[chat.PostMessageResponse]("chat.postMessage.json") { r =>
        r.channel.value should not be empty
        r.ts should not be empty
      }
    }

    "chat.update" in {
      parseOk[chat.UpdateResponse]("chat.update.json") { r =>
        r.channel.value should not be empty
        r.ts should not be empty
      }
    }

    "chat.delete" in {
      parseOk[chat.DeleteResponse]("chat.delete.json") { r =>
        r.channel.value should not be empty
        r.ts should not be empty
      }
    }

    "chat.postEphemeral" in {
      parseOk[chat.PostEphemeralResponse]("chat.postEphemeral.json") { r =>
        r.message_ts should not be empty
      }
    }

    "reactions.add" in {
      parseOk[reactions.AddResponse]("reactions.add.json") { _ => /* AddResponse is empty */ }
    }

    "reactions.remove" in {
      parseOk[reactions.RemoveResponse]("reactions.remove.json") { _ => /* RemoveResponse is empty */ }
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
      value.ts should not be empty
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
}
