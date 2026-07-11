# Local Model Setup

This repository does not include local model binaries.

That is intentional.

The app is structured to support offline bounded rendering, but large model assets and private runtime payloads are kept out of the public repository for security, licensing, and repository-size reasons.

## What is excluded

- `app/src/main/assets/model/*.task`
- any private local model pack staged for Gemma or other bounded local engines

## Current expected model path

The current Android app expects the local Gemma task asset to be staged privately at:

`app/src/main/assets/model/gemma3-1B-it-int4.task`

This file is ignored by Git and must be supplied manually in local/private environments.

## How to stage the model locally

1. Obtain the approved local model asset through your private/internal distribution path.
2. Create the folder if needed:
   `app/src/main/assets/model/`
3. Place the model file there using the expected filename:
   `gemma3-1B-it-int4.task`
4. Build locally:
   `./gradlew.bat :app:assembleDebug`

## Important constraints

- Do not commit local model binaries to the public repository.
- Do not attach model binaries to public releases unless you have explicitly decided to distribute them that way.
- Deterministic forensic analysis remains authoritative whether the bounded model is present or not.
- The only activation-ready bounded lane in this milestone is the human brief, and it remains downstream, audited, and fail-closed.

## Related guarded settings

Expected guarded runtime posture for this milestone:

- `boundedHumanBriefEnabled = false` by default
- `boundedPoliceSummaryEnabled = false`
- `boundedLegalStandingEnabled = false`
- `boundedRenderAuditRequired = true`
- `boundedRenderFailClosed = true`
