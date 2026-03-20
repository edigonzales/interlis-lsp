parser grammar InterlisParser;
options { tokenVocab=InterlisLexer; }

// Règles syntaxiques INTERLIS 2.4 définies avec les référénce eCH-0031
// INTERLIS 2.4 Syntaxregeln definiert mit eCH-0031-Referenzen

// 3.3 Règle principale - Hauptregel

interlis2def
    : (INTERLIS Dec SEMI modeldef?
      | TRANSFER INTERLIS1 SEMI)
    ;

// 3.5 Modèles, thèmes, classes - Modelle, Themen, Klassen
// 3.5.1 Modèles - Modelle

modeldef 
  : CONTRACTED? (TYPE | REFSYSTEM | SYMBOLOGY)?
    MODEL Name (LPAR Name RPAR)?
    (AT STRING VERSION STRING Explanation?)?
    (TRANSLATION OF Name LSBR STRING RSBR)?
    EQ
    (CONTRACT ISSUED BY Name SEMI)?
    (IMPORTS UNQUALIFIED? (Name | INTERLIS) (COMMA UNQUALIFIED? (Name | INTERLIS))* SEMI)*
    (metaDataBasketDef
    | unitDef
    | functionDef
    | functionDecl // newly added; functionDecl should probably be renamed to functionDef and the actual functionDef renamned
    | lineFormTypeDef
    | domainDef
    | contextDef
    | runTimeParameterDef
    | classDef
    | structureDef
    | topicDef)*
    END Name DOT;

// 3.5.2 Thèmes - Themen

topicDef
  : VIEW? TOPIC Name
    (LPAR (ABSTRACT | FINAL) RPAR)?
    (EXTENDS topicRef)? EQ
    (BASKET? OID AS (Name | Name DOT Name |  (INTERLIS DOT)? UUIDOID) SEMI)?
    (OID AS (Name | Name DOT Name | (INTERLIS DOT)? UUIDOID | INTERLIS DOT ANYOID) SEMI)?
    (DEPENDS ON topicRef (COMMA topicRef)* SEMI)?
    (DEFERRED GENERICS genericRef (COMMA genericRef)* SEMI)?
    definitions*
    END Name SEMI
  ;

definitions : metaDataBasketDef
            | unitDef
            | functionDef
            | domainDef
            | contextDef
            | classDef
            | structureDef
            | associationDef
            | constraintsDef
            | viewDef
            | graphicDef;

topicRef : (Name DOT)? Name;

genericRef : domainRef;

// 3.5.3 Classes et structures - Klassen und Strukturen

classDef : CLASS Name
             (LPAR (ABSTRACT | EXTENDED | FINAL) RPAR)?
             (EXTENDS classOrStructureRef)? EQ
             ((OID AS (Name | Name DOT Name | INTERLIS DOT (Name | UUIDOID)) | NO OID) SEMI)?
             classOrStructureDef?
           END Name SEMI;

structureDef : STRUCTURE Name
                 (LPAR (ABSTRACT | EXTENDED | FINAL) RPAR)?
                 (EXTENDS structureRef)? EQ
                 classOrStructureDef?
               END Name SEMI;

classRef : (INTERLIS DOT REFSYSTEM)
         | (INTERLIS DOT Name (DOT Name)*)
         | Name (DOT Name)*;

classOrStructureDef : (ATTRIBUTE? attributeDef+ | constraintDef+ | PARAMETER? parameterDef+)+;

structureRef : (INTERLIS DOT (Name | BOOLEAN | UUIDOID | URI) (DOT Name)*)
             | Name (DOT Name)*;

classOrStructureRef : classRef | structureRef;

// 3.6 Attributs - Attribute

attributeDef : CONTINUOUS? SUBDIVISION?
               Name (LPAR(ABSTRACT | EXTENDED | FINAL | TRANSIENT)RPAR)?
               COLON (attrTypeDef | lineType)
               (ASSIGN? factor (COMMA factor)*)? SEMI;

attrTypeDef : MANDATORY? (attrType 
            | enumeration 
            | (numeric (CIRCULAR)? (LSBR unitRef RSBR)?)
            | (NUMERIC (LSBR unitRef RSBR)))
            | (BAG | LIST) cardinality? OF restrictedStructureRef;

attrType : type
         | domainRef
         | referenceAttr
         | restrictedStructureRef;

