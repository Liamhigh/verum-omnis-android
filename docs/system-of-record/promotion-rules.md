# Promotion Rules

## Purpose
Promotion converts structured machine findings into certified findings. It does not render reports.

## Contradictions
- `VERIFIED` contradiction requires:
  - paired propositions
  - conflict type
  - anchors on both sides
  - explicit status trace in the contradiction register
- Otherwise use `CANDIDATE`

## Certification
- A finding is publishable as certified only when guardian approval is `true`.
- Guardian approval does not automatically create:
  - a harmed-party label
  - a direct offence label
  - a single adverse-actor conclusion

## Status Consistency
- `INDETERMINATE DUE TO CONCEALMENT` must not be flattened into naked `DETERMINATE`.
- If contradiction maturity is incomplete or coverage gaps remain material, prefer:
  - `DETERMINATE_WITH_MATERIAL_COVERAGE_GAPS`

## Role Promotion
- Do not promote harmed-party labels from contradiction pressure alone.
- Do not promote adverse-actor labels from candidate contradiction posture alone.
- Direct role labels require separate anchor support.

## Visual Findings
- High-severity visual anomalies may be promoted into explicit visual findings only when:
  - page is explicit
  - signal type is explicit
  - severity is explicit
- Visual promotion must stay separate from contradiction verification.
