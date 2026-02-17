---
sidebar_position: 4
title: Raw Client
---

# Raw Client

The `chatops4s-slack-client` module is a standalone, low-level Slack API client that `chatops4s-slack` builds on. You generally won't need it directly, but it's available if you need access to Slack API types or want to call endpoints that the high-level API doesn't expose.

## Design

The client was **AI-generated** with a focus on:

- **Tailored for Scala** — uses idiomatic Scala 3 features: opaque types for IDs and tokens, `derives Codec` for JSON, enums for closed sets, `Option` for nullable fields.
- **Strong type safety** — token types (`SlackBotToken`, `SlackAppToken`) validate their format at construction. Entity IDs (`ChannelId`, `UserId`, `TeamId`, etc.) are opaque types that prevent accidental mixing.
- **Verified against official sources** — every model type was cross-referenced against the [Slack API docs](https://docs.slack.dev/) and the [Java Slack SDK](https://github.com/slackapi/java-slack-sdk). Source models include comment links to their reference documentation and Java SDK counterparts.

## Usage

Construct a `SlackApi` with any sttp `Backend` and a `SlackBotToken`, then call methods directly:

```scala file=chatops4s-examples/src/main/scala/example/docs/RawClientPage.scala start=start_raw_client_usage end=end_raw_client_usage
```

The high-level `chatops4s-slack` module re-exports everything you typically need, so you only need to reach into `chatops4s.slack.api` for lower-level types.
