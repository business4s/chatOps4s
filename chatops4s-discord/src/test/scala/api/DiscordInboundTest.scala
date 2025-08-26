package api

import cats.effect.IO
import models.{Button, InteractionContext}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class DiscordInboundTest extends AnyFunSuite with Matchers {
  test("registerAction should store handler and return a button factory") {
    val discordInbound = new DiscordInbound

    var executed = false
    val handler: InteractionContext => IO[Unit] = _ => IO { executed = true }

    val registeredAction = discordInbound.registerAction(handler)
    val button = registeredAction.render("Click me")

    button.label shouldBe "Click me"
    button.value should not be empty

    discordInbound.handlers.get(button.value) should not be empty

    discordInbound.handlers(button.value)(InteractionContext("user", "channelId", "messageId")).unsafeRunSync()
    executed shouldBe true
  }

  test("each registered action should have a unique id") {
    val discordInbound = new DiscordInbound
    val handler: InteractionContext => IO[Unit] = _ => IO { }

    val registeredAction1 = discordInbound.registerAction(handler).render("")
    val registeredAction2 = discordInbound.registerAction(handler).render("")

    registeredAction1.value should not equal registeredAction2.value
  }
}
