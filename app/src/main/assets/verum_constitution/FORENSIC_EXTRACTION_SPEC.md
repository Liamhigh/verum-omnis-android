# Verum Omnis Forensic Extraction Specification

Status: Authoritative implementation spec
Purpose: Ensure contradiction-clean, court-usable forensic outputs.

---

## Core Objective
Output only court-usable findings. Prefer:
- truth over volume
- propositions over keywords
- actor-linked facts over vague suspicion
- ordinal confidence over numeric scoring
- deterministic repeatability

---

## Golden Rule
Extract propositions, not keywords. Verify conflicts, not themes. Count only what survives actor attribution, source anchoring, and contradiction testing.

---

## Contradiction Rule
A contradiction = conflict between two anchored propositions.

Required:
1. Proposition A
2. Proposition B
3. Material conflict
4. Source anchor
5. Actor attribution

If any missing -> NOT a contradiction.

---

## Allowed Classes
- PROPOSITION_CONFLICT
- TIMELINE_CONFLICT
- DOCUMENT_VS_STATEMENT_CONFLICT
- SOURCE_VS_SOURCE_CONFLICT
- METADATA_VS_CONTENT_CONFLICT

---

## NOT Contradictions
- "fake", "fraud", "forgery" mentions
- technical explanations
- summaries
- unresolved actors
- keyword hits

---

## Output Layers
- VERIFIED
- CANDIDATE
- REJECTED
- NARRATIVE_THEME

---

## Actor Rule
No actor = no liability.

---

## Confidence
Use only:
VERIFIED, HIGH, MODERATE, LOW, INSUFFICIENT

---

## Financial Rule
Do not count numbers. Reconcile flows.

---

## Timeline Rule
Every key claim must have date + actor + anchor.

---

## Document Integrity
Use:
- MISSING_COUNTERSIGNATURE
- BACKDATING_RISK
- METADATA_ANOMALY

---

## De-duplication
Same issue = one finding, many anchors.

---

## Prohibitions
Do NOT:
- count keywords as contradictions
- use fake blockchain anchors
- use numeric confidence
- inflate counts

---

## Minimal Verified Object

```json
{
  "status": "VERIFIED",
  "conflictType": "PROPOSITION_CONFLICT",
  "actor": "Actor Name",
  "propositionA": {"text": "...", "anchor": {"page": 1}},
  "propositionB": {"text": "...", "anchor": {"page": 2}},
  "whyItConflicts": "...",
  "confidence": "MODERATE"
}
```

---

## Final Principle
System must behave like a hostile cross-examiner, not a keyword detector.
