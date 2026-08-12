# Veltrix Hom vNext checkpoint import bootstrap

This repository was empty when the Back-end Agent began importing the accepted local foundation checkpoint.

Original local checkpoint commit: `d47d0876eb2ee30a480549a89b42382b4a31d8ed`
Original bundle SHA-256: `86afa879d27b9bc3121eb869826dbccdb3706c2d8100cd622a72bd8d71a5c31c`
Original source ZIP SHA-256: `b5e8e74cfc02b873f092460591314cf88bcf7b125feffdd0a5392c8baf743b36`

Direct git transport from the original sandbox was unavailable due DNS; GitHub API transport was used instead.

## Sealed completion source transport recovery

The completion source manifest declares archive SHA-256:
`1b363898c79f8eb7a74f5c279bc7c5f015f18703538c81ae34afc8aca1cdb2dc`.

Audit found two single-character transport corruptions in `c02.b64` and `c09.b64`. Exact half-chunks already present in repository history plus independent manifest-hash recovery established the intended bytes. Commit `df3ac22c0f501098ff91989ea00dcc565f89d172` restored the canonical 12 chunks and removed temporary half-chunk transport files.

Post-repair verification:
- all `c00`..`c11` SHA-256 values match `ci/source-v2/MANIFEST.txt`;
- reconstructed archive SHA-256 matches `1b363898c79f8eb7a74f5c279bc7c5f015f18703538c81ae34afc8aca1cdb2dc`;
- `xz -t` passes;
- exact archive extracts successfully;
- deterministic local regression independently rerun after recovery: Core 48/48 + Foundation 57/57 = 105/105 PASS.

This provenance note intentionally triggers the remote Part 1 final gate from the repaired transport state.
