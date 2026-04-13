# Publication Rules

## Purpose
Publication converts certified/normalized findings into user-facing reports.

## Hard Boundary
Renderers do not reason. They format already-decided data.

## Required Inputs
- `RunMetadata`
- `GuardianDecision`
- `PublicationFinding[]`
- `ContradictionEntry[]`
- anchored chronology items

## Required Output Discipline
- Every report sentence must map to at least one anchor page through a normalized object.
- If anchor support is missing, the sentence must not render.
- If a role label is not separately anchored, it must be withheld.

## Contradiction Report
- Publishes contradiction counts, contradiction posture, contradiction ledger, and anchored timeline.
- Does not enlarge into harmed-party or legal-role conclusions by itself.
- Optional legal/advisory material must be marked non-core if included at all.

## Constitutional Vault Report
- May include broader synthesis than the contradiction report.
- Must not silently overwrite contradiction posture with stronger legal narration.
- If material coverage gaps remain, top-line status must carry that qualification.

## Human Report / Readable Brief
- Must consume `PublicationFinding[]` only.
- Must preserve:
  - case ID
  - evidence hash prefix
  - contradiction counts
  - guardian-approved certified finding count
  - anchor pages
- Must not publish:
  - raw engine noise
  - prompt truncation artifacts
  - unanchored harmed-party labels
  - contradiction upgrades

## Visual Memo
- Visual findings must state:
  - page
  - signal type
  - severity
- Visual findings remain a separate specialist layer.
