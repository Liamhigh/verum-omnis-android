# Security

## Public repository policy

This repository is public-facing code only.

The following must never be committed:

- signing keys or keystores
- API secrets or SMTP credentials
- local model binaries
- release APK/AAB artifacts
- local vault outputs
- device logs
- machine-local Gradle property files containing secrets

## Secret handling

Use local/private property injection for secrets.

Examples:

- `gradle.properties.local`
- user-scoped Gradle properties
- environment-specific private storage

Do not hardcode secrets in Java, Kotlin, Gradle, or resource files.

## Local model handling

Local bounded-model assets are intentionally excluded from Git.

See [SETUP_LOCAL_MODELS.md](/C:/Users/Gary/Downloads/v1verum/project/SETUP_LOCAL_MODELS.md:1) for the expected local staging path and constraints.

## Release posture for this milestone

This repository includes the bounded-render architecture, but only one bounded lane is considered activation-ready under guardrails:

- bounded human brief

The following remain intentionally gated off:

- bounded police summary
- bounded legal standing

## Constitutional safety posture

- Deterministic truth production remains authoritative.
- Bounded rendering is downstream only.
- Audit pass is required before bounded publication.
- Fail-closed fallback to legacy output remains enabled.

## Incident response guidance

If any secret has ever been committed, embedded in source, or included in build artifacts:

1. Rotate it immediately.
2. Remove it from source.
3. Amend or replace affected Git history if appropriate.
4. Verify public release artifacts do not retain the secret.

This rule applies even if the secret was later removed.
