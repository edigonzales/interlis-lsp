import {
  EditorContextService,
  FeatureModule,
  FeedbackAwareSetModelCommand,
  IDiagramStartup,
  IModelChangeService,
  ViewportChange,
  isSelectableAndBoundsAware,
  toAbsoluteBounds
} from "@eclipse-glsp/client";
import {
  CommandExecutionContext,
  Dimension,
  GModelRoot,
  IActionDispatcher,
  Point,
  SetModelAction,
  SetModelCommand,
  SetViewportAction,
  TYPES,
  Viewport,
  isViewport
} from "@eclipse-glsp/sprotty";
import { decorate, inject, injectable, postConstruct, preDestroy } from "inversify";

const VIEWPORT_STATE_KEY = "interlisViewportState";
const VIEWPORT_STATE_VERSION = 1;
const VIEWPORT_PERSIST_DELAY_MS = 80;
const VIEWPORT_EPSILON = 0.5;

type ViewportStateApi = {
  getState?(): unknown;
  setState?(state: unknown): void;
};

type DiagramViewportSnapshot = {
  sourceUri: string;
  diagramType: string;
  zoom: number;
  scroll: Point;
  center: Point;
  savedAt: number;
  anchorElementId?: string;
  anchorOffset?: Point;
};

type StoredViewportState = {
  version: number;
  snapshots: Record<string, DiagramViewportSnapshot>;
};

type WebviewState = Record<string, unknown> & {
  interlisViewportState?: StoredViewportState;
};

type AnchorCandidate = {
  id: string;
  center: Point;
};

let viewportStateApi: ViewportStateApi | undefined;
let fallbackWebviewState: WebviewState = {};

export function initializeViewportStateApi(api: ViewportStateApi | undefined): void {
  viewportStateApi = api;
  const rawState = api?.getState?.();
  if (rawState && typeof rawState === "object" && !Array.isArray(rawState)) {
    fallbackWebviewState = { ...(rawState as Record<string, unknown>) };
  }
}

export class InterlisPreservingSetModelCommand extends FeedbackAwareSetModelCommand {
  constructor(action: SetModelAction) {
    super(action);
  }

  override execute(context: CommandExecutionContext): GModelRoot {
    const previousRoot = context.root;
    const newRoot = super.execute(context);

    if (previousRoot.type === newRoot.type && Dimension.isValid(previousRoot.canvasBounds)) {
      newRoot.canvasBounds = previousRoot.canvasBounds;
    }
    if (isViewport(previousRoot) && isViewport(newRoot)) {
      newRoot.zoom = previousRoot.zoom;
      newRoot.scroll = previousRoot.scroll;
    }

    return newRoot;
  }
}

export class InterlisViewportPersistence implements IDiagramStartup {
  protected editorContext!: EditorContextService;
  protected modelChangeService!: IModelChangeService;
  protected actionDispatcher!: IActionDispatcher;

  readonly rank = 1000;

  protected viewportSubscription: { dispose(): void } | undefined;
  protected pendingPersistTimer: ReturnType<typeof setTimeout> | undefined;
  protected latestSnapshot: DiagramViewportSnapshot | undefined;

  protected initialize(): void {
    this.viewportSubscription = this.modelChangeService.onViewportChanged(change => {
      this.handleViewportChanged(change);
    });
  }

  protected dispose(): void {
    if (this.pendingPersistTimer !== undefined) {
      clearTimeout(this.pendingPersistTimer);
      this.pendingPersistTimer = undefined;
      if (this.latestSnapshot) {
        this.writeSnapshot(this.latestSnapshot);
      }
    }
    this.viewportSubscription?.dispose();
  }

  async postModelInitialization(): Promise<void> {
    const viewport = this.safeViewport();
    const snapshot = this.readSnapshot();
    if (!viewport || !snapshot) {
      return;
    }

    const restored = this.computeRestoredViewport(snapshot, viewport);
    if (!restored || this.isViewportSimilar(viewport, restored)) {
      return;
    }

    await this.actionDispatcher.dispatch(SetViewportAction.create(viewport.id, restored, { animate: false }));
  }

