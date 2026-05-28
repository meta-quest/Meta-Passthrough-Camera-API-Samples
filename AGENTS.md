# Agent Instructions — Meta Passthrough Camera API Samples

Native Android (Kotlin/Gradle) sample app that demonstrates the **Meta Passthrough Camera API** on Meta Quest — accessing the headset's passthrough camera feeds from an Android app.

## Source-of-truth files (read these first, do not duplicate their contents in this file)

For setup, build steps, SDK versions, and project layout, read:

- `README.md` — official setup, requirements, and build/run instructions
- `build.gradle.kts` (root + `app/`) — Android Gradle, AGP, Kotlin, SDK versions
- `gradle.properties` — Gradle JVM and AndroidX settings
- `settings.gradle.kts` — module layout
- `app/src/main/AndroidManifest.xml` — package id, permissions, target API
- `LICENSE` — license terms

## Quest / Horizon-specific notes

- The Passthrough Camera API is a sensitive headset capability. Do not strip or weaken manifest permissions when refactoring without verifying the current requirements in the Meta developer documentation.
- `README.md` License section mistakenly references "Project Flowerbed" — this is the Passthrough Camera samples repo. Do not propagate that typo into any new docs.

## Meta Quest tooling

This repository is part of the Meta Quest / Horizon OS ecosystem (a sample, library, template, or related project — the bespoke intro above describes which). Use that intro and the source-of-truth files it references for project-specific decisions; don't restate or invent facts from memory.

When the user asks anything about Quest device behavior, build / deploy / debug / capture flows, on-device performance, or Horizon OS APIs, reach for these tools instead of generic Android answers:

- **`hzdb`** — Quest-aware ADB wrapper (device list, install / launch / stop, logs, screenshots, Perfetto traces, on-device docs search). Already wired up as an MCP server via `.mcp.json`, `.vscode/mcp.json`, and `.cursor/mcp.json`. Also runnable directly: `npx -y @meta-quest/hzdb <subcommand>`.
- **Meta Quest Agentic Tools** — the full skill set, including Android-specific skills: [github.com/meta-quest/agentic-tools](https://github.com/meta-quest/agentic-tools). Install per your client (Claude Code: `/plugin install meta-vr@meta-quest`; Gemini CLI: `gemini extensions install https://github.com/meta-quest/agentic-tools`; Cursor / VS Code: install the **Meta Horizon** extension from the Marketplace).

A few behavior expectations:

- **Read this repo's files first.** Before answering anything project-specific, read `README.md` and whichever source-of-truth files the intro above points at. Don't restate their contents in chat — quote or link instead.
- **Use `hzdb` for device-side work.** Anything that touches an attached Quest (install, launch, logs, screenshot, capture, manifest inspection) goes through `hzdb`, not raw `adb`.
- **Check live Horizon OS docs before answering API questions.** `hzdb docs search "..."` queries the live docs; training data on Horizon OS APIs goes stale fast.
- **Don't fabricate SDK / engine versions.** If a version isn't visible in this repo's files, say so rather than guessing.
