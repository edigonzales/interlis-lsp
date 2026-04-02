#!/usr/bin/env python3
import argparse
import json
import subprocess
import sys
import threading
import time


def encode_message(payload: dict) -> bytes:
    body = json.dumps(payload).encode("utf-8")
    header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
    return header + body


def read_message(stream) -> dict:
    headers = {}
    while True:
        line = stream.readline()
        if not line:
            raise RuntimeError("LSP server closed stdout before sending a full message.")
        if line in (b"\r\n", b"\n"):
            break
        decoded = line.decode("ascii", errors="strict").strip()
        if not decoded:
            break
        name, value = decoded.split(":", 1)
        headers[name.strip().lower()] = value.strip()

    content_length = headers.get("content-length")
    if content_length is None:
        raise RuntimeError(f"Missing Content-Length header in LSP response: {headers!r}")

    body = stream.read(int(content_length))
    if len(body) != int(content_length):
        raise RuntimeError("LSP server closed stdout before sending the full response body.")
    return json.loads(body.decode("utf-8"))


def read_response(stream, request_id: int) -> dict:
    while True:
        message = read_message(stream)
        if message.get("id") == request_id:
            return message
        if "method" in message:
            print(f"ignoring notification: {message['method']}")
            continue
        raise RuntimeError(f"Unexpected LSP message while waiting for response {request_id}: {message!r}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke-test the INTERLIS LSP stdio bootstrap.")
    parser.add_argument("--java", required=True, help="Path to the Java executable")
    parser.add_argument("--jar", required=True, help="Path to the fat JAR")
    parser.add_argument("--timeout", type=float, default=20.0, help="Timeout in seconds")
    args = parser.parse_args()

    process = subprocess.Popen(
        [args.java, "-jar", args.jar],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    assert process.stdin is not None
    assert process.stdout is not None
    assert process.stderr is not None

    stderr_lines = []

    def capture_stderr() -> None:
        for line in iter(process.stderr.readline, b""):
            stderr_lines.append(line.decode("utf-8", errors="replace").rstrip())

    stderr_thread = threading.Thread(target=capture_stderr, daemon=True)
    stderr_thread.start()

    try:
        initialize = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "processId": None,
                "clientInfo": {"name": "github-action-smoke", "version": "1"},
                "rootUri": None,
                "capabilities": {},
            },
        }
        process.stdin.write(encode_message(initialize))
        process.stdin.flush()

        started = time.time()
        initialize_response = read_response(process.stdout, 1)
        if initialize_response.get("id") != 1 or "result" not in initialize_response:
            raise RuntimeError(f"Unexpected initialize response: {initialize_response!r}")
        print(f"initialize ok after {time.time() - started:.2f}s")

        initialized = {"jsonrpc": "2.0", "method": "initialized", "params": {}}
        process.stdin.write(encode_message(initialized))
        process.stdin.flush()

        shutdown = {"jsonrpc": "2.0", "id": 2, "method": "shutdown", "params": None}
        process.stdin.write(encode_message(shutdown))
        process.stdin.flush()

        shutdown_response = read_response(process.stdout, 2)
        if shutdown_response.get("id") != 2 or "result" not in shutdown_response:
            raise RuntimeError(f"Unexpected shutdown response: {shutdown_response!r}")
        print("shutdown ok")

        exit_notification = {"jsonrpc": "2.0", "method": "exit", "params": None}
        process.stdin.write(encode_message(exit_notification))
        process.stdin.flush()
        process.stdin.close()

        try:
            return_code = process.wait(timeout=5)
            if return_code != 0:
                raise RuntimeError(f"LSP process exited with code {return_code}")
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)

        if stderr_lines:
            print("stderr during smoke test:", file=sys.stderr)
            for line in stderr_lines:
                print(line, file=sys.stderr)
        return 0
    except Exception as exc:
        print(f"Smoke test failed: {exc}", file=sys.stderr)
        if stderr_lines:
            print("captured stderr:", file=sys.stderr)
            for line in stderr_lines:
                print(line, file=sys.stderr)
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
        return 1
    finally:
        stderr_thread.join(timeout=1)


if __name__ == "__main__":
    sys.exit(main())