  protected handleViewportChanged(_change: ViewportChange): void {
    const snapshot = this.captureSnapshot();
    if (!snapshot) {
      return;
    }

    this.latestSnapshot = snapshot;
    if (this.pendingPersistTimer !== undefined) {
      clearTimeout(this.pendingPersistTimer);
    }
    this.pendingPersistTimer = setTimeout(() => {
      this.pendingPersistTimer = undefined;
      if (this.latestSnapshot) {
        this.writeSnapshot(this.latestSnapshot);
      }
    }, VIEWPORT_PERSIST_DELAY_MS);
  }

  protected captureSnapshot(): DiagramViewportSnapshot | undefined {
    const viewport = this.safeViewport();
    const sourceUri = this.editorContext.sourceUri;
    if (!viewport || !sourceUri || !isFiniteViewport(viewport) || !Dimension.isValid(viewport.canvasBounds)) {
      return undefined;
    }

    const center = viewportCenter(viewport);
    const anchor = this.findAnchorCandidate(viewport, center);

    return {
      sourceUri,
      diagramType: this.editorContext.diagramType,
      zoom: viewport.zoom,
      scroll: clonePoint(viewport.scroll),
      center,
      savedAt: Date.now(),
      ...(anchor
        ? {
            anchorElementId: anchor.id,
            anchorOffset: {
              x: center.x - anchor.center.x,
              y: center.y - anchor.center.y
            }
          }
        : {})
    };
  }

  protected computeRestoredViewport(
    snapshot: DiagramViewportSnapshot,
    viewport: Readonly<GModelRoot & Viewport>
  ): Viewport | undefined {
    if (!isValidSnapshot(snapshot) || !Dimension.isValid(viewport.canvasBounds)) {
      return undefined;
    }

    const anchorViewport = this.anchorViewport(snapshot, viewport);
    if (anchorViewport) {
      return anchorViewport;
    }

    return {
      zoom: snapshot.zoom,
      scroll: clonePoint(snapshot.scroll)
    };
  }

  protected anchorViewport(
    snapshot: DiagramViewportSnapshot,
    viewport: Readonly<GModelRoot & Viewport>
  ): Viewport | undefined {
    if (!snapshot.anchorElementId || !snapshot.anchorOffset) {
      return undefined;
    }

    const anchorElement = viewport.index.getById(snapshot.anchorElementId);
    if (!anchorElement || !isSelectableAndBoundsAware(anchorElement)) {
      return undefined;
    }

    const anchorBounds = toAbsoluteBounds(anchorElement);
    if (!isFiniteBounds(anchorBounds)) {
      return undefined;
    }

    const anchorCenter = boundsCenter(anchorBounds);
    const desiredCenter = {
      x: anchorCenter.x + snapshot.anchorOffset.x,
      y: anchorCenter.y + snapshot.anchorOffset.y
    };

    return {
      zoom: snapshot.zoom,
      scroll: {
        x: desiredCenter.x - viewport.canvasBounds.width / (2 * snapshot.zoom),
        y: desiredCenter.y - viewport.canvasBounds.height / (2 * snapshot.zoom)
      }
    };
  }

  protected findAnchorCandidate(
    viewport: Readonly<GModelRoot & Viewport>,
    center: Point
  ): AnchorCandidate | undefined {
    const visibleBounds = {
      x: viewport.scroll.x,
      y: viewport.scroll.y,
      width: viewport.canvasBounds.width / viewport.zoom,
      height: viewport.canvasBounds.height / viewport.zoom
    };

    const allCandidates = Array.from(viewport.index.all())
      .filter(isSelectableAndBoundsAware)
      .map(element => ({ element, bounds: toAbsoluteBounds(element) }))
      .filter(candidate => isFiniteBounds(candidate.bounds));

    const preferredCandidates = allCandidates.filter(candidate => boundsOverlap(candidate.bounds, visibleBounds));
    const candidates = preferredCandidates.length > 0 ? preferredCandidates : allCandidates;
    if (candidates.length === 0) {
      return undefined;
    }

    candidates.sort((left, right) => {
      const leftDistance = distanceSquared(boundsCenter(left.bounds), center);
      const rightDistance = distanceSquared(boundsCenter(right.bounds), center);
      if (leftDistance !== rightDistance) {
        return leftDistance - rightDistance;
      }

      const leftArea = left.bounds.width * left.bounds.height;
      const rightArea = right.bounds.width * right.bounds.height;
      if (leftArea !== rightArea) {
        return leftArea - rightArea;
      }

      return left.element.id.localeCompare(right.element.id);
    });

    const winner = candidates[0]!;
    return {
      id: winner.element.id,
      center: boundsCenter(winner.bounds)
    };
  }

