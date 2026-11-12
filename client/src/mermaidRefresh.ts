export type MermaidDiagram = {
  source: string | null;
  hideEmptyMembersBox: boolean;
  status: "current" | "last-valid" | "empty" | "unavailable";
};

type View = { id: number; uri: string; kind: "mermaid" | "plantuml" };
export type MermaidMessage = {
  type: "mermaid:update" | "mermaid:invalidate" | "mermaid:error";
  viewId: string;
  generation: number;
  diagram?: MermaidDiagram;
};

/** Coordinates one shared UML panel; automatic rendering never opens/reveals that panel. */
export class MermaidRefreshController {
  private view: View | undefined;
  private opening: View | undefined;
  private nextId = 0;
  private generation = 0;
  private ready = false;
  private dirty = false;
  private running = false;

  constructor(
    private readonly request: (uri: string) => Promise<MermaidDiagram>,
    private readonly post: (message: MermaidMessage) => void,
    private readonly debug: (error: unknown) => void
  ) {}

  beginOpen(uri: string, kind: View["kind"]): number {
    const id = ++this.nextId;
    this.opening = { id, uri, kind };
    this.invalidate();
    return id;
  }

  completeOpen(id: number, success: boolean): boolean {
    if (this.opening?.id !== id) return false;
    if (success) {
      this.view = this.opening;
      this.ready = false;
    }
    this.opening = undefined;
    this.invalidate();
    this.dirty = this.view?.kind === "mermaid";
    void this.pump();
    return success;
  }

  webviewReady(viewId: string): void {
    if (String(this.view?.id) !== viewId || this.view?.kind !== "mermaid") return;
    this.ready = true;
    void this.pump();
  }

  refresh(uri?: string): void {
    const relevant = [this.view, this.opening].some(view =>
      view?.kind === "mermaid" && (uri === undefined || view.uri === uri));
    if (!relevant) return;
    this.dirty = true;
    this.invalidate();
    void this.pump();
  }

  close(): void {
    this.generation++;
    this.view = undefined;
    this.opening = undefined;
    this.ready = false;
    this.dirty = false;
  }

  private invalidate(): void {
    this.generation++;
    if (this.view?.kind === "mermaid" && this.ready) {
      this.post({ type: "mermaid:invalidate", viewId: String(this.view.id), generation: this.generation });
    }
  }

  private async pump(): Promise<void> {
    if (this.running || !this.dirty || this.opening || !this.ready || this.view?.kind !== "mermaid") return;
    const view = this.view;
    const generation = this.generation;
    this.running = true;
    this.dirty = false;
    const current = () => this.view === view && this.generation === generation && !this.opening && this.ready;
    try {
      const diagram = await this.request(view.uri);
      if (current()) this.post({ type: "mermaid:update", viewId: String(view.id), generation, diagram });
    } catch (error) {
      this.debug(error);
      if (current()) this.post({ type: "mermaid:error", viewId: String(view.id), generation });
    } finally {
      this.running = false;
      void this.pump();
    }
  }
}