referenceAttr : REFERENCE TO (LPAR EXTERNAL RPAR)? restrictedClassOrAssRef;

restrictedClassOrAssRef : (classOrAssociationRef | ANYCLASS)
                        (RESTRICTION LPAR (classOrAssociationRef (SEMI classOrAssociationRef)*) RPAR)?;

classOrAssociationRef : classRef | associationRef;

restrictedStructureRef : (structureRef | type | ANYSTRUCTURE)
                       (RESTRICTION LPAR structureRef (COMMA structureRef)* RPAR)?;

restrictedClassOrStructureRef
    : (classOrStructureRef | ANYSTRUCTURE)
      (RESTRICTION LPAR classOrStructureRef (SEMI classOrStructureRef)* RPAR)?;

// 3.7 Relations vraies - Eigentliche Beziehungen
//3.7.1 Description des relations - Beschreibung von Beziehungen

associationDef : ASSOCIATION Name?
                     ( LPAR (ABSTRACT | EXTENDED | FINAL | OID) RPAR)?
                     (EXTENDS associationRef)?
                     (DERIVED FROM Name)? EQ
                     ((OID AS Name | NO OID) SEMI)?
                     roleDef*
                     (ATTRIBUTE attributeDef*)?
                     (CARDINALITY EQ cardinality SEMI)?
                     constraintDef*
                 END Name? SEMI;

associationRef : (Name DOT (Name DOT)?)? Name;

roleDef : Name 
          (LPAR ( (ABSTRACT | EXTENDED | FINAL | HIDING | ORDERED | EXTERNAL) 
                 (COMMA (ABSTRACT | EXTENDED | FINAL | HIDING | ORDERED | EXTERNAL))*
               )? RPAR)?
          (MINUS MINUS | MINUS LT GT | MINUS LT HASH GT)? cardinality?
          restrictedClassOrAssRef (OR restrictedClassOrAssRef)*
          (ASSIGN STRING)? SEMI
        | Name COLON MANDATORY? (attrTypeDef | enumeration | numeric | constraintDef) SEMI;

cardinality : LCBR (MUL | PosNumber (DOTDOT (PosNumber | MUL))?) RCBR;

// 3.8 Domaines de valeurs et constantes - Wertebereiche und Konstanten

domainDef
  : DOMAIN?
    (
      (Name | UUIDOID)
      (LPAR (ABSTRACT | FINAL | GENERIC) RPAR)?
      (EXTENDS domainRef)?
      EQ (MANDATORY? (type | numeric | enumeration | (STRING DOTDOT STRING) | CLASS (RESTRICTION LPAR classOrAssociationRef (SEMI classOrAssociationRef)* RPAR)?))
      (CONSTRAINTS (Name COLON constraintDef) (COMMA Name COLON constraintDef)*)?
      SEMI
    )+
  ;

type : baseType
     | lineType
     | STRING DOTDOT STRING;

domainRef : (Name DOT (Name DOT)*)? Name
          | INTERLIS DOT Name;          

baseType : textType
           | enumerationType
           | enumTreeValueType
           | alignmentType
           | booleanType
           | numericType
           | formattedType
           | dateTimeType
           | coordinateType
           | oIDType
           | blackboxType
           | classType
           | attributeType;

constant : UNDEFINED
         | numericConst
         | textConst
         | formattedConst
         | enumerationConst
         | classConst
         | attributePathConst;

// 3.8.1 Chaînes de caractères - Zeichenketten

textType : MTEXT (MUL PosNumber)?
         | TEXT (MUL PosNumber)?
         | NAME
         | URI;

textConst : STRING;

// 3.8.2 Enumérations - Aufzählungen

enumerationType : ENUM LCBR enumElement (COMMA enumElement)* RCBR (ORDERED | CIRCULAR)?;

enumTreeValueType : ALL OF domainRef;

enumeration : LPAR enumElement (COMMA enumElement)* (COLON FINAL)? RPAR (ORDERED | CIRCULAR)?;

enumElement
    : (Name | LOCAL | BASKET) (DOT Name)* (enumeration)?
    ;

enumerationConst : HASH (Name (DOT Name)* (DOT OTHERS)? | OTHERS);


// 3.8.3 Alignement du texte - Textausrichtungen

alignmentType : ( HALIGNMENT | VALIGNMENT );

// 3.8.4 Boolean - Boolean

booleanType : BOOLEAN;

// 3.8.5 Types de données numériques - Numerische Datentypen

