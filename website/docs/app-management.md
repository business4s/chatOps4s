---
sidebar_position: 4
title: App Management
---

# App Management

ChatOps4s automatically generates a [Slack app manifest](https://docs.slack.dev/reference/app-manifest) from your registered handlers — the right OAuth scopes, event subscriptions, and slash commands are derived for you.

There are two ways to keep your Slack app in sync with your code:

| Approach | Method | What it does |
|----------|--------|--------------|
| **Semi-automated** | `validateSetup` / `checkSetup` | Writes a manifest file to disk; you copy-paste it into Slack's UI |
| **Fully automated** | `SlackConfigApi` (raw client) | Creates and updates the app via Slack's manifest API |

Most apps should use the semi-automated approach. The fully automated path is significantly more complex due to Slack's configuration token model.

## Semi-automated: Setup Verification

### `validateSetup`

The simplest option. Call it before `start` — it fails if the manifest is new or has changed, printing instructions to update your Slack app:

```scala file=chatops4s-examples/src/main/scala/example/docs/AppManagementPage.scala start=start_validate_setup end=end_validate_setup
```

On each run:

1. **First run** — writes the manifest file and fails with a setup guide containing a URL that opens [api.slack.com/apps](https://api.slack.com/apps) with the manifest pre-filled.
2. **Manifest unchanged** — succeeds silently.
3. **Manifest changed** (e.g. you added a command) — overwrites the file, fails with a diff and instructions to update your Slack app settings.

This fail-fast behavior is intentional: it surfaces configuration drift immediately during development.

### `checkSetup`

Returns a `SetupVerification` value instead of failing, so you can handle the result programmatically:

```scala file=chatops4s-examples/src/main/scala/example/docs/AppManagementPage.scala start=start_check_setup end=end_check_setup
```

The three cases:

| Case | Fields | Meaning |
|------|--------|---------|
| `UpToDate` | — | Manifest file matches; nothing to do |
| `Created` | `path`, `createAppUrl`, `message` | First run; file written, URL ready for app creation |
| `Changed` | `path`, `diff`, `message` | File updated; diff shows what changed |

This is useful when you want to log drift without aborting, integrate with CI checks, or build a custom setup flow.

### Custom Manifest Modification

Both methods accept a `modifier` to tweak the generated manifest before it is written:

```scala file=chatops4s-examples/src/main/scala/example/docs/GettingStarted.scala start=start_custom_manifest end=end_custom_manifest
```

Use this when your app needs settings that are not inferred from registered handlers (e.g. outgoing domains, extra scopes).

## Fully Automated: Manifest API

For fully automated app management — creating, updating, and deleting Slack apps from code — use `SlackConfigApi` from the [Raw Client](/docs/raw-client). This is the right choice for multi-tenant platforms or CI/CD pipelines that provision Slack apps programmatically.

### Configuration Tokens

Slack's manifest API uses **configuration tokens** (`xoxe.xoxp-`), which are fundamentally different from bot tokens:

- They expire after **12 hours**.
- Rotation requires a **refresh token** (`xoxe-`), which is **single-use** — each rotation invalidates the previous refresh token and returns a new one.
- There are no per-app configuration tokens. Configuration tokens are tied to a **workspace admin**, not to a specific app.

### Token Rotation

`RefreshingSlackConfigApi` handles automatic token rotation:

```scala file=chatops4s-examples/src/main/scala/example/docs/AppManagementPage.scala start=start_refreshing_config_api end=end_refreshing_config_api
```

`withApi` checks the token expiry before each call and rotates if needed. It tracks the `exp` claim from `tooling.tokens.rotate` and rotates when the token is within 5 minutes of expiry (configurable via `refreshMargin`).

The `ConfigTokenStore` trait abstracts token persistence. An in-memory implementation is provided; for production use you'll typically back it with a database or secret manager.

### HA Deployment Challenges

Running the manifest API from multiple instances is non-trivial:

- **Single-use refresh tokens**: If two instances race to rotate, the loser's refresh token is already invalid. The store implementation must use atomic compare-and-swap (e.g. Redis `WATCH`/`MULTI`, database row versioning) and the losing instance must re-read the store to pick up the winner's tokens.
- **No per-app tokens**: Configuration tokens are tied to a workspace admin, not scoped to a single app. This means all apps managed by the same admin share one token rotation chain.
- **In-memory store is single-process only**: `ConfigTokenStore.inMemory` does not synchronize across instances.

For these reasons, the semi-automated approach (`validateSetup` / `checkSetup`) is simpler and more robust for most deployments.

### API Operations

`SlackConfigApi` exposes the full manifest API:

| Method | Purpose |
|--------|---------|
| `apps.manifest.create` | Create a new Slack app from a manifest |
| `apps.manifest.update` | Update an existing app's manifest |
| `apps.manifest.validate` | Validate a manifest without creating an app |
| `apps.manifest.export` | Export an existing app's current manifest |
| `apps.manifest.delete` | Delete a Slack app |

See the [Raw Client](/docs/raw-client) page for more on the four client classes and their token types.
