# INTERLIS LSP (Java 21 + lsp4j) — Gradle Groovy

This is a minimal Language Server for INTERLIS using lsp4j. It exposes a command
`interlis.validate` that validates a `.ili` file via the Java ili2c library
(**no external process**) and returns the compiler log; it also publishes LSP diagnostics.

> The ili2c API calls here are placeholders. Replace them with the real library calls and add the proper dependency in `build.gradle`.

## Build & Run

```bash
./gradlew run
```

The server uses stdio transport. Your editor/extension should spawn it and connect via stdio.

## Project layout

- `InterlisLanguageServer` implements `LanguageServer` and `LanguageClientAware`.
- `InterlisTextDocumentService` and `InterlisWorkspaceService` wire document events and the `interlis.validate` command.
- `InterlisValidator` calls ili2c (placeholder) and returns both text log and structured messages.
- `DiagnosticsMapper` converts messages to LSP `Diagnostic`s.
- Tests: simple unit tests for the validator and a smoke test for the command handler.
