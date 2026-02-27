---
sidebar_position: 3
title: Forms
---

# Forms

Forms (Slack modals) let you collect structured input from users. ChatOps4s derives the entire form definition from a case class — fields, input types, labels, and optionality are all inferred automatically.

## Defining a Form

```scala file=chatops4s-examples/src/main/scala/example/docs/FormsPage.scala start=start_form_definition end=end_form_definition
```

`derives FormDef` generates a form with:
- A text input for `service`
- A text input for `version`
- A checkbox for `dryRun`
- Field labels derived from the field names ("Service", "Version", "Dry Run")
- Fields wrapped in `Option[T]` become optional; all others are required

## Registering and Opening a Form

```scala file=chatops4s-examples/src/main/scala/example/docs/FormsPage.scala start=start_form_open end=end_form_open
```

`registerForm` returns a `FormId[T, M]` that you pass to `openForm`. You can open forms from:
- A slash command handler (using `cmd.triggerId`)
- A button click handler (using `click.triggerId`)

## Form Submission Context

The handler receives a `FormSubmission[T, M]` with:

| Field | Type | Description |
|-------|------|-------------|
| `values` | `T` | The parsed form values as your case class |
| `userId` | `UserId` | Who submitted the form |
| `metadata` | `M` | Custom metadata (set when opening the form, `String` by default) |

## Supported Field Types

`FormDef` derivation supports these Scala types, each mapped to a Slack input element:

| Scala Type | Slack Input |
|------------|-------------|
| `String` | Plain text input |
| `Int`, `Long` | Number input (integer) |
| `Double`, `Float`, `BigDecimal` | Number input (decimal) |
| `Boolean` | Checkbox |
| `LocalDate` | Date picker |
| `LocalTime` | Time picker |
| `Instant` | Datetime picker |
| `Email` | Email input |
| `Url` | URL input |
| `UserId` | User select |
| `List[UserId]` | Multi-user select |
| `ChannelId` | Channel select |
| `List[ChannelId]` | Multi-channel select |
| `ConversationId` | Conversation select |
| `List[ConversationId]` | Multi-conversation select |
| `RichTextBlock` | Rich text input |

Wrap any type in `Option[T]` to make it optional.

## Initial Values

Pre-fill form fields using `InitialValues`:

```scala file=chatops4s-examples/src/main/scala/example/docs/FormsPage.scala start=start_initial_values end=end_initial_values
```

The `.set` method uses a field selector lambda for type-safe access — the compiler ensures you're setting a value of the correct type for each field.

## Form Metadata

You can attach metadata when opening a form, and read it back in the submission handler. By default metadata is a `String`, but you can use any type that has a `MetadataCodec` (including all Circe-encodable types):

```scala file=chatops4s-examples/src/main/scala/example/docs/FormsPage.scala start=start_form_metadata end=end_form_metadata
```

This is useful for passing context (like which message triggered the form) through the form lifecycle.

## Custom Field Types

For field types beyond the built-in list, provide a `FieldCodec` instance. The most common case is a static select menu mapped to a custom enum:

```scala file=chatops4s-examples/src/main/scala/example/docs/FormsPage.scala start=start_static_select end=end_static_select
```

The same pattern works for `FieldCodec.radioButtons`, `FieldCodec.checkboxes`, `FieldCodec.multiStaticSelect`, and `FieldCodec.externalSelect`.

## AllInputs Example

The [AllInputs example](https://github.com/business4s/chatops4s/blob/main/chatops4s-examples/src/main/scala/example/AllInputs.scala) demonstrates every supported field type in a single form, including pre-filled initial values. It's a good reference for seeing all the available input types in action.
