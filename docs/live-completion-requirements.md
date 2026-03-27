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
- Mehrzeilige Block-Snippets fuer `CLASS` und `STRUCTURE` trennen den Header in zwei Stopps:
  - Placeholder `1` ist nur der reine Name
  - Placeholder `2` ist ein optionaler Header-Suffix direkt vor einem festen `=`
- `END Name;` spiegelt nur Placeholder `1`; Modifier und spaetere `EXTENDS`-Teile des Headers werden nicht nach `END` gespiegelt.
- Der Body dieser mehrzeiligen Block-Snippets liegt auf der finalen Cursor-Position `$0`, damit `Tab` den Namen direkt in den Body bestaetigt und die Snippet-Navigation dort endet.
- Mehrzeilige Topic-Body-Block-Snippets werden mit `InsertTextMode.AsIs` ausgeliefert, damit der Editor ihre Einrueckung beim Einfuegen nicht nochmals automatisch umformt.
- In VS Code darf `Enter` bei einem weiteren Snippet-Placeholder in INTERLIS-Dateien denselben Sprung wie `Tab` ausloesen; im Body verhaelt sich `Enter` danach wieder als normales Newline.
- In VS Code gibt es im `TOPIC`-Body kein Auto-Popup direkt nach `Enter` auf leerer Zeile.
- Das Auto-Popup erfolgt dort erst nach einem Identifier-Prefix wie `CL`, `STR` oder `SIG`; manuelle Completion auf leerer Topic-Zeile bleibt moeglich.

## MODEL-Body-Completion

- Auf leerer oder nur mit einem Identifier-Prefix begonnenen Zeile innerhalb eines `MODEL`-Bodies werden nur grammatikalisch erlaubte model-level Starts angeboten.
- Erlaubt sind in v1:
  - `TOPIC`
  - `CLASS`
  - `STRUCTURE`
  - `DOMAIN`
  - `UNIT`
  - `FUNCTION`
  - `CONTEXT`
  - `LINE FORM`
- Nicht angeboten werden dort insbesondere `ASSOCIATION`, `VIEW`, `GRAPHIC`, `CONSTRAINTS`, `SIGN BASKET`, `REFSYSTEM BASKET`, `MODEL` und `IMPORTS`.
- `TOPIC` wird dort sowohl als nacktes Keyword als auch als Block-Snippet `TOPIC Name = ... END Name;` angeboten.
- `FUNCTION` und `LINE FORM` bleiben in v1 keyword-only.
- Weitere model-level Deklarationen wie `CLASS`, `STRUCTURE`, `DOMAIN`, `UNIT` und `CONTEXT` duerfen zusaetzlich Starter-Snippets liefern.
- Im `MODEL`-Body gibt es in VS Code wie im `TOPIC`-Body kein Auto-Popup direkt nach leerem `Enter`; Auto-Popup beginnt erst nach einem Identifier-Prefix oder bei manueller Completion.

## Top-Level-`MODEL`

- Das Dokument-Top-Level nach `INTERLIS ...;` verwendet fuer `MODEL` einen expliziten Completion-/Snippet-Pfad, nicht den On-Type-Auto-Closer auf `=`.
- Auf leerer oder nur mit einem Identifier-Prefix begonnenen Zeile im Dokument-Root wird `MODEL` angeboten.
- Das gilt auch nach einem bereits abgeschlossenen `MODEL` sowie nach Kommentar-, Banner- und `!!@`-Zeilen zwischen zwei Modellen, solange der Cursor weiterhin im Dokument-Root und ausserhalb aller Container steht.
- Zusaetzlich gibt es dort ein Full-Snippet `MODEL Name (lang) AT ... VERSION ... = ... END Name.`.
- Das `MODEL`-Snippet ist name-first mit weiteren Header-Stopps fuer Sprache, URL und `VERSION`.
- Der Modellbody liegt auf der finalen Cursor-Position `$0`.
- `END Name.` spiegelt nur den Modellnamen.
- Das Modell-Snippet bleibt meinungsstark:
  - das Banner und die Modell-Metaattribute werden als konkrete Beispielwerte eingefuegt
  - nur Name, Sprache, URL und `VERSION` sind editierbare Placeholder
- In den vier editierbaren `MODEL`-Header-Placeholdern gibt es in VS Code bewusst keine Completion-Popups; `Tab` und `Enter` navigieren dort immer zum naechsten Placeholder.
- In VS Code gibt es am Dokument-Root kein Auto-Popup direkt auf leerer Zeile; Auto-Popup beginnt dort erst nach einem Identifier-Prefix wie `MO` oder bei manueller Completion.

## Header-Folge-Completion fuer `CLASS`, `STRUCTURE` und `TOPIC`

