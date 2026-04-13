# Golden Fixtures

## Purpose
Golden fixtures lock report behavior to a known case so renderer drift is caught by tests instead of by post-run review.

## Recommended Layout
```text
tests/fixtures/<case-id>/
  forensic-findings.json
  expected-audit-report.json
  expected-contradiction-report.json
  expected-human-brief.json
  expected-readable-brief.json
```

Large sealed evidence PDFs should stay out of the repo if size is a problem. The minimum useful golden fixture is the findings package plus expected rendered outputs.

## Minimum Assertions
- case ID preserved
- evidence hash prefix preserved
- verified contradiction count preserved
- candidate contradiction count preserved
- guardian-approved certified finding count preserved
- no unanchored harmed-party label
- anchor pages preserved

## Current Priority Case
- Greensky fixture:
  - `case-32d3a9e6f5190fa551b2e712`
  - hash prefix `32d3a9e6f5190fa5...`
  - `verified contradictions: 0`
  - `candidate contradictions: 1`
  - `guardian-approved certified findings: 3`

## Red-Line Tests To Add
- readable brief must not say `financial irregularities` unless the normalized finding type supports that label
- human report must not name a harmed party when publication rules require withholding
- contradiction report must not say `verified` when source contradiction status is `CANDIDATE`
- renderer must fail or withhold when anchor pages are missing
