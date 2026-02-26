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
- **Manifest generation** — `validateSetup` generates and checks your Slack app manifest automatically
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

The `validateSetup` call keeps your Slack app configuration in sync with your code. On first run it generates a manifest file and prints a setup guide with a one-click app creation URL. On subsequent runs it compares the manifest against the file on disk and fails with a diff if they diverge.

You never need to manually figure out which OAuth scopes or event subscriptions your app needs — the library derives them from your handler registrations.

See [App Management](/docs/app-management) for the full guide, including the programmatic `checkSetup` alternative, custom manifest modification, and fully automated app management via the manifest API.

## Next Steps

- [Basic Operations](/docs/basic-ops) — sending messages, replies, reactions
- [Interactions](/docs/interactions/) — slash commands, buttons, forms
- [App Management](/docs/app-management) — setup verification and manifest API
- [Raw Client](/docs/raw-client) — the underlying Slack API client