  protected safeViewport(): Readonly<GModelRoot & Viewport> | undefined {
    try {
      return this.editorContext.viewport;
    } catch {
      return undefined;
    }
  }

  protected snapshotKey(): string | undefined {
    return snapshotKey(this.editorContext.diagramType, this.editorContext.sourceUri);
  }

  protected readSnapshot(): DiagramViewportSnapshot | undefined {
    const key = this.snapshotKey();
    if (!key) {
      return undefined;
    }

    const state = readStoredViewportState();
    const snapshot = state.snapshots[key];
    return snapshot && isValidSnapshot(snapshot) ? snapshot : undefined;
  }

  protected writeSnapshot(snapshot: DiagramViewportSnapshot): void {
    const key = snapshotKey(snapshot.diagramType, snapshot.sourceUri);
    if (!key) {
      return;
    }

    const webviewState = readWebviewState();
    const storedState = readStoredViewportState();
    const nextState: StoredViewportState = {
      version: VIEWPORT_STATE_VERSION,
      snapshots: {
        ...storedState.snapshots,
        [key]: snapshot
      }
    };

    writeWebviewState({
      ...webviewState,
      [VIEWPORT_STATE_KEY]: nextState
    });
  }

  protected isViewportSimilar(
    current: Readonly<GModelRoot & Viewport>,
    desired: Readonly<Viewport>
  ): boolean {
    return (
      almostEquals(current.zoom, desired.zoom) &&
      almostEquals(current.scroll.x, desired.scroll.x) &&
      almostEquals(current.scroll.y, desired.scroll.y)
    );
  }
}

export const interlisViewportPersistenceModule = new FeatureModule((bind, unbind, isBound, rebind) => {
  rebind(SetModelCommand).to(InterlisPreservingSetModelCommand).inTransientScope();
  bind(InterlisViewportPersistence).toSelf().inSingletonScope();
  bind(TYPES.IDiagramStartup).toService(InterlisViewportPersistence);
});

decorate(inject(TYPES.Action), InterlisPreservingSetModelCommand, 0);
decorate(injectable(), InterlisPreservingSetModelCommand);
decorate(injectable(), InterlisViewportPersistence);
decorate(inject(EditorContextService), InterlisViewportPersistence.prototype, "editorContext");
decorate(inject(TYPES.IModelChangeService), InterlisViewportPersistence.prototype, "modelChangeService");
decorate(inject(TYPES.IActionDispatcher), InterlisViewportPersistence.prototype, "actionDispatcher");
decorate(postConstruct(), InterlisViewportPersistence.prototype, "initialize");
decorate(preDestroy(), InterlisViewportPersistence.prototype, "dispose");

function readWebviewState(): WebviewState {
  const raw = viewportStateApi?.getState?.();
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return { ...fallbackWebviewState };
  }
  fallbackWebviewState = { ...(raw as Record<string, unknown>) };
  return { ...fallbackWebviewState };
}

function writeWebviewState(state: WebviewState): void {
  fallbackWebviewState = { ...state };
  viewportStateApi?.setState?.(fallbackWebviewState);
}

