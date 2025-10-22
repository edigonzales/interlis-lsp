# INTERLIS GLSP Server

This project hosts the Eclipse GLSP server that renders UML class diagrams for INTERLIS models. The server is packaged with the VS Code extension so that diagrams can be shown alongside the textual `.ili` editor.

The current prototype extracts the top-level classes from the active INTERLIS model and lays them out in a simple grid. Each node
is rendered using the GLSP default rectangle view with INTERLIS-specific colours, so that the diagram updates as soon as the
underlying `.ili` file is saved or re-opened.

## Request flow at a glance

```mermaid
sequenceDiagram
    participant VSCode as VS Code extension
    participant Webview as GLSP webview host
    participant Client as GLSP browser client
    participant Server as GLSP Java server
    participant Ili2c as ili2c compiler

    VSCode->>VSCode: User runs "Open GLSP class diagram"
    VSCode->>Webview: Create webview, launch GLSP server process
    Webview-->>Client: Load bundled GLSP frontend
    Client->>Server: Connect via websocket & request model (sourceUri)
    Server->>Server: InterlisSourceModelStorage records URI
    Server->>Server: InterlisGModelFactory asks InterlisDiagramService for model
    Server->>Ili2c: Compile INTERLIS transfer description
    Ili2c-->>Server: TransferDescription
    Server->>Client: Return GModel with class nodes
    Client-->>Webview: Render diagram
```

## Opening the diagram next to the text editor

The extension keeps the standard text editor as the default view for `.ili` files. Use the **INTERLIS: Open GLSP class diagram** command to open the diagram while the text editor remains visible:

1. Open the INTERLIS model in the normal text editor.
2. Run the command from the Command Palette or the status bar. The GLSP webview opens in a column beside the text editor, allowing both views to stay in sync.

You can also reach the same behaviour through **Open With… → INTERLIS UML Diagram (GLSP)** on any `.ili` file. Multiple diagram editors can be opened at once, each showing the live diagram for its corresponding document.

