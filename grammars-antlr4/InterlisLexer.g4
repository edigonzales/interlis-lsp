lexer grammar InterlisLexer;

// Règles lexicales INTERLIS 2.4 définies avec les référénce eCH-0031
// INTERLIS 2.4 lexikalische Regeln, definiert mit eCH-0031-Referenzen

// 3.2.7 Signes particuliers et mots réservés - Sonderzeichen und reservierte Wörter

ABSTRACT : 'ABSTRACT';
ACCORDING : 'ACCORDING';
AGGREGATES : 'AGGREGATES';
AGGREGATION : 'AGGREGATION';
AGGREGATION_OF : 'AGGREGATION OF';
ALL : 'ALL';
AND : 'AND';
ANY : 'ANY';
ANYCLASS : 'ANYCLASS';
ANYSTRUCTURE : 'ANYSTRUCTURE';
ANYOID : 'ANYOID';
ARCS : 'ARCS';
AREA : 'AREA';
AS : 'AS';
ASSIGN: ':=';
ASSOCIATION : 'ASSOCIATION';
AT : 'AT';
AT_SYMBOL : '@';
ATTRIBUTE : 'ATTRIBUTE';
ATTRIBUTES : 'ATTRIBUTES';
BACKSLASH: '\\';
BAG : 'BAG';
BASE : 'BASE';
BASED : 'BASED';
BASED_ON : 'BASED ON';
BASKET : 'BASKET';
BINARY : 'BINARY';
BLACKBOX : 'BLACKBOX';
BLANK : 'BLANK';
BOOLEAN : 'BOOLEAN';
BY : 'BY';
CARDINALITY : 'CARDINALITY';
CHARSET : 'CHARSET';
CIRCULAR : 'CIRCULAR';
CLASS : 'CLASS';
CLOCKWISE : 'CLOCKWISE';
CODE : 'CODE';
CONSTRAINT : 'CONSTRAINT';
CONSTRAINTS : 'CONSTRAINTS';
CONTEXT : 'CONTEXT';
CONTINUE : 'CONTINUE';
CONTINUOUS : 'CONTINUOUS';
CONTOUR : 'CONTOUR';
CONTRACT : 'CONTRACT'; // check if relevant, used in BJ/KS3-20060703.ili
CONTRACTED : 'CONTRACTED';
COORD : 'COORD';
COORD2 : 'COORD2';
COORD3 : 'COORD3';
COUNTERCLOCKWISE : 'COUNTERCLOCKWISE';
DATE : 'DATE';
DATETIME : 'DATETIME';
DEFAULT : 'DEFAULT';
DEFERRED : 'DEFERRED';
DEFINED : 'DEFINED';
DEGREES : 'DEGREES';
DEPENDS : 'DEPENDS';
DERIVATIVES : 'DERIVATIVES';
DERIVED : 'DERIVED';
DIM1 : 'DIM1';
DIM2 : 'DIM2';
DIRECTED : 'DIRECTED';
DIV: '/';
DOMAIN : 'DOMAIN';
END : 'END';
ENUM : 'ENUM';
ENUMTREEVAL : 'ENUMTREEVAL';
ENUMVAL : 'ENUMVAL';
EQUAL : 'EQUAL';
EXISTENCE : 'EXISTENCE';
EXTENDED : 'EXTENDED';
EXTENDS : 'EXTENDS';
EXTERNAL : 'EXTERNAL';
FINAL : 'FINAL';
FIRST : 'FIRST';
FIX : 'FIX';
FONT : 'FONT';
FORM : 'FORM';
FORMAT : 'FORMAT';
FREE : 'FREE';
FROM : 'FROM';
FUNCTION : 'FUNCTION';
GENERIC : 'GENERIC';
GENERICS : 'GENERICS';
GRADS : 'GRADS';
GRAPHIC : 'GRAPHIC';
HALIGNMENT : 'HALIGNMENT';
HASH : '#';
HIDING : 'HIDING';
I16 : 'I16';
I32 : 'I32';
IDENT : 'IDENT';
IMPORTS : 'IMPORTS';
IN : 'IN';
INHERITANCE : 'INHERITANCE';
INSPECTION : 'INSPECTION';
INSPECTION_OF : 'INSPECTION OF';
INTERLIS : 'INTERLIS';
INTERLIS1 : 'INTERLIS1';
ISSUED : 'ISSUED'; // check if relevant, used in BJ/KS3-20060703.ili
JOIN : 'JOIN';
JOIN_OF  : 'JOIN OF';
LAST : 'LAST';
LINE : 'LINE';
LINEATTR : 'LINEATTR';
LINESIZE : 'LINESIZE';
LIST : 'LIST';
LNBASE : 'LNBASE';
LOCAL : 'LOCAL';
MANDATORY : 'MANDATORY';
METAOBJECT : 'METAOBJECT';
MOD: '%';
MODEL : 'MODEL';
MTEXT : 'MTEXT';
POW: '**';
MUL: '*';
MULTIAREA : 'MULTIAREA';
MULTICOORD : 'MULTICOORD';
MULTIPOLYLINE : 'MULTIPOLYLINE';
MULTISURFACE : 'MULTISURFACE';
NAME : 'NAME';
NO : 'NO';
NOINCREMENTALTRANSFER : 'NOINCREMENTALTRANSFER';
NOT : 'NOT';
NULL : 'NULL';
NUMERIC : 'NUMERIC';
OBJECT : 'OBJECT';
OBJECTS : 'OBJECTS';
OF : 'OF';
OID : 'OID';
ON : 'ON';
OPTIONAL : 'OPTIONAL';
OR : 'OR';
ORDERED : 'ORDERED';
OTHERS : 'OTHERS';
OVERLAPS : 'OVERLAPS';
PARAMETER : 'PARAMETER';
PARENT : 'PARENT';
PERIPHERY : 'PERIPHERY';
PI : 'PI';
POLYLINE : 'POLYLINE';
PROJECTION : 'PROJECTION';
PROJECTION_OF : 'PROJECTION OF';
RADIANS : 'RADIANS';
REFERENCE : 'REFERENCE';
REFSYS : 'REFSYS';
REFSYSTEM : 'REFSYSTEM';
REQUIRED : 'REQUIRED';
RESTRICTION : 'RESTRICTION';
ROTATION : 'ROTATION';
SET : 'SET';
SIGN : 'SIGN';
STRAIGHTS : 'STRAIGHTS';
STRUCTURE : 'STRUCTURE';
SUBDIVISION : 'SUBDIVISION';
SURFACE : 'SURFACE';
SYMBOLOGY : 'SYMBOLOGY';
TABLE : 'TABLE';
TEXT : 'TEXT';
THATAREA : 'THATAREA';
THIS : 'THIS';
THISAREA : 'THISAREA';
TID : 'TID';
TIDSIZE : 'TIDSIZE';
TILDE : '~'; // newly added
TIMEOFDAY : 'TIMEOFDAY';
TO : 'TO';
TOPIC : 'TOPIC';
TRANSFER : 'TRANSFER';
TRANSIENT : 'TRANSIENT';
TRANSLATION : 'TRANSLATION';
TYPE : 'TYPE';
UNDEFINED : 'UNDEFINED';
UNION : 'UNION';
UNION_OF : 'UNION_OF';
UNIQUE : 'UNIQUE';
UNIT : 'UNIT';
UNQUALIFIED : 'UNQUALIFIED';
URI : 'URI';
UUIDOID : 'UUIDOID';
VALIGNMENT : 'VALIGNMENT';
VERSION : 'VERSION';
VERTEX : 'VERTEX';
VERTEXINFO : 'VERTEXINFO';
VIEW : 'VIEW';
WHEN : 'WHEN';
WHERE : 'WHERE';
WITH : 'WITH';
WITHOUT : 'WITHOUT';
XML : 'XML';        
XMLNS : 'XMLNS';

