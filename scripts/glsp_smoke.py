#!/usr/bin/env python3
"""Smoke test that ensures the embedded GLSP server starts and reports itself."""

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


def send_message(proc: subprocess.Popen[Any], payload: Dict[str, Any]) -> None:
    body = json.dumps(payload).encode('utf-8')
    header = f"Content-Length: {len(body)}\r\n\r\n".encode('ascii')
    if proc.stdin is None:
        raise RuntimeError('server process does not expose stdin')
    proc.stdin.write(header + body)
    proc.stdin.flush()


def read_message(proc: subprocess.Popen[Any], timeout: float = 5.0) -> Optional[Dict[str, Any]]:
    if proc.stdout is None:
        raise RuntimeError('server process does not expose stdout')

    deadline = time.time() + timeout
    buffer = b''
    while time.time() < deadline:
        chunk = proc.stdout.readline()
        if not chunk:
            time.sleep(0.01)
            continue
        buffer += chunk
        if b"\r\n\r\n" in buffer:
            break

    if b"\r\n\r\n" not in buffer:
        return None

    header, rest = buffer.split(b"\r\n\r\n", 1)
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
    text = body.decode('utf-8')
    if not text.strip():
        return None

    return json.loads(text)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=pathlib.Path, default=DEFAULT_JAR,
                        help='Path to the fat JAR built via ./gradlew shadowJar.')
    args = parser.parse_args()

    jar = args.jar
    if not jar.is_file():
        print(f"error: {jar} not found. Build it first with ./gradlew shadowJar", file=sys.stderr)
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
        if proc.stderr is None:
            return
        for raw_line in proc.stderr:
            stderr_lines.append(raw_line.decode('utf-8', errors='replace').rstrip())

    threading.Thread(target=collect_stderr, daemon=True).start()

    send_message(proc, {
        'jsonrpc': '2.0',
        'id': 1,
        'method': 'initialize',
        'params': {
            'processId': None,
            'clientInfo': {'name': 'glsp-smoke', 'version': '1.0'},
            'capabilities': {},
            'rootUri': None,
            'initializationOptions': {
                'suppressRepositoryLogs': True
            }
        }
    })
    init_response = read_message(proc, timeout=10)
    print('initialize ->', init_response)

    send_message(proc, {
        'jsonrpc': '2.0',
        'method': 'initialized',
        'params': {}
    })

    time.sleep(1.0)

    info_result: Optional[Dict[str, Any]] = None
    for attempt in range(10):
        if proc.poll() is not None:
            break
        info_request_id = 2 + attempt
        send_message(proc, {
            'jsonrpc': '2.0',
            'id': info_request_id,
            'method': 'interlis/glspInfo',
            'params': {}
        })

        deadline = time.time() + 2
        while time.time() < deadline:
            message = read_message(proc, timeout=0.5)
            if not message:
                continue
            print('message ->', message)
            if message.get('id') == info_request_id:
                info_result = message.get('result')
                break
        if info_result:
            break
        time.sleep(0.5)

    if info_result is None:
        print('error: did not receive interlis/glspInfo response', file=sys.stderr)
        proc.terminate()
        return 1

    host = info_result.get('host')
    port = info_result.get('port')
    path = info_result.get('path')
    running = info_result.get('running')
    print(f"GLSP server running={running} at ws://{host}:{port}{path}")

    send_message(proc, {
        'jsonrpc': '2.0',
        'id': info_request_id + 1,
        'method': 'shutdown',
        'params': None
    })
    print('shutdown ->', read_message(proc, timeout=5))

    send_message(proc, {
        'jsonrpc': '2.0',
        'method': 'exit',
        'params': None
    })

    proc.wait(timeout=5)

    if stderr_lines:
        print('\n[stderr]')
        for line in stderr_lines:
            print(line)

    return 0 if running else 2


if __name__ == '__main__':
    sys.exit(main())
