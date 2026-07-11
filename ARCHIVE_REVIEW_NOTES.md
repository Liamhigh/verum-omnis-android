# Archive Review Notes

These notes record what was useful from the archived material reviewed on 2026-03-25.

## Archived ChatGPT Material

Useful files:

- [README_Greensky_AI_Legal_Framework_Liam_Highcock.md](/C:/Users/Gary/Downloads/exeriment/tmp/chatgpt_archive_review/chatgpt_zips_1/Chatgpt%20zips/README_Greensky_AI_Legal_Framework_Liam_Highcock.md)
  - useful for product framing, intended outcomes, and guardrails
  - not a source of forensic engine rules

- [LIMITATIONS.md](/C:/Users/Gary/Downloads/exeriment/tmp/chatgpt_archive_review/chatgpt_zips_1/Chatgpt%20zips/LIMITATIONS.md)
  - most useful engineering source from the archive
  - turned into backlog items for citation validation, evidence-export help, affidavit templates, and usability improvements

- [Greensky_AI_Legal_Strategy_Template_Liam_Highcock.md](/C:/Users/Gary/Downloads/exeriment/tmp/chatgpt_archive_review/chatgpt_zips_1/Chatgpt%20zips/Greensky_AI_Legal_Strategy_Template_Liam_Highcock.md)
  - useful for workflow ideas such as timeline extraction and bundle assembly
  - still legal-strategy oriented, not forensic-engine oriented

Low-value material:

- raw ChatGPT export zips with `conversations.json`, `chat.html`, and attachments
  - useful only as optional idea mines
  - not reliable enough to drive engine behavior

## Phone Download Archive

Useful files discovered in [Downloadgoogle.zip](/C:/Users/Gary/Downloads/Downloadgoogle.zip):

- `gift_rules_v5.json`
  - useful as an example of centralized rule definitions
  - not suitable as-is because some rules are too simplistic and keyword-driven

- `forensic.vfp.json`
  - useful for envelope structure, synthesis structure, and run metadata ideas
  - shows prior chain-of-custody style packaging

- `forensic_findings.json`
  - useful for seeing failure modes in older actor extraction and synthesis logic
  - should be treated as a cautionary example, not copied directly

- `brain-logs-1.json`
  - useful for pipeline-stage hash logging and chain-hash ideas
  - should inform audit logging in `VerumOmnisV1`

- `findings-summary.txt`
  - useful for compact operator summaries
  - confirms the value of a terse machine-readable run summary

- `contradiction-register.txt`
  - useful as an example of a simple contradiction register output
  - helpful for text export format only

- `VO_Codex_Playbook_2_Forensic_Engine.pdf`
  - likely useful for report/pipeline design
  - not yet mined in detail

Other notable files in the phone archive:

- many sealed PDFs and duplicate reports
- `.sha512.txt`, `.asc`, `.ots`, and manifest artifacts
- prior app APKs
- configs such as `package.json`, `tsconfig.json`, and `metadata.json`

These are useful mostly for packaging and operator workflow reference, not core forensic logic.

## Main Takeaway

The best reusable value from the archives is:

1. limitations and missing-feature lists
2. packaging and audit-log structures
3. manifest and sealing workflow ideas

The least reusable value is:

1. raw ChatGPT conversation dumps
2. narrative legal strategy prose
3. old findings outputs with bad actor extraction
