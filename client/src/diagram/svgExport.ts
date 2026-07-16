const SVG_NAMESPACE = "http://www.w3.org/2000/svg";
const XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/";
const INKSCAPE_NAMESPACE = "http://www.inkscape.org/namespaces/inkscape";
const EXPORT_BACKGROUND_MARKER = "data-interlis-export-background";
const IDENTITY_EPSILON = 0.000001;

const ROOT_LAYOUT_PROPERTIES = new Set([
  "width",
  "height",
  "inline-size",
  "block-size",
  "min-width",
  "min-height",
  "max-width",
  "max-height",
  "display",
  "flex",
  "flex-basis",
  "flex-grow",
  "flex-shrink",
  "position",
  "overflow"
]);

/**
 * Serializes the SVG currently visible in the GLSP editor without changing
 * the live viewport. The viewport transform is intentionally kept on the
 * cloned SVG so zoom and scroll are reflected in the export.
 */
export function serializeVisibleSvg(source: SVGSVGElement): string {
  const bounds = source.getBoundingClientRect();
  const width = resolveDimension(bounds.width, source.clientWidth, source.getAttribute("width"));
  const height = resolveDimension(bounds.height, source.clientHeight, source.getAttribute("height"));

  if (width <= 0 || height <= 0) {
    throw new Error("The visible diagram has no measurable size.");
  }

  const clone = source.cloneNode(true) as SVGSVGElement;
  clone.setAttribute("xmlns", SVG_NAMESPACE);
  clone.setAttributeNS(XMLNS_NAMESPACE, "xmlns:inkscape", INKSCAPE_NAMESPACE);
  clone.setAttribute("version", "1.1");
  clone.setAttribute("viewBox", `0 0 ${width} ${height}`);
  clone.setAttribute("width", String(width));
  clone.setAttribute("height", String(height));

  copyComputedStyles(source, clone);
  normalizeSemanticSvgGroups(clone);
  clone.style.setProperty("width", `${width}px`);
  clone.style.setProperty("height", `${height}px`);
  clone.style.setProperty("background", "#ffffff");

  return ensureInkscapeNamespace(ensureWhiteSvgBackground(new XMLSerializer().serializeToString(clone)));
}

/**
 * Removes only the technical Sprotty groups that do not carry any semantic
 * information. Model element groups keep their IDs, classes, styles and
 * children so that classes, containers and edges remain useful Inkscape
 * objects.
 *
 * A non-identity viewport transform is intentionally retained. It is the
 * only technical group needed by the visible export to preserve zoom and
 * scroll exactly.
 */
export function normalizeSemanticSvgGroups(root: SVGSVGElement): void {
  normalizeIdentityTechnicalGroups(root);
}

/**
 * Ensures that an SVG has a concrete white background instead of relying on
 * the CSS of the originating webview.
 */
export function ensureWhiteSvgBackground(svg: string): string {
  if (!svg || svg.includes(EXPORT_BACKGROUND_MARKER)) {
    return svg;
  }

  const openingTag = /<svg\b[^>]*>/i.exec(svg);
  if (!openingTag || openingTag.index === undefined) {
    return svg;
  }

  const background = `<rect ${EXPORT_BACKGROUND_MARKER}="true" x="0" y="0" width="100%" height="100%" fill="#ffffff"/>`;
  const insertAt = openingTag.index + openingTag[0].length;
  return `${svg.slice(0, insertAt)}${background}${svg.slice(insertAt)}`;
}

/**
 * Adds the Inkscape namespace to exports that contain Inkscape connector
 * attributes. The full GLSP exporter serializes in a separate hidden viewer,
 * so this string-level fallback keeps that export valid as well.
 */
export function ensureInkscapeNamespace(svg: string): string {
  if (!svg || !svg.includes("inkscape:")) {
    return svg;
  }

  const openingTag = /<svg\b[^>]*>/i.exec(svg);
  if (!openingTag || openingTag.index === undefined || /\bxmlns:inkscape\s*=/i.test(openingTag[0])) {
    return svg;
  }

  const namespaceAttribute = ` xmlns:inkscape="${INKSCAPE_NAMESPACE}"`;
  const insertAt = openingTag.index + openingTag[0].length - 1;
  return `${svg.slice(0, insertAt)}${namespaceAttribute}${svg.slice(insertAt)}`;
}

