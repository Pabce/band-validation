# Verification of the public packaging

This directory contains a small, sanitized summary of checks run with the
standalone checkout on JLab. It intentionally excludes site paths, raw event
data, event-level streams and private environment information. Full local
logs/manifests remain under the ignored build/results directories.

The historical service-task campaign is described separately in
`docs/PROVENANCE.md`. A synthetic self-test does not replace realistic data,
MC or calibrated-laser validation.

## Standalone checkout verified on JLab, 2026-10-15

The focused Java reactor and all external drivers compiled successfully.
C++ dependency libraries were built in a new harness build directory. The
check registered the existing clean pinned source checkouts and used the
existing Maven cache; it did not test downloading every dependency into an
empty cache. Maven dependency-classpath resolution was exercised.

The synthetic self-test passed:

| Check | Coverage | Result |
| --- | --- | --- |
| Calibrated selector parity | 49 named + 10,000 generated cases | Exact match |
| Reconstruction contracts | 21 | Pass |
| Neutron integration contracts | 17 | Pass |
| Synthetic raw data / MC | 147 + 147 records | Zero mismatches |
| Run transitions in one Java engine | 6 records | Zero mismatches |
| Timing-free synthetic data / MC | 147 + 147 records | Zero mismatches |
| Closed-file HIPO readback and links | Compared Java outputs | Pass |

The schema seed contains one empty synthetic event: a dictionary-only file
has no data record and fails trailer scanning in the installed HIPO reader.
No experimental event is needed to generate the synthetic suite.

The real-data smoke test compared 10,000 records from RGB run 6363 in ten
chunks: 320 eligible hits and 206 accepted neutron events in each language,
with zero mismatches and successful HIPO readback. The frozen original
input was reused; neither it nor event-level outputs are published.

Repeating the synthetic command reused all 39 completed subprocess phases
without launching new reconstruction processes. Comparators were rerun
over the saved streams.

[`jlab-validation.json`](jlab-validation.json) records the source hashes,
pins, counts and sanitized results for this packaging verification.

The real-data repeat also reused all 31 completed subprocess phases; no
new C++/Java reconstruction or readback processes were launched.
