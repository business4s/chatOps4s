---
sidebar_position: 1
---

# Getting Started

ChatOps4s is a Scala library for building chat-ops workflows. It provides a simple, type-safe API for sending messages, handling button interactions, and updating messages in Slack.

## Installation

import SbtDependency from '@site/src/components/SbtDependency';

<SbtDependency moduleName={"chatops4s-slack"} />

You also need an [sttp](https://sttp.softwaremill.com/en/latest/backends/summary.html) backend that supports WebSockets. For example, with fs2:

```scala
"com.softwaremill.sttp.client4" %% "fs2" % "4.0.9"
```

## Slack App Setup

ChatOps4s uses [Socket Mode](https://api.slack.com/apis/socket-mode), so no public URL is needed.

1. Go to [api.slack.com/apps](https://api.slack.com/apps) and create a new app **from a manifest**.
2. Use `SlackSetup.manifest(appName = "MyApp")` to generate the manifest YAML.
3. After creating the app, grab two tokens:
   - **Bot Token** (`xoxb-...`): found under *OAuth & Permissions*
   - **App-Level Token** (`xapp-...`): create one under *Basic Information → App-Level Tokens* with `connections:write` scope.

## Minimal Example

```scala
import cats.effect.{IO, IOApp}
import chatops4s.slack.{SlackGateway, SlackSetup}
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object Main extends IOApp.Simple {

  // Print the manifest to paste into Slack's "Create from manifest" dialog
  println(SlackSetup.manifest(appName = "MyApp"))

  private val token    = sys.env("SLACK_BOT_TOKEN")   // xoxb-...
  private val appToken = sys.env("SLACK_APP_TOKEN")    // xapp-...
  private val channel  = sys.env("SLACK_CHANNEL")      // #my-channel

  override def run: IO[Unit] =
    HttpClientFs2Backend.resource[IO]().use { backend =>
      for {
        slack <- SlackGateway.create(token, appToken, backend)
        _     <- slack.send(channel, "Hello from ChatOps4s!")
        _     <- slack.listen
      } yield ()
    }
}
```

## Interactive Buttons

Register button handlers before calling `listen`. Each handler receives a typed `ButtonClick[T]` and the gateway itself:

```scala
import chatops4s.slack._

// Define a typed button handler
val program: IO[Unit] =
  HttpClientFs2Backend.resource[IO]().use { backend =>
    for {
      slack      <- SlackGateway.create(token, appToken, backend)
      approveBtn <- slack.onButton[String] { (click, gw) =>
                      gw.update(click.messageId, s"Approved by <@${click.userId}>")
                        .void
                    }
      rejectBtn  <- slack.onButton[String] { (click, gw) =>
                      gw.update(click.messageId, s"Rejected by <@${click.userId}>")
                        .void
                    }
      _          <- slack.send(
                      channel,
                      "Deploy v1.2.3 to production?",
                      Seq(
                        approveBtn.toButton("Approve", "v1.2.3"),
                        rejectBtn.toButton("Reject", "v1.2.3"),
                      ),
                    )
      _          <- slack.listen
    } yield ()
  }
```

## API Overview

The `SlackGateway[F]` trait provides four operations:

| Method   | Description                                         |
|----------|-----------------------------------------------------|
| `send`   | Send a message to a channel, optionally with buttons |
| `reply`  | Reply in a thread under an existing message          |
| `update` | Edit an existing message (e.g. to remove buttons)    |
| `listen` | Start the Socket Mode event loop (blocks)            |

`SlackSetup[F]` (mixed in by `SlackGateway.create`) adds:

| Method     | Description                                          |
|------------|------------------------------------------------------|
| `onButton` | Register a typed button handler, returns a `ButtonId[T]` |

Use `ButtonId[T].toButton(label, value)` to create `Button` instances for `send`/`reply`/`update`.
