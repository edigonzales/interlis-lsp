import { expect } from 'chai';
import type * as vscode from 'vscode';
import { RequestModelAction } from '@eclipse-glsp/protocol';
import { isInterlisDocument, refreshDiagramForDocument } from '../glsp/diagram-refresh';

describe('diagram-refresh helpers', () => {
  describe('isInterlisDocument', () => {
    const uri = { fsPath: '/tmp/example.ili' } as unknown as vscode.Uri;

    it('accepts documents with the interlis language id', () => {
      expect(isInterlisDocument({ languageId: 'interlis', uri })).to.be.true;
      expect(isInterlisDocument({ languageId: 'INTERLIS', uri })).to.be.true;
    });

    it('accepts documents with the .ili extension when the language id differs', () => {
      expect(isInterlisDocument({ languageId: 'plaintext', uri })).to.be.true;
    });

    it('accepts documents with uppercase .ILI extensions', () => {
      const uppercase = { fsPath: '/tmp/EXAMPLE.ILI' } as unknown as vscode.Uri;
      expect(isInterlisDocument({ languageId: 'plaintext', uri: uppercase })).to.be.true;
    });

    it('rejects non-interlis documents', () => {
      const otherUri = { fsPath: '/tmp/example.txt' } as unknown as vscode.Uri;
      expect(isInterlisDocument({ languageId: 'plaintext', uri: otherUri })).to.be.false;
    });
  });

  describe('refreshDiagramForDocument', () => {
    it('dispatches a RequestModelAction for every matching client', () => {
      const dispatched: RequestModelAction[] = [];
      const recipients: (string | undefined)[] = [];
      const dispatcher = {
        dispatchAction: (action: unknown, clientId?: string) => {
          dispatched.push(action as RequestModelAction);
          recipients.push(clientId);
        }
      };
      const registry = {
        getClientIdsForDocument: () => ['a', 'b']
      };
      const uri = { toString: () => 'file:///tmp/example.ili' } as unknown as vscode.Uri;

      const count = refreshDiagramForDocument(dispatcher, registry, uri);

      expect(count).to.equal(2);
      expect(dispatched).to.have.lengthOf(2);
      expect(recipients).to.deep.equal(['a', 'b']);
      dispatched.forEach(action => {
        expect(action.kind).to.equal(RequestModelAction.KIND);
        expect(action.options).to.deep.equal({ sourceUri: 'file:///tmp/example.ili' });
        expect(action.requestId).to.be.a('string').and.to.not.equal('');
      });
    });

    it('does nothing when no diagrams are registered for the document', () => {
      const dispatcher = {
        dispatchAction: () => {
          throw new Error('should not be called');
        }
      };
      const registry = {
        getClientIdsForDocument: () => [] as string[]
      };
      const uri = { toString: () => 'file:///tmp/example.ili' } as unknown as vscode.Uri;

      const count = refreshDiagramForDocument(dispatcher, registry, uri);
      expect(count).to.equal(0);
    });
  });
});
