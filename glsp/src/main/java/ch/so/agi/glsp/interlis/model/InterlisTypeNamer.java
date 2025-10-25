package ch.so.agi.glsp.interlis.model;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.ObjectType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.ReferenceType;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.TextOIDType;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;

/**
 * Utility that mirrors the attribute type naming logic used by the INTERLIS LSP.
 */
final class InterlisTypeNamer {

    private InterlisTypeNamer() {
    }

    static String nameOf(final AttributeDef attribute) {
        Type domain = attribute.getDomain();
        if (domain == null) {
            return "<Unknown>";
        }
        if (domain instanceof ObjectType) {
            return "ObjectType";
        }
        if (domain instanceof ReferenceType reference) {
            AbstractClassDef target = reference.getReferred();
            if (target != null) {
                return target.getName();
            }
        } else if (domain instanceof CompositionType composition) {
            AbstractClassDef target = composition.getComponentType();
            if (target != null) {
                return target.getName();
            }
        } else if (domain instanceof SurfaceType) {
            return "Surface";
        } else if (domain instanceof PolylineType) {
            return "Polyline";
        } else if (domain instanceof CoordType coordType) {
            NumericalType[] dimensions = coordType.getDimensions();
            return "Coord" + dimensions.length;
        } else if (domain instanceof NumericType) {
            return "Numeric";
        } else if (domain instanceof TextType) {
            return "String";
        } else if (domain instanceof EnumerationType enumeration) {
            if (attribute.isDomainBoolean()) {
                return "Boolean";
            }
            if (enumeration.getName() != null && !enumeration.getName().isEmpty()) {
                return enumeration.getName();
            }
            if (attribute.getContainer() != null) {
                return attribute.getContainer().getName();
            }
        } else if (domain instanceof TextOIDType textOidType) {
            Type oidType = textOidType.getOIDType();
            if (oidType instanceof TypeAlias alias) {
                return alias.getName() != null ? alias.getName() : alias.getAliasing().toString();
            }
            return "OID (Text)";
        } else if (domain instanceof TypeAlias alias) {
            if (alias.getName() != null) {
                return alias.getName();
            }
            Object aliased = alias.getAliasing();
            if (aliased != null) {
                return aliased.getClass().getSimpleName();
            }
        }
        return domain.getClass().getSimpleName();
    }
}
