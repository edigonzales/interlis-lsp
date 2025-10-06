package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.TransferDescription;

import java.util.Iterator;

final class InterlisNameResolver {
    private InterlisNameResolver() {
    }

    static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.';
    }

    static String lastSegment(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        int idx = token.lastIndexOf('.');
        return idx >= 0 ? token.substring(idx + 1) : token;
    }

    static Element resolveElement(TransferDescription td, String token) {
        if (td == null || token == null || token.isBlank()) {
            return null;
        }

        Element element = td.getElement(token);
        if (element != null) {
            return element;
        }

        String[] segments = token.split("\\.");
        if (segments.length > 1) {
            Element current = td.getElement(segments[0]);
            if (current == null) {
                return null;
            }
            for (int i = 1; i < segments.length && current != null; i++) {
                if (!(current instanceof ch.interlis.ili2c.metamodel.Container<?> container)) {
                    current = null;
                    break;
                }
                current = findChild(container, segments[i]);
            }
            if (current != null) {
                return current;
            }
        }

        String segment = lastSegment(token);
        if (!segment.equals(token)) {
            element = td.getElement(segment);
        }

        return element;
    }

    static Model findEnclosingModel(Element element) {
        if (element instanceof Model) {
            return (Model) element;
        }

        Element current = element;
        while (current != null) {
            ch.interlis.ili2c.metamodel.Container<?> container = current.getContainer();
            if (container instanceof Model) {
                return (Model) container;
            }
            current = container instanceof Element ? (Element) container : null;
        }
        return null;
    }

    private static Element findChild(ch.interlis.ili2c.metamodel.Container<?> container, String name) {
        if (container == null || name == null || name.isBlank()) {
            return null;
        }

        Iterator<?> iterator = container.iterator();
        while (iterator.hasNext()) {
            Object candidate = iterator.next();
            if (candidate instanceof Element child && name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }
}
