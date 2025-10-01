package ch.so.agi.lsp.interlis;

import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.View;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;
import ch.interlis.ili2c.metamodel.Table;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Builds {@link DocumentSymbol} hierarchies from an INTERLIS {@link TransferDescription}.
 */
final class InterlisDocumentSymbolCollector {
    private final String documentText;

    InterlisDocumentSymbolCollector(String documentText) {
        this.documentText = documentText != null ? documentText : "";
    }

    List<DocumentSymbol> collect(TransferDescription td) {
        if (td == null) {
            return Collections.emptyList();
        }

        List<DocumentSymbol> result = new ArrayList<>();
        Model[] models = td.getModelsFromLastFile();
        if (models == null) {
            return result;
        }

        Set<Model> seen = new HashSet<>();
        Arrays.stream(models)
                .filter(model -> model != null)
                .forEach(model -> collectModelSymbols(model, seen, result));

        return result;
    }

    private void collectModelSymbols(Model model, Set<Model> seen, List<DocumentSymbol> result) {
        if (!seen.add(model)) {
            return;
        }

        DocumentSymbol modelSymbol = buildModelSymbol(model);
        result.add(modelSymbol);

        Range fallbackRange = copyRange(modelSymbol.getRange());
        collectImportedModelSymbols(model, fallbackRange, seen, result);
    }

    private void collectImportedModelSymbols(Model model, Range fallbackRange, Set<Model> seen, List<DocumentSymbol> result) {
        Model[] imports = model != null ? model.getImporting() : null;
        if (imports == null || imports.length == 0) {
            return;
        }

        for (Model imported : imports) {
            if (imported == null || !seen.add(imported)) {
                continue;
            }

            DocumentSymbol importedSymbol = buildModelSymbol(imported);
            if (fallbackRange != null) {
                Range copy = copyRange(fallbackRange);
                alignSymbolRange(importedSymbol, copy);
            }
            result.add(importedSymbol);

            collectImportedModelSymbols(imported, fallbackRange, seen, result);
        }
    }

    private void alignSymbolRange(DocumentSymbol symbol, Range fallbackRange) {
        if (symbol == null || fallbackRange == null) {
            return;
        }
        Range selectionCopy = copyRange(fallbackRange);
        symbol.setRange(copyRange(fallbackRange));
        symbol.setSelectionRange(selectionCopy);
        if (symbol.getChildren() != null) {
            for (DocumentSymbol child : symbol.getChildren()) {
                alignSymbolRange(child, fallbackRange);
            }
        }
    }

    private DocumentSymbol buildModelSymbol(Model model) {
        DocumentSymbol symbol = createSymbol(model, "MODEL", SymbolKind.Module);
        symbol.setChildren(processContainer(model, model));
        return symbol;
    }

    private List<DocumentSymbol> processContainer(Container<?> container, Model ownerModel) {
        List<DocumentSymbol> children = new ArrayList<>();
        Iterator<?> it = container != null ? container.iterator() : Collections.emptyIterator();
        while (it.hasNext()) {
            Object candidate = it.next();
            if (!(candidate instanceof Element element)) {
                continue;
            }
            if (modelOf(element) != ownerModel) {
                continue;
            }

            if (element instanceof Topic topic) {
                DocumentSymbol topicSymbol = createSymbol(topic, "TOPIC", SymbolKind.Namespace);
                topicSymbol.setChildren(processContainer(topic, ownerModel));
                children.add(topicSymbol);
            } else if (element instanceof Viewable viewable) {
                DocumentSymbol viewableSymbol = buildViewableSymbol(viewable, ownerModel);
                if (viewableSymbol != null) {
                    children.add(viewableSymbol);
                }
            } else if (element instanceof Domain domain) {
                DocumentSymbol domainSymbol = createSymbol(domain, "DOMAIN", SymbolKind.TypeParameter);
                children.add(domainSymbol);
            }
        }
        return children;
    }

    private DocumentSymbol buildViewableSymbol(Viewable viewable, Model ownerModel) {
        String detail = determineViewableDetail(viewable);
        SymbolKind kind = determineViewableKind(viewable);
        DocumentSymbol viewableSymbol = createSymbol(viewable, detail, kind);
        viewableSymbol.setChildren(collectAttributes(viewable, ownerModel));
        return viewableSymbol;
    }

