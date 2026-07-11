# VerumOmnisV1 Forensic Backlog

This backlog converts the useful parts of the archived ChatGPT material and the phone-download archive into concrete engineering work for `VerumOmnisV1`.

Priority order is based on what most improves forensic reliability, report quality, and operator trust.

## P0: Forensic Correctness

- Enforce contradiction-first processing.
  Source: `ENGINE_SPEC.md`, archived strategy notes, `gift_rules_v5.json`.
  Work:
  - run proposition pairing before narrative assembly
  - do not allow a contradiction into `VERIFIED` without both anchored sides
  - keep rejected and candidate contradiction entries in audit output

- Finish actor normalization.
  Source: live Greensky failures, `forensic_findings.json`, prior phone reports.
  Work:
  - separate person actors from organization actors
  - stop OCR fragments and object names from becoming actors
  - preserve unresolved actors explicitly instead of guessing

- Strengthen evidence-class separation.
  Source: `ENGINE_SPEC.md`, `LIMITATIONS.md`, Greensky report defects.
  Work:
  - keep primary evidence, secondary narrative, technical seal text, and generated analysis in separate classes
  - block secondary narrative from human-facing findings unless backed by primary anchors
  - keep technical anti-forgery language out of contradiction logic

- Gate case determinacy harder.
  Source: `LIMITATIONS.md`, live Greensky runs.
  Work:
  - require usable anchor density for `DETERMINATE`
  - keep `INDETERMINATE DUE TO CONCEALMENT` when OCR failure or contamination is too high
  - expose the reason for indeterminacy in both JSON and PDF outputs

## P1: Human Report Quality

- Keep the human PDF anchor-first and non-argumentative.
  Source: live Greensky human report audits.
  Work:
  - use `actor | label | page` style summaries where possible
  - remove narrative-theme output from the human report
  - avoid legal-overstatement wording such as likely guilt or likely criminal exposure

- Split person, organization, and document sections cleanly.
  Source: Greensky actor bleed and organization bleed.
  Work:
  - `Principal actors` for humans only
  - `Principal entities` for companies and institutions
  - `Core documents` for agreements, notices, invoices, screenshots

- Reduce visual memo noise.
  Source: live visual memo output, `VO_Codex_Playbook_2_Forensic_Engine.pdf`.
  Work:
  - deduplicate repeated page findings
  - show only medium/high exceptions by default
  - keep low/info findings in sealed audit output only

## P1: Auditability And Trust

- Add full export validation.
  Source: `case.schema.json`, `ForensicSchemaValidator.java`, `LIMITATIONS.md`.
  Work:
  - validate every exported JSON package
  - fail export on missing required fields
  - log validation failures to the audit ledger

- Add chain-of-custody style run logs.
  Source: `brain-logs-1.json`, `forensic.vfp.json`.
  Work:
  - preserve module start/end times
  - preserve module input/output hashes
  - preserve chain hash across pipeline stages
  - expose a compact audit summary in the sealed package

- Version rules and config explicitly.
  Source: `gift_rules_v5.json`, `ENGINE_SPEC.md`.
  Work:
  - centralize thresholds, weights, and labels
  - store config checksum in each run
  - do not let silent threshold changes alter case outcomes

## P2: Evidence Intake And Usability

- Add guided evidence-export help.
  Source: `LIMITATIONS.md`.
  Work:
  - instructions for WhatsApp, Gmail, screenshots, PDFs, and metadata exports
  - mobile-specific guidance for evidence capture
  - explain when native export is better than screenshots

- Add affidavit/declaration templates.
  Source: `LIMITATIONS.md`, prior legal framework docs.
  Work:
  - witness declaration template
  - chain-of-custody declaration template
  - operator submission declaration template

- Improve hashing and sealing UX.
  Source: `LIMITATIONS.md`, phone archive artifacts such as `.sha512.txt`, `.asc`, `.ots`, `VO_Seal_Manifest.json`.
  Work:
  - simple in-app hash explanation
  - one-tap sealed package export
  - manifest view showing evidence hash, case id, report hash, and timestamp

## P2: Jurisdiction And Citation Safety

- Add citation freshness checks.
  Source: `LIMITATIONS.md`.
  Work:
  - validate that referenced legal material is still current before use
  - separate legal subject flags from legal advice
  - show citation verification date in generated legal-facing outputs

- Support jurisdiction-specific packaging.
  Source: `LIMITATIONS.md`, prior Greensky legal framework docs.
  Work:
  - translation/notary guidance hooks
  - jurisdiction-specific export packs
  - separate civil, criminal, and regulatory routing bundles

## P3: Archive Mining

- Mine useful prior rule artifacts from phone-download zip.
  Source files:
  - `gift_rules_v5.json`
  - `forensic.vfp.json`
  - `forensic_findings.json`
  - `brain-logs-1.json`
  - `contradiction-register.txt`
  - `findings-summary.txt`
  Work:
  - extract reusable status labels and audit structures
  - discard branding and unsupported “9-brain” claims
  - keep only rule mechanics that improve determinism or auditability

- Ignore or quarantine low-value archives.
  Source:
  - raw ChatGPT chat exports
  - duplicated sealed PDFs
  - audio overview files
  Work:
  - do not use these as source of truth for engine logic
  - only mine them for operator docs or examples

## Recommended Build Order

1. Finalize actor normalization and evidence-class separation.
2. Lock contradiction-first ordering.
3. Clean the human PDF and visual memo renderers.
4. Add audit ledger and config checksum to every run.
5. Add guided evidence-export help and affidavit templates.
6. Add citation freshness and jurisdiction packaging.

## Done Criteria

- JSON output is schema-valid.
- Human report contains only anchor-backed findings and explicit limitations.
- No object names or OCR fragments appear as actors.
- Contradiction register is explainable and stable across reruns.
- Visual memo is concise and exception-focused.
- Audit log preserves module hashes, timing, and versioning.