numeric
  : (Number DOTDOT Number
    | Number DOTDOT PosNumber
    | PosNumber DOTDOT PosNumber
    | Dec DOTDOT Dec)
    (CIRCULAR)?
    (LSBR unitRef RSBR)?
    (CLOCKWISE | COUNTERCLOCKWISE)?
    (LCBR Name LSBR PosNumber RSBR RCBR)?
    (LT Name GT)?
  ;

numericType : NUMERIC
            | NUMERIC numeric CIRCULAR?
            | NUMERIC (LSBR unitRef RSBR)
            | NUMERIC numeric CIRCULAR? (LSBR unitRef RSBR)?
            | NUMERIC numeric (CLOCKWISE | COUNTERCLOCKWISE | refSys)?;

refSys : LCBR metaObjectRef (LSBR PosNumber RSBR)? RCBR
     | LT domainRef (LSBR PosNumber RSBR)? GT;

decConst : Dec | PI | LNBASE | PosNumber;

numericConst : decConst (LSBR unitRef RSBR)?;

// 3.8.6 Domaines de valeurs formatés - Formatierte Wertebereiche

formattedType : FORMAT INTERLIS DOT Name STRING DOTDOT STRING
              | FORMAT BASED_ON structureRef formatDef
              | FORMAT domainRef STRING DOTDOT STRING;

formatDef : LPAR INHERITANCE? STRING? (baseAttrRef STRING)* baseAttrRef STRING? RPAR;

baseAttrRef : Name (DIV PosNumber)?
      | Name DIV domainRef;

formattedConst : STRING;

// 3.8.7 Date et heure - Datum und Zeit

dateTimeType : ( DATE | TIMEOFDAY | DATETIME );

// 3.8.8 Coordonnées - Koordinaten

coordinateType : (COORD | MULTICOORD)
               | (COORD | MULTICOORD) (numeric | NUMERIC)
                 (COMMA (numeric | NUMERIC)
                 (COMMA (numeric | NUMERIC))?)?
               | (COORD | MULTICOORD) numeric
                 (COMMA numeric (COMMA numeric)?
                 (COMMA rotationDef)?)?;

rotationDef : ROTATION PosNumber MINUS GT PosNumber;

contextDef : CONTEXT? Name EQ 
                (domainRef EQ domainRef (OR domainRef)* SEMI)+ ;

// 3.8.9 Domaines de valeurs des identifications d’objet - Wertebereiche von Objektidentifikationen

oIDType
  : OID (ANY | numeric | textType)
  | UUIDOID
  ;

// 3.8.10 Boîtes noires - Gefässe

blackboxType : BLACKBOX ( XML | BINARY );

// 3.8.11 Domaines de valeurs de classes et chemins d’attributs - Wertebereiche von Klassen und Attributpfaden

classType : CLASS
        (RESTRICTION LPAR viewableRef (COMMA viewableRef)* RPAR)?
      | STRUCTURE
        (RESTRICTION LPAR classOrStructureRef (COMMA classOrStructureRef)* RPAR)?;

attributeType : ATTRIBUTE
          (OF (classType DOT attributePath | AT_SYMBOL Name))?
          (RESTRICTION LPAR attrTypeDef (COMMA attrTypeDef)* RPAR)?;

classConst : GT viewableRef;

attributePathConst : GT GT (viewableRef DOT)? Name;

// 3.8.12 Polylignes - Linienzüge
// 3.8.12.2 Polyligne comportant des segments de droite et des arcs de cercle en tant qu’éléments de portion de courbe prédéfinis
// 3.8.12.2 Linienzug mit Strecken und Kreisbogen als vordefinierte Kurvenstücke

lineType : ( DIRECTED? POLYLINE | SURFACE | AREA | DIRECTED? MULTIPOLYLINE | MULTISURFACE | MULTIAREA )
        lineForm? controlPoints? intersectionDef? (LINE ATTRIBUTES Name)?;

lineForm : WITH LPAR lineFormType (COMMA lineFormType)* RPAR;

lineFormType : STRAIGHTS | ARCS | Name DOT Name;

controlPoints : VERTEX Name (DOT Name)*;

intersectionDef : WITHOUT OVERLAPS GT Dec;

// 3.8.12.3 Formes de portions de courbes supplémentaires - Weitere Kurvenstück-Formen

lineFormTypeDef : LINE FORM LCBR Name COLON Name SEMI RCBR;

