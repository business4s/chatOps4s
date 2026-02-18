---
sidebar_position: 2
title: Buttons
---

# Buttons

Buttons are interactive elements attached to messages. ChatOps4s makes buttons type-safe by associating each button handler with a value type.

## Registering a Button Handler

```scala file=chatops4s-examples/src/main/scala/example/docs/ButtonsPage.scala start=start_register_button end=end_register_button
```

`registerButton[T]` returns a `ButtonId[T]` — a typed handle you use to create button instances. The type parameter `T` (which must be a subtype of `String`) is the type of value the button carries.

## Rendering Buttons on Messages

Use `ButtonId[T].render` to create `Button` instances and attach them to messages:

```scala file=chatops4s-examples/src/main/scala/example/docs/ButtonsPage.scala start=start_render_buttons end=end_render_buttons
```

The first argument is the button label (displayed to the user), the second is the typed value passed to your handler when clicked.

## Typed Button Values

The type parameter on `ButtonId` and `ButtonClick` constrains what values a button can carry. The most common use is an opaque type that restricts the set of valid values:

```scala file=chatops4s-examples/src/main/scala/example/docs/ButtonsPage.scala start=start_constrained_type end=end_constrained_type
```

```scala file=chatops4s-examples/src/main/scala/example/docs/ButtonsPage.scala start=start_constrained_usage end=end_constrained_usage
```

This ensures only valid `Environment` values can be passed to `render` — the compiler prevents mistakes at the call site rather than at runtime.

## Removing Buttons After Click

A common pattern is to replace the buttons with a status message after a click:

```scala file=chatops4s-examples/src/main/scala/example/docs/ButtonsPage.scala start=start_remove_buttons end=end_remove_buttons
```

`update` replaces the entire message content, including removing any buttons unless you pass new ones.
