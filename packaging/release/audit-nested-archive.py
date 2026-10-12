#!/usr/bin/env python3
# Source-checkout package audit; deliberately not installed by the DEB.
"""Fail-closed credential, key, and deployment-identifier audit for ZIP/JAR payloads."""

from __future__ import annotations

import io
import hashlib
import re
import sys
import zipfile
from pathlib import PurePosixPath

from private_key_material import contains_private_key_material


MAX_ENTRIES = 100_000
MAX_TOTAL_UNCOMPRESSED = 2 * 1024 * 1024 * 1024
MAX_NESTED_ARCHIVE = 512 * 1024 * 1024
MAX_DEPTH = 3

FORBIDDEN_PATH = re.compile(
    r"(^|/)(firebase-service-account\.json|\.env\.secrets|"
    r"application-secrets\.ya?ml|[^/]*(client-secret|provider-credentials)"
    r"[^/]*\.json|id_(rsa|dsa|ecdsa|ed25519)|"
    r"deployment-private-key\.pem|registry-demo-signing-key\.pem|"
    r"[^/]*\.(p12|pfx|jks|keystore))$",
    re.IGNORECASE,
)
PERSONAL_EMAIL = re.compile(
    rb"[A-Z0-9._%+-]+@(?:gmail\.com|googlemail\.com|outlook\.com|"
    rb"hotmail\.com|live\.com|yahoo\.[A-Z.]+|icloud\.com|me\.com|"
    rb"proton(?:mail)?\.com)",
    re.IGNORECASE,
)
PERSONAL_SOCIAL_PROFILE = re.compile(
    rb"https?://(?:[^/\"\s]+\.)?(?:"
    rb"instagram\.com/[^/\"\s][^\"\s]*|"
    rb"facebook\.com/[^/\"\s][^\"\s]*|"
    rb"youtube\.com/(?:@|channel/|c/|user/)[^/\"\s][^\"\s]*)",
    re.IGNORECASE,
)
PUBLIC_PRODUCT_SOCIAL_PROFILES = (
    b"https://www.instagram.com/myscoutee",
    b"https://www.facebook.com/myscoutee",
    b"https://www.youtube.com/@myscoutee",
    b"https://facebook.com/username",
    b"https://instagram.com/username",
    b"https://www.facebook.com/legal/terms",
)
FIXED_BOOTSTRAP_PASSWORD_HASH = re.compile(
    rb"operator[-_]bootstrap[-_]password[-_]sha256[=:][ \t]*[0-9a-f]{64}",
    re.IGNORECASE,
)
FIXED_BOOTSTRAP_EMAIL = re.compile(
    rb"operator[-_]bootstrap[-_]email[=:][ \t]*"
    rb"operator-[0-9a-f]{24}@deployment\.invalid",
    re.IGNORECASE,
)
TEXT_KEY_EXTENSIONS = {
    ".cfg",
    ".conf",
    ".env",
    ".json",
    ".key",
    ".p8",
    ".pem",
    ".properties",
    ".xml",
    ".yaml",
    ".yml",
}
NESTED_EXTENSIONS = {".jar", ".war", ".zip"}
PUBLIC_TRUSTSTORE_PATHS = {
    "com/google/api/client/googleapis/google.jks",
}
# google-api-client 2.7.2: verified PKCS12 containing 70 trusted certificate
# entries and no private keys. Pin the reviewed bytes; the filename alone
# must never exempt an arbitrary PKCS12 credential from the audit.
PUBLIC_TRUSTSTORE_SHA256 = {
    "com/google/api/client/googleapis/google.p12":
        "c98fba5f11f2f078ddf798fac0dcffa7e4a0ba1a9848e3e23f14210a506e827c",
}


class AuditBudget:
    def __init__(self) -> None:
        self.entries = 0
        self.uncompressed = 0

    def add(self, info: zipfile.ZipInfo) -> None:
        self.entries += 1
        self.uncompressed += info.file_size
        if self.entries > MAX_ENTRIES:
            raise ValueError("nested archive entry limit exceeded")
        if self.uncompressed > MAX_TOTAL_UNCOMPRESSED:
            raise ValueError("nested archive uncompressed-size limit exceeded")


def normalized_name(raw_name: str) -> str:
    name = raw_name.replace("\\", "/").lstrip("/")
    path = PurePosixPath(name)
    if any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError("unsafe nested archive member path")
    return str(path)


def contains_private_key_marker(entry: zipfile.ZipExtFile) -> bool:
    return contains_private_key_material(entry)


