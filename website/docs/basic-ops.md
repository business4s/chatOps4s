---
sidebar_position: 2
title: Basic Operations
---

# Basic Operations

The `SlackGateway[F]` trait provides basic messaging operations. All methods return values wrapped in your effect type `F`.

## Sending Messages

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_send end=end_send
```

`send` returns a `MessageId` (channel + timestamp) that you can use to reply, update, or delete the message later.

## Replying in Threads

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_reply end=end_reply
```

`reply` posts a message as a thread reply under the message identified by `MessageId`.

## Updating Messages

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_update end=end_update
```

`update` replaces the text (and optionally the buttons) of an existing message. This is commonly used to replace a message's buttons with a status after a user clicks one.

## Deleting Messages

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_delete end=end_delete
```

## Reactions

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_reactions end=end_reactions
```

Reactions are a lightweight way to show status on a message without updating its text.

## Ephemeral Messages

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_ephemeral end=end_ephemeral
```

Ephemeral messages are visible only to the specified user. They're useful for command acknowledgments or error messages that shouldn't clutter the channel.

## Buttons on Messages

All message-sending methods (`send`, `reply`, `update`) accept an optional `buttons` parameter:

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_buttons_on_messages end=end_buttons_on_messages
```

See [Buttons](/docs/interactions/buttons) for how to register button handlers.

## User Info Cache

`getUserInfo` fetches user profile data from Slack and caches it in memory (15-minute TTL, 1000 entries by default):

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_user_info end=end_user_info
```

You can provide a custom cache implementation:

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_custom_cache end=end_custom_cache
```

Or disable caching entirely with `UserInfoCache.noCache`.

## Idempotent Sending

`send` and `reply` accept an optional `idempotencyKey` parameter. When provided, the gateway checks for a recently sent message with the same key before posting a new one. If a match is found, the existing `MessageId` is returned and no duplicate message is sent.

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_idempotent_send end=end_idempotent_send
```

This also works for thread replies:

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_idempotent_reply end=end_idempotent_reply
```

The key is embedded in the message's [metadata](https://docs.slack.dev/metadata) field and is used for duplicate detection.

### How it works

By default, the gateway uses a **Slack scan** strategy: before posting, it calls `conversations.history` (or `conversations.replies` for threads) and looks for a message whose metadata contains the matching key. This works across restarts since the key is persisted on the message itself.

### Customizing the check

You can swap the idempotency strategy using `withIdempotencyCheck`:

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_custom_idempotency end=end_custom_idempotency
```

Available implementations:
- **`IdempotencyCheck.slackScan`** (default) — scans recent Slack messages via the API. Survives restarts and works across multiple app instances, but makes an API call per send.
- **`IdempotencyCheck.inMemory`** — fast in-memory cache with configurable TTL and max entries. No extra API calls, but state is lost on restart and **not shared across multiple app instances** — each instance maintains its own cache, so duplicates can still occur if the same key is sent from different instances.
- **`IdempotencyCheck.noCheck`** — disables idempotency checks entirely. Messages are always sent.

The `IdempotencyCheck` trait is simple to implement, so you can provide your own backed by Redis, a database, or any shared store if you need cross-instance deduplication without the per-send Slack API cost.

### Caveats

- **Race condition**: If two processes send with the same key simultaneously, both may send before either's message appears in the history scan. The `inMemory` check has the same limitation across instances.
- **Rate limiting**: The default `slackScan` makes a `conversations.history`/`conversations.replies` call for each send with a key. For high-volume use, prefer `inMemory` or a custom implementation.
- `update` and `delete` are not affected — they are already naturally idempotent by `MessageId`.

## Error Handling

By default, exceptions thrown in interaction handlers (buttons, commands, forms) are logged and swallowed so the WebSocket connection stays alive. You can replace the default handler with `onError`:

```scala file=chatops4s-examples/src/main/scala/example/docs/BasicOps.scala start=start_on_error end=end_on_error
```

This is useful for sending errors to an alerting system or logging them in a structured format. The handler replaces any previously set handler.
