#!/usr/bin/env python3
"""Minimal smoke test that talks to the INTERLIS language server over stdio."""

from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys
import threading
import time
from typing import Any, Dict, Optional

DEFAULT_JAR = pathlib.Path('build/libs/interlis-lsp-0.0.LOCALBUILD-all.jar')
DEFAULT_MODEL = pathlib.Path('src/test/data/TestSuite_mod-0.ili')

def send_message(proc: subprocess.Popen[Any], payload: Dict[str, Any]) -> None:
    body = json.dumps(payload).encode('utf-8')
    header = f"Content-Length: {len(body)}\r\n\r\n".encode('ascii')
    proc.stdin.write(header + body)
    proc.stdin.flush()

def read_message(proc: subprocess.Popen[Any], timeout: float = 5.0) -> Optional[Dict[str, Any]]:
    deadline = time.time() + timeout
    buffer = b''
    while time.time() < deadline:
        chunk = proc.stdout.readline()
        if not chunk:
            time.sleep(0.01)
            continue
        buffer += chunk
        if b'\r\n\r\n' in buffer:
            break
    if b'\r\n\r\n' not in buffer:
        return None
    header, rest = buffer.split(b'\r\n\r\n', 1)
    headers: Dict[str, str] = {}
    for line in header.decode('utf-8').split('\r\n'):
        if ':' not in line:
            continue
        key, value = line.split(':', 1)
        headers[key.strip().lower()] = value.strip()
    length = int(headers.get('content-length', '0'))
    body = rest
    while len(body) < length:
        chunk = proc.stdout.read(length - len(body))
        if not chunk:
            break
        body += chunk
    if len(body) != length:
        return None
    return json.loads(body.decode('utf-8'))

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=pathlib.Path, default=DEFAULT_JAR,
                        help='Path to the fat JAR built via ./gradlew shadowJar.')
    parser.add_argument('--model', type=pathlib.Path, default=DEFAULT_MODEL,
                        help='Path to the INTERLIS .ili file to compile.')
    args = parser.parse_args()

    jar = args.jar
    if not jar.is_file():
        print(f"error: {jar} not found. Build it first with ./gradlew shadowJar", file=sys.stderr)
        return 1

    model_path = args.model.resolve()
    if not model_path.is_file():
        print(f"error: {model_path} is not a file", file=sys.stderr)
        return 1

    proc = subprocess.Popen(
        ['java', '-jar', str(jar)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0
    )

    stderr_lines: list[str] = []
    def collect_stderr() -> None:
        for raw_line in proc.stderr:
            stderr_lines.append(raw_line.decode('utf-8', errors='replace').rstrip())
    threading.Thread(target=collect_stderr, daemon=True).start()

    root_uri = model_path.parent.resolve().as_uri()
    model_uri = model_path.as_uri()

    send_message(proc, {
        'jsonrpc': '2.0',
        'id': 1,
        'method': 'initialize',
        'params': {
            'processId': None,
            'clientInfo': {'name': 'smoke-test', 'version': '1.0'},
            'rootUri': root_uri,
            'capabilities': {},
            'initializationOptions': {
                'modelRepositories': model_path.parent.resolve().as_uri(),
                'suppressRepositoryLogs': True
            }
        }
    })
    print('initialize ->', read_message(proc, timeout=10))

    text = model_path.read_text(encoding='utf-8')
    send_message(proc, {
        'jsonrpc': '2.0',
        'method': 'textDocument/didOpen',
        'params': {
            'textDocument': {
                'uri': model_uri,
                'languageId': 'interlis',
                'version': 1,
                'text': text
            }
        }
    })
    print('didOpen ->', read_message(proc, timeout=10))

    send_message(proc, {
        'jsonrpc': '2.0',
        'id': 2,
        'method': 'workspace/executeCommand',
        'params': {
            'command': 'interlis.compile',
            'arguments': [model_uri]
        }
    })

    while True:
        message = read_message(proc, timeout=5)
        if not message:
            break
        print('message ->', message)
        if message.get('id') == 2:
            break

    proc.terminate()
    proc.wait(timeout=5)
    if stderr_lines:
        print('\n[stderr]')
        for line in stderr_lines:
            print(line)
    return 0

if __name__ == '__main__':
    sys.exit(main())