- Modifier und `EXTENDS` werden nicht als Variantenflut im ersten `TOPIC`-Body-Popup angeboten.
- Im ersten Popup bleiben die Basisstarter wie `CLASS` und `STRUCTURE`; das nackte Keyword steht dort vor dem Full-Block-Snippet.
- Die Folge-Completion ist gestuft:
  - nach `CLASS Name ` bzw. `STRUCTURE Name ` werden `(ABSTRACT)`, `(EXTENDED)`, `(FINAL)`, `EXTENDS` und `=` angeboten
  - nach `TOPIC Name ` werden `(ABSTRACT)`, `(FINAL)`, `EXTENDS` und `=` angeboten
  - nach `(` werden nur die fuer den jeweiligen Header erlaubten Modifier-Keywords angeboten
  - nach einem vollstaendigen Modifier ohne `)` wird nur `)` angeboten
  - nach `)` werden `EXTENDS` und `=` angeboten
  - nach `EXTENDS <target> ` wird nur `=` angeboten
- Nach dem nackten Namensende ohne bestaetigende Boundary, z.B. `CLASS Foo` oder `TOPIC T`, gibt es keine Header-Folge-Completion.
- Im zweiten Header-Stopp der Block-Snippets direkt vor dem festen `=` gibt es dieselbe gestufte Folge-Completion, aber ohne redundantes `=`-Item:
  - `CLASS Name <caret>=` bzw. `STRUCTURE Name <caret>=` bieten Modifier und `EXTENDS`, aber kein `=`
  - `TOPIC Name <caret>=` bietet `(ABSTRACT)`, `(FINAL)` und `EXTENDS`, aber kein `=`
  - nach `(` werden nur die erlaubten Modifier angeboten
  - nach einem vollstaendigen Modifier ohne `)` wird nur `)` angeboten
  - nach `)` wird nur `EXTENDS` angeboten
  - nach `EXTENDS ` werden nur passende Ziele angeboten
- `CLASS` und `STRUCTURE` erlauben als Modifier `(ABSTRACT)`, `(EXTENDED)` und `(FINAL)`.
- `TOPIC` erlaubt als Modifier nur `(ABSTRACT)` und `(FINAL)`.
- `EXTENDS` bleibt zielgerichtet:
  - `CLASS` darf `CLASS` und `STRUCTURE` erweitern
  - `STRUCTURE` darf nur `STRUCTURE` erweitern
  - `TOPIC` darf nur `TOPIC` erweitern
- Die automatische VS-Code-Completion fuer diese Folge-Slots erfolgt nur auf committed boundaries wie `CLASS Foo `, `)`, `EXTENDS ` oder `EXTENDS Base `, nicht mitten im Namens-Tippen.
- Im aktiven Namens-Placeholder der Block-Snippets wie `CLASS Name = ... END Name;` darf ohne bestaetigende Boundary keine Modifier-Folge-Completion erscheinen.
- Der Wechsel vom Namen in den leeren zweiten Header-Stopp oeffnet in VS Code nicht automatisch erneut das Popup; Auto-Popup beginnt dort erst nach einem getippten Header-Prefix wie `(` oder `E`.
- `ASSOCIATION`, `VIEW` und `GRAPHIC` bleiben in dieser Ausbaustufe ausserhalb der Header-Folge-Completion.
- In VS Code springt `Enter` in INTERLIS-Snippets nur dann wie `Tab`, wenn kein Suggest-Widget sichtbar ist; bei sichtbarer Completion akzeptiert `Enter` den selektierten Vorschlag normal.

## Header-Folge-Completion fuer `DOMAIN`

- `DOMAIN` folgt demselben staged-completion-Prinzip wie `CLASS`, `STRUCTURE` und `TOPIC`.
- Nach `DOMAIN Name ` werden `(ABSTRACT)`, `(FINAL)`, `(GENERIC)`, `EXTENDS` und `=` angeboten.
- Nach `DOMAIN Name (` werden nur `ABSTRACT`, `FINAL` und `GENERIC` angeboten.
- Nach einem vollstaendigen Modifier ohne `)` wird nur `)` angeboten.
- Nach `DOMAIN Name (FINAL) ` werden `EXTENDS` und `=` angeboten.
- Nach `DOMAIN Name EXTENDS ` werden nur Domain-Ziele angeboten.
- Nach `DOMAIN Name EXTENDS Base ` wird `=` angeboten.
- Im einzeiligen `DOMAIN`-Snippet `DOMAIN Name = ...;` gibt es denselben zweiten Header-Stopp direkt vor dem festen `=`:
  - dort erscheinen Modifier und `EXTENDS`, aber kein redundantes `=`
  - nach `DOMAIN Name <caret>= ...;` werden `(ABSTRACT)`, `(FINAL)`, `(GENERIC)` und `EXTENDS` angeboten
  - nach `DOMAIN Name (FINAL) <caret>= ...;` wird nur `EXTENDS` angeboten
  - der Wechsel in diesen leeren zweiten Header-Stopp oeffnet in VS Code noch kein RHS-Popup

