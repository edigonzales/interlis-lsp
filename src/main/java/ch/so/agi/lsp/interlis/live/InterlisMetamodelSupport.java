package ch.so.agi.lsp.interlis.live;

import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.Function;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.TypeAlias;
import ch.interlis.ili2c.metamodel.Unit;
import ch.interlis.ili2c.metamodel.View;

public final class InterlisMetamodelSupport {
    private InterlisMetamodelSupport() {
    }

    public static InterlisSymbolKind toSymbolKind(Element element) {
        if (element instanceof Model) {
            return InterlisSymbolKind.MODEL;
        }
        if (element instanceof Topic) {
            return InterlisSymbolKind.TOPIC;
        }
        if (element instanceof View) {
            return InterlisSymbolKind.VIEW;
        }
        if (element instanceof AssociationDef) {
            return InterlisSymbolKind.ASSOCIATION;
        }
        if (element instanceof Table table) {
            return table.isIdentifiable() ? InterlisSymbolKind.CLASS : InterlisSymbolKind.STRUCTURE;
        }
        if (element instanceof Domain) {
            return InterlisSymbolKind.DOMAIN;
        }
        if (element instanceof Unit) {
            return InterlisSymbolKind.UNIT;
        }
        if (element instanceof Function) {
            return InterlisSymbolKind.FUNCTION;
        }
        return InterlisSymbolKind.CLASS;
    }

    public static boolean isFormattedDomain(Element element) {
        if (!(element instanceof Domain domain)) {
            return false;
        }
        return isFormattedType(domain.getType());
    }

    private static boolean isFormattedType(Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof FormattedType) {
            return true;
        }
        if (type instanceof TypeAlias alias) {
            try {
                return alias.resolveAliases() instanceof FormattedType;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }
}
