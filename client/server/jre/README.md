# Bundled Java runtimes

Populate each platform directory with the contents of the corresponding JRE build before packaging the extension.

Expected layout:

```
darwin-arm64/
darwin-x64/
linux-x64/
linux-arm64/
win32-x64/
```

Each folder should mirror the root of a JRE distribution (e.g. include `bin/java`).
