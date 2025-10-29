import type * as vscode from 'vscode';
import { RequestAction, RequestModelAction } from '@eclipse-glsp/protocol';
import type { GlspVscodeConnector } from '@eclipse-glsp/vscode-integration';

/**
 * Minimal interface that exposes the subset of {@link GlspVscodeConnector}
 * functionality required for triggering diagram refreshes.
 */
export type DiagramActionDispatcher = Pick<GlspVscodeConnector, 'dispatchAction'>;

/**
 * Contract implemented by the {@link InterlisGlspEditorProvider} that allows
 * querying for all GLSP clients that render a given INTERLIS source document.
 */
export interface DiagramClientRegistry {
  getClientIdsForDocument(documentUri: vscode.Uri): readonly string[];
}

/**
 * Determines whether the given text document represents an INTERLIS model.
 * The language id can be customised by the user, therefore we also check the
 * file extension.
 */
export function isInterlisDocument(document: { languageId?: string; uri: vscode.Uri }): boolean {
  const languageMatches = document.languageId?.toLowerCase() === 'interlis';
  if (languageMatches) {
    return true;
  }

  const fsPath = document.uri.fsPath;
  return fsPath ? fsPath.toLowerCase().endsWith('.ili') : false;
}

/**
 * Triggers a diagram refresh for all GLSP clients that render the provided
 * INTERLIS source document.
 *
 * @returns the number of diagram clients that received a refresh request.
 */
export function refreshDiagramForDocument(
  dispatcher: DiagramActionDispatcher,
  registry: DiagramClientRegistry,
  documentUri: vscode.Uri
): number {
  const clientIds = registry.getClientIdsForDocument(documentUri);
  if (clientIds.length === 0) {
    return 0;
  }

  for (const clientId of clientIds) {
    const action = RequestModelAction.create({
      options: { sourceUri: documentUri.toString() },
      requestId: RequestAction.generateRequestId()
    });
    dispatcher.dispatchAction(action, clientId);
  }

  return clientIds.length;
}