## RHS-Completion fuer `DOMAIN = ...`

- Rechts von `=` in einer `DOMAIN`-Definition werden nur domain-legale Typausdruecke angeboten.
- Angeboten werden insbesondere:
  - `MANDATORY`
  - Basistypen wie `TEXT`, `MTEXT`, `NAME`, `URI`, `BOOLEAN`, `NUMERIC`, `FORMAT`, `DATE`, `TIMEOFDAY`, `DATETIME`, `COORD`, `MULTICOORD`, `POLYLINE`, `AREA`, `SURFACE`, `OID`, `UUIDOID`, `BLACKBOX`
  - Meta-/Typkonstrukte wie `CLASS`, `STRUCTURE`, `ATTRIBUTE` und `ALL OF`
  - kuratierte Snippets wie `TEXT*<length>`, `MTEXT*<length>`, `1 .. 10`, `(A, B, C)`, `CLASS RESTRICTION (...)` und `ALL OF BaseDomain`
- Nicht angeboten werden dort Attribut-only Konstrukte wie `REFERENCE`, `BAG` oder `LIST`.
- Die bestehenden Tail-Mechanismen gelten auch im `DOMAIN`-RHS:
  - nach `TEXT` bzw. `MTEXT` werden `*` und `* <length>` angeboten
  - die Root-Snippets `TEXT*<length>` und `MTEXT*<length>` verwenden als Insert-Text weiterhin `${1:255}` als Defaultlänge
  - das allgemeine `DOMAIN`-Root bietet aktiv nur ein Numeric-Range-Snippet `1 .. 10` an, auch wenn String-Ranges grammatikalisch weiterhin erlaubt sind
  - nach einem nackten numerischen Literal werden `..` und `.. <upper>` angeboten
  - nach `FORMAT` greift die bestehende format-spezifische Folge-Completion
  - nach `CLASS`, `STRUCTURE` und `ATTRIBUTE` greift die bestehende `RESTRICTION`-/`OF`-Folge-Completion
- In VS Code wird die Suggest-Box fuer `DOMAIN`-Header wie bei den anderen Deklarationen provider-bestaetigt auto-geoeffnet; nach `=` in einem `DOMAIN`-RHS wird ebenfalls auto-geoeffnet, wenn der Provider domain-legale RHS-Vorschlaege liefert.
- Im `DOMAIN`-Snippet oeffnet sich dieses RHS-Popup auch nach reiner Snippet-Navigation:
  - nach dem ersten `Tab`/`Enter` bleibt der Cursor im optionalen Header-Suffix vor `=` ohne Popup
  - nach dem zweiten `Tab`/`Enter` landet der Cursor hinter `=` und vor `;`; dort wird das RHS-Popup ohne zusaetzlichen Leerschlag geoeffnet
  - beliebige Maus-Klicks oder normale Cursorbewegungen in bestehende `DOMAIN ... = ;`-Zeilen duerfen dieses Popup nicht automatisch ausloesen

## Header- und RHS-Completion fuer `UNIT`

- `UNIT` folgt wie `DOMAIN` einem gestuften Flow aus Header-Folge-Completion und RHS-Completion.
- Nach `UNIT Name ` werden `[Name]`, `(ABSTRACT)`, `EXTENDS` und `=` angeboten.
- Nach `UNIT Name [abbr] ` werden `(ABSTRACT)`, `EXTENDS` und `=` angeboten; `[Name]` wird dort nicht erneut angeboten.
- Nach `UNIT Name (` wird nur `ABSTRACT` angeboten.
- Nach einem vollstaendigen Modifier ohne `)` wird nur `)` angeboten.
- Nach `UNIT Name (ABSTRACT) ` werden `EXTENDS` und `=` angeboten.
- Nach `UNIT Name EXTENDS ` werden nur `UNIT`-Ziele angeboten.
- Im einzeiligen `UNIT`-Snippet `UNIT Name = ...;` gibt es denselben zweiten Header-Stopp direkt vor dem festen `=` wie bei `DOMAIN`:
  - dort erscheinen `[Name]`, `(ABSTRACT)` und `EXTENDS`, aber kein redundantes `=`
  - nach dem finalen `Tab`/`Enter` landet der Cursor hinter `=` und vor `;`
- Rechts von `=` in einer `UNIT`-Definition werden in v1 nur kuratierte, unit-spezifische Formen aktiv angeboten:
  - `[BaseUnit]`
  - `1000 [BaseUnit]`
  - `(UnitA / UnitB)`
  - `(UnitA * UnitB)`
