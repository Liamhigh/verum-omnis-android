The forensic regression suite is now an instrumentation test in:

- `app/src/androidTest/java/com/verum/omnis/ForensicRegressionTest.kt`

Active fixtures:

- `app/src/androidTest/assets/tdpi_case.pdf`
- `app/src/androidTest/assets/tdpi_benchmark.json`
- `app/src/androidTest/assets/greensky_case.pdf`
- `app/src/androidTest/assets/greensky_benchmark.json`
- `app/src/androidTest/assets/simple_contract_case.pdf`
- `app/src/androidTest/assets/simple_contract_benchmark.json`

The suite does five things for each active fixture:

1. copies the PDF from instrumented-test assets into the app's internal storage
2. runs `AnalysisEngine.analyze(...)` on that staged internal file
3. builds the sealed payload with `ForensicPackageWriter.buildPayload(...)`
4. verifies guardian and certification fields when the benchmark declares them
5. checks named-party hygiene, primary-evidence registers, signal terms, and normalized finding types against the benchmark

Run the active suite with:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.verum.omnis.ForensicRegressionTest"
```

The latest passing device run for the active three-fixture suite completed on March 27, 2026.

The test writes the latest sealed payload snapshots to the app cache under:

- `cache/forensic-regression/tdpi-latest.json`
- `cache/forensic-regression/greensky-latest.json`
- `cache/forensic-regression/simple-contract-latest.json`

The simple contract fixture now validates the short-document path with promoted top-level findings as well as register hygiene. The current compact-document expectation is:

- verified contradiction findings must appear in the sealed `findings` array
- a verified `UNPAID_INVOICE` finding must appear in the sealed `findings` array
- guardian approval may still remain false in the instrumentation environment, so the benchmark asserts promoted findings even when certification is denied

There is also a separate legal-grounding instrumentation test:

- `app/src/androidTest/java/com/verum/omnis/GreenskyLegalAdvisoryTest.kt`

That test validates the Greensky mixed-jurisdiction grounding package and writes a debug legal artifact under:

- `cache/gemma-regression/greensky-legal.json`

It validates the sealed grounding package, legal-pack retrieval, and `.legal.json`-style metadata. It does not currently exercise live Gemma inference inside instrumentation because the MediaPipe runtime is unstable in this test environment.
