# Live Completion Requirements

## Purpose

Diese Datei ist die normative Referenz fuer die Live-Completion im INTERLIS-LSP.
Sie beschreibt bewusst nicht alle formalen Moeglichkeiten der Sprache, sondern die
vereinbarten Regeln fuer editorisch sinnvolle Vorschlaege beim Tippen.

## Leitlinien

- Completion optimiert auf praktisches Modellieren, nicht auf die vollstaendige Anzeige aller formal moeglichen INTERLIS-Konstrukte.
- Ausserhalb bekannter Slots wird keine Completion angeboten.
- Lokale Symbole werden nur vorgeschlagen, wenn sie an der Caret-Position bereits sichtbar sind.
- Spaeter in derselben Datei deklarierte Symbole duerfen vor ihrer Deklaration nicht vorgeschlagen werden.
- Importierte Namen im Dirty-State werden nur ueber den aktuell geparsten `IMPORTS`-Block und den letzten autoritativen Snapshot akzeptiert.
- Seltene Meta-Konstrukte werden zurueckhaltend exponiert.
- `ATTRIBUTE OF ...` wird nicht als Default-Snippet angeboten.
- Gleichrangige Grammatikalternativen muessen in der Completion symmetrisch behandelt werden, insbesondere `LIST` und `BAG`.
- Container-Body-Completion wird v1 nur fuer `TOPIC` aktiviert und bleibt dort keyword-/snippet-first.

## Icon-Legende

Die angezeigten Icons sind keine eigene Sprachsemantik. Sie ergeben sich aus den vom Server gelieferten `CompletionItemKind`s:

- lokale Typ-/Domain-Symbole: Symbol-Items
- Sprach-Keywords wie `TEXT`, `BAG`, `DATE`: `Keyword`
- Templates wie `LIST OF ...`, `BAG OF ...`, `REFERENCE TO ...`: `Snippet`
- Bei Tail-Operatoren wie `*` und `..` bleibt diese Trennung sichtbar:
  - nackter Operator als `Keyword`
  - gefuehrter Folgeschritt als `Snippet`

Wie diese `CompletionItemKind`s konkret gerendert werden, entscheidet der Editor.

## Attributtyp-Root nach `:`

- Root-Vorschlaege enthalten nur editorisch sinnvolle Standardpfade.
- Root- und Tail-Slots nach `:` liefern keine normalen Symbol-Items.
- In diesen Slots werden nur Keywords, Operatoren, Snippets und portable `INTERLIS.XML*`-Vorschlaege geliefert.
- `MANDATORY` wird nach `:` als Keyword angeboten.
- Nach `MANDATORY ` werden dieselben Typvorschlaege wie nach `:` angeboten, aber `MANDATORY` selbst nicht erneut.
- Seltene Meta-Typen wie `CLASS`, `STRUCTURE`, `ATTRIBUTE` erscheinen nicht als Default-Root-Vorschlaege, sondern nur bei passendem Prefix.
- `LIST` und `BAG` muessen sowohl als Keywords als auch als symmetrische Snippets angeboten werden.
- `ATTRIBUTE OF ...` wird bewusst nicht als Root-Snippet angeboten.
- Qualifizierte Pfade im Attributtyp-Root duerfen nicht auf Root-Fallbacks zurueckfallen; nach `INTERLIS.` werden nur die portablen `XML*`-Built-ins angeboten.

## Sichtbarkeit

- Completion folgt der strikten lokalen Sichtbarkeit analog zum Compiler.
- Lokale Vorwaertsverweise werden nicht gruen gerechnet.
- Qualifizierte Namen muessen dieselbe Sichtbarkeitsregel einhalten wie unqualifizierte Namen.
- Nach einem abschliessenden Punkt bleibt die Completion im qualifizierten Slot; sie darf nicht auf den Root-Slot zurueckfallen.

## Collections