function readStoredViewportState(): StoredViewportState {
  const raw = readWebviewState()[VIEWPORT_STATE_KEY];
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return { version: VIEWPORT_STATE_VERSION, snapshots: {} };
  }

  const maybeState = raw as Partial<StoredViewportState>;
  const snapshots = maybeState.snapshots;
  if (
    maybeState.version !== VIEWPORT_STATE_VERSION ||
    !snapshots ||
    typeof snapshots !== "object" ||
    Array.isArray(snapshots)
  ) {
    return { version: VIEWPORT_STATE_VERSION, snapshots: {} };
  }

  const validSnapshots = Object.fromEntries(
    Object.entries(snapshots).filter((entry): entry is [string, DiagramViewportSnapshot] => {
      return isValidSnapshot(entry[1]);
    })
  );

  return {
    version: VIEWPORT_STATE_VERSION,
    snapshots: validSnapshots
  };
}

function clonePoint(point: Point): Point {
  return { x: point.x, y: point.y };
}

function snapshotKey(diagramType: string, sourceUri: string | undefined): string | undefined {
  if (!sourceUri) {
    return undefined;
  }
  return `${diagramType}::${sourceUri}`;
}

function viewportCenter(viewport: Readonly<GModelRoot & Viewport>): Point {
  return {
    x: viewport.scroll.x + viewport.canvasBounds.width / (2 * viewport.zoom),
    y: viewport.scroll.y + viewport.canvasBounds.height / (2 * viewport.zoom)
  };
}

function boundsCenter(bounds: { x: number; y: number; width: number; height: number }): Point {
  return {
    x: bounds.x + bounds.width / 2,
    y: bounds.y + bounds.height / 2
  };
}

function boundsOverlap(
  left: { x: number; y: number; width: number; height: number },
  right: { x: number; y: number; width: number; height: number }
): boolean {
  return (
    left.x < right.x + right.width &&
    left.x + left.width > right.x &&
    left.y < right.y + right.height &&
    left.y + left.height > right.y
  );
}

function distanceSquared(left: Point, right: Point): number {
  const dx = left.x - right.x;
  const dy = left.y - right.y;
  return dx * dx + dy * dy;
}

function almostEquals(left: number, right: number): boolean {
  return Math.abs(left - right) <= VIEWPORT_EPSILON;
}

function isFinitePoint(point: unknown): point is Point {
  if (!point || typeof point !== "object" || Array.isArray(point)) {
    return false;
  }
  const maybePoint = point as Partial<Point>;
  return Number.isFinite(maybePoint.x) && Number.isFinite(maybePoint.y);
}

function isFiniteBounds(bounds: unknown): bounds is { x: number; y: number; width: number; height: number } {
  if (!bounds || typeof bounds !== "object" || Array.isArray(bounds)) {
    return false;
  }
  const maybeBounds = bounds as Partial<{ x: number; y: number; width: number; height: number }>;
  const width = maybeBounds.width ?? Number.NaN;
  const height = maybeBounds.height ?? Number.NaN;
  return Number.isFinite(maybeBounds.x)
    && Number.isFinite(maybeBounds.y)
    && Number.isFinite(width)
    && Number.isFinite(height)
    && width > 0
    && height > 0;
}

function isFiniteViewport(viewport: Readonly<Viewport>): boolean {
  return viewport.zoom > 0 && Number.isFinite(viewport.zoom) && isFinitePoint(viewport.scroll);
}

function isValidSnapshot(value: unknown): value is DiagramViewportSnapshot {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }

  const snapshot = value as Partial<DiagramViewportSnapshot>;
  const hasAnchor = snapshot.anchorElementId === undefined && snapshot.anchorOffset === undefined
    ? true
    : typeof snapshot.anchorElementId === "string"
      && snapshot.anchorElementId.length > 0
      && isFinitePoint(snapshot.anchorOffset);

  return typeof snapshot.sourceUri === "string"
    && snapshot.sourceUri.length > 0
    && typeof snapshot.diagramType === "string"
    && snapshot.diagramType.length > 0
    && typeof snapshot.savedAt === "number"
    && Number.isFinite(snapshot.savedAt)
    && typeof snapshot.zoom === "number"
    && snapshot.zoom > 0
    && isFinitePoint(snapshot.scroll)
    && isFinitePoint(snapshot.center)
    && hasAnchor;
}
