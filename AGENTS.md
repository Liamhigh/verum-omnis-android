# Verum Omnis Agent Map

## Purpose
- Keep report generation constitutional, deterministic, and anchored.
- Treat this repository as a bounded transformation pipeline, not a freeform narrative system.
- Prefer narrow edits with tests over broad "fix the reports" changes.

## Source Of Truth
- Canonical report/data contract:
  - [findings-schema.md](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/docs/system-of-record/findings-schema.md)
- Promotion rules:
  - [promotion-rules.md](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/docs/system-of-record/promotion-rules.md)
- Publication rules:
  - [publication-rules.md](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/docs/system-of-record/publication-rules.md)
- Golden fixture guidance:
  - [README.md](C:/Users/Gary/Downloads/exeriment/VerumOmnisV1/tests/fixtures/README.md)

## Canonical Flow
1. sealed evidence
2. machine findings / structured extraction
3. certification / guardian filtering
4. publication normalization
5. report renderers

Renderers must consume normalized findings only. They must not inspect raw evidence directly and must not invent role labels or legal conclusions outside the normalized model.

## Repository Truth Boundaries
- Extraction / synthesis:
  - `app/src/main/java/com/verum/omnis/core/AnalysisEngine.java`
  - `app/src/main/java/com/verum/omnis/core/ForensicSynthesisEngine.java`
  - `app/src/main/java/com/verum/omnis/ai/RulesEngine.java`
- Publication normalization:
  - `app/src/main/java/com/verum/omnis/core/FindingPublicationNormalizer.java`
  - `app/src/main/java/com/verum/omnis/core/ForensicReportAssembler.java`
  - `app/src/main/java/com/verum/omnis/core/CanonicalFindingBridge.java`
- Rendering:
  - `app/src/main/java/com/verum/omnis/MainActivity.java`
  - `app/src/main/java/com/verum/omnis/core/TruthInCodeEngine.kt`

## Non-Negotiables
- Never infer harmed-party labels unless separately anchored.
- Never upgrade a candidate contradiction to verified without paired anchored propositions.
- Never render a sentence that lacks anchor support through a normalized finding, contradiction entry, chronology item, or certified issue card.
- Never change hashes, case IDs, or run metadata except through the pipeline code that actually computes them.
- Never let a renderer become a reasoning engine.
- Never let the contradiction-engine report be overwritten by downstream legal narration.
- Never publish raw engine noise, placeholder text, or prompt truncation artifacts.

## Allowed Edit Zones
- `app/src/main/java/com/verum/omnis/core/**`
- `app/src/main/java/com/verum/omnis/ai/**`
- `app/src/main/java/com/verum/omnis/MainActivity.java`
- `app/src/test/**`
- `docs/system-of-record/**`
- `tests/fixtures/**`

## Use Extra Care In
- Security and integrity:
  - `app/src/main/java/com/verum/omnis/security/**`
- Sealing:
  - `app/src/main/java/com/verum/omnis/core/PDFSealer.java`
- Evidence anchors and case packaging:
  - `app/src/main/java/com/verum/omnis/forensic/**`
  - `app/src/main/java/com/verum/omnis/casefile/**`

## Refusal Rules
- Do not add cloud dependencies for core forensic/report operation.
- Do not add non-deterministic behavior to report content, anchor generation, or identity/status logic.
- Do not paraphrase legal/offence findings more strongly than the normalized finding allows.
- Do not "fix" outputs by editing archived artifacts; fix the pipeline that produced them.

## Required Verification
- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat :app:assembleDebug`

When working on report-layer logic, also verify:
- `app/src/test/java/com/verum/omnis/core/FindingPublicationNormalizerTest.java`
- `app/src/test/java/com/verum/omnis/core/ForensicReportAssemblerTest.java`
- `app/src/test/java/com/verum/omnis/core/ForensicSynthesisEngineTest.java`

## Task Style
- Work one lane at a time:
  - schema / contract
  - normalization
  - renderer
  - fixture / tests
- Prefer prompts/scopes like:
  - "Refactor the readable brief renderer to consume normalized findings only."
  - "Add a red-line test that forbids harmed-party publication without separate anchor support."
- Avoid prompts like:
  - "Fix Verum Omnis"
  - "Make the reports better"

## Done Means
- Same case ID across artifacts
- Same evidence hash across artifacts
- Same contradiction counts across artifacts
- Same guardian-approved count across artifacts
- No unanchored role labels
- No renderer-specific legal embellishment
- Tests green

## Cursor Cloud specific instructions

This is a single-module (`:app`) Android app (AGP 9.1.0, Gradle 9.3.1 via the wrapper,
Kotlin 2.2.10, compileSdk/targetSdk 35, minSdk 27). The canonical dev environment is
Windows (`./gradlew.bat`); on the Linux cloud VM use `./gradlew` instead.

Environment already provisioned in the VM snapshot (do not reinstall): OpenJDK 17 at
`/usr/lib/jvm/java-17-openjdk-amd64` and the Android SDK (cmdline-tools, platform-tools,
`platforms;android-35`, `build-tools;35.0.0`) at `~/android-sdk`.

Non-obvious gotchas:
- `gradle.properties` pins `org.gradle.java.home` to a Windows JDK path that does not
  exist on Linux. Do NOT edit that file (it would break Windows devs). Instead it is
  overridden by `~/.gradle/gradle.properties` (`org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64`),
  which takes precedence over the project file. The startup update script recreates this
  override and `local.properties` (`sdk.dir=~/android-sdk`) if missing.
- No emulator/instrumented tests in this VM: there is no `/dev/kvm`, so an API-35 emulator
  cannot run. `connectedDebugAndroidTest` and the `ForensicRegressionTest` /
  `GreenskyLegalAdvisoryTest` instrumentation suites cannot be executed here. The core
  forensic/report pipeline is fully covered by pure-JVM unit tests (`app/src/test`), which
  is the primary verification path — most `core` report classes (e.g. `ReadableBriefBuilder`)
  have no `android.*` deps.
- `./gradlew :app:lintDebug` currently reports pre-existing code errors (e.g. `NewApi`:
  `InputStream#readAllBytes` in `CaseFileManager.java` requires API 33 but minSdk is 27).
  These are codebase issues, not environment issues; lint itself runs correctly.

Standard commands (see AGENTS.md "Required Verification" above): `./gradlew testDebugUnitTest`
and `./gradlew :app:assembleDebug` (debug APK lands in `app/build/outputs/apk/debug/`).
