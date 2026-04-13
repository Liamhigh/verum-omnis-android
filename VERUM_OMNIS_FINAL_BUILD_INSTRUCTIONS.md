# Verum Omnis Constitutional Forensic Engine - Final Build

## 1. Core Principles

1. Case-agnostic. No hardcoded names, dates, page numbers, or specific phrases in brain logic. All case-specific data comes from extraction and configuration files.
2. Deterministic. Same inputs -> same outputs. No randomness, no non-deterministic scoring.
3. Triple verification. Every VERIFIED finding must have:
   - At least one anchor
   - At least two independent corroborating sources
   - A contradiction check that does not invalidate it
4. Contradictions preserved. Never smooth away contradictions. They must be visible in the audit trail.
5. Auditable. Every VERIFIED finding must have a full audit trail and a certification block.
6. Minimal disclosure. Report only anchors, short excerpts, rationale. Raw evidence stays in the vault.

## 2. High-Level Architecture

```text
Evidence Files
  -> Intake (hash, manifest)
  -> Extraction (text, metadata, anchors)
  -> Tagging (legal subjects, entities)
  -> 9 Brains (each returns List<Finding>)
  -> Orchestrator (merge, apply corroboration & contradiction checks)
  -> Promotion Review (P1-P7)
  -> Audit Generation (FindingAudit)
  -> Oversight (B8)
  -> Guardian (B9)
  -> Certification (for VERIFIED)
  -> Report (JSON + PDF/A-3B)
```

## 3. Data Models

```kotlin
enum class ProcessingStatus { COMPLETED, INDETERMINATE_DUE_TO_CONCEALMENT, HARD_STOP_INTEGRITY_MISMATCH }
enum class FindingLayer { VERIFIED, CANDIDATE, REJECTED, NARRATIVE_THEME }
enum class OrdinalConfidence { VERIFIED, HIGH, MODERATE, LOW, INSUFFICIENT }
enum class ContradictionClass {
    PROPOSITION_CONFLICT, TIMELINE_CONFLICT, DOCUMENT_VS_STATEMENT_CONFLICT,
    SOURCE_VS_SOURCE_CONFLICT, METADATA_VS_CONTENT_CONFLICT
}

data class Anchor(
    val evidenceId: String,
    val page: Int? = null,
    val bbox: String? = null,
    val messageId: String? = null,
    val timestampIso: String? = null,
    val offset: Int? = null
)

data class Proposition(
    val text: String,
    val actor: String? = null,
    val anchors: List<Anchor> = emptyList()
)

data class Finding(
    val findingType: String,
    val layer: FindingLayer,
    val confidence: OrdinalConfidence,
    val contradictionClass: ContradictionClass? = null,
    val actors: List<String> = emptyList(),
    val propositions: List<Proposition> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val rationale: String = "",
    val corroboration: List<String> = emptyList(),
    val dedupeKey: String
)

data class FindingAudit(
    val findingId: String,
    val brainIds: List<String>,
    val findingType: String,
    val actors: List<String>,
    val sourceAnchors: List<Anchor>,
    val sourceEvidenceIds: List<String>,
    val supportingExcerpts: List<String>,
    val ruleHits: List<String>,
    val contradictionChecks: List<String>,
    val corroborationChecks: List<String>,
    val exclusionChecks: List<String>,
    val uncertainties: List<String>,
    val internalScores: Map<String, Double>,
    val promotionReason: String,
    val demotionReason: String? = null
)

data class FindingCertification(
    val constitutionHash: String,
    val rulePackVersion: String,
    val engineVersion: String,
    val deterministicRunId: String,
    val evidenceBundleHash: String,
    val findingHash: String,
    val promotionHash: String,
    val guardianApproval: Boolean,
    val guardianReason: String?,
    val reproducibilityStatement: String,
    val certifiedAtIso: String
)

data class CertifiedFinding(
    val finding: Finding,
    val audit: FindingAudit,
    val certification: FindingCertification
)

data class ForensicReport(
    val caseId: String,
    val processingStatus: ProcessingStatus,
    val findings: List<Finding>,
    val certifiedFindings: List<CertifiedFinding>,
    val dishonestyMetrics: DishonestyMetrics? = null,
    val legalSubjects: Map<String, List<Anchor>>? = null,
    val recommendedActions: List<String>,
    val sealedReportHash: String
)
```

## 4. Nine Brain Implementations

### B1 - Evidence Brain
- Does not produce findings.
- Validates input, computes SHA-512, checks integrity, creates anchors.

### B2 - Contradiction Brain
- Extracts propositions from text using configurable patterns from `detection_rules.json`.
- Compares propositions with rule-based or deterministic NLI conflict logic.
- VERIFIED requires actor attribution, anchors, and material conflict.
- Otherwise output CANDIDATE.

