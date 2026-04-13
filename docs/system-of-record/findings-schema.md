# Findings Schema

## Purpose
This file is the system of record for publication-safe forensic reporting in Verum Omnis.

The engine must follow this flow:
1. sealed evidence
2. raw machine findings
3. certified findings / guardian decision
4. publication findings
5. renderers

Renderers must not reason from raw evidence. They render only normalized, publication-safe objects.

## Core Objects

### `RunMetadata`
- `caseId: String`
- `evidenceHashSha512: String`
- `evidenceHashPrefix: String`
- `jurisdiction: String`
- `jurisdictionName: String`
- `processingStatus: ProcessingStatus`
- `verifiedContradictionCount: Int`
- `candidateContradictionCount: Int`
- `guardianApprovedCertifiedFindingCount: Int`
- `sourcePageCount: Int`
- `reproducibilityVersion: String`

### `GuardianDecision`
- `approved: Boolean`
- `reason: String`
- `reviewedAtUtc: String`
- `certifier: String`

### `ContradictionEntry`
- `id: String`
- `status: ContradictionStatus`
- `conflictType: String`
- `summary: String`
- `actors: List<String>`
- `anchorPages: List<Int>`
- `sourceAnchors: List<String>`
- `confidenceOrdinal: ConfidenceOrdinal`
- `notes: List<String>`

Rules:
- `VERIFIED` requires explicit paired propositions and anchor support on both sides.
- If paired support is incomplete, downgrade to `CANDIDATE`.
- Contradiction reports render from these entries directly.

### `RawFinding`
- `id: String`
- `findingType: String`
- `status: FindingStatus`
- `summary: String`
- `actor: String`
- `anchorPages: List<Int>`
- `excerpt: String`
- `confidenceOrdinal: ConfidenceOrdinal`
- `sourcePath: String`

### `CertifiedFinding`
- `id: String`
- `findingType: String`
- `status: FindingStatus`
- `summary: String`
- `actor: String`
- `anchorPages: List<Int>`
- `confidenceOrdinal: ConfidenceOrdinal`
- `guardianDecision: GuardianDecision`
- `contradictionStatus: ContradictionStatus`
- `renderWarnings: List<String>`

Rules:
- `CERTIFIED` requires guardian approval.
- A finding may remain raw `CANDIDATE` but still be publication-ready only if the publication layer explicitly carries that distinction.

### `PublicationFinding`
- `id: String`
- `findingType: String`
- `publicationRoleStatus: PublicationRoleStatus`
- `publicSummary: String`
- `publicActorLabel: String`
- `anchorPages: List<Int>`
- `confidenceOrdinal: ConfidenceOrdinal`
- `guardianApproved: Boolean`
- `rawFindingStatus: FindingStatus`
- `contradictionStatus: ContradictionStatus`
- `withheldFields: List<String>`
- `renderWarnings: List<String>`

Rules:
- Publication findings are the only findings objects renderers may consume.
- Every publication finding must include at least one anchor page.
- Role labels must be withheld when they are not separately anchored.

## Enums

### `ProcessingStatus`
- `INDETERMINATE`
- `DETERMINATE`
- `DETERMINATE_WITH_MATERIAL_COVERAGE_GAPS`

### `FindingStatus`
- `REJECTED`
- `CANDIDATE`
- `CERTIFIED`

### `ContradictionStatus`
- `NONE`
- `CANDIDATE`
- `VERIFIED`

### `PublicationRoleStatus`
- `WITHHOLD`
- `PUBLISH`

### `ConfidenceOrdinal`
- `VERY_HIGH`
- `HIGH`
- `MODERATE`
- `LOW`
- `INSUFFICIENT`

## Red Lines
- No renderer may invent a harmed-party label.
- No renderer may upgrade contradiction status.
- No renderer may output a sentence that cannot be traced to a normalized finding, contradiction entry, or chronology item with anchors.
- Visual findings must carry explicit page/signal metadata.
