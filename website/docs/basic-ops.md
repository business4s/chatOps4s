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
