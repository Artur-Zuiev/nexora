#!/usr/bin/env python3
"""Local Docker Compose PostgreSQL backup/verification/restore for Nexora.

Restore always targets a NEW database. Never overwrites existing databases.
Requires Python 3.10+, Docker Compose, and the local disposable Nexora stack.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ROOT / "docker-compose.yml"
DB_USER = "nexora"
SOURCE_DB = "nexora"
NAME = re.compile(r"[a-z][a-z0-9_]{0,62}\Z")


def docker(*args: str) -> list[str]:
    return ["docker", "compose", "-f", str(COMPOSE), "exec", "-T", "postgres", *args]


def execute(command: list[str], *, stdin=None, stdout=None) -> subprocess.CompletedProcess:
    return subprocess.run(command, stdin=stdin, stdout=stdout, stderr=subprocess.PIPE,
                          check=False)


def fail(message: str) -> None:
    raise RuntimeError(message)


def checksum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_archive(path: Path) -> str:
    if not path.is_file() or path.stat().st_size < 32:
        fail("Backup file is missing or too small.")
    expected = path.with_suffix(path.suffix + ".sha256")
    observed = checksum(path)
    if expected.exists():
        stored = expected.read_text(encoding="ascii").strip()
        if observed != stored:
            fail("Checksum mismatch: backup may be corrupted or altered.")
    with path.open("rb") as file:
        result = execute(docker("pg_restore", "--list"), stdin=file)
    if result.returncode != 0:
        fail("PostgreSQL cannot read this archive: " + result.stderr.decode(errors="replace")[-600:])
    return observed


def backup(output: Path) -> None:
    output = output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        fail("Refusing to overwrite existing backup: " + str(output))
    fd, temporary = tempfile.mkstemp(prefix=".nexora-backup-", dir=output.parent)
    try:
        os.chmod(temporary, 0o600)
        with os.fdopen(fd, "wb") as stream:
            result = execute(docker("pg_dump", "-U", DB_USER, "-d", SOURCE_DB,
                                    "--format=custom", "--no-owner", "--no-privileges"),
                             stdout=stream)
        if result.returncode != 0:
            fail("pg_dump failed: " + result.stderr.decode(errors="replace")[-600:])
        check_archive(Path(temporary))
        os.replace(temporary, output)
        digest = checksum(output)
        sidecar = output.with_suffix(output.suffix + ".sha256")
        sidecar.write_text(digest + "\n", encoding="ascii")
        os.chmod(sidecar, 0o600)
        print(f"Backup verified: {output} ({output.stat().st_size} bytes)")
        print("SHA-256:", digest)
        print("WARNING: contains account password hashes and customer records; store privately.")
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def restore(archive: Path, database: str, confirmation: str) -> None:
    archive = archive.expanduser().resolve()
    if not NAME.fullmatch(database):
        fail("Destination database name must start with a lowercase letter and contain only a-z, 0-9, _.")
    if database in {"postgres", "template0", "template1", SOURCE_DB}:
        fail("Refusing to restore into a protected or live Nexora database.")
    if confirmation != "RESTORE:" + database:
        fail("Explicit confirmation required: --confirm RESTORE:" + database)
    digest = check_archive(archive)

    query = subprocess.run(docker("psql", "-U", DB_USER, "-d", "postgres", "-tAc",
                                   f"SELECT 1 FROM pg_database WHERE datname = '{database}'"),
                           capture_output=True, check=False)
    if query.returncode != 0:
        fail("Cannot verify whether the destination already exists.")
    if query.stdout.strip():
        fail("Destination already exists; refusing to overwrite or drop anything.")

    creation = execute(docker("createdb", "-U", DB_USER, "--template=template0", database))
    if creation.returncode != 0:
        fail("Could not create empty destination: " + creation.stderr.decode(errors="replace")[-600:])
    with archive.open("rb") as file:
        result = execute(docker("pg_restore", "-U", DB_USER, "-d", database,
                                "--no-owner", "--no-privileges", "--exit-on-error"),
                         stdin=file)
    if result.returncode != 0:
        fail("Restore failed; a PARTIAL NEW database remains for inspection. "
             "Nothing was deleted: " + result.stderr.decode(errors="replace")[-600:])
    print(f"Restored {database} from {archive.name} (SHA-256: {digest})")
    print("The original nexora database was NOT modified. Start no app against this restored copy until reviewed.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    b = commands.add_parser("backup")
    b.add_argument("--output", type=Path, required=True)
    v = commands.add_parser("verify")
    v.add_argument("--input", type=Path, required=True)
    r = commands.add_parser("restore")
    r.add_argument("--input", type=Path, required=True)
    r.add_argument("--database", required=True)
    r.add_argument("--confirm", required=True)
    args = parser.parse_args()
    try:
        if args.action == "backup":
            backup(args.output)
        elif args.action == "verify":
            digest = check_archive(args.input.expanduser().resolve())
            print("Archive integrity verified (SHA-256:", digest + ")")
        else:
            restore(args.input, args.database, args.confirm)
        return 0
    except (OSError, RuntimeError) as error:
        print("ERROR:", error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
