// Generated from grammars/InterlisParser.g4 by ANTLR 4.13.2
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link InterlisParser}.
 */
public interface InterlisParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link InterlisParser#interlis2def}.
	 * @param ctx the parse tree
	 */
	void enterInterlis2def(InterlisParser.Interlis2defContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#interlis2def}.
	 * @param ctx the parse tree
	 */
	void exitInterlis2def(InterlisParser.Interlis2defContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#modeldef}.
	 * @param ctx the parse tree
	 */
	void enterModeldef(InterlisParser.ModeldefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#modeldef}.
	 * @param ctx the parse tree
	 */
	void exitModeldef(InterlisParser.ModeldefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#topicDef}.
	 * @param ctx the parse tree
	 */
	void enterTopicDef(InterlisParser.TopicDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#topicDef}.
	 * @param ctx the parse tree
	 */
	void exitTopicDef(InterlisParser.TopicDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#definitions}.
	 * @param ctx the parse tree
	 */
	void enterDefinitions(InterlisParser.DefinitionsContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#definitions}.
	 * @param ctx the parse tree
	 */
	void exitDefinitions(InterlisParser.DefinitionsContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#topicRef}.
	 * @param ctx the parse tree
	 */
	void enterTopicRef(InterlisParser.TopicRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#topicRef}.
	 * @param ctx the parse tree
	 */
	void exitTopicRef(InterlisParser.TopicRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#genericRef}.
	 * @param ctx the parse tree
	 */
	void enterGenericRef(InterlisParser.GenericRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#genericRef}.
	 * @param ctx the parse tree
	 */
	void exitGenericRef(InterlisParser.GenericRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#classDef}.
	 * @param ctx the parse tree
	 */
	void enterClassDef(InterlisParser.ClassDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#classDef}.
	 * @param ctx the parse tree
	 */
	void exitClassDef(InterlisParser.ClassDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#structureDef}.
	 * @param ctx the parse tree
	 */
	void enterStructureDef(InterlisParser.StructureDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#structureDef}.
	 * @param ctx the parse tree
	 */
	void exitStructureDef(InterlisParser.StructureDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#classRef}.
	 * @param ctx the parse tree
	 */
	void enterClassRef(InterlisParser.ClassRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#classRef}.
	 * @param ctx the parse tree
	 */
	void exitClassRef(InterlisParser.ClassRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#classOrStructureDef}.
	 * @param ctx the parse tree
	 */
	void enterClassOrStructureDef(InterlisParser.ClassOrStructureDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#classOrStructureDef}.
	 * @param ctx the parse tree
	 */
	void exitClassOrStructureDef(InterlisParser.ClassOrStructureDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#structureRef}.
	 * @param ctx the parse tree
	 */
	void enterStructureRef(InterlisParser.StructureRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#structureRef}.
	 * @param ctx the parse tree
	 */
	void exitStructureRef(InterlisParser.StructureRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#classOrStructureRef}.
	 * @param ctx the parse tree
	 */
	void enterClassOrStructureRef(InterlisParser.ClassOrStructureRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#classOrStructureRef}.
	 * @param ctx the parse tree
	 */
	void exitClassOrStructureRef(InterlisParser.ClassOrStructureRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#attributeDef}.
	 * @param ctx the parse tree
	 */
	void enterAttributeDef(InterlisParser.AttributeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#attributeDef}.
	 * @param ctx the parse tree
	 */
	void exitAttributeDef(InterlisParser.AttributeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#attrTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterAttrTypeDef(InterlisParser.AttrTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#attrTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitAttrTypeDef(InterlisParser.AttrTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#attrType}.
	 * @param ctx the parse tree
	 */
	void enterAttrType(InterlisParser.AttrTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#attrType}.
	 * @param ctx the parse tree
	 */
	void exitAttrType(InterlisParser.AttrTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#referenceAttr}.
	 * @param ctx the parse tree
	 */
	void enterReferenceAttr(InterlisParser.ReferenceAttrContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#referenceAttr}.
	 * @param ctx the parse tree
	 */
	void exitReferenceAttr(InterlisParser.ReferenceAttrContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#restrictedClassOrAssRef}.
	 * @param ctx the parse tree
	 */
	void enterRestrictedClassOrAssRef(InterlisParser.RestrictedClassOrAssRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#restrictedClassOrAssRef}.
	 * @param ctx the parse tree
	 */
	void exitRestrictedClassOrAssRef(InterlisParser.RestrictedClassOrAssRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#classOrAssociationRef}.
	 * @param ctx the parse tree
	 */
	void enterClassOrAssociationRef(InterlisParser.ClassOrAssociationRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#classOrAssociationRef}.
	 * @param ctx the parse tree
	 */
	void exitClassOrAssociationRef(InterlisParser.ClassOrAssociationRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#restrictedStructureRef}.
	 * @param ctx the parse tree
	 */
	void enterRestrictedStructureRef(InterlisParser.RestrictedStructureRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#restrictedStructureRef}.
	 * @param ctx the parse tree
	 */
	void exitRestrictedStructureRef(InterlisParser.RestrictedStructureRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#restrictedClassOrStructureRef}.
	 * @param ctx the parse tree
	 */
	void enterRestrictedClassOrStructureRef(InterlisParser.RestrictedClassOrStructureRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#restrictedClassOrStructureRef}.
	 * @param ctx the parse tree
	 */
	void exitRestrictedClassOrStructureRef(InterlisParser.RestrictedClassOrStructureRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#associationDef}.
	 * @param ctx the parse tree
	 */
	void enterAssociationDef(InterlisParser.AssociationDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#associationDef}.
	 * @param ctx the parse tree
	 */
	void exitAssociationDef(InterlisParser.AssociationDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#associationRef}.
	 * @param ctx the parse tree
	 */
	void enterAssociationRef(InterlisParser.AssociationRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#associationRef}.
	 * @param ctx the parse tree
	 */
	void exitAssociationRef(InterlisParser.AssociationRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#roleDef}.
	 * @param ctx the parse tree
	 */
	void enterRoleDef(InterlisParser.RoleDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#roleDef}.
	 * @param ctx the parse tree
	 */
	void exitRoleDef(InterlisParser.RoleDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#cardinality}.
	 * @param ctx the parse tree
	 */
	void enterCardinality(InterlisParser.CardinalityContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#cardinality}.
	 * @param ctx the parse tree
	 */
	void exitCardinality(InterlisParser.CardinalityContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#domainDef}.
	 * @param ctx the parse tree
	 */
	void enterDomainDef(InterlisParser.DomainDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#domainDef}.
	 * @param ctx the parse tree
	 */
	void exitDomainDef(InterlisParser.DomainDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(InterlisParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(InterlisParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#domainRef}.
	 * @param ctx the parse tree
	 */
	void enterDomainRef(InterlisParser.DomainRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#domainRef}.
	 * @param ctx the parse tree
	 */
	void exitDomainRef(InterlisParser.DomainRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#baseType}.
	 * @param ctx the parse tree
	 */
	void enterBaseType(InterlisParser.BaseTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#baseType}.
	 * @param ctx the parse tree
	 */
	void exitBaseType(InterlisParser.BaseTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#constant}.
	 * @param ctx the parse tree
	 */
	void enterConstant(InterlisParser.ConstantContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#constant}.
	 * @param ctx the parse tree
	 */
	void exitConstant(InterlisParser.ConstantContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#textType}.
	 * @param ctx the parse tree
	 */
	void enterTextType(InterlisParser.TextTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#textType}.
	 * @param ctx the parse tree
	 */
	void exitTextType(InterlisParser.TextTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#textConst}.
	 * @param ctx the parse tree
	 */
	void enterTextConst(InterlisParser.TextConstContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#textConst}.
	 * @param ctx the parse tree
	 */
	void exitTextConst(InterlisParser.TextConstContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#enumerationType}.
	 * @param ctx the parse tree
	 */
	void enterEnumerationType(InterlisParser.EnumerationTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#enumerationType}.
	 * @param ctx the parse tree
	 */
	void exitEnumerationType(InterlisParser.EnumerationTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#enumTreeValueType}.
	 * @param ctx the parse tree
	 */
	void enterEnumTreeValueType(InterlisParser.EnumTreeValueTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#enumTreeValueType}.
	 * @param ctx the parse tree
	 */
	void exitEnumTreeValueType(InterlisParser.EnumTreeValueTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#enumeration}.
	 * @param ctx the parse tree
	 */
	void enterEnumeration(InterlisParser.EnumerationContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#enumeration}.
	 * @param ctx the parse tree
	 */
	void exitEnumeration(InterlisParser.EnumerationContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#enumElement}.
	 * @param ctx the parse tree
	 */
	void enterEnumElement(InterlisParser.EnumElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#enumElement}.
	 * @param ctx the parse tree
	 */
	void exitEnumElement(InterlisParser.EnumElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#enumerationConst}.
	 * @param ctx the parse tree
	 */
	void enterEnumerationConst(InterlisParser.EnumerationConstContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#enumerationConst}.
	 * @param ctx the parse tree
	 */
	void exitEnumerationConst(InterlisParser.EnumerationConstContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#alignmentType}.
	 * @param ctx the parse tree
	 */
	void enterAlignmentType(InterlisParser.AlignmentTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#alignmentType}.
	 * @param ctx the parse tree
	 */
	void exitAlignmentType(InterlisParser.AlignmentTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#booleanType}.
	 * @param ctx the parse tree
	 */
	void enterBooleanType(InterlisParser.BooleanTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#booleanType}.
	 * @param ctx the parse tree
	 */
	void exitBooleanType(InterlisParser.BooleanTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#numeric}.
	 * @param ctx the parse tree
	 */
	void enterNumeric(InterlisParser.NumericContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#numeric}.
	 * @param ctx the parse tree
	 */
	void exitNumeric(InterlisParser.NumericContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#numericType}.
	 * @param ctx the parse tree
	 */
	void enterNumericType(InterlisParser.NumericTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#numericType}.
	 * @param ctx the parse tree
	 */
	void exitNumericType(InterlisParser.NumericTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#refSys}.
	 * @param ctx the parse tree
	 */
	void enterRefSys(InterlisParser.RefSysContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#refSys}.
	 * @param ctx the parse tree
	 */
	void exitRefSys(InterlisParser.RefSysContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#decConst}.
	 * @param ctx the parse tree
	 */
	void enterDecConst(InterlisParser.DecConstContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#decConst}.
	 * @param ctx the parse tree
	 */
	void exitDecConst(InterlisParser.DecConstContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#numericConst}.
	 * @param ctx the parse tree
	 */
	void enterNumericConst(InterlisParser.NumericConstContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#numericConst}.
	 * @param ctx the parse tree
	 */
	void exitNumericConst(InterlisParser.NumericConstContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#formattedType}.
	 * @param ctx the parse tree
	 */
	void enterFormattedType(InterlisParser.FormattedTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#formattedType}.
	 * @param ctx the parse tree
	 */
	void exitFormattedType(InterlisParser.FormattedTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#formatDef}.
	 * @param ctx the parse tree
	 */
	void enterFormatDef(InterlisParser.FormatDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#formatDef}.
	 * @param ctx the parse tree
	 */
	void exitFormatDef(InterlisParser.FormatDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#baseAttrRef}.
	 * @param ctx the parse tree
	 */
	void enterBaseAttrRef(InterlisParser.BaseAttrRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#baseAttrRef}.
	 * @param ctx the parse tree
	 */
	void exitBaseAttrRef(InterlisParser.BaseAttrRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#formattedConst}.
	 * @param ctx the parse tree
	 */
	void enterFormattedConst(InterlisParser.FormattedConstContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#formattedConst}.
	 * @param ctx the parse tree
	 */
	void exitFormattedConst(InterlisParser.FormattedConstContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#dateTimeType}.
	 * @param ctx the parse tree
	 */
	void enterDateTimeType(InterlisParser.DateTimeTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#dateTimeType}.
	 * @param ctx the parse tree
	 */
	void exitDateTimeType(InterlisParser.DateTimeTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#coordinateType}.
	 * @param ctx the parse tree
	 */
	void enterCoordinateType(InterlisParser.CoordinateTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#coordinateType}.
	 * @param ctx the parse tree
	 */
	void exitCoordinateType(InterlisParser.CoordinateTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#rotationDef}.
	 * @param ctx the parse tree
	 */
	void enterRotationDef(InterlisParser.RotationDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#rotationDef}.
	 * @param ctx the parse tree
	 */
	void exitRotationDef(InterlisParser.RotationDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#contextDef}.
	 * @param ctx the parse tree
	 */
	void enterContextDef(InterlisParser.ContextDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#contextDef}.
	 * @param ctx the parse tree
	 */
	void exitContextDef(InterlisParser.ContextDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#oIDType}.
	 * @param ctx the parse tree
	 */
	void enterOIDType(InterlisParser.OIDTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#oIDType}.
	 * @param ctx the parse tree
	 */
	void exitOIDType(InterlisParser.OIDTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#blackboxType}.
	 * @param ctx the parse tree
	 */
	void enterBlackboxType(InterlisParser.BlackboxTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#blackboxType}.
	 * @param ctx the parse tree
	 */
	void exitBlackboxType(InterlisParser.BlackboxTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#classType}.
	 * @param ctx the parse tree
	 */
	void enterClassType(InterlisParser.ClassTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#classType}.
	 * @param ctx the parse tree
	 */
	void exitClassType(InterlisParser.ClassTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#attributeType}.
	 * @param ctx the parse tree
	 */
	void enterAttributeType(InterlisParser.AttributeTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#attributeType}.
	 * @param ctx the parse tree
	 */
	void exitAttributeType(InterlisParser.AttributeTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#classConst}.
	 * @param ctx the parse tree
	 */
	void enterClassConst(InterlisParser.ClassConstContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#classConst}.
	 * @param ctx the parse tree
	 */
	void exitClassConst(InterlisParser.ClassConstContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#attributePathConst}.
	 * @param ctx the parse tree
	 */
	void enterAttributePathConst(InterlisParser.AttributePathConstContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#attributePathConst}.
	 * @param ctx the parse tree
	 */
	void exitAttributePathConst(InterlisParser.AttributePathConstContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#lineType}.
	 * @param ctx the parse tree
	 */
	void enterLineType(InterlisParser.LineTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#lineType}.
	 * @param ctx the parse tree
	 */
	void exitLineType(InterlisParser.LineTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#lineForm}.
	 * @param ctx the parse tree
	 */
	void enterLineForm(InterlisParser.LineFormContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#lineForm}.
	 * @param ctx the parse tree
	 */
	void exitLineForm(InterlisParser.LineFormContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#lineFormType}.
	 * @param ctx the parse tree
	 */
	void enterLineFormType(InterlisParser.LineFormTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#lineFormType}.
	 * @param ctx the parse tree
	 */
	void exitLineFormType(InterlisParser.LineFormTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#controlPoints}.
	 * @param ctx the parse tree
	 */
	void enterControlPoints(InterlisParser.ControlPointsContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#controlPoints}.
	 * @param ctx the parse tree
	 */
	void exitControlPoints(InterlisParser.ControlPointsContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#intersectionDef}.
	 * @param ctx the parse tree
	 */
	void enterIntersectionDef(InterlisParser.IntersectionDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#intersectionDef}.
	 * @param ctx the parse tree
	 */
	void exitIntersectionDef(InterlisParser.IntersectionDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#lineFormTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterLineFormTypeDef(InterlisParser.LineFormTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#lineFormTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitLineFormTypeDef(InterlisParser.LineFormTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#unitDef}.
	 * @param ctx the parse tree
	 */
	void enterUnitDef(InterlisParser.UnitDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#unitDef}.
	 * @param ctx the parse tree
	 */
	void exitUnitDef(InterlisParser.UnitDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#derivedUnit}.
	 * @param ctx the parse tree
	 */
	void enterDerivedUnit(InterlisParser.DerivedUnitContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#derivedUnit}.
	 * @param ctx the parse tree
	 */
	void exitDerivedUnit(InterlisParser.DerivedUnitContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#composedUnit}.
	 * @param ctx the parse tree
	 */
	void enterComposedUnit(InterlisParser.ComposedUnitContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#composedUnit}.
	 * @param ctx the parse tree
	 */
	void exitComposedUnit(InterlisParser.ComposedUnitContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#unitRef}.
	 * @param ctx the parse tree
	 */
	void enterUnitRef(InterlisParser.UnitRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#unitRef}.
	 * @param ctx the parse tree
	 */
	void exitUnitRef(InterlisParser.UnitRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#metaDataBasketDef}.
	 * @param ctx the parse tree
	 */
	void enterMetaDataBasketDef(InterlisParser.MetaDataBasketDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#metaDataBasketDef}.
	 * @param ctx the parse tree
	 */
	void exitMetaDataBasketDef(InterlisParser.MetaDataBasketDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#metaDataBasketRef}.
	 * @param ctx the parse tree
	 */
	void enterMetaDataBasketRef(InterlisParser.MetaDataBasketRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#metaDataBasketRef}.
	 * @param ctx the parse tree
	 */
	void exitMetaDataBasketRef(InterlisParser.MetaDataBasketRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#metaObjectRef}.
	 * @param ctx the parse tree
	 */
	void enterMetaObjectRef(InterlisParser.MetaObjectRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#metaObjectRef}.
	 * @param ctx the parse tree
	 */
	void exitMetaObjectRef(InterlisParser.MetaObjectRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#parameterDef}.
	 * @param ctx the parse tree
	 */
	void enterParameterDef(InterlisParser.ParameterDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#parameterDef}.
	 * @param ctx the parse tree
	 */
	void exitParameterDef(InterlisParser.ParameterDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#runTimeParameterDef}.
	 * @param ctx the parse tree
	 */
	void enterRunTimeParameterDef(InterlisParser.RunTimeParameterDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#runTimeParameterDef}.
	 * @param ctx the parse tree
	 */
	void exitRunTimeParameterDef(InterlisParser.RunTimeParameterDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#constraintDef}.
	 * @param ctx the parse tree
	 */
	void enterConstraintDef(InterlisParser.ConstraintDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#constraintDef}.
	 * @param ctx the parse tree
	 */
	void exitConstraintDef(InterlisParser.ConstraintDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#mandatoryConstraint}.
	 * @param ctx the parse tree
	 */
	void enterMandatoryConstraint(InterlisParser.MandatoryConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#mandatoryConstraint}.
	 * @param ctx the parse tree
	 */
	void exitMandatoryConstraint(InterlisParser.MandatoryConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#plausibilityConstraint}.
	 * @param ctx the parse tree
	 */
	void enterPlausibilityConstraint(InterlisParser.PlausibilityConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#plausibilityConstraint}.
	 * @param ctx the parse tree
	 */
	void exitPlausibilityConstraint(InterlisParser.PlausibilityConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#existenceConstraint}.
	 * @param ctx the parse tree
	 */
	void enterExistenceConstraint(InterlisParser.ExistenceConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#existenceConstraint}.
	 * @param ctx the parse tree
	 */
	void exitExistenceConstraint(InterlisParser.ExistenceConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#uniquenessConstraint}.
	 * @param ctx the parse tree
	 */
	void enterUniquenessConstraint(InterlisParser.UniquenessConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#uniquenessConstraint}.
	 * @param ctx the parse tree
	 */
	void exitUniquenessConstraint(InterlisParser.UniquenessConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#globalUniqueness}.
	 * @param ctx the parse tree
	 */
	void enterGlobalUniqueness(InterlisParser.GlobalUniquenessContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#globalUniqueness}.
	 * @param ctx the parse tree
	 */
	void exitGlobalUniqueness(InterlisParser.GlobalUniquenessContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#uniqueEl}.
	 * @param ctx the parse tree
	 */
	void enterUniqueEl(InterlisParser.UniqueElContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#uniqueEl}.
	 * @param ctx the parse tree
	 */
	void exitUniqueEl(InterlisParser.UniqueElContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#localUniqueness}.
	 * @param ctx the parse tree
	 */
	void enterLocalUniqueness(InterlisParser.LocalUniquenessContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#localUniqueness}.
	 * @param ctx the parse tree
	 */
	void exitLocalUniqueness(InterlisParser.LocalUniquenessContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#setConstraint}.
	 * @param ctx the parse tree
	 */
	void enterSetConstraint(InterlisParser.SetConstraintContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#setConstraint}.
	 * @param ctx the parse tree
	 */
	void exitSetConstraint(InterlisParser.SetConstraintContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#constraintsDef}.
	 * @param ctx the parse tree
	 */
	void enterConstraintsDef(InterlisParser.ConstraintsDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#constraintsDef}.
	 * @param ctx the parse tree
	 */
	void exitConstraintsDef(InterlisParser.ConstraintsDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(InterlisParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(InterlisParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#term}.
	 * @param ctx the parse tree
	 */
	void enterTerm(InterlisParser.TermContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#term}.
	 * @param ctx the parse tree
	 */
	void exitTerm(InterlisParser.TermContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#term0}.
	 * @param ctx the parse tree
	 */
	void enterTerm0(InterlisParser.Term0Context ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#term0}.
	 * @param ctx the parse tree
	 */
	void exitTerm0(InterlisParser.Term0Context ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#term1}.
	 * @param ctx the parse tree
	 */
	void enterTerm1(InterlisParser.Term1Context ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#term1}.
	 * @param ctx the parse tree
	 */
	void exitTerm1(InterlisParser.Term1Context ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#term2}.
	 * @param ctx the parse tree
	 */
	void enterTerm2(InterlisParser.Term2Context ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#term2}.
	 * @param ctx the parse tree
	 */
	void exitTerm2(InterlisParser.Term2Context ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterPredicate(InterlisParser.PredicateContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitPredicate(InterlisParser.PredicateContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#relation}.
	 * @param ctx the parse tree
	 */
	void enterRelation(InterlisParser.RelationContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#relation}.
	 * @param ctx the parse tree
	 */
	void exitRelation(InterlisParser.RelationContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#factor}.
	 * @param ctx the parse tree
	 */
	void enterFactor(InterlisParser.FactorContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#factor}.
	 * @param ctx the parse tree
	 */
	void exitFactor(InterlisParser.FactorContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#objectOrAttributePath}.
	 * @param ctx the parse tree
	 */
	void enterObjectOrAttributePath(InterlisParser.ObjectOrAttributePathContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#objectOrAttributePath}.
	 * @param ctx the parse tree
	 */
	void exitObjectOrAttributePath(InterlisParser.ObjectOrAttributePathContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#attributePath}.
	 * @param ctx the parse tree
	 */
	void enterAttributePath(InterlisParser.AttributePathContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#attributePath}.
	 * @param ctx the parse tree
	 */
	void exitAttributePath(InterlisParser.AttributePathContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#pathEl}.
	 * @param ctx the parse tree
	 */
	void enterPathEl(InterlisParser.PathElContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#pathEl}.
	 * @param ctx the parse tree
	 */
	void exitPathEl(InterlisParser.PathElContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#associationPath}.
	 * @param ctx the parse tree
	 */
	void enterAssociationPath(InterlisParser.AssociationPathContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#associationPath}.
	 * @param ctx the parse tree
	 */
	void exitAssociationPath(InterlisParser.AssociationPathContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#attributeRef}.
	 * @param ctx the parse tree
	 */
	void enterAttributeRef(InterlisParser.AttributeRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#attributeRef}.
	 * @param ctx the parse tree
	 */
	void exitAttributeRef(InterlisParser.AttributeRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#functionCall}.
	 * @param ctx the parse tree
	 */
	void enterFunctionCall(InterlisParser.FunctionCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#functionCall}.
	 * @param ctx the parse tree
	 */
	void exitFunctionCall(InterlisParser.FunctionCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(InterlisParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(InterlisParser.ArgumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#functionDef}.
	 * @param ctx the parse tree
	 */
	void enterFunctionDef(InterlisParser.FunctionDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#functionDef}.
	 * @param ctx the parse tree
	 */
	void exitFunctionDef(InterlisParser.FunctionDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#argumentDef}.
	 * @param ctx the parse tree
	 */
	void enterArgumentDef(InterlisParser.ArgumentDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#argumentDef}.
	 * @param ctx the parse tree
	 */
	void exitArgumentDef(InterlisParser.ArgumentDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#argumentType}.
	 * @param ctx the parse tree
	 */
	void enterArgumentType(InterlisParser.ArgumentTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#argumentType}.
	 * @param ctx the parse tree
	 */
	void exitArgumentType(InterlisParser.ArgumentTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#viewDef}.
	 * @param ctx the parse tree
	 */
	void enterViewDef(InterlisParser.ViewDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#viewDef}.
	 * @param ctx the parse tree
	 */
	void exitViewDef(InterlisParser.ViewDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#viewRef}.
	 * @param ctx the parse tree
	 */
	void enterViewRef(InterlisParser.ViewRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#viewRef}.
	 * @param ctx the parse tree
	 */
	void exitViewRef(InterlisParser.ViewRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#formationDef}.
	 * @param ctx the parse tree
	 */
	void enterFormationDef(InterlisParser.FormationDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#formationDef}.
	 * @param ctx the parse tree
	 */
	void exitFormationDef(InterlisParser.FormationDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#projection}.
	 * @param ctx the parse tree
	 */
	void enterProjection(InterlisParser.ProjectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#projection}.
	 * @param ctx the parse tree
	 */
	void exitProjection(InterlisParser.ProjectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#join}.
	 * @param ctx the parse tree
	 */
	void enterJoin(InterlisParser.JoinContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#join}.
	 * @param ctx the parse tree
	 */
	void exitJoin(InterlisParser.JoinContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#union}.
	 * @param ctx the parse tree
	 */
	void enterUnion(InterlisParser.UnionContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#union}.
	 * @param ctx the parse tree
	 */
	void exitUnion(InterlisParser.UnionContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#aggregation}.
	 * @param ctx the parse tree
	 */
	void enterAggregation(InterlisParser.AggregationContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#aggregation}.
	 * @param ctx the parse tree
	 */
	void exitAggregation(InterlisParser.AggregationContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#inspection}.
	 * @param ctx the parse tree
	 */
	void enterInspection(InterlisParser.InspectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#inspection}.
	 * @param ctx the parse tree
	 */
	void exitInspection(InterlisParser.InspectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#renamedViewableRef}.
	 * @param ctx the parse tree
	 */
	void enterRenamedViewableRef(InterlisParser.RenamedViewableRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#renamedViewableRef}.
	 * @param ctx the parse tree
	 */
	void exitRenamedViewableRef(InterlisParser.RenamedViewableRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#viewableRef}.
	 * @param ctx the parse tree
	 */
	void enterViewableRef(InterlisParser.ViewableRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#viewableRef}.
	 * @param ctx the parse tree
	 */
	void exitViewableRef(InterlisParser.ViewableRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#baseExtensionDef}.
	 * @param ctx the parse tree
	 */
	void enterBaseExtensionDef(InterlisParser.BaseExtensionDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#baseExtensionDef}.
	 * @param ctx the parse tree
	 */
	void exitBaseExtensionDef(InterlisParser.BaseExtensionDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#selection}.
	 * @param ctx the parse tree
	 */
	void enterSelection(InterlisParser.SelectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#selection}.
	 * @param ctx the parse tree
	 */
	void exitSelection(InterlisParser.SelectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#viewAttributes}.
	 * @param ctx the parse tree
	 */
	void enterViewAttributes(InterlisParser.ViewAttributesContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#viewAttributes}.
	 * @param ctx the parse tree
	 */
	void exitViewAttributes(InterlisParser.ViewAttributesContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#graphicDef}.
	 * @param ctx the parse tree
	 */
	void enterGraphicDef(InterlisParser.GraphicDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#graphicDef}.
	 * @param ctx the parse tree
	 */
	void exitGraphicDef(InterlisParser.GraphicDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#graphicRef}.
	 * @param ctx the parse tree
	 */
	void enterGraphicRef(InterlisParser.GraphicRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#graphicRef}.
	 * @param ctx the parse tree
	 */
	void exitGraphicRef(InterlisParser.GraphicRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#drawingRule}.
	 * @param ctx the parse tree
	 */
	void enterDrawingRule(InterlisParser.DrawingRuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#drawingRule}.
	 * @param ctx the parse tree
	 */
	void exitDrawingRule(InterlisParser.DrawingRuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#condSignParamAssignment}.
	 * @param ctx the parse tree
	 */
	void enterCondSignParamAssignment(InterlisParser.CondSignParamAssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#condSignParamAssignment}.
	 * @param ctx the parse tree
	 */
	void exitCondSignParamAssignment(InterlisParser.CondSignParamAssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#signParamAssignment}.
	 * @param ctx the parse tree
	 */
	void enterSignParamAssignment(InterlisParser.SignParamAssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#signParamAssignment}.
	 * @param ctx the parse tree
	 */
	void exitSignParamAssignment(InterlisParser.SignParamAssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#enumAssignment}.
	 * @param ctx the parse tree
	 */
	void enterEnumAssignment(InterlisParser.EnumAssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#enumAssignment}.
	 * @param ctx the parse tree
	 */
	void exitEnumAssignment(InterlisParser.EnumAssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link InterlisParser#enumRange}.
	 * @param ctx the parse tree
	 */
	void enterEnumRange(InterlisParser.EnumRangeContext ctx);
	/**
	 * Exit a parse tree produced by {@link InterlisParser#enumRange}.
	 * @param ctx the parse tree
	 */
	void exitEnumRange(InterlisParser.EnumRangeContext ctx);
}