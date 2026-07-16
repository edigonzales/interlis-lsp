declare module "ws" {
  export class WebSocket {
    constructor(address: string);
    on(event: "open", listener: () => void): this;
    on(event: "error", listener: (error: Error) => void): this;
    on(event: "close", listener: (code: number, reason: Buffer) => void): this;
    once(event: "open", listener: () => void): this;
    once(event: "error", listener: (error: Error) => void): this;
    once(event: "close", listener: (code: number, reason: Buffer) => void): this;
    send(content: string): void;
    close(): void;
    onmessage: ((event: { data: unknown }) => void) | null;
    onclose: ((event: { code: number; reason: string }) => void) | null;
    onerror: ((event: { error?: unknown }) => void) | null;
  }
}