- Die Kompositions-Snippets behalten diese lesbaren Labels, lassen im Insert-Text aber den zweiten Operand leer:
  - `(UnitA / UnitB)` -> `(${1:UnitA} / ${2})`
  - `(UnitA * UnitB)` -> `(${1:UnitA} * ${2})`
- Nicht aktiv angeboten werden dort allgemeine freie Ausdruecke, `functionDef`-Formen oder fachfremde Domain-/Attribut-Typen.
- Die RHS-Folge-Completion fuer `UNIT` ist eng gefuehrt:
  - nach `= [` werden nur `UNIT`-Referenzen angeboten
  - nach `= (` sowie nach `*`, `/` oder `**` in einer komponierten Unit werden nur `UNIT`-Referenzen angeboten
  - nach einer `UNIT`-Referenz in einer komponierten Unit werden `*`, `/`, `**` und `)` angeboten
- In VS Code wird die Suggest-Box fuer `UNIT` provider-bestaetigt auto-geoeffnet:
  - nach `UNIT Name `, `UNIT Name [abbr] `, `UNIT Name (ABSTRACT) ` und `UNIT Name EXTENDS `
  - nach `=` im `UNIT`-RHS
  - nach `[` sowie nach `(`, `*`, `/` und `**` in komponierten Units
  - ein rechter literaler `)`-Suffix blockiert diese komponierten `UNIT`-Auto-Popups nicht

## Kontextuelle `!!@`-Metaattribute

- `!!@`-Completion wird heuristisch auf Kommentarzeilen umgesetzt; die Grammatik und die Live-Slots bleiben dafuer unveraendert.
- In v1 werden nur Metaattribute aus den Familien `ili2db` und `ilivalidator` aktiv vorgeschlagen.
- Vorschlaege erscheinen nur dann, wenn die `!!@`-Zeile einem klar erkennbaren Folgetarget zugeordnet werden kann; verwaiste `!!@`-Zeilen ohne erkennbares Zielelement liefern nichts.
- Die `!!@`-Completion ist zweistufig:
  - nach `!!@` erscheinen kontextsensitive Assignment-Snippets
  - nach `=` erscheinen bei bekannten Metaattributen enge Wertvorschlaege
- Die Zielmatrix ist konservativ:
  - vor `STRUCTURE` nur struktur-sinnvolle `ili2db.mapping`-Varianten (`MultiSurface`, `MultiLine`, `MultiPoint`, `Multilingual`, `Localised`) sowie `ili2db.dispName`
  - vor `CLASS` nur `ili2db.dispName`, `ili2db.oid`, `ilivalid.keymsg` und `ilivalid.keymsg_<lang>`
  - vor Attributen immer `ili2db.dispName`, `ilivalid.type` und `ilivalid.multiplicity`
  - zusaetzlich bei strukturartigen Attributen bzw. `LIST`/`BAG OF` Strukturattributen `ili2db.mapping=ARRAY|JSON|EXPAND`
  - zusaetzlich bei Referenz- oder strukturartigen Attributen `ilivalid.requiredIn`
  - vor Rollen nur `ilivalid.target`, `ilivalid.multiplicity` und `ilivalid.requiredIn`
  - vor Constraints nur `ilivalid.check`, `category`, `ilivalid.msg`, `ilivalid.msg_<lang>`, `message`, `message_<lang>` und `name`
  - vor Enum-Elementen nur `ili2db.dispName`
- Die Value-Completion ist ebenfalls zielgerichtet:
  - `ili2db.mapping=` bietet auf `STRUCTURE` nur die strukturbezogenen Mapping-Werte und auf strukturartigen Attributen nur `ARRAY`, `JSON`, `EXPAND`
  - `ilivalid.type=`, `ilivalid.multiplicity=`, `ilivalid.target=` und `ilivalid.check=` bieten nur `on`, `warning`, `off`
  - `ili2db.dispName=`, `ilivalid.keymsg=`, `ilivalid.msg=` und `message=` bieten ein quoted Placeholder-Snippet
  - `ili2db.oid=`, `ilivalid.requiredIn=`, `category=` und `name=` bieten jeweils nur einen passenden Placeholder-Default
- In VS Code wird die Suggest-Box provider-bestaetigt geoeffnet:
  - auf `!!@`-Zeilen beim Tippen des Metaattributnamens
  - nach `=` bei bekannten Metaattributen mit enger Value-Completion
  - normale Cursorbewegung oder verwaiste `!!@`-Zeilen duerfen kein Fallback-Popup erzwingen
- V1 bleibt completion-only:
  - es gibt keine Diagnostics fuer falsche Metaattribute am falschen Ort
  - allgemeine Modell-Metaattribute wie `technicalContact`, `title` oder `tags` gehoeren weiterhin nicht zu dieser `!!@`-Completion