// 3.9 Unités - Einheiten

// 3.9.3 Unités composées - Zusammengesetzte Einheiten

unitDef
  : UNIT? Name
    (LSBR Name RSBR)?
    (LPAR ABSTRACT RPAR)?          
    (EXTENDS unitRef)?             
    EQ (
        expression (LSBR unitRef RSBR)?
        | composedUnit
        | functionDef
        | LSBR unitRef RSBR
    )?
    SEMI
  ;

derivedUnit
    : decConst ((MUL | DIV |POW) decConst)* LSBR unitRef RSBR
    ;

composedUnit : LPAR (unitRef | Name | INTERLIS DOT Name) ((MUL | DIV |POW) (unitRef | INTERLIS DOT Name | Name))* RPAR;

unitRef
  : Name (DOT Name)*
  | INTERLIS DOT Name
  ;

// 3.10 Traitement des méta-objets - Umgang mit Metaobjekten

metaDataBasketDef : (SIGN | REFSYSTEM) BASKET Name
           FINAL?
           (EXTENDS metaDataBasketRef)?
           TILDE topicRef
           (OBJECTS OF Name COLON (Name (COMMA Name)*) SEMI?)+;

metaDataBasketRef : (Name DOT (Name DOT)?)? Name;

metaObjectRef : (metaDataBasketRef DOT)? Name;

// 3.10.2 Paramètres - Parameter
// 3.10.2.2 Paramètres des signatures - Parameter von Signaturen

parameterDef : PARAMETER Name
               (LPAR (ABSTRACT | EXTENDED | FINAL) RPAR)?
               COLON (attrTypeDef | METAOBJECT (OF metaObjectRef)?) SEMI;

// 3.11 Paramètres d’exécution - Laufzeitparameter

runTimeParameterDef : PARAMETER Name (ABSTRACT | EXTENDED | FINAL)?
            COLON attrTypeDef SEMI;

// 3.12 Conditions de cohérence - Konsistenzbedingungen

constraintDef : mandatoryConstraint
        | plausibilityConstraint
        | existenceConstraint
        | uniquenessConstraint
        | setConstraint
        | expression SEMI;

mandatoryConstraint : MANDATORY CONSTRAINT (Name COLON)? expression SEMI;

plausibilityConstraint
    : CONSTRAINT (Name COLON)?
      (LTEQ | GTEQ) numericConst MOD
      expression SEMI
    ;

existenceConstraint
    : EXISTENCE CONSTRAINT 
      (Name COLON)?
      attributePath
      REQUIRED IN
      viewableRef
      COLON
      attributePath
      SEMI
    ;

uniquenessConstraint
    : UNIQUE (Name COLON)?
      (LPAR (LOCAL | BASKET) RPAR)?
      (Name COLON)?
      (globalUniqueness | localUniqueness)+
      SEMI
    ;

globalUniqueness : uniqueEl ( COMMA uniqueEl )*;

uniqueEl : objectOrAttributePath;

localUniqueness
    : UNIQUE (LPAR (LOCAL | BASKET) RPAR)?
      (Name COLON)?
      Name
      (MINUS GT Name)* (COLON Name (COMMA Name)*)?
      SEMI
    ;

setConstraint
  : SET CONSTRAINT
      (LPAR (LOCAL | BASKET) RPAR)?
      (Name COLON WHERE expression COLON expression
        | (WHERE expression COLON)? (Name COLON | INTERLIS COLON)? expression)
      SEMI
  ;

constraintsDef : CONSTRAINTS OF classOrAssociationRef EQ
        ( constraintDef )*
        END SEMI;

// 3.13 Expressions - Ausdrücke

expression : term;

term : term0 ( EQ GT term0 )?;
term0 : term1 ( ( OR | PLUS | MINUS ) term1 )*;
term1 : term2 ( ( AND | MUL | DIV | POW) term2 )*;
term2 : predicate ( relation predicate )?;

predicate : ( factor
      | ( NOT )? LPAR expression RPAR
      | DEFINED LPAR factor RPAR
      | LPAR BASKET RPAR factor
      );

relation : ( EQ EQ | NOT_EQ | LT GT | LTEQ | GTEQ | LT | GT );
      
