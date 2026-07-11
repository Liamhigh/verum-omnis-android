# First Golden Fixture Spec: Greensky Readable Brief

## Goal
Lock the readable brief to the normalized publication layer and stop drift between:
- findings JSON
- contradiction counts
- guardian-approved certified findings
- published pages and role labels

## Actual Repository Paths In Scope
- readable brief entry point:
  - [MainActivity.java](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/app/src/main/java/com/verum/omnis/MainActivity.java)
  - `generateReadableFindingsBriefReport(...)`
  - `buildReadableFindingsBriefFallback(...)`
- normalization and publication model:
  - [FindingPublicationNormalizer.java](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/app/src/main/java/com/verum/omnis/core/FindingPublicationNormalizer.java)
  - [ForensicReportAssembler.java](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/app/src/main/java/com/verum/omnis/core/ForensicReportAssembler.java)

## Narrow Refactor Target
Extract the deterministic fallback readable brief into a dedicated helper that consumes:
- `AnalysisEngine.ForensicReport`
- normalized certified findings
- contradiction counts
- assembled issue cards / pages

That helper must not:
- inspect raw evidence pages directly
- read archived PDFs directly
- infer harmed-party labels
- upgrade contradiction status

## Fixture Inputs
Store under:
- [tests/fixtures/case-32d3a9e6f5190fa551b2e712](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/tests/fixtures/case-32d3a9e6f5190fa551b2e712)

Minimum inputs:
- `forensic-findings.json`
- approved readable brief text or normalized JSON expectation

Optional large artifacts:
- sealed evidence PDF

## Assertions
- case ID preserved
- evidence hash prefix preserved
- verified contradictions = `0`
- candidate contradictions = `1`
- guardian-approved certified findings = `3`
- anchor pages include `127, 128, 129, 132, 27, 47`
- no harmed-party publication unless separately anchored
- no candidate-to-verified contradiction upgrade
- no unanchored role prose

## Suggested Test Shape
1. load fixture JSON
2. reconstruct `ForensicReport`
3. apply `FindingPublicationNormalizer.applyToReport(report)`
4. build `ForensicReportAssembler.Assembly`
5. render readable brief from deterministic helper
6. assert required counts, pages, and forbidden phrases

## Forbidden Drift Phrases
- `financial irregularities`
- `harmed party:` when role must be withheld
- `verified contradiction` when the contradiction status is candidate-only
- any visible prompt truncation artifact