- Nach `LIST` oder `BAG` werden `{` und `OF` angeboten.
- Nach `LIST {..}` oder `BAG {..}` wird nur `OF` angeboten.
- Nach `LIST OF ` und `BAG OF ` werden nur zulaessige Strukturziele vorgeschlagen.
- `LIST` und `BAG` muessen auf Root- und Folge-Ebene gleich behandelt werden.
- Zielgerichtete Slots wie `LIST OF`, `BAG OF`, `REFERENCE TO` und `FORMAT` duerfen weiterhin echte Zieltypen als Symbol-Items liefern.

## Text-Typen

- Nach `TEXT` oder `MTEXT` wird `*` angeboten.
- Zusaetzlich wird ein Längen-Snippet mit dem Label `* <length>` und dem Insert-Text `*${1:255}` angeboten.
- Nach `TEXT*`, `TEXT *`, `MTEXT*` oder `MTEXT *` wird ein zweiter Tail-Slot fuer die Länge angeboten.
- Dieser zweite Tail-Slot ist snippet-only und fuegt nur `${1:255}` ein.
- Diese Folge-Completion gilt auch hinter `MANDATORY`, z.B. `MANDATORY TEXT`.
- In VS Code wird die Suggest-Box nach `TEXT` und `MTEXT` aktiv erneut geoeffnet, sobald der aktuelle Prefix auf diesen Tail-Slot endet; sie ist dabei nicht von einem nachfolgenden Leerschlag abhaengig.
- In VS Code wird die Suggest-Box auch nach einem nackten `*` hinter `TEXT`/`MTEXT` erneut geoeffnet, damit die Länge direkt vorgeschlagen wird.
- Damit alte Root-Vorschlaege nicht stehenbleiben, wird das Suggest-Widget bei diesen Tail-Uebergaengen sichtbar geschlossen und erneut geoeffnet.
- In VS Code wird der Tail-Retrigger fuer `TEXT`/`MTEXT` und Inline-Numerics zusaetzlich ueber einen stillen Completion-Probe-Call bestaetigt.
- Bei Replacement-/Accept-Faellen mit mehrzeichenigem Insert oder `rangeLength > 0` wird ein zweiter verzoegerter Retry eingeplant, damit Tail-Vorschlaege auch nach Completion-Accept stabil sichtbar werden.

## Inline Numeric Ranges

- Wenn nach `:` direkt ein numerisches Literal getippt wurde, wird `..` angeboten.
- Zusaetzlich wird ein Range-Snippet mit dem Label `.. <upper>` und dem Insert-Text `.. ${1}` angeboten.
- Nach `<numeric>..` und `<numeric> ..` wird ein zweiter Tail-Slot fuer den oberen Wert angeboten.
- Dieser zweite Tail-Slot ist snippet-only und fuegt nur `${1}` ein.
- Diese Regel gilt auch hinter `MANDATORY`.
- Diese Regel gilt nur fuer direkt getippte Inline-Zahlen, nicht fuer den `NUMERIC`-Pfad.
- In VS Code wird die Suggest-Box nach einem direkt getippten Integer- oder Double-Literal aktiv erneut geoeffnet, damit die Tail-Completion sichtbar wird.
- In VS Code wird die Suggest-Box auch nach einem nackten `..` erneut geoeffnet, damit der obere Wert direkt vorgeschlagen wird.
- Auch hier wird das Suggest-Widget fuer den Tail-Wechsel sichtbar refresht, statt nur ein weiteres Trigger-Signal zu senden.

## Datum und Zeit

- `DATE`, `TIMEOFDAY` und `DATETIME` werden nur als nackte Typvorschlaege fuer `INTERLIS >= 2.4` angeboten.
- `INTERLIS.XMLDate`, `INTERLIS.XMLDateTime` und `INTERLIS.XMLTime` werden in `INTERLIS 2.3` und `INTERLIS 2.4` angeboten.
- Die portable `INTERLIS.XML*`-Familie bleibt deshalb immer verfuegbar.
- Nach `INTERLIS.` werden nur `XMLDate`, `XMLDateTime` und `XMLTime` angeboten.

## FORMAT

