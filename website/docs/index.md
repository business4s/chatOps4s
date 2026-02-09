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
2. Use `slack.manifest("MyApp")` (after registering handlers) to generate the manifest YAML.
3. After creating the app, grab two tokens:
   - **Bot Token** (`xoxb-...`): found under *OAuth & Permissions*
   - **App-Level Token** (`xapp-...`): create one under *Basic Information → App-Level Tokens* with `connections:write` scope.

## Minimal Example

```scala file=./main/scala/example/docs/GettingStarted.scala start=start_minimal end=end_minimal
```

## Interactive Buttons

Register button handlers before calling `listen`. Each handler receives a typed `ButtonClick[T]` and the gateway itself:

```scala file=./main/scala/example/docs/GettingStarted.scala start=start_buttons end=end_buttons
```

## API Overview

The `SlackGateway[F]` trait provides four operations:

| Method   | Description                                         |
|----------|-----------------------------------------------------|
| `send`   | Send a message to a channel, optionally with buttons |
| `reply`  | Reply in a thread under an existing message          |
| `update` | Edit an existing message (e.g. to remove buttons)    |
| `listen`   | Start the Socket Mode event loop (takes `appToken`)  |

`SlackSetup[F]` (mixed in by `SlackGateway.create`) adds:

| Method      | Description                                              |
|-------------|----------------------------------------------------------|
| `onButton`  | Register a typed button handler, returns a `ButtonId[T]` |
| `onCommand` | Register a slash command handler with optional description |
| `manifest`  | Generate app manifest YAML from registered handlers      |

Use `ButtonId[T].toButton(label, value)` to create `Button` instances for `send`/`reply`/`update`.
