import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/node';

export interface GlspConnectionInfo {
    host: string;
    port: number;
    path: string;
    running: boolean;
}

export class GlspSupport {
    private cached: GlspConnectionInfo | undefined;

    constructor(private readonly client: LanguageClient) {}

    async refresh(): Promise<GlspConnectionInfo | undefined> {
        try {
            const info = await this.client.sendRequest<GlspConnectionInfo>('interlis/glspInfo', {});
            this.cached = info;
            return info;
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : String(err);
            void vscode.window.showWarningMessage(`INTERLIS GLSP server info unavailable: ${message}`);
            return undefined;
        }
    }

    get info(): GlspConnectionInfo | undefined {
        return this.cached;
    }
}