- `FORMAT` wird nach `:` als Root-Keyword angeboten.
- Nach `FORMAT ` werden angeboten:
  - `INTERLIS.XMLDate`
  - `INTERLIS.XMLDateTime`
  - `INTERLIS.XMLTime`
  - sichtbare formatierte Domains
  - ein minimaler Starter `BASED ON ...`
- `FORMAT`-Optimierung bleibt in dieser Ausbaustufe bewusst auf die haeufigen Datum/Zeit-Pfade und einen minimalen `BASED ON`-Starter beschraenkt.

## FORMAT und Bounds

- `FORMAT INTERLIS.XMLDate*` und `FORMAT INTERLIS.XMLTime` brauchen String-Bounds.
- Ohne `FORMAT` werden nach `INTERLIS.XMLDate`, `INTERLIS.XMLDateTime` und `INTERLIS.XMLTime` keine Bounds vorgeschlagen.
- Nach `FORMAT INTERLIS.XMLDate` wird ein Bounds-Snippet fuer Datumswerte angeboten.
- Nach `FORMAT INTERLIS.XMLDateTime` wird ein Bounds-Snippet fuer DateTime-Werte angeboten.
- Nach `FORMAT INTERLIS.XMLTime` wird ein Bounds-Snippet fuer Zeitwerte angeboten.

## Live-Validierung fuer qualifizierte Namen

- Die Live-Validierung loest lokale Namen zuerst ueber den Live-Scope und externe qualifizierte Namen danach ueber den letzten erfolgreichen autoritativen Snapshot auf.
- `INTERLIS.*` gilt als vordefiniertes Modell und braucht keinen `IMPORTS`-Eintrag.
- Andere externe qualifizierte Namen wie `GeometryCHLV95_V1.MultiSurface` bleiben im Dirty-State an die aktuell geparste `IMPORTS`-Liste gebunden.
- Die Live-Validierung darf lokale Vorwaertsverweise nicht ueber den Snapshot gruen rechnen.
- Auf `didChange` wird dafuer kein neuer `ili2c`-Compile gestartet; verwendet wird nur der letzte erfolgreiche Snapshot.

## Unused Imports

- Unbenutzte `IMPORTS` werden live als `Warning` markiert, nicht als Error.
- Die Warning wird nur auf dem Modellnamen im `IMPORTS`-Block gesetzt.
- Der Diagnostic-Tag dafuer ist `Unnecessary`.
- Nach erfolgreichem `didSave` oder explizitem Compile bleibt diese Warning ebenfalls sichtbar; sie wird dann als eigener persistenter Lint-Diagnostic neben den ili2c-Diagnostics publiziert.
- Bei Compile-Fehlern wird keine zusaetzliche unused-import-Warning an die ili2c-Diagnostics angehaengt.
- Qualifizierte Verwendungen wie `BaseModel.T.Type` zaehlen sofort als Nutzung.
- Unqualifizierte Verwendungen zaehlen als Nutzung, wenn sie ueber den letzten autoritativen Snapshot auf ein Element des importierten Modells aufgeloest werden koennen.
- Halbfertige qualifizierte Pfade wie `BaseModel.` oder `BaseModel.T.` zaehlen bereits konservativ als Nutzung.
- Bei kaputten `IMPORTS`-Zeilen oder ueberlappender Syntaxdiagnostik wird keine unused-import-Warning erzeugt.

## Fehlendes Semikolon

- Wenn nach einer Attributdefinition nur der Strichpunkt fehlt, wird die Typklausel markiert, nicht das nachfolgende `END`.
- Bei einfachen Typen wie `BOOLEAN` ist das nur der Typname.
- Bei qualifizierten oder mehrteiligen Typklauseln wie `INTERLIS.XMLDate`, `LIST {..} OF Foo` oder `REFERENCE TO Bar` wird die komplette Typklausel markiert.

## Fehlender Attributkopf

