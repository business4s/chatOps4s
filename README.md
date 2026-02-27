# chatops4s

[![Discord](https://img.shields.io/discord/1240565362601230367?style=flat-square&logo=discord)](https://bit.ly/business4s-discord)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/org.business4s/chatops4s-slack_3?server=https%3A%2F%2Foss.sonatype.org&style=flat-square)

**chatops4s** is a lightweight Scala library that makes building Slack chatops simple enough that you'll actually do it.

See the [**Website**](https://business4s.github.io/chatops4s/) for details and join our [**Discord**](https://bit.ly/business4s-discord) for discussions.

## Getting started

Add the dependency:

```scala
"org.business4s" %% "chatops4s-slack" % "<version>"
```

## Features

- **Simple messaging** -- send, reply, update, and delete messages with a clean API
- **Typed interactions** -- buttons, slash commands, and forms with compile-time derivation
- **Manifest generation** -- `validateSetup` generates a Slack app manifest and setup instructions so you don't hand-configure anything
- **Socket Mode** -- real-time WebSocket connection, no public URL or HTTP server needed

## Example

```scala
for {
  slack      <- SlackGateway.create(backend)
  approveBtn <- slack.registerButton[String] { click =>
                  slack.update(click.messageId, s"Approved by <@${click.userId}>").void
                }
  rejectBtn  <- slack.registerButton[String] { click =>
                  slack.update(click.messageId, s"Rejected by <@${click.userId}>").void
                }
  _          <- slack.registerCommand[String]("deploy", "Deploy to production") { _ =>
                  slack
                    .send(
                      channel,
                      "Deploy v1.2.3?",
                      Seq(
                        approveBtn.render("Approve", "v1.2.3"),
                        rejectBtn.render("Reject", "v1.2.3"),
                      ),
                    )
                    .as(CommandResponse.Silent)
                }
  _          <- slack.validateSetup("MyApp", "slack-manifest.yml")
  _          <- slack.start(botToken, Some(appToken))
} yield ()
```

See the [full documentation](https://business4s.github.io/chatops4s/) for getting started, interactions, and more.
