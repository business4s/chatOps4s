---
sidebar_position: 0
title: Overview
---

# Interactions

ChatOps4s supports Slack interactions exclusively through [Socket Mode](https://api.slack.com/apis/socket-mode). This means your application connects to Slack over a WebSocket — no public URL, no HTTP server, no ingress configuration needed.

## Socket Mode

When you call `slack.start(botToken, Some(appToken))`, the library:

1. Calls [apps.connections.open](https://api.slack.com/methods/apps.connections.open) to get a WebSocket URL.
2. Opens and maintains a WebSocket connection.
3. Routes incoming events (button clicks, slash commands, form submissions) to the handlers you registered.

The `appToken` (an `xapp-...` token with `connections:write` scope) is required for Socket Mode. If you only need to send messages without receiving interactions, you can pass `None` for the app token — but then you won't receive button clicks, slash commands, or form submissions.

## Disconnect Handling

Slack [periodically disconnects](https://docs.slack.dev/apis/events-api/using-socket-mode#disconnect) Socket Mode clients (e.g. during deployments or when a connection has been open too long). ChatOps4s handles these disconnects automatically by reconnecting when it receives a `disconnect` frame with reasons like `link_disabled`, `warning`, or `refresh_requested`.

You don't need to add any retry logic — the library takes care of it.

## Interaction Types

ChatOps4s supports three types of interactive features, each covered in its own section:

| Feature | Description |
|---------|-------------|
| [Slash Commands](commands) | Users type `/command args` in Slack. Arguments parsed into typed values. |
| [Buttons](buttons) | Clickable buttons attached to messages. Handlers receive typed payloads. |
| [Forms](forms) | Modal dialogs with input fields. Derived from case classes. |

All interactions require Socket Mode (i.e. an `appToken`).
