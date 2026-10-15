# Source map

## Supported entry points

- `scripts/setup.py`: clone/register exact dependency commits.
- `scripts/build.py`: build the BAND module and external drivers using JLab's stack.
- `scripts/validate.py`: synthetic suite, single-run comparisons and derived timing-free inputs.
- `scripts/convert_calibrations.py`: reference-file routing and exact-run v2 table conversion; never writes CCDB.
- `scripts/make_neutron_cases.py`: 49 named plus 10,000 deterministic calibrated-hit cases (seed 636304).
- `scripts/common.py`: hashing, immutable configuration, process progress and receipts.

## Main comparison code

| Files | Responsibility |
| --- | --- |
| `campaign_cpp.cpp`, `CampaignJava.java` | Full hit and neutron comparison on raw-bank HIPO |
| `neutron_selector_cpp.cpp`, `NeutronSelectorJava.java` | Injected calibrated-hit comparisons against original goodNeutronEvent |
| `hit_only_cpp.cpp`, `HitOnlyJava.java` | Reconstruction without REC timing or neutron selection |
| `SnapshotConstants.java`, `CampaignConstants.java` | External IndexedTable snapshots and exact-run routing |
| `compare.py`, `compare_neutrons.py`, `compare_hits.py` | Independent alignment, provenance and numerical checks |
| `NeutronHipoReadback.java`, `HitOnlyReadback.java` | Reopen generated HIPO and check output fields/links |
| `BandPortContractTests.java`, `NeutronContractTests.java` | Calibration, integration, precision and rejection contracts |
| `CampaignFixtures.java`, `SchemaSeed.java` | Public synthetic inputs without a private seed event |

All files in the table are under `harness/`. The C++ selector fixture driver
implements only the simple storeHits energy prefilter for injected hits and
calls the original compiled helper for selection and pairwise blocker checks.
The real/raw-HIPO driver independently calls the original storeHits itself.

## Historical and expert utilities

The complete external source collection is retained for review. These tools
are not all part of the supported one-command workflow:

- `baseline_cpp.cpp`, `BaselineJava.java`, `reconstruction_cpp.cpp`,
  `PortJava.java`: original baseline and intermediate port comparisons. The
  legacy Java driver expects a legacy implementation/classpath and live
  legacy CCDB; the current pinned build is the completed port. Do not treat
  these as a fresh reproduction of the original frozen baseline.
- `selector_contract.cpp`: early hand-written original-helper checks.
- `MakeBandFixtures.java`: original 23-case-per-mode fixtures that depend on
  a particular reference event. The public self-test instead uses
  `CampaignFixtures` with a generated dictionary and calibration-driven signals.
- `FreezeHipo.java`, `FreezeTaggedHipo.java`: bounded sample extraction,
  including tag-0 extraction where needed. Outputs preserve source dictionaries.
- `FilterTimedHipo.java`: explicit, audited timing preselection. It accepts
  finite -1000 for reference compatibility and is never run silently.
- `RemoveRecTiming.java`: remove timing/selection banks in a new file.
- `InspectHipo.java`, `InventoryBandHipo.java`, `BandMultiplicity.java`:
  inspection and occupancy inventories; raw occupancy is not calibrated laser classification.
- `LaserDecodeAudit.java`: hardware mapping/FADC configuration audit for a
  supplied decoded stream and DAQ tables. Final laser calibration remains a review decision.
- `QAAudit.cpp`: native QADB policy investigation, requiring the matching
  installed cook/database. It is supplied as source but not built by the
  default build. The optional electron mode in `campaign_cpp` also requires
  RCDB/network and matching QADB; the public compare command does not enable it.

The project-specific campaign inventories, personal login wrappers, private
HIPO paths, payload archives and historical runtime receipts were not copied
into the public source tree. Original analysis/QADB policy is not changed.
