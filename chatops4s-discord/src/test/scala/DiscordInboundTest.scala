import api.DiscordInbound
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import models.InteractionContext
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DiscordInboundTest extends AnyFreeSpec with Matchers {
  "DiscordInbound" - {
    "registerAction should store handler and return the rendered button" in {
      // arrange
      val discordInbound                          = new DiscordInbound
      var executed                                = false
      val handler: InteractionContext => IO[Unit] = _ =>
        IO {
          executed = true
        }
      // act
      val registeredAction                        = discordInbound.registerAction(handler)
      val button                                  = registeredAction.render("Click me")
      // assert
      button.label shouldBe "Click me"
      button.value should not be empty
      discordInbound.handlers.get(button.value) should not be empty
      discordInbound.handlers(button.value)(InteractionContext("user", "channelId", "messageId")).unsafeRunSync()
      executed shouldBe true
    }
    "each registered action should have a unique id" in {
      // arrange
      val discordInbound                          = new DiscordInbound
      val handler: InteractionContext => IO[Unit] = _ => IO {}
      // act
      val registeredAction1                       = discordInbound.registerAction(handler).render("")
      val registeredAction2                       = discordInbound.registerAction(handler).render("")
      // assert
      registeredAction1.value should not equal registeredAction2.value
    }
  }
}
