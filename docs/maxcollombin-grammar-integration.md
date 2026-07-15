# upstream grammar integration experiment

Date: 2026-03-27

## Setup

- Vendored upstream grammar into `grammars-ng/` from `maxcollombin/interlis-antlr4` commit `2666dd98cb5fb779ebaf732da6d6c23fc23d1ee5`.
- Verified exact file identity via SHA-256:
  - `InterlisLexer.g4`: `dedcfe5e5c27fcad2cd84e3ad87c1ec32ed97936f9394dfb9706b84af97a4c6c`
  - `InterlisParser.g4`: `ce9f91b4f1d91b168668b6ea56ba728009784e8b94c33f1a5ecd0c5c1ad2c1a6`
- Added Gradle property switch `interlisGrammarDir` in `build.gradle`.
- Default ANTLR source directory is now `grammars-ng`.

## Commands and results

### Default experiment: `grammars-ng`

- `./gradlew clean generateGrammarSource compileJava`
  - Result: success
- `./gradlew test --tests ch.so.agi.lsp.interlis.InterlisLiveAnalyzerTest --tests ch.so.agi.lsp.interlis.InterlisLiveEditingFeatureTest`
  - Result: `77 tests completed, 5 failed`
- `./gradlew test --tests ch.so.agi.lsp.interlis.InterlisLiveEditingFeatureTest`
  - Result: success

### Comparison run: `grammars-antlr4`

- `./gradlew clean generateGrammarSource compileJava test --tests ch.so.agi.lsp.interlis.InterlisLiveAnalyzerTest --tests ch.so.agi.lsp.interlis.InterlisLiveEditingFeatureTest -PinterlisGrammarDir=grammars-antlr4`
  - Result: success

## Findings by bucket

### Compile and codegen

- No issues.
- The upstream grammar generates Java classes compatible with the current `InterlisLiveAnalyzer` listener usage.

### Syntax diagnostics on dirty editor input

Five regressions are specific to dirty attribute declarations:

- `missingAttributeTypeMarksAttributeHeadWithColon`
- `missingAttributeTypeMarksAttributeHeadWithColonBeforeEnd`
- `factorOnlyAttributeValueMarksNumericLiteral`
- `factorOnlyAttributeValueBeforeEndMarksNumericLiteral`
- `factorOnlyAttributeValueMarksStringLiteral`

Observed analyzer output with upstream grammar:

- For `attr:` before `END`:
  - `no viable alternative at input 'attr:END'`
  - `mismatched input ':' expecting ';'`
  - `mismatched input ';' expecting '.'`
  - cascading `END name mismatch` diagnostics
- For `attr:0;` and `attr:0`:
  - `Missing ';' after attribute definition`
- For `attr:"x";`:
  - `Missing ';' after attribute definition`

Expected local behavior from the existing test suite:

- `attr:` should produce `Missing type after ':'`
- `attr:0` and `attr:"x"` should produce `Missing type before value after ':'`

### Completion slot detection on dirty input

- No failures in `InterlisLiveEditingFeatureTest`.
- The experiment did not expose immediate completion regressions in the existing test corpus.

### Dirty import and `END` handling

- No dedicated test failures in this run.
- The `attr:` case shows that strict upstream parsing can cascade into misleading `END name mismatch` diagnostics when recovery happens too late.

## Minimal patch list after the raw experiment

1. Restore editor-tolerant handling for incomplete attribute declarations.
2. Fix the factor-only attribute heuristic so `attr:0` and `attr:"x"` are not misdiagnosed as missing semicolons.
3. Keep the upstream grammar strict elsewhere unless a failing live-analysis case proves a parser-side relaxation is needed.

## Likely implementation direction

- Prefer Java-side heuristics first for:
  - `Missing type after ':'`
  - `Missing type before value after ':'`
- Only reintroduce parser relaxations where the analyzer cannot recover enough structure from the strict upstream grammar.
- The first candidate for a parser-side relaxation is `attributeDef`, because all current regressions are concentrated there.
