# Task Templates

Use these prompts for narrow, verifiable work.

## Task Template
```text
Task:
Implement <single feature>.

Scope:
Edit only:
- <file 1>
- <file 2>

Do not edit:
- <protected paths>

Requirements:
- Consume normalized findings only.
- Preserve caseId, evidenceHashPrefix, guardianApprovedCount,
  verifiedContradictions, candidateContradictions.
- Do not infer harmed-party labels.
- Do not upgrade candidate contradictions to verified.
- Every rendered finding must include anchor pages.

Verification:
- ./gradlew.bat testDebugUnitTest
- ./gradlew.bat :app:assembleDebug

Definition of done:
- Golden fixture matches expected output.
- No schema/rule violations.
```

## First Three Repository Tasks

### Task 1
Create Java/Kotlin schema documentation and validation tests for:
- `RunMetadata`
- `GuardianDecision`
- `CertifiedFinding`
- `PublicationFinding`
- `ContradictionEntry`

### Task 2
Implement or tighten publication normalization so it:
- converts certified findings into publication-safe findings
- withholds harmed-party labels without separate anchor support
- preserves contradiction status
- requires anchor pages

### Task 3
Refactor the readable brief renderer to consume normalized publication findings only and add a golden test for the Greensky fixture.
