# BAND C++ / coatjava validation harness

External comparison tools for the CLAS12 BAND C++-to-Java port. The drivers
execute the original `BANDReco` / `goodNeutronEvent` and the actual Java
`BANDEngine`; they compare PMTs, reconstructed bars, neutron eligibility and
selection, then reopen the Java HIPO output to verify serialization and links.

**Start with the synthetic self-test below. It needs no private event data.**
Real HIPO files, generated output, private site configuration, credentials and
the two main repositories are not included in this repository.

## Pinned implementations

| Implementation | Revision |
| --- | --- |
| [Java port](https://github.com/Pabce/coatjava/tree/bb16902238d802e16d7113556a6990ba37347f5b) | `bb16902238d802e16d7113556a6990ba37347f5b` |
| [C++ reference](https://github.com/Pabce/bandsoft_tools/tree/1749c49d5165a8b66e949607eb3b3176427ed04b) | `1749c49d5165a8b66e949607eb3b3176427ed04b` |

The pins are in [`versions.json`](versions.json). Current C++ behavior governs
the comparison, including differences from the 2022 technote. The Java port
is opt-in and its neutron banks are provisional; this harness does not
install CCDB assignments or activate a production reconstruction chain.

## Requirements and execution environment

Build and run on **JLab/ifarm with the existing CLAS12 software stack**. The
tested environment uses Java 21, Maven 3.9, GCC 11, ROOT 6.38, CMake 3.29,
Python 3.13, LZ4, MySQL development libraries and QADB 3.4.1 headers. Python
tools use only the standard library. Older Python needs at least 3.9.

Use your site's normal CLAS12 setup in a task-local shell. For the original
JLab environment this is:

```tcsh
tcsh
source ~/rgm_env_ifarm.csh
```

That setup file is personal/site configuration, not supplied here. An
equivalent setup must make `java`, `javac`, `mvn`, `c++`, `cmake`, `root-config`
and `python3` available, and set `QADB` to the installed QADB tree. The C++
build also uses the reference repository's bundled HIPO implementation.

Put the checkout, build/cache and results under work/project storage rather
than a small home directory. All examples below are run from the repository
root. Builds/tests are deliberately refused on macOS.

## Clone and build

```tcsh
# First cd to a work/project directory where you have write access.
git clone https://github.com/Pabce/band-validation.git
cd band-validation
python3 scripts/setup.py
python3 scripts/build.py --jobs 2
```

`setup.py` clones the two pinned dependencies under ignored `deps/`, checks
their exact commits and cleanliness, and writes ignored `local-config.json`.
It never resets an existing checkout. To use existing pinned checkouts:

```tcsh
python3 scripts/setup.py --coatjava /path/to/coatjava --bandsoft /path/to/bandsoft_tools
python3 scripts/build.py --jobs 2 --maven-cache /path/to/work/maven-cache
```

The build installs the focused Java BAND Maven reactor and compiles all Java
drivers. It builds C++ dependency libraries, then compiles the **unchanged
upstream** `bandreco.cpp` and `readhipo_helper.cpp` with the upstream CMake
flags. The upstream analysis executables are not required. No patch to
either source repository is applied. Maven writes normal target products
and the selected cache; C++ products live in this harness's build directory.

For an existing compatible build, optional `--reuse-cpp-build /path/to/build/bandsoft`
reuses its libraries/flags, and `--java-dependencies /path/to/dependencies.txt`
reuses a Maven dependency classpath. The source pins and CMake source location
are checked. `build/manifest.json` records source/runtime artifact hashes,
versions and the explicit classpath. The installed coatjava selected by your
shell does **not** override this checkout's newly built BAND classes.

## Self-test without experimental data

```tcsh
python3 scripts/validate.py selftest --output results/selftest
```

The suite generates a schema-seed HIPO with one empty event and synthetic raw inputs using
the calibration files in the pinned public C++ reference. It runs:

- 49 named boundary/topology cases plus 10,000 deterministic selector cases.
- 21 reconstruction contracts and 17 neutron integration contracts.
- Comparator mutation/unit tests.
- 147 data and 147 MC raw-bank fixtures, covering all physical bar channels
  and the hit/laser multiplicity boundaries, plus six run-transition events.
- The 294 data/MC fixtures again with REC timing removed, using the hit-only
  path; no start time or neutron decision is fabricated.
- Closed-file HIPO readback for compared output, including source indices.

The final `results/selftest/summary.json` reports zero mismatches on success.
Synthetic MC is a behavior test, not realistic simulation coverage. See
[`validation/README.md`](validation/README.md) for this public checkout's JLab
verification and the distinction from the earlier service-task campaign.

## Compare your own HIPO file

Supply a **single-run** HIPO file containing raw `BAND::adc`, `BAND::tdc`,
`RUN::config` and, for neutron selection, one finite `REC::Event.startTime`
per physics event. The dictionaries must preserve the reference field order.
Empty run-zero metadata is handled separately. Do not use a DST that has
dropped the raw BAND banks. Inputs are read, never modified.

```tcsh
python3 scripts/validate.py compare \
  --input /path/to/input.hipo --run 6363 --period 0 \
  --count 10000 --chunk-size 1000 --output results/run6363
```

`--count` is the exact number of records to compare, including metadata;
`--start` defaults to zero. A short input fails rather than claiming the
requested coverage. Set `--mc` for MC, and pass its actual run number (often
11) and the intended RGB period. The Java snapshot is restricted to that
exact run. A mode/run inconsistency is an error, not a fallback.

The CLI currently exposes RGB periods **0-3**: spring 2019 10.6/10.2 GeV,
fall 2019 10.4 GeV, and fall 2019 low energy, respectively. The converter
retains later-period reference routing for expert use, but broad validity
and later run groups are not established by this release.

Constants are converted from the pinned C++ files into an external
`IndexedTable` adapter. No live reconstruction CCDB access is needed after
building. `tables.json`, copied source files and hashes, and `runs.json`
record the exact snapshot. This tests code equivalence under common constants;
it does not prove that these constants are appropriate for every chosen run.

Neutron mode deliberately preserves the reference's acceptance of finite
start time **-1000**. It does not establish physical timing validity. Missing
or nonfinite timing fails; there is no implicit timing substitution/filter.

## Timing-free hit comparison and laser preparation

If your input contains REC timing, first make a separate timing-free copy:

```tcsh
python3 scripts/validate.py strip-timing \
  --input /path/to/input.hipo --output results/timing-free-input
python3 scripts/validate.py compare --mode hits \
  --input results/timing-free-input/input-without-timing.hipo \
  --run 6363 --period 0 --count 10000 --output results/hits6363
```

Already timing-free decoded inputs can be passed directly to `--mode hits`.
Java requires REC::Event to be absent and verifies no neutron/timing bank is
created. This compares reconstruction, not `storeHits`/neutron selection.

Standalone laser runs require a **reviewed calibration choice** before using
`--reviewed-standalone-period`. That flag only relaxes the C++ run/period
guard in hit-only mode; it does not infer a valid snapshot or fix headers.
The raw laser decoding audits are supplied as expert tools, not an automatic
decoder pipeline. Calibrated real-laser parity remains pending review.

## Results, resume and reproducibility

Each comparison chunk writes C++/Java JSONL, Java HIPO, a comparison summary,
logs and phase receipts. Drivers print event/case counts; the launcher reports
elapsed time and log growth every 15 seconds during quiet processes.

Repeat the **same command** to resume. Completed phases are reused only when
their command and output hashes match. Source, input, calibration or runtime
changes require a rebuild and/or a new output directory. An interrupted
unreceipted phase is preserved under `incomplete/` before rerunning that phase;
completed chunks are not regenerated. Keep the work filesystem results and
Maven cache if exact replay matters. Do not use Python's `-O` option: existing
comparators use assertions to enforce their contracts.

The comparison checks exact detector identities, raw-row/source links,
eligibility and neutron decisions. Continuous detector values are compared
at common HIPO float32 precision with **no numerical tolerance**. Selector
calculations retain doubles; controlled selector diagnostics are compared
exactly. Readback checks persistence and source-link invariants. This proves
reference fidelity for the tested domain, not neutron purity or full-chain
production readiness.

## Source map

See [`docs/CODE_MAP.md`](docs/CODE_MAP.md) for the drivers and historical/expert
utilities, and [`docs/PROVENANCE.md`](docs/PROVENANCE.md) for extraction and
scope. Upstream sources/calibrations are fetched from their repositories and
retain their original terms. This repository does not relicense them.
