# INTERLIS Editor

<p align="center">
<img src="https://raw.githubusercontent.com/edigonzales/interlis-lsp/refs/heads/main/client/images/preview.gif" alt="Preview GIF" />
</p>

## Description
🛠️ The INTERLIS Editor extension brings the full INTERLIS-focused language server directly into VS Code. It understands `.ili` models, validates them with [ili2c](https://github.com/claeis/ili2c), and augments the authoring experience with completions, code navigation, formatting, diagrams, and documentation exports. The extension bundles the Java server and runtime so teams can share a reproducible setup while still allowing power users to point to custom installations.

## Features

### Language server highlights ✨
- **Validation with diagnostics** ✅ – the server compiles INTERLIS models on open and save, converts ili2c messages to LSP diagnostics, and streams the compiler log to the client output channel for quick feedback.
- **Smart completions** 🎯 – context-aware proposals for `IMPORTS`, dotted type references, and model names are built from the live compilation result and discovered repositories.
- **Go to definition** 🔍 – jump to definitions inside the current workspace or linked model files based on the compiled transfer description.
- **Symbol rename** ✏️ – rename models, topics, classes, structures, and associations while updating matching references across files.
- **Document symbols** 🗂️ – browse topics, classes, associations, domains, and attributes via the VS Code outline populated from the INTERLIS AST.
- **Formatting & pretty print** 🧹 – format whole documents with ili2c’s pretty printer and on-type helpers.
- **On-type templates** ⚙️ – typing `=` after model, topic, class, or structure headers injects boilerplate blocks, meta-attributes, and matching `END` statements while restoring the caret.
- **Cached compilation** 💾 – results are cached between document events for responsive completions and navigation.
- **Diagram generation** 🖼️ – generate Mermaid or PlantUML class diagrams from the compiled model and display them in the editor.
- **GLSP diagram editor (topic containers)** 🧭 – open a dedicated `.ili` diagram editor beside the source view; it renders classes inside topic containers from the compiled transfer description via an embedded Eclipse GLSP server.
- **HTML & DOCX exports** 📄 – render human-readable documentation (including custom titles) as HTML or export styled Word documents.

### VS Code client experience 💡
- **Activation on INTERLIS files** 📂 – the extension activates for `.ili` files and contributes a TextMate grammar and language configuration for syntax highlighting and editor defaults.
- **Bundled runtime** 📦 – ships with a self-contained fat JAR and optional platform-specific JRE; paths can be overridden via settings.
- **Commands palette** 🎛️ – run "New from Template", "Compile current file", "Show Mermaid UML class diagram", "Show PlantUML class diagram", "Show documentation as HTML", and "Export documentation as DOCX" directly from VS Code.
- **Automatic side-by-side diagram opening** ↔️ – optionally opens the read-only diagram editor beside INTERLIS text files when they become active.
- **Integrated output channel** 📢 – compiler logs land in a dedicated "INTERLIS LSP" output channel that can clear itself when new runs start.
- **Configurable repositories** 🗄️ – choose preferred model repositories via settings passed to the server at initialization.
- **Remote model template** 🧾 – create a new unsaved INTERLIS document from a configurable remote template URL.
- **Caret-aware templates** 🧠 – caret tracking middleware makes sure auto-inserted templates leave the cursor at the expected position after edits are applied.
- **Webview downloads** 💾 – UML previews support saving generated SVG diagrams next to the source model.

### Example: MODEL template via completion 🧱
After `INTERLIS 2.4;`, trigger completion on a new top-level line and choose the `MODEL` template. It inserts a documentation banner, example meta-attributes, and a full `MODEL ... END` block with editable stops for model name, language, URL, and version. The four header fields are intentionally popup-free, so `Tab` and `Enter` just move to the next placeholder:

```ili
/** !!------------------------------------------------------------------------------
 * !! Version    | wer | Änderung
 * !!------------------------------------------------------------------------------
 * !! 2024-12-09 | abr  | Initalversion
 * !!==============================================================================
 */
!!@ technicalContact=mailto:acme@example.com
!!@ furtherInformation=https://example.com/path/to/information
!!@ title="a title"
!!@ shortDescription="a short description"
!!@ tags="foo,bar,fubar"
MODEL MyNewModel (de)
  AT "https://example.com"
  VERSION "2024-12-09"
  =
  -- cursor moves here
END MyNewModel.
```

### Example: generating UML and documentation 🗺️
1. Open an `.ili` file and run **INTERLIS: Compile current file** to validate it.
2. Run **INTERLIS: Show Mermaid UML class diagram** to open an interactive Mermaid diagram, or **INTERLIS: Show PlantUML class diagram** for PlantUML output.
3. Use **INTERLIS: Show documentation as HTML** for a rendered manual, or **INTERLIS: Export documentation as DOCX** to save a styled Word file.

### Example: creating a new model from a template 🧱
1. Run **INTERLIS: New from Template** from the command palette.
2. The extension downloads the configured template and opens it as a new unsaved INTERLIS document.
3. Optionally override `interlisLsp.template.url` in the settings to point to your own template source.

### Configuration options ⚙️
- `interlisLsp.server.jarPath` – override the bundled language server JAR.
- `interlisLsp.javaPath` – point to a custom Java runtime if the bundled runtime is missing.
- `interlisLsp.server.jvmArgs` – optional extra JVM arguments (for example `-Dinterlis.glsp.debugFile=/tmp/interlis-diagram-debug.json` to dump generated GLSP nodes/edges as JSON).
- `interlisLsp.modelRepositories` – comma-separated repositories resolved by the model discovery service and completion engine.
- `interlisLsp.template.url` – template URL used by **INTERLIS: New from Template**; leave empty to fall back to the default template.
- `interlisLsp.autoShowOutputOnStart` – show the INTERLIS output channel when the extension activates.
- `interlisLsp.diagram.autoOpenBeside` – auto-open the diagram editor beside `.ili` files.

### Getting started 🚀
1. Install the extension from the Marketplace.
2. Open an INTERLIS workspace, create a new `.ili` file, or run **INTERLIS: New from Template**.
3. Start typing – validation, completions, and navigation features light up automatically.
4. Use the command palette (`Ctrl+Shift+P` / `Cmd+Shift+P`) to access the template, compilation, diagram, and export commands.

### Feedback & contributions 💬
Contributions and feedback are welcome! File issues or pull requests in the [interlis-lsp](https://github.com/edigonzales/interlis-lsp) repository.
