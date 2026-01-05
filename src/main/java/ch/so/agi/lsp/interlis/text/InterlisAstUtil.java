package ch.so.agi.lsp.interlis.text;

import ch.interlis.ili2c.metamodel.*;

import java.lang.reflect.Method;
import java.util.*;

import org.eclipse.lsp4j.CompletionItemKind;

/**
 * Helpers for navigating ili2c's AST. Extracted from the jEdit plugin so it can be reused
 * by completion/definition features in the LSP server without UI dependencies.
 */
final class InterlisAstUtil {
    private InterlisAstUtil() {
    }

    static Model resolveModel(TransferDescription td, String name) {
        if (td == null || name == null) {
            return null;
        }
        String target = name.toUpperCase(Locale.ROOT);

        for (Model model : td.getModelsFromLastFile()) {
            String modelName = model != null ? model.getName() : null;
            if (modelName != null && modelName.toUpperCase(Locale.ROOT).equals(target)) {
                return model;
            }
        }

        for (Model model : td.getModelsFromLastFile()) {
            Model found = findInImportsRecursive(model, target, new HashSet<>());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Model findInImportsRecursive(Model model, String targetUpper, Set<Model> seen) {
        if (model == null || !seen.add(model)) {
            return null;
        }
        Model[] imports = model.getImporting();
        if (imports == null) {
            return null;
        }
        for (Model imp : imports) {
            if (imp == null) {
                continue;
            }
            String name = imp.getName();
            if (name != null && name.toUpperCase(Locale.ROOT).equals(targetUpper)) {
                return imp;
            }
            Model found = findInImportsRecursive(imp, targetUpper, seen);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static Element findChildInModelByName(Model model, String name) {
        if (model == null || name == null) {
            return null;
        }
        String target = name.toUpperCase(Locale.ROOT);
        for (Iterator<?> it = model.iterator(); it.hasNext(); ) {
            Object next = it.next();
            if (!(next instanceof Element element)) {
                continue;
            }
            String elementName = element.getName();
            if (elementName != null && elementName.toUpperCase(Locale.ROOT).equals(target)) {
                return element;
            }
        }
        return null;
    }

    static Element findChildInContainerByName(Container<?> container, String name) {
        if (container == null || name == null) {
            return null;
        }
        String target = name.toUpperCase(Locale.ROOT);
        for (Iterator<?> it = container.iterator(); it.hasNext(); ) {
            Object next = it.next();
            if (!(next instanceof Element element)) {
                continue;
            }
            String elementName = element.getName();
            if (elementName != null && elementName.toUpperCase(Locale.ROOT).equals(target)) {
                return element;
            }
        }
        return null;
    }

    static boolean hasAttributeOrRole(Viewable viewable, String name) {
        if (viewable == null || name == null) {
            return false;
        }
        String target = name.toUpperCase(Locale.ROOT);
        for (Iterator<?> it = viewable.getAttributesAndRoles2(); it.hasNext(); ) {
            ViewableTransferElement element = (ViewableTransferElement) it.next();
            Object obj = element.obj;
            String elementName = null;
            if (obj instanceof AttributeDef attribute) {
                elementName = attribute.getName();
            } else {
                try {
                    Method method = obj.getClass().getMethod("getName");
                    elementName = (String) method.invoke(obj);
                } catch (Exception ignored) {
                }
            }
            if (elementName != null && elementName.toUpperCase(Locale.ROOT).equals(target)) {
                return true;
            }
        }
        return false;
    }

    static List<ChildCandidate> collectChildren(Object parent) {
        List<ChildCandidate> result = new ArrayList<>();
        if (parent instanceof Model model) {
            for (Iterator<?> it = model.iterator(); it.hasNext(); ) {
                Object next = it.next();
                if (!(next instanceof Element element)) {
                    continue;
                }
                String name = element.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                CompletionItemKind kind = CompletionItemKind.Text;
                if (element instanceof Topic) {
                    kind = CompletionItemKind.Module;
                } else if (element instanceof Viewable) {
                    kind = CompletionItemKind.Class;
                } else if (element instanceof Domain) {
                    kind = CompletionItemKind.Struct;
                } else if (element instanceof Unit) {
                    kind = CompletionItemKind.Unit;
                } else if (element instanceof Function) {
                    kind = CompletionItemKind.Function;
                }
                result.add(new ChildCandidate(name, kind));
            }
        } else if (parent instanceof Topic topic) {
            for (Iterator<?> it = topic.iterator(); it.hasNext(); ) {
                Object next = it.next();
                if (!(next instanceof Element element)) {
                    continue;
                }
                String name = element.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                CompletionItemKind kind = CompletionItemKind.Text;
                if (element instanceof Viewable) {
                    kind = CompletionItemKind.Class;
                } else if (element instanceof Domain) {
                    kind = CompletionItemKind.Struct;
                } else if (element instanceof Unit) {
                    kind = CompletionItemKind.Unit;
                } else if (element instanceof Function) {
                    kind = CompletionItemKind.Function;
                }
                result.add(new ChildCandidate(name, kind));
            }
        } else if (parent instanceof Viewable viewable) {
            for (Iterator<?> it = viewable.getAttributesAndRoles2(); it.hasNext(); ) {
                ViewableTransferElement element = (ViewableTransferElement) it.next();
                Object obj = element.obj;
                String name = null;
                CompletionItemKind kind = CompletionItemKind.Field;
                if (obj instanceof AttributeDef attribute) {
                    name = attribute.getName();
                } else {
                    try {
                        Method method = obj.getClass().getMethod("getName");
                        name = (String) method.invoke(obj);
                        kind = CompletionItemKind.Property;
                    } catch (Exception ignored) {
                    }
                }
                if (name != null && !name.isBlank()) {
                    result.add(new ChildCandidate(name, kind));
                }
            }
        }
        return result;
    }

    static List<String> modelNamesForDocument(TransferDescription td) {
        if (td == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Model model : td.getModelsFromLastFile()) {
            if (model == null) {
                continue;
            }
            String name = model.getName();
            if (name != null) {
                names.add(name);
            }
            Model[] imports = model.getImporting();
            if (imports != null) {
                for (Model imp : imports) {
                    if (imp != null && imp.getName() != null) {
                        names.add(imp.getName());
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    record ChildCandidate(String name, CompletionItemKind kind) {
    }

}
