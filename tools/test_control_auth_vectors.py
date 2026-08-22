"""Golden vectors for remote-config auth — must match Kotlin ControlAuthTest and firmware PacketAuth."""

from __future__ import annotations

import hashlib
import hmac
import unittest


def pbkdf2_hmac_sha256_block(password: bytes, salt: bytes, iterations: int) -> bytes:
    """Single 32-byte PBKDF2 block (matches ChatKeyDerivation / PacketAuth)."""
    first = salt + b"\x00\x00\x00\x01"
    block = hmac.new(password, first, hashlib.sha256).digest()
    result = bytearray(block)
    for _ in range(iterations - 1):
        block = hmac.new(password, block, hashlib.sha256).digest()
        for i, b in enumerate(block):
            result[i] ^= b
    return bytes(result)


# Shared fixture: sender=1, recipient=2, session=0x0102030405060708, counter=7,
# NodeConfig name=Relay SF9 BW125 TX22 role=1 telem=60 screen=30 posPrec=100.
V2_CANONICAL_HEX = "414d4346473201000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000"
V2_TAG_HEX = "165a8fa5f809a08d3063ea46c78c64e4"
V3_CANONICAL_HEX = "414d4346473301000000020000000807060504030201070000000552656c6179090000000000fa421600000000000000010000003c0000001e0000000064000000000000000000000000000000000000000000000000000000000000000000000000000000"
V3_TAG_HEX = "0cd1d291935a725a4ea210f3ac3dddbf"
PASSWORD = b"admin-key"
SALT = b"AMCTRL1\x00"
ITERATIONS = 120_000


class ControlAuthVectorTest(unittest.TestCase):
    def test_v2_tag_matches_published_vector(self) -> None:
        canon = bytes.fromhex(V2_CANONICAL_HEX)
        tag = hmac.new(PASSWORD, canon, hashlib.sha256).digest()[:16]
        self.assertEqual(V2_TAG_HEX, tag.hex())

    def test_v3_tag_matches_published_vector(self) -> None:
        key = pbkdf2_hmac_sha256_block(PASSWORD, SALT, ITERATIONS)
        canon = bytes.fromhex(V3_CANONICAL_HEX)
        self.assertEqual(b"AMCFG3", canon[:6])
        tag = hmac.new(key, canon, hashlib.sha256).digest()[:16]
        self.assertEqual(V3_TAG_HEX, tag.hex())

    def test_v3_canonical_is_v2_with_domain_swap(self) -> None:
        v2 = bytes.fromhex(V2_CANONICAL_HEX)
        v3 = bytes.fromhex(V3_CANONICAL_HEX)
        self.assertEqual(b"AMCFG2", v2[:6])
        self.assertEqual(b"AMCFG3" + v2[6:], v3)


if __name__ == "__main__":
    unittest.main()
