# Greensky Golden Fixture

## Purpose
This fixture locks the first readable-brief refactor to one approved Greensky case.

## Source Artifacts To Capture
- sealed evidence source
- findings package JSON
- normalized/certified findings snapshot
- approved readable brief output

Do not regenerate the expected readable brief automatically once approved. Treat it as a hand-approved golden output.

## Required Stable Values
- case ID: `case-32d3a9e6f5190fa551b2e712`
- evidence SHA-512 prefix: `32d3a9e6f5190fa5...`
- verified contradictions: `0`
- candidate contradictions: `1`
- guardian-approved certified findings: `3`

## Required Readable Brief Assertions
- case ID preserved
- evidence hash preserved or correctly shortened
- contradiction counts preserved
- guardian-approved certified finding count preserved
- only certified/publication-safe findings appear
- no harmed-party label unless separately anchored
- every published finding carries anchor pages
- candidate findings are not rewritten as verified

## Required Evidence Pages
- `127`
- `128`
- `129`
- `132`
- `27`
- `47`

## Red-Line Failures
- readable brief says `financial irregularities` when normalized findings do not support that label
- readable brief names a harmed party when publication rules require withholding
- readable brief upgrades a candidate contradiction to verified
- readable brief emits prose without anchor pages