    private List<DocumentSymbol> collectAttributes(Viewable viewable, Model ownerModel) {
        List<DocumentSymbol> attributes = new ArrayList<>();
        Iterator<ViewableTransferElement> it = viewable.getAttributesAndRoles2();
        while (it.hasNext()) {
            ViewableTransferElement vte = it.next();
            if (!(vte.obj instanceof AttributeDef attribute)) {
                continue;
            }
            if (modelOf(attribute) != ownerModel) {
                continue;
            }
            DocumentSymbol attributeSymbol = createSymbol(attribute, null, SymbolKind.Property);
            attributes.add(attributeSymbol);
        }
        return attributes;
    }

    private static Range copyRange(Range original) {
        if (original == null) {
            return null;
        }
        Position start = copyPosition(original.getStart());
        Position end = copyPosition(original.getEnd());
        return new Range(start, end);
    }

    private static Position copyPosition(Position position) {
        if (position == null) {
            return new Position(0, 0);
        }
        return new Position(position.getLine(), position.getCharacter());
    }

    private DocumentSymbol createSymbol(Element element, String detail, SymbolKind kind) {
        String name = element != null ? element.getName() : null;
        if (name == null) {
            name = "";
        }
        Range lineRange = lineRange(element);
        Range selection = selectionRange(element, lineRange);

        DocumentSymbol symbol = new DocumentSymbol();
        symbol.setName(name);
        symbol.setDetail(detail);
        symbol.setKind(kind);
        symbol.setRange(lineRange);
        symbol.setSelectionRange(selection);
        return symbol;
    }

    private Range lineRange(Element element) {
        int lineIndex = element != null ? Math.max(element.getSourceLine() - 1, 0) : 0;
        if (documentText.isEmpty()) {
            Position pos = new Position(lineIndex, 0);
            return new Range(pos, pos);
        }

        int startOffset = DocumentTracker.lineStartOffset(documentText, lineIndex);
        int endOffset = DocumentTracker.lineStartOffset(documentText, lineIndex + 1);
        if (endOffset < startOffset) {
            endOffset = documentText.length();
        }
        Position start = DocumentTracker.positionAt(documentText, startOffset);
        Position end = DocumentTracker.positionAt(documentText, endOffset);
        return new Range(start, end);
    }

    private Range selectionRange(Element element, Range fallback) {
        if (element == null) {
            return fallback;
        }
        String name = element.getName();
        if (name == null || name.isBlank() || documentText.isEmpty()) {
            return fallback;
        }

        int sourceLine = Math.max(element.getSourceLine() - 1, 0);
        int startOffset = DocumentTracker.lineStartOffset(documentText, sourceLine);
        int endOffset = DocumentTracker.lineStartOffset(documentText, sourceLine + 1);
        if (endOffset < startOffset) {
            endOffset = documentText.length();
        }

        int nameOffset = -1;
        if (startOffset < documentText.length()) {
            int safeEnd = Math.min(endOffset, documentText.length());
            String lineText = documentText.substring(startOffset, safeEnd);
            int idx = lineText.indexOf(name);
            if (idx >= 0) {
                nameOffset = startOffset + idx;
            }
        }
        if (nameOffset < 0) {
            nameOffset = documentText.indexOf(name);
        }
        if (nameOffset < 0) {
            return fallback;
        }
        Position start = DocumentTracker.positionAt(documentText, nameOffset);
        Position end = DocumentTracker.positionAt(documentText, nameOffset + name.length());
        return new Range(start, end);
    }

    private static String determineViewableDetail(Viewable viewable) {
        if (viewable instanceof Table table) {
            return table.isIdentifiable() ? "CLASS" : "STRUCTURE";
        }
        if (viewable instanceof AssociationDef) {
            return "ASSOCIATION";
        }
        if (viewable instanceof View) {
            return "VIEW";
        }
        return "VIEWABLE";
    }

    private static SymbolKind determineViewableKind(Viewable viewable) {
        if (viewable instanceof Table table) {
            return table.isIdentifiable() ? SymbolKind.Class : SymbolKind.Struct;
        }
        if (viewable instanceof AssociationDef) {
            return SymbolKind.Interface;
        }
        if (viewable instanceof View) {
            return SymbolKind.Interface;
        }
        return SymbolKind.Object;
    }

    private static Model modelOf(Element element) {
        if (element == null) {
            return null;
        }
        Element current = element;
        while (current != null) {
            if (current instanceof Model model) {
                return model;
            }
            Container<?> container = current.getContainer();
            current = container instanceof Element ? (Element) container : null;
        }
        return null;
    }
}