### B3 - Timeline Brain
- Extracts all dates from text and anchors.
- Orders events chronologically.
- Promotes to VERIFIED only when timeline order materially affects interpretation.

### B4 - Jurisdiction Brain
- Maps extracted legal subjects to statutes using `jurisdiction_packs.json`.
- Outputs only NARRATIVE_THEME findings.

### B5 - Behavioural Brain
- Detects evasion, gaslighting, or manipulation using `detection_rules.json`.
- Outputs only CANDIDATE findings unless conduct is explicitly admitted in anchored text.

### B6 - Harm Analysis Brain
- Uses already-verified findings to describe downstream harm.
- Outputs NARRATIVE_THEME only.

### B7 - Financial Brain
- Extracts actor, amount, currency, basis, counterparty, and anchor.
- Uses configurable regex and keyword lists.
- VERIFIED requires all five elements plus anchored reconciliation.

### B8 - Oversight Brain
- Examines findings and raw evidence for suppression, imbalance, or hidden contradictions.
- Outputs CANDIDATE findings for one-sided requests, ignored meeting requests, fabricated evidence patterns, and similar issues.

### B9 - Guardian Brain
- Does not produce findings.
- Runs after all other brains and promotion.
- Validates constitution hash, evidence integrity, provenance, contradiction resolution, and corroboration.
- If approval fails, all VERIFIED findings are downgraded to CANDIDATE with reason recorded.

## 5. Promotion Doctrine (P1-P7)

1. P1 - Anchor rule. At least one direct anchor exists.
2. P2 - Actor rule. If the finding concerns a person or entity, the actor is identified or explicitly marked unresolved actor.
3. P3 - Evidentiary sufficiency. The evidence supports the precise proposition.
4. P4 - Corroboration rule. At least two independent supports exist.
5. P5 - Contradiction check. No unresolved evidence invalidates the finding.
6. P6 - Provenance rule. Every anchor points to an evidence item with preserved provenance and integrity hash.
7. P7 - Explainability rule. The finding can be explained in plain language from anchors and rule hits.

Corroboration independence examples:
- Valid: invoice + agreement + email admission
- Invalid: same screenshot repeated three times

## 6. Certification

Every VERIFIED finding must carry a `FindingCertification` block.

Preconditions:
1. FC1 - Constitution lock
2. FC2 - Rule pack lock
3. FC3 - Engine lock
4. FC4 - Evidence lock
5. FC5 - Promotion lock
6. FC6 - Guardian approval
7. FC7 - Reproducibility statement

If any precondition fails, demote to CANDIDATE and append the reason to `FindingAudit.demotionReason`.

## 7. Report Generation

Final output must be:
- canonical JSON
- sealed PDF/A-3B

PDF must include:
- Case identification
- Processing status
- Constitution, rules, and evidence hashes
- Executive Summary
- Verified Findings
- Candidate Findings
- Narrative Themes
- Contradiction Register
- Evidence Gap Register
- Legal Subject Map
- Recommended Actions
- Seal / SHA-512

Verified finding narrative template:

```text
### [Finding Type]

What happened: [Rationale from the finding]

Evidence: [List anchors with page references]

Why it matters: [Legal or practical implication derived from legal subjects]

Actors: [List actors]
```

## 8. Configuration Files

All case-specific patterns must be stored in JSON files loaded at runtime:
- `detection_rules.json`
- `jurisdiction_packs.json`
- `subject_keywords.json`
- `integrity_keywords.json`

These files are part of the constitution asset bundle and are hashed for integrity.

## 9. Implementation Steps

1. Set up project structure.
2. Implement data models.
3. Build extraction layer.
4. Implement tagging based on config files.
5. Create BaseBrain and nine brains with no hardcoded case facts.
6. Build orchestrator that merges findings and applies corroboration and contradiction checks.
7. Implement Promotion Review (P1-P7) as a separate decision layer.
8. Implement Guardian Brain as validator only.
9. Implement certification service.
10. Generate JSON and PDF/A-3B reports with certification blocks.
11. Add deterministic tests asserting repeatable output.

## 10. Testing with Greensky

Use Greensky as test input only, not as the engine definition.
The engine should produce the same type of findings through configuration and extraction, not hardcoded phrases.

## 11. Final Reminders

- Never hardcode case-specific text, page numbers, or dates in core logic.
- Never use page numbers as anchors unless they come from extraction.
- Never infer guilt from behavioural language alone.
- Always keep the audit trail.
- Always certify VERIFIED findings.
- Always remain deterministic.
