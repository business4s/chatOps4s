---
sidebar_position: 1
title: Commands
---

# Slash Commands

Register slash commands with `registerCommand`. Each command gets a typed argument parser and an optional description (used in the Slack manifest and help text).

## Basic Command

The simplest form receives the raw command text as a `String`:

```scala file=chatops4s-examples/src/main/scala/example/docs/CommandsPage.scala start=start_basic_command end=end_basic_command
```

## Command Responses

Handlers return a `CommandResponse`:

| Response | Behavior |
|----------|----------|
| `CommandResponse.Ephemeral(text)` | Reply visible only to the user who ran the command |
| `CommandResponse.InChannel(text)` | Reply visible to everyone in the channel |
| `CommandResponse.Silent` | No visible reply |

## Derived Case Class Parsing

For commands with multiple arguments, derive `CommandParser` from a case class. Each field needs a `CommandArgCodec` instance (built-in for `String`, `Int`, `Long`, `Double`, `Float`, `BigDecimal`, `Boolean`, and `Option[T]`):

```scala file=chatops4s-examples/src/main/scala/example/docs/CommandsPage.scala start=start_derived_command end=end_derived_command
```

## Custom CommandParser

For single-argument commands where you want to validate or constrain the input, define a `CommandParser` instance manually:

```scala file=chatops4s-examples/src/main/scala/example/docs/CommandsPage.scala start=start_parser_def end=end_parser_def
```

```scala file=chatops4s-examples/src/main/scala/example/docs/CommandsPage.scala start=start_parser_usage end=end_parser_usage
```

When parsing fails, the error message is shown to the user as an ephemeral response.

## Usage Hints

The `usageHint` from your `CommandParser` is included in the generated Slack manifest, so users see help text when typing your command. For derived case classes, hints are auto-generated from field names (e.g. `[service] [replicas]`).

You can also provide an explicit usage hint when registering:

```scala file=chatops4s-examples/src/main/scala/example/docs/CommandsPage.scala start=start_usage_hint end=end_usage_hint
```

Note that usage hints are part of the Slack app manifest — they're not dynamic. If you change a hint, you need to update the manifest in your Slack app settings (the `verifySetup` call will tell you when this is needed).
