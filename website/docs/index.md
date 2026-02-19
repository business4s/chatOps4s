---
sidebar_position: 1
title: Getting Started
---

# Getting Started

ChatOps4s is a Scala library for building chat-ops workflows in Slack. It gives you a type-safe, minimal API for messages, buttons, slash commands, and modal forms — all over [Socket Mode](https://api.slack.com/apis/socket-mode), so you don't need a public URL.

```scala file=chatops4s-examples/src/main/scala/example/docs/GettingStarted.scala start=start_hero end=end_hero
```

What you get:

- **Simple API** — `send`, `reply`, `update`, `delete`, reactions, ephemeral messages
- **Typed buttons** — handlers receive `ButtonClick[T]` with your value type
- **Typed commands** — argument parsing derived from case classes
- **Modal forms** — `derives FormDef` turns a case class into a Slack modal with 15+ field types
- **Manifest generation** — `verifySetup` generates and checks your Slack app manifest automatically
- **Runtime-agnostic** — built on [sttp](https://sttp.softwaremill.com), works with any backend that supports WebSockets
- **Standalone Slack client** — a [type-safe, AI-generated Slack API client](/docs/raw-client) you can use independently

## Installation

import SbtDependency from '@site/src/components/SbtDependency';

<SbtDependency moduleName={"chatops4s-slack"} />

You also need an [sttp](https://sttp.softwaremill.com/en/latest/backends/summary.html) backend that supports WebSockets. For example, with fs2:

```scala
"com.softwaremill.sttp.client4" %% "fs2" % "4.0.9"
```

## Minimal Example

```scala file=chatops4s-examples/src/main/scala/example/docs/GettingStarted.scala start=start_minimal end=end_minimal
```

## Setup Verification

The `verifySetup` call is the recommended way to keep your Slack app configuration in sync with your code. On each run it:

1. **First run** — generates a manifest YAML file from your registered handlers and prints a setup guide:
   - A URL that opens [api.slack.com/apps](https://api.slack.com/apps) with the manifest pre-filled, so you can create the app in one click.
   - Instructions for installing the app and obtaining tokens.
2. **Subsequent runs** — compares the generated manifest against the file on disk. If they differ (e.g. you added a new command), it prints a diff and instructions to update your Slack app.

You can also customize the generated manifest before it is verified and written to disk:

```scala file=chatops4s-examples/src/main/scala/example/docs/GettingStarted.scala start=start_custom_manifest end=end_custom_manifest
```

This is useful when your app needs extra settings that are not inferred from registered handlers.

The generated (and optionally customized) manifest can also be used with the [Raw Client](/docs/raw-client) for automated app creation and updates via Slack's `apps.manifest.create` and `apps.manifest.update` APIs.

This means you never need to manually figure out which OAuth scopes or event subscriptions your app needs — the library derives them from your handler registrations. Here's an example of what the generated manifest looks like:

```yaml file=chatops4s-slack/src/test/resources/snapshots/manifest-with-commands.yaml
```

## Next Steps

- [Basic Operations](/docs/basic-ops) — sending messages, replies, reactions
- [Interactions](/docs/interactions/) — slash commands, buttons, forms
- [Raw Client](/docs/raw-client) — the underlying Slack API client
