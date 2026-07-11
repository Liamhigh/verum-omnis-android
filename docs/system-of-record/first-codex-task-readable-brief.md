# First Codex Task: Readable Brief Only

## Task
Refactor the readable brief renderer to consume normalized publication-safe findings only.

## Scope
Edit only:
- [MainActivity.java](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/app/src/main/java/com/verum/omnis/MainActivity.java)
- a new helper under `app/src/main/java/com/verum/omnis/core/` if needed for the readable brief only
- readable-brief tests under `app/src/test/java/com/verum/omnis/core/`

Do not edit:
- extraction / promotion logic
- contradiction verification logic
- certification logic
- legal mapping logic

## Requirements
- Renderer consumes normalized findings / assembled publication-safe data only.
- Do not read raw evidence pages directly.
- Preserve:
  - case ID
  - evidence hash prefix
  - guardian-approved certified finding count
  - verified contradiction count
  - candidate contradiction count
  - anchor pages
- Do not infer harmed-party labels.
- Do not upgrade candidate contradictions to verified.
- Every published finding line must include anchor pages.

## Verification
- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`

## Definition Of Done
- Golden fixture `case-32d3a9e6f5190fa551b2e712` matches expected readable brief behavior.
- No schema/rule violations.
- No visible renderer drift phrases.

## Red-Line Tests
- fails if renderer reads raw evidence directly
- fails if renderer upgrades contradiction status
- fails if renderer emits unanchored role language
- fails if renderer paraphrases beyond allowed template language
