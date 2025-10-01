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

## Reminder
- OUTPUT-Tab kann nicht gelöscht werden. Man müsste einen eigenen LanguageClient (aufm Server) schreiben und dann auch ein Signal senden (zum Clearen).

## TODO

- ~~rename validate -> compile~~
- ~~messaging, logging finalisieren. inkl client popup (?) + Parser für ili2c log~~
- ~~compile onSave (und nicht onChange). Falls man onChange möchte, darf nicht die gespeicherte Datei zum LSP geschickt werden, sondern der Memory-Inhalt.~~
- probiere onChange mit "fast approach" siehe Frage bei jEdit.
- ~~Enable assert in tests.~~ And more tests.
- snippets / autoclose
- ~~pretty print (2 lokalen Modellen)~~    
- syntax highlighting
- syntax highlighting: brackets nur für Klammern (Wörter mit Snippets oder onEnterRules)
- syntax highlighting: autoClosingPairs auch hier word pairs mit snippets oder onEnterRules)
- syntax highlighting: 2) Enhance with Semantic Tokens from your Java LSP ?? Semantic folding
Real “fold by AST” works best via your LSP (foldingRangeProvider) so blocks fold exactly from … = to the matching END ….
- Server Capabilities dokumentieren, e.g. initializeAdvertisesFormattingCapability für output verwenden.
- UML
- Hyperlink: -> Debug output, um besser zu verstehen, was abgeht. Auch die gefunden Modelle etc. pp
- Hyperlink: auch Strukturen und Domains etc.
- word output
 