factor
  : objectOrAttributePath
  | (inspection | INSPECTION viewableRef) (OF objectOrAttributePath)?
  | functionCall
  | INTERLIS DOT (Name | URI | UUIDOID) (LPAR (expression (COMMA expression)*)? RPAR)?
  | PARAMETER (Name DOT)? Name
  | ALL (OF objectOrAttributePath)?
  | constant
  | Number
  ;

objectOrAttributePath : pathEl (MINUS GT pathEl)*;

attributePath : objectOrAttributePath;

pathEl : THIS
        | THISAREA
        | THATAREA
        | PARENT
        | Name (LSBR Name RSBR)?
        | Name COLON
        | associationPath
        | attributeRef
        | Name EQ EQ STRING;

associationPath : BACKSLASH? Name;

attributeRef : Name (LSBR (FIRST | LAST | Number) RSBR)?
              | AGGREGATES;

functionCall : (Name DOT)? (Name DOT)? Name
              LPAR argument (COMMA argument)* RPAR;

argument : expression
          | ALL (LPAR restrictedClassOrAssRef | viewableRef RPAR)?;

// 3.14 Fonctions

functionDecl
  : FUNCTION Name
    LPAR argumentDef (SEMI argumentDef)* RPAR
    COLON (BOOLEAN | attrTypeDef | Name)
    SEMI
  ;

functionDef
    : UNIT? Name
      (LSBR Name RSBR)?
      (EXTENDS unitRef)?
      | (EQ FUNCTION Explanation LSBR unitRef RSBR)?
      SEMI
    ;

argumentDef : Name COLON argumentType;

argumentType : attrTypeDef
         | (OBJECT | OBJECTS) OF (restrictedClassOrAssRef | viewRef)
         | ENUMVAL
         | ENUMTREEVAL;

// 3.15 Vues

viewDef : VIEW Name
        (ABSTRACT | EXTENDED | FINAL | TRANSIENT)?
        ( formationDef | EXTENDS viewRef )?
        baseExtensionDef*
        selection*
        EQ
        viewAttributes?
        constraintDef*
        END Name SEMI;

viewRef : (Name DOT (Name DOT)?)? Name;

formationDef : projection 
       | join 
       | union 
       | aggregation
       | inspection;

projection : PROJECTION_OF renamedViewableRef SEMI;

join : JOIN_OF renamedViewableRef
     (COMMA renamedViewableRef (LPAR OR NULL RPAR)?)* SEMI;

union : UNION_OF renamedViewableRef
    (COMMA renamedViewableRef)* SEMI;

aggregation : AGGREGATION_OF renamedViewableRef
        (ALL | EQUAL LPAR uniqueEl RPAR) SEMI;

inspection : (AREA? INSPECTION_OF renamedViewableRef
        MINUS GT Name
        (MINUS GT Name)*) SEMI;

renamedViewableRef : (Name TILDE)? viewableRef;

viewableRef : (Name DOT (Name DOT)?)?
        (Name
        | Name
        | Name
        | Name);

baseExtensionDef : BASE Name EXTENDED BY
           renamedViewableRef (COMMA renamedViewableRef)*;

selection : WHERE expression SEMI;

viewAttributes
  : ATTRIBUTE?
    (
      ALL OF Name SEMI (Name ASSIGN expression SEMI)*
    | (Name ASSIGN expression SEMI)+
    | attributeDef
    | (ABSTRACT | EXTENDED | FINAL | TRANSIENT)? ASSIGN expression SEMI
    );
    
// 3.16 Représentations graphiques

graphicDef : GRAPHIC Name (ABSTRACT | FINAL)?
     (EXTENDS graphicRef)?
     (BASED ON viewableRef)? EQ
     (selection)*
     (drawingRule)*
     END Name SEMI;

graphicRef : (Name DOT (Name DOT)?)? Name;

drawingRule : Name (ABSTRACT | EXTENDED | FINAL)?
  (OF classRef)?
  COLON condSignParamAssignment (COMMA condSignParamAssignment)* SEMI;

condSignParamAssignment : (WHERE? expression)? 
        LPAR signParamAssignment ( SEMI signParamAssignment )* RPAR;

signParamAssignment : Name
            ASSIGN ( LCBR metaObjectRef RCBR
               | factor
               | ACCORDING attributePath
                LPAR enumAssignment 
          ( COMMA enumAssignment )* RPAR );

enumAssignment : ( LCBR metaObjectRef RCBR | constant )
       WHEN IN enumRange;

enumRange : enumerationConst (DOTDOT enumerationConst)?;
