Instrumented regression fixtures for the Android forensic engine.

Fixtures:

- `tdpi_case.pdf`
  Benchmark: `tdpi_benchmark.json`
  Purpose: large mixed bundle with narrative-noise filtering, named-party hygiene, and guardian-denied output.

- `greensky_case.pdf`
  Benchmark: `greensky_benchmark.json`
  Purpose: shareholder-oppression and financial-conflict bundle with synthetic-label exclusion and contradiction preservation.

- `greensky_expected.json`
  Purpose: richer Greensky oracle fixture that records the target guardian-approved, ten-certified-finding output shape after the constitutional bundle and B1 corroboration fixes. This is kept alongside the lighter `greensky_benchmark.json` until the live runtime fully matches the oracle.

- `simple_contract_case.pdf`
  Benchmark: `simple_contract_benchmark.json`
  Purpose: short self-contained contract dispute that must surface promoted top-level `CONTRADICTION` and `UNPAID_INVOICE` findings even when guardian certification is denied in the test environment.

Supporting tests:

- `com.verum.omnis.ForensicRegressionTest`
  Runs the three forensic fixtures and checks schema, named parties, evidence-register hygiene, signal terms, and required finding types.

- `com.verum.omnis.GreenskyLegalAdvisoryTest`
  Validates the Greensky mixed-jurisdiction legal grounding package and writes a `.legal.json`-style debug artifact to app cache.
