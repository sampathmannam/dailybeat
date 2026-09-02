#!/usr/bin/python3 -I
"""Require PatrolGrid ciphertext to have one exact OpenPGP recipient packet."""

from __future__ import annotations

import os
from pathlib import Path
import stat
import sys
from typing import BinaryIO, NoReturn


MAX_RECIPIENT_PACKET = 1024 * 1024
DISCARD_CHUNK_SIZE = 1024 * 1024
ALLOWED_ENCRYPTED_TAGS = {18, 20}  # SEIPD or AEAD; unauthenticated tag 9 is forbidden.


def stop(message: str) -> NoReturn:
    raise SystemExit(f"PatrolGrid OpenPGP packet check stopped: {message}")


def read_exact(source: BinaryIO, length: int) -> bytes:
    value = source.read(length)
    if len(value) != length:
        stop("ciphertext ended inside an OpenPGP packet")
    return value


def discard_exact(source: BinaryIO, length: int) -> None:
    """Consume a large packet body without trusting its declared size for allocation."""
    remaining = length
    while remaining:
        value = source.read(min(remaining, DISCARD_CHUNK_SIZE))
        if not value:
            stop("ciphertext ended inside an OpenPGP packet")
        remaining -= len(value)


def new_length(source: BinaryIO) -> tuple[int, bool]:
    first = read_exact(source, 1)[0]
    if first < 192:
        return first, False
    if first < 224:
        second = read_exact(source, 1)[0]
        return ((first - 192) << 8) + second + 192, False
    if first == 255:
        return int.from_bytes(read_exact(source, 4), "big"), False
    return 1 << (first & 0x1F), True


def packet_header(source: BinaryIO) -> tuple[int, int, bool]:
    first = read_exact(source, 1)[0]
    if not first & 0x80:
        stop("invalid OpenPGP packet header")
    if first & 0x40:
        length, partial = new_length(source)
        return first & 0x3F, length, partial
    tag = (first >> 2) & 0x0F
    length_type = first & 0x03
    if length_type == 3:
        stop("indeterminate old-format OpenPGP packets are forbidden")
    width = (1, 2, 4)[length_type]
    return tag, int.from_bytes(read_exact(source, width), "big"), False


def consume_final_packet(source: BinaryIO, length: int, partial: bool) -> None:
    while partial:
        discard_exact(source, length)
        length, partial = new_length(source)
    discard_exact(source, length)
    if source.read(1):
        stop("encrypted-data packet is not the final OpenPGP packet")


def verify(path: Path, expected_key_id: str) -> int:
    try:
        metadata = path.lstat()
    except OSError as error:
        stop(f"cannot inspect ciphertext: {error}")
    if (not stat.S_ISREG(metadata.st_mode) or stat.S_ISLNK(metadata.st_mode) or
            metadata.st_size <= 0):
        stop("ciphertext is not a non-empty regular file")
    if not expected_key_id.isascii() or len(expected_key_id) != 16 or any(
        character not in "0123456789ABCDEF" for character in expected_key_id
    ):
        stop("expected OpenPGP key id must be 16 uppercase hexadecimal characters")

    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    try:
        with os.fdopen(descriptor, "rb", closefd=False) as source:
            tag, length, partial = packet_header(source)
            if tag != 1 or partial or length > MAX_RECIPIENT_PACKET:
                stop("ciphertext must begin with one bounded public-key recipient packet")
            recipient = read_exact(source, length)
            if (len(recipient) < 10 or recipient[0] != 3 or
                    recipient[1:9].hex().upper() != expected_key_id or recipient[9] != 18):
                stop("public-key recipient is not the exact pinned ECDH subkey")
            encrypted_tag, encrypted_length, encrypted_partial = packet_header(source)
            if encrypted_tag not in ALLOWED_ENCRYPTED_TAGS:
                stop("ciphertext has an additional recipient or unauthenticated data packet")
            consume_final_packet(source, encrypted_length, encrypted_partial)
    finally:
        os.close(descriptor)
    return encrypted_tag


def main(arguments: list[str]) -> int:
    if len(arguments) != 2:
        stop("usage: verify_patrolgrid_openpgp_packets.py <ciphertext> <16-hex-key-id>")
    tag = verify(Path(arguments[0]), arguments[1])
    print(f"OPENPGP_RECIPIENT_OK {arguments[1]} DATA_TAG {tag}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
