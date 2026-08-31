#!/usr/bin/env python3
"""Fail closed when a Point load-test textual bundle contains sensitive data."""

import argparse
import base64
import json
import os
import re
import stat
import sys
from pathlib import Path

SUPPORTED_EXTENSIONS = {".json", ".log", ".txt"}
JWT_CANDIDATE = re.compile(
    r"(?<![A-Za-z0-9_-])([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)(?![A-Za-z0-9_-])"
)
UUID = re.compile(r"(?<![0-9a-fA-F])[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?![0-9a-fA-F])")
VALUE = r'(?:"([^"]*)"|\'([^\']*)\'|([^\s,}\]\r\n]+))'
AUTHORIZATION = re.compile(r"(?:[\"']authorization[\"']|\bauthorization\b)\s*[:=]\s*" + VALUE, re.IGNORECASE)
SENSITIVE_KEY = re.compile(r"[\"']?(accessToken|access_token|memberUuid|member_uuid)[\"']?\s*[:=]", re.IGNORECASE)
SETUP_DATA = re.compile(r"[\"']?setup_data[\"']?\s*[:=]", re.IGNORECASE)
BEARER = re.compile(r"\bbearer\b[ \t]+(?:\"[^\"]+\"|\'[^\']+\'|(?=[^ \t\r\n\"\']))", re.IGNORECASE)


def value_is_present(value):
    value = value.strip().strip("\"' ,}]\t")
    return bool(value) and value.lower() != "bearer"


def is_jwt(candidate):
    header = candidate.group(1)
    try:
        decoded = base64.urlsafe_b64decode(header + "=" * (-len(header) % 4))
        parsed = json.loads(decoded.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        return False
    return isinstance(parsed, dict) and isinstance(parsed.get("alg"), str) and bool(parsed["alg"].strip())


def detectors(text):
    found = set()
    if any(is_jwt(candidate) for candidate in JWT_CANDIDATE.finditer(text)):
        found.add("jwt")
    if any(value_is_present(next(value for value in match.groups() if value is not None)) for match in AUTHORIZATION.finditer(text)):
        found.add("authorization")
    if BEARER.search(text):
        found.add("bearer")
    for match in SENSITIVE_KEY.finditer(text):
        found.add("access-token" if match.group(1).lower().startswith("access") else "member-uuid")
    if SETUP_DATA.search(text):
        found.add("setup_data")
    if UUID.search(text):
        found.add("uuid")
    return sorted(found)


def is_text(text):
    return not any(
        ord(character) < 32 and character not in "\t\n\r"
        or 127 <= ord(character) <= 159
        for character in text
    )


def inspect(root):
    if not root.is_dir() or root.is_symlink():
        print("artifact-safety: <input>: unreadable-directory", file=sys.stderr)
        return 2

    failures = []

    def record_walk_error(error):
        path = Path(error.filename) if error.filename else root
        try:
            relative = path.relative_to(root).as_posix()
        except ValueError:
            relative = "."
        failures.append((relative, "unreadable-directory"))

    for current, directories, files in os.walk(root, followlinks=False, onerror=record_walk_error):
        current_path = Path(current)
        for name in directories + files:
            path = current_path / name
            relative = path.relative_to(root).as_posix()
            try:
                mode = path.lstat().st_mode
            except OSError:
                failures.append((relative, "unreadable-directory" if name in directories else "unreadable"))
                continue
            if stat.S_ISLNK(mode):
                failures.append((relative, "symlink"))
            elif name in files and not stat.S_ISREG(mode):
                failures.append((relative, "unsupported-file"))

        directories[:] = [name for name in directories if not (current_path / name).is_symlink()]
        for name in files:
            path = current_path / name
            try:
                mode = path.lstat().st_mode
            except OSError:
                continue
            if not stat.S_ISREG(mode) or stat.S_ISLNK(mode):
                continue
            relative = path.relative_to(root).as_posix()
            if path.suffix not in SUPPORTED_EXTENSIONS:
                failures.append((relative, "unsupported-extension"))
                continue
            try:
                text = path.read_bytes().decode("utf-8")
            except (OSError, UnicodeDecodeError):
                failures.append((relative, "unreadable-text"))
                continue
            if not is_text(text):
                failures.append((relative, "binary-content"))
                continue
            failures.extend((relative, detector) for detector in detectors(text))

    for relative, detector in sorted(set(failures)):
        print(f"artifact-safety: {relative}: {detector}", file=sys.stderr)
    return 1 if failures else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase_directory", type=Path)
    return inspect(parser.parse_args().phase_directory)


if __name__ == "__main__":
    raise SystemExit(main())
