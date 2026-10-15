# Provenance and limits

This repository extracts the external harness from the BAND service-task
project at commit `6d084f1`, after completion of the C++-equivalent coatjava
port and expanded validation. The Java and C++ revisions are pinned in
`versions.json`; the original repositories remain separate.

The source drivers/comparators were retained. Public packaging adds a
synthetic schema seed containing one empty event and configurable build/run/setup commands.
The case generator now takes an explicit output directory. The calibration
converter takes an explicit C++ source directory and refuses to overwrite
an existing output. These changes do not alter the reference equations or
the production Java implementation.

## Earlier service-task evidence

The earlier JLab campaign reported zero C++/Java mismatches for:

- 119,810 original RGB comparison records (4,116 bars).
- 4,296 realistic-MC/small-low-energy records (145 bars).
- 21,403 expanded low-energy records (193 bars across 87 channels).
- 4,590 timing-free workload records (1,307 bars).

These workloads include metadata, reuse and overlapping prefixes. They must
not be added as independent physics events. Their private event payloads and
site-specific receipts are not published here. This repository's own fresh
verification is documented separately in `validation/`.

## Boundaries

The production bank interface, CCDB assignments/run validity, post-EBTB
selection service, missing-timing policy and calibrated standalone-laser
comparison remain open. A finite start-time sentinel is reference behavior,
not measured physical time. The tests do not establish neutron purity or
full-detector reconstruction-chain consistency.

The pinned dependency repositories contain their own upstream contributions
and notices. They are cloned separately; no upstream source, calibration
dataset, technical note or experimental HIPO is vendored here.