def contains_personal_email(entry: zipfile.ZipExtFile) -> bool:
    overlap = b""
    while True:
        chunk = entry.read(1024 * 1024)
        if not chunk:
            return False
        candidate = overlap + chunk
        if PERSONAL_EMAIL.search(candidate):
            return True
        overlap = candidate[-256:]


def contains_personal_social_profile(entry: zipfile.ZipExtFile) -> bool:
    overlap = b""
    while True:
        chunk = entry.read(1024 * 1024)
        if not chunk:
            return False
        candidate = overlap + chunk
        scrubbed = candidate
        for public_profile in PUBLIC_PRODUCT_SOCIAL_PROFILES:
            scrubbed = scrubbed.replace(public_profile, b"")
        if PERSONAL_SOCIAL_PROFILE.search(scrubbed):
            return True
        overlap = candidate[-512:]


def contains_fixed_bootstrap_password_hash(entry: zipfile.ZipExtFile) -> bool:
    overlap = b""
    while True:
        chunk = entry.read(1024 * 1024)
        if not chunk:
            return False
        candidate = overlap + chunk
        if FIXED_BOOTSTRAP_PASSWORD_HASH.search(candidate):
            return True
        overlap = candidate[-256:]


def contains_fixed_bootstrap_email(entry: zipfile.ZipExtFile) -> bool:
    overlap = b""
    while True:
        chunk = entry.read(1024 * 1024)
        if not chunk:
            return False
        candidate = overlap + chunk
        if FIXED_BOOTSTRAP_EMAIL.search(candidate):
            return True
        overlap = candidate[-256:]


def is_application_owned_text(name: str, suffix: str) -> bool:
    return (
        suffix in TEXT_KEY_EXTENSIONS
        and (
            name.startswith("BOOT-INF/classes/")
            or name.startswith("WEB-INF/classes/")
            or name.startswith("public/")
            or name.startswith("static/")
        )
    )


def audit_zip(
    source: str | io.BytesIO,
    label: str,
    budget: AuditBudget,
    depth: int,
) -> None:
    try:
        archive = zipfile.ZipFile(source)
    except (OSError, zipfile.BadZipFile) as exc:
        raise ValueError(f"invalid nested archive: {label}") from exc

    with archive:
        for info in archive.infolist():
            budget.add(info)
            name = normalized_name(info.filename)
            if info.is_dir():
                continue
            if FORBIDDEN_PATH.search(name) and name not in PUBLIC_TRUSTSTORE_PATHS:
                approved_digest = PUBLIC_TRUSTSTORE_SHA256.get(name)
                if (approved_digest is None or info.file_size > 1024 * 1024
                        or hashlib.sha256(archive.read(info)).hexdigest() != approved_digest):
                    raise ValueError(f"forbidden credential path in nested archive: {label}!/{name}")

            suffix = PurePosixPath(name).suffix.lower()
            if suffix in TEXT_KEY_EXTENSIONS:
                with archive.open(info, "r") as entry:
                    if contains_private_key_marker(entry):
                        raise ValueError(
                            f"private key material in nested archive: {label}!/{name}"
                        )
            if is_application_owned_text(name, suffix):
                with archive.open(info, "r") as entry:
                    if contains_personal_email(entry):
                        raise ValueError(
                            "personal deployment email in application archive: "
                            f"{label}!/{name}"
                        )
                with archive.open(info, "r") as entry:
                    if contains_personal_social_profile(entry):
                        raise ValueError(
                            "personal social-page URL in application archive: "
                            f"{label}!/{name}"
                        )
                with archive.open(info, "r") as entry:
                    if contains_fixed_bootstrap_password_hash(entry):
                        raise ValueError(
                            "fixed Operator bootstrap password digest in application archive: "
                            f"{label}!/{name}"
                        )
                with archive.open(info, "r") as entry:
                    if contains_fixed_bootstrap_email(entry):
                        raise ValueError(
                            "fixed Operator bootstrap subject in application archive: "
                            f"{label}!/{name}"
                        )

            if suffix not in NESTED_EXTENSIONS or depth >= MAX_DEPTH:
                continue
            if info.file_size > MAX_NESTED_ARCHIVE:
                raise ValueError(f"nested archive member is too large to audit: {label}!/{name}")
            with archive.open(info, "r") as entry:
                nested = io.BytesIO(entry.read())
            audit_zip(nested, f"{label}!/{name}", budget, depth + 1)


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: audit-nested-archive.py <zip-or-jar>", file=sys.stderr)
        return 2
    try:
        audit_zip(sys.argv[1], sys.argv[1], AuditBudget(), 0)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
