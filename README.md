# INTERLIS LSP 

This is a minimal Language Server for INTERLIS using lsp4j. It exposes a command
`interlis.validate` that validates a `.ili` file via the Java ili2c library
(**no external process**) and returns the compiler log; it also publishes LSP diagnostics.

> The ili2c API calls here are placeholders. Replace them with the real library calls and add the proper dependency in `build.gradle`.

## Build & Run

```bash
./gradlew run
```

The server uses stdio transport. Your editor/extension should spawn it and connect via stdio.

### Bundling with the VS Code extension

The VS Code client located in `client/` can ship a self-contained runtime. The
extension looks for the following layout relative to the extension root when no
custom paths are configured:

```
client/
  server/
    interlis-lsp-<version>-all.jar  # fat jar produced by `./gradlew shadowJar`
    jre/
      darwin-arm64/
      darwin-x64/
      linux-x64/
      linux-arm64/
      win32-x64/
        bin/java(.exe)
```

Use your existing GitHub Actions artifacts to populate the platform-specific
JRE folders before packaging the extension (`vsce package`). Users can still
override both paths with the `interlisLsp.server.jarPath` and
`interlisLsp.javaPath` settings if they need to point to custom locations.

### Publishing the VS Code extension via GitHub Actions

The `build and publish` workflow packages the extension (`interlis-lsp.vsix`) and
publishes it to the Visual Studio Marketplace whenever the
`VS_MARKETPLACE_TOKEN` secret is present. To configure the token:

1. Create a Personal Access Token for the Marketplace by visiting
   <https://aka.ms/vscodepat> and generating a **Publish Extensions** token for
   your publisher account.
2. In your GitHub repository, open **Settings → Secrets and variables → Actions**
   and create a new repository secret named `VS_MARKETPLACE_TOKEN` that contains
   the token value from step 1. GitHub stores the value securely and only exposes
   it to workflow runs.

During the workflow run, the secret is injected as the `VSCE_PAT` environment
variable for the publish step and consumed by `vsce publish`. No code or logs in
the repository can read the secret directly, keeping the credential scoped to
the workflow.

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
- ~~syntax highlighting~~
- ~~syntax highlighting: brackets nur für Klammern (Wörter mit Snippets oder onEnterRules)~~
- ~~syntax highlighting: autoClosingPairs auch hier word pairs mit snippets oder onEnterRules)~~
- syntax highlighting: 2) Enhance with Semantic Tokens from your Java LSP ?? Semantic folding
Real “fold by AST” works best via your LSP (foldingRangeProvider) so blocks fold exactly from … = to the matching END ….
- Server Capabilities dokumentieren, e.g. initializeAdvertisesFormattingCapability für output verwenden.
- ~~UML~~
- UML: plantuml support (auch mit PLANTUML SOURCE)
- ~~Hyperlink: -> Debug output, um besser zu verstehen, was abgeht. Auch die gefunden Modelle etc. pp~~
- ~~Hyperlink: auch Strukturen und Domains etc.~~
- word output
- Sequence Diagramme für verschiedene Features
- Autocomplete / Suggestions
 
 
 ## Develop
 
 ```
 git fetch origin pull/9/head:codex/update-interlistextdocumentservice-to-bypass-cache
 ```
 
 ```
 git checkout codex/update-interlistextdocumentservice-to-bypass-cache
 ```
 
 ```
 git checkout main
 ```
 

 