function copyComputedStyles(source: SVGSVGElement, target: SVGSVGElement): void {
  const sourceElements = [source, ...Array.from(source.querySelectorAll("*"))];
  const targetElements = [target, ...Array.from(target.querySelectorAll("*"))];
  const count = Math.min(sourceElements.length, targetElements.length);

  for (let index = 0; index < count; index++) {
    const sourceElement = sourceElements[index];
    const targetElement = targetElements[index];
    if (sourceElement.localName === "style" || targetElement.localName === "style") {
      continue;
    }

    const computed = getComputedStyle(sourceElement);
    for (let propertyIndex = 0; propertyIndex < computed.length; propertyIndex++) {
      const property = computed[propertyIndex];
      if (index === 0 && ROOT_LAYOUT_PROPERTIES.has(property)) {
        continue;
      }

      const value = computed.getPropertyValue(property);
      if (!value) {
        continue;
      }

      if (!(targetElement instanceof SVGElement) && !(targetElement instanceof HTMLElement)) {
        continue;
      }
      targetElement.style.setProperty(property, value, computed.getPropertyPriority(property));
    }
  }
}

function normalizeIdentityTechnicalGroups(parent: Element): void {
  let child = parent.firstElementChild;
  while (child) {
    const nextSibling = child.nextElementSibling;
    if (isTechnicalTransformGroup(child)) {
      if (isIdentityTransform(child)) {
        unwrapGroup(child);
        normalizeIdentityTechnicalGroups(parent);
        return;
      }

      // Keep a non-identity viewport transform, but remove any identity
      // technical wrappers nested below it.
      normalizeIdentityTechnicalGroups(child);
    }
    child = nextSibling;
  }
}

function isTechnicalTransformGroup(element: Element): element is SVGGElement {
  if (element.localName !== "g" || !element.hasAttribute("transform")) {
    return false;
  }

  for (const attribute of Array.from(element.attributes)) {
    if (attribute.name !== "transform") {
      return false;
    }
  }
  return true;
}

function isIdentityTransform(group: SVGGElement): boolean {
  try {
    const transformList = group.transform?.baseVal;
    if (transformList) {
      if (transformList.numberOfItems === 0) {
        return true;
      }

      const matrix = transformList.consolidate()?.matrix;
      if (matrix) {
        return Math.abs(matrix.a - 1) < IDENTITY_EPSILON
          && Math.abs(matrix.b) < IDENTITY_EPSILON
          && Math.abs(matrix.c) < IDENTITY_EPSILON
          && Math.abs(matrix.d - 1) < IDENTITY_EPSILON
          && Math.abs(matrix.e) < IDENTITY_EPSILON
          && Math.abs(matrix.f) < IDENTITY_EPSILON;
      }
    }
  } catch {
    // Fall through to the conservative textual check below. Exporting must
    // not fail just because a browser cannot consolidate an SVG transform.
  }

  const transform = group.getAttribute("transform")?.replace(/\s+/g, "") ?? "";
  return transform === ""
    || transform === "matrix(1,0,0,1,0,0)"
    || transform === "matrix(1 0 0 1 0 0)"
    || transform === "scale(1)translate(0,0)"
    || transform === "translate(0,0)scale(1)";
}

function unwrapGroup(group: SVGGElement): void {
  const parent = group.parentElement;
  if (!parent) {
    return;
  }

  while (group.firstChild) {
    parent.insertBefore(group.firstChild, group);
  }
  parent.removeChild(group);
}

function resolveDimension(...candidates: Array<number | string | null | undefined>): number {
  for (const candidate of candidates) {
    const value = typeof candidate === "number" ? candidate : Number.parseFloat(candidate ?? "");
    if (Number.isFinite(value) && value > 0) {
      return Math.max(1, Math.round(value));
    }
  }
  return 0;
}
