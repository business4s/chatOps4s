---
sidebar_position: 1
title: Getting Started
---

# Getting Started

ChatOps4s is a Scala library for building chat-ops workflows in Slack. It gives you a type-safe, minimal API for messages, buttons, slash commands, and modal forms — all over [Socket Mode](https://api.slack.com/apis/socket-mode), so you don't need a public URL.

## Installation

import SbtDependency from '@site/src/components/SbtDependency';

<SbtDependency moduleName={"chatops4s-slack"} />

You also need an [sttp](https://sttp.softwaremill.com/en/latest/backends/summary.html) backend that supports WebSockets. For example, with fs2:

```scala
"com.softwaremill.sttp.client4" %% "fs2" % "4.0.9"
```

## Define Your App

Write your handlers first. Here's an app with buttons and a slash command — you'll need a Slack workspace to run it, but write the code first:

```scala file=chatops4s-examples/src/main/scala/example/docs/GettingStarted.scala start=start_hero end=end_hero
```

## Set Up Your Slack App

The `validateSetup` call in the example above is doing the heavy lifting. On first run it:

1. Generates a [Slack app manifest](https://docs.slack.dev/reference/app-manifest) from your registered handlers (the right OAuth scopes, event subscriptions, and slash commands are derived automatically)
2. Writes it to a file (`slack-manifest.yml`)
3. Prints a setup guide with a **one-click URL** that opens [api.slack.com/apps](https://api.slack.com/apps) with the manifest pre-filled

You never need to manually figure out which scopes or event subscriptions your app needs. On subsequent runs, `validateSetup` checks the manifest against the file on disk and fails with a diff if they diverge.

See [App Management](/docs/app-management) for the programmatic `checkSetup` alternative, custom manifest modification, and fully automated app management via the manifest API.

## Run Your App

After creating your Slack app from the generated manifest, grab your tokens from the app settings page and run:

```scala file=chatops4s-examples/src/main/scala/example/docs/GettingStarted.scala start=start_minimal end=end_minimal
```

The bot token (`xoxb-`) comes from **OAuth & Permissions**, and the app token (`xapp-`) from **Basic Information > App-Level Tokens** (with the `connections:write` scope).

## What You Get

- **Simple API** — `send`, `reply`, `update`, `delete`, reactions, ephemeral messages
- **Typed buttons** — handlers receive `ButtonClick[T]` with your value type
- **Typed commands** — argument parsing derived from case classes
- **Modal forms** — `derives FormDef` turns a case class into a Slack modal with 15+ field types
- **Manifest generation** — `validateSetup` generates and checks your Slack app manifest automatically
- **Runtime-agnostic** — built on [sttp](https://sttp.softwaremill.com), works with any backend that supports WebSockets
- **Standalone Slack client** — a [type-safe, AI-generated Slack API client](/docs/raw-client) you can use independently

## Next Steps

- [Basic Operations](/docs/basic-ops) — sending messages, replies, reactions
- [Interactions](/docs/interactions/) — slash commands, buttons, forms
- [App Management](/docs/app-management) — setup verification and manifest API
- [Raw Client](/docs/raw-client) — the underlying Slack API client
