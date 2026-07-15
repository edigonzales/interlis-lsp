import * as vscode from "vscode";
import { ActionMessage, NavigateToExternalTargetAction } from "@eclipse-glsp/protocol";

const JSON_OPENER_OPTIONS = "jsonOpenerOptions";

/**
 * Reuses the editor group of an already open text tab before the GLSP connector
 * handles an external navigation target.
 */
export function reuseOpenTextEditorColumn(message: unknown): unknown {
  if (!ActionMessage.is(message) || !NavigateToExternalTargetAction.is(message.action)) {
    return message;
  }

  const target = message.action.target;
  const openerOptionsValue = target.args?.[JSON_OPENER_OPTIONS];
  if (openerOptionsValue === undefined) {
    return message;
  }

  let openerOptions: Record<string, unknown>;
  try {
    const parsed = JSON.parse(openerOptionsValue.toString());
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      return message;
    }
    openerOptions = parsed as Record<string, unknown>;
  } catch {
    return message;
  }

  let targetUri: vscode.Uri;
  try {
    targetUri = vscode.Uri.parse(target.uri);
  } catch {
    return message;
  }

  const existingColumn = findOpenTextEditorColumn(targetUri);
  if (existingColumn === undefined) {
    return message;
  }

  return {
    ...message,
    action: {
      ...message.action,
      target: {
        ...target,
        args: {
          ...(target.args ?? {}),
          [JSON_OPENER_OPTIONS]: JSON.stringify({
            ...openerOptions,
            viewColumn: existingColumn
          })
        }
      }
    }
  };
}

function findOpenTextEditorColumn(uri: vscode.Uri): vscode.ViewColumn | undefined {
  const activeEditor = vscode.window.activeTextEditor;
  if (activeEditor && sameUri(activeEditor.document.uri, uri)) {
    return activeEditor.viewColumn;
  }

  for (const editor of vscode.window.visibleTextEditors) {
    if (sameUri(editor.document.uri, uri)) {
      return editor.viewColumn;
    }
  }

  for (const group of vscode.window.tabGroups.all) {
    for (const tab of group.tabs) {
      if (isTextTabForUri(tab.input, uri)) {
        return group.viewColumn;
      }
    }
  }

  return undefined;
}

function isTextTabForUri(input: unknown, uri: vscode.Uri): boolean {
  if (input instanceof vscode.TabInputText) {
    return sameUri(input.uri, uri);
  }
  if (input instanceof vscode.TabInputTextDiff) {
    return sameUri(input.modified, uri);
  }
  return false;
}

function sameUri(left: vscode.Uri, right: vscode.Uri): boolean {
  return left.toString() === right.toString();
}
