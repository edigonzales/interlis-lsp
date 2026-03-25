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