- Wenn in einem Attributkontext nur der Attributname ohne `:` und Typ steht, z.B. `attr;` oder `attr` vor `END`, wird der Attributname markiert.
- Die Meldung dafuer lautet `Missing ':' and type after attribute name`.
- Wenn der Attributkopf bereits `:` enthaelt, aber kein Typ mehr folgt, z.B. `attr:` oder `attr:   ` vor `END`, wird die ganze Kopfspanne `attr:` markiert.
- Die Meldung dafuer lautet `Missing type after ':' in attribute definition`.
- Wenn nach `:` direkt ein Wert/Factor statt eines Typs steht, z.B. `attr:0;` oder `attr:"x";`, ist die Klausel live ebenfalls ungueltig.
- Markiert wird dann das erste unzulaessige Pseudo-Typ-Token, nicht der ganze Attributkopf.
- Die Meldung dafuer lautet `Missing type before value after ':' in attribute definition`.
- Diese Regel aendert die Tail-Completion nicht: nach `attr:0` duerfen `..` und `.. <upper>` weiterhin als Reparaturhilfe angeboten werden.

## IMPORTS und INTERLIS-Version

- `IMPORTS`-Completion folgt einer Strict-Same-Policy fuer Repository-Modelle.
- In `INTERLIS 2.3;` werden bevorzugt bzw. bei bekannter Metadatenlage ausschliesslich `ili2_3`-Modelle vorgeschlagen.
- In `INTERLIS 2.4;` werden bevorzugt bzw. bei bekannter Metadatenlage ausschliesslich `ili2_4`-Modelle vorgeschlagen.
- Wenn die Dokumentversion oder die Repository-Metadaten eines Modells nicht sicher auswertbar sind, wird das Modell nicht weggefiltert.

## TOPIC-Body-Completion

- Auf leerer oder nur mit einem Identifier-Prefix begonnenen Zeile innerhalb eines `TOPIC`-Bodies werden nur grammatikalisch erlaubte Topic-Starts angeboten.
- Erlaubt sind in v1:
  - `CLASS`
  - `STRUCTURE`
  - `ASSOCIATION`
  - `VIEW`
  - `GRAPHIC`
  - `DOMAIN`
  - `UNIT`
  - `FUNCTION`
  - `CONTEXT`
  - `CONSTRAINTS`
  - `SIGN BASKET`
  - `REFSYSTEM BASKET`
- Nicht angeboten werden dort insbesondere `TOPIC`, `MODEL`, `IMPORTS`, `LINE FORM` und andere model-only Starts.
- `FUNCTION` ist in v1 keyword-only; die anderen haeufigen Topic-Starts duerfen zusaetzlich Starter-Snippets liefern.
- Innerhalb von `CLASS`-, `STRUCTURE`- oder anderen nicht-Topic-Containern darf dieser Slot nicht feuern.
- Topic-Body-Snippets richten ihre Block-Einrueckung an der semantischen Body-Einrueckung des `TOPIC`-Containers aus, nicht an der aktuellen Leerzeile.
- Bei Block-Snippets wie `CLASS Name = ... END Name;` muessen `CLASS` und `END Name;` gleich eingerueckt sein.
- Mehrzeilige Topic-Body-Snippets sind name-first: zuerst wird der Blockname editiert, danach der Body.
- `END Name;` spiegelt den Namen ueber einen nicht-aktiven Mirror, nicht ueber einen zweiten aktiven Cursor.
- Der Body dieser mehrzeiligen Block-Snippets liegt auf der finalen Cursor-Position `$0`, damit `Tab` den Namen direkt in den Body bestaetigt und die Snippet-Navigation dort endet.
- Mehrzeilige Topic-Body-Block-Snippets werden mit `InsertTextMode.AsIs` ausgeliefert, damit der Editor ihre Einrueckung beim Einfuegen nicht nochmals automatisch umformt.
- In VS Code darf `Enter` bei einem weiteren Snippet-Placeholder in INTERLIS-Dateien denselben Sprung wie `Tab` ausloesen; im Body verhaelt sich `Enter` danach wieder als normales Newline.
- In VS Code gibt es im `TOPIC`-Body kein Auto-Popup direkt nach `Enter` auf leerer Zeile.
- Das Auto-Popup erfolgt dort erst nach einem Identifier-Prefix wie `CL`, `STR` oder `SIG`; manuelle Completion auf leerer Topic-Zeile bleibt moeglich.