// 3.2.4 Nombres - Zahlen
//  (intervient avant les Noms car des règles de Noms sont utilisées dans les Nombres) - (kommt vor den Namen, da Namensregeln in Zahlen verwendet werden)

EQ : '=';
NOT_EQ : '!='; // newly added
Scaling : ('e' | 'E') Number;
// LPAR : '(';
LPAR : '(';
// RPAR : ')';
RPAR : ')';
COMMA : ',';
COLON: ':';
SEMI : ';';
LT : '<';
LTEQ: '<=';
GT : '>';
GTEQ: '>=';
DOT: '.';
DOTDOT: '..';
MINUS: '-';
PLUS: '+';
LCBR: '{';
RCBR: '}';
LSBR: '[';
RSBR: ']';

PosNumber : Digit+;
Number : (PLUS | MINUS)? PosNumber;
Dec : (Number (DOT PosNumber)? | Float);
Float : (PLUS | MINUS)? Digit+ (DOT Digit+)? Scaling?;

// 3.2.2 Noms - Namen

Name : Letter (Letter | Digit | '_')*;
Letter : [A-Za-z];
Digit : [0-9];
HexDigit : [0-9A-Fa-f];

// 3.2.3 Chaîne de caractères - Zeichenketten

STRING : '"' ( ~['\\"] | '\\"' | '\\\\' | '\\u' HexDigit HexDigit HexDigit HexDigit )* '"';

// 3.2.5 Ensembles de propriétés - Eigenschaftsmengen

// Toutes les propriétés sont définies dans les règles spécifiques.
// Il n'est donc pas nécessaire de les définir ici sans quoi cela peut créer des ambiguïtés notamment avec Name dans modelDef.
// Property : [a-zA-Z0-9_]+;
// Properties : LPAR Property (COMMA Property)* RPAR;
// Properties : LPAR ('ABSTRACT' | 'EXTENDED' | 'FINAL') RPAR;

// 3.2.6 Explications - Erläuterungen

Explanation
    : '//' (~[/] | '/' ~[/])* '//'
    ;

// 3.2.8 Commentaires - Kommentare

// 3.2.8.1 Commentaire rédigé sur une ligne - Zeilenkommentar

SingleLineComment : '!!' ~[\r\n]* -> channel(HIDDEN); // ignore comments

// 3.2.8.2 Bloc de commentaire - Blockkommentar

BlockComment : '/*' .*? '*/' -> channel(HIDDEN); // ignore comments

// Ignore whitespace

WS : [ \t\r\n]+ -> skip;
