# Bounded Human Brief Controlled Internal Example

Date: 2026-04-13

Status:
Controlled internal bounded human brief example completed on physical Android hardware.

## Scope

This run exercised the bounded human brief lane only.

Still intentionally disabled:

- bounded police summary
- bounded legal standing

Still enforced:

- `boundedRenderAuditRequired = true`
- `boundedRenderFailClosed = true`

## Device

- `RZCY31BNT5A`

## Fixed Internal Case Shape

Test fixture used:

- `simple_contract_case.pdf`

Purpose:

- drive one controlled end-to-end bounded human brief success path
- drive one controlled end-to-end bounded human brief rejection path
- validate bounded rendering record and fallback under the real Android publication flow

## Controlled Outcomes Validated

### 1. Bounded Success

Validated:

- governed bundle created
- bounded human brief rendered
- audit passed
- downstream advisory included bounded rendering record
- machine-readable ledger artifact was written

### 2. Forced Audit Rejection

Validated:

- bounded human brief was attempted
- audit was forced to reject
- final advisory body fell back cleanly
- bounded rendering record still remained visible for transparency
- ledger captured fallback and rejection state

## Verification Command

```powershell
./gradlew.bat ":app:connectedDebugAndroidTest" "-Pandroid.testInstrumentationRunnerArguments.class=com.verum.omnis.BoundedHumanBriefActivationInstrumentationTest" --console=plain
```

## Evidence of Pass

Gradle connected test report:

- [com.verum.omnis.BoundedHumanBriefActivationInstrumentationTest.html](/C:/Users/Gary/Downloads/v1verum/project/app/build/reports/androidTests/connected/debug/com.verum.omnis.BoundedHumanBriefActivationInstrumentationTest.html)

Result summary from that report:

- `fixedCase_boundedHumanBriefSuccess_writesAppendixAndLedger` passed
- `fixedCase_boundedHumanBriefAuditRejection_fallsBackCleanly` passed

## Important Transparency Note

The device-visible installed package name exposed through `adb shell pm list packages` was:

- `com.verum.omnis.charter`

The instrumentation target package remained `com.verum.omnis`.

Because of that mismatch, direct `run-as` retrieval of the app sandbox artifacts was not available from this workspace, even though the connected instrumentation run succeeded and the tests verified ledger creation from within the app process.

This is a retrieval limitation, not a bounded-lane validation failure.

## Assessment

The bounded human brief lane has now been validated for controlled internal use across:

- architecture
- JVM governance tests
- Android hardware settings activation
- Android hardware fixed-case success
- Android hardware fixed-case forced fallback

## Recommended Next Use

Use bounded human brief only for controlled internal runs.

Do not enable:

- bounded police summary
- bounded legal standing

until separate downstream validation and governance review are completed.
