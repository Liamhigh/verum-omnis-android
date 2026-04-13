package com.verum.omnis.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReadableBriefBuilder {

    private ReadableBriefBuilder() {}

    public static ReadableBriefModel build(ReadableBriefModel.Input input) {
        return new ReadableBriefModel(sanitize(input));
    }

    public static String render(ReadableBriefModel model) {
        if (model == null) {
            return "";
        }
        boolean hasCanonicalConclusion = !model.conclusionWhatHappened.isEmpty()
                || !model.conclusionPrimaryImplicatedActor.isEmpty()
                || !model.conclusionBoundary.isEmpty();
        StringBuilder sb = new StringBuilder();
        sb.append("Case Snapshot\n");
        sb.append("This brief is the short human-readable version of the sealed case file. ");
        sb.append("The sealed evidence, findings package, and audit ledger remain the controlling record.\n\n");
        sb.append("Case ID: ").append(model.caseId).append("\n");
        sb.append("Jurisdiction: ").append(model.jurisdictionName)
                .append(" (").append(model.jurisdictionCode).append(")\n");
        sb.append("Evidence SHA-512: ").append(model.evidenceHashPrefix).append("\n\n");
        sb.append("Guardian-approved certified findings: ")
                .append(model.guardianApprovedCertifiedFindingCount)
                .append("\n");
        sb.append("Verified contradictions: ").append(model.verifiedContradictionCount).append("\n");
        sb.append("Candidate contradiction leads: ").append(model.candidateContradictionCount).append("\n\n");

        if (hasCanonicalConclusion) {
            sb.append("Forensic Conclusion\n");
            if (!model.conclusionWhatHappened.isEmpty()) {
                sb.append("* What happened: ").append(model.conclusionWhatHappened).append("\n");
            }
            if (!model.conclusionPrimaryImplicatedActor.isEmpty()) {
                sb.append("* Primary implicated actor: ").append(model.conclusionPrimaryImplicatedActor).append("\n");
            }
            if (!model.conclusionWhy.isEmpty()) {
                sb.append("* Why this conclusion is leading:\n");
                for (String item : model.conclusionWhy) {
                    sb.append("  - ").append(item).append("\n");
                }
            }
            if (!model.conclusionTimelineHighlights.isEmpty()) {
                sb.append("* How the record unfolds:\n");
                for (String item : model.conclusionTimelineHighlights) {
                    sb.append("  - ").append(item).append("\n");
                }
            }
            if (!model.conclusionOtherLinkedActors.isEmpty()) {
                sb.append("* Other linked actors: ").append(joinStrings(model.conclusionOtherLinkedActors)).append("\n");
            }
            if (!model.conclusionProven.isEmpty()) {
                sb.append("* What the sealed record already proves:\n");
                for (String item : model.conclusionProven) {
                    sb.append("  - ").append(item).append("\n");
                }
            }
            if (!model.conclusionBoundary.isEmpty()) {
                sb.append("* Boundary for this pass: ").append(model.conclusionBoundary).append("\n");
            }
            if (!model.conclusionPages.isEmpty()) {
                sb.append("* Pages to read first: ").append(joinStrings(model.conclusionPages)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("What the Record Shows\n");
        String primaryNarrative = firstNonEmpty(model.truthSummary, model.patternLine);
        if (!primaryNarrative.isEmpty()) {
            sb.append(primaryNarrative).append("\n");
        }
        if (!model.completedHarmLine.isEmpty()
                && !model.completedHarmLine.equalsIgnoreCase(primaryNarrative)) {
            sb.append(model.completedHarmLine).append("\n");
        }
        if (!hasCanonicalConclusion && !model.otherAffectedParties.isEmpty()) {
            sb.append("Other people or entities already tied into the same pattern are ")
                    .append(joinStrings(model.otherAffectedParties))
                    .append(".\n");
        }
        if (!hasCanonicalConclusion && !model.offenceFindings.isEmpty()) {
            sb.append(model.offenceFindings.get(0)).append("\n");
        } else if (!hasCanonicalConclusion && !model.actorConclusion.isEmpty()) {
            sb.append(model.actorConclusion).append("\n");
        }
        if (!model.patternOriginLine.isEmpty()) {
            sb.append("How this pattern shows up in the record: ")
                    .append(model.patternOriginLine)
                    .append("\n");
        }
        if (endsWithSectionOnly(sb, "What the Record Shows\n") && !model.fallbackSummary.isEmpty()) {
            sb.append("This pass still relies on the fallback summary: ")
                    .append(model.fallbackSummary)
                    .append("\n");
        }
        sb.append("\n");

        sb.append("People and Roles\n");
        if (hasCanonicalConclusion) {
            if (model.suppressRoleNarration) {
                sb.append("- Harmed party: this pass does not resolve that role cleanly enough to publish it.\n");
            } else if (!model.primaryHarmedParty.isEmpty()) {
                sb.append("- Harmed party: ").append(model.primaryHarmedParty).append("\n");
            }
            if (!model.conclusionPrimaryImplicatedActor.isEmpty()) {
                sb.append("- Primary implicated actor: ").append(model.conclusionPrimaryImplicatedActor).append("\n");
            }
            if (!model.conclusionOtherLinkedActors.isEmpty()) {
                sb.append("- Other linked actors: ").append(joinStrings(model.conclusionOtherLinkedActors)).append("\n");
            } else if (!model.otherAffectedParties.isEmpty()) {
                sb.append("- Other linked actors: ").append(joinStrings(model.otherAffectedParties)).append("\n");
            }
            if (!model.conclusionBoundary.isEmpty()) {
                sb.append("- Publication boundary: ").append(model.conclusionBoundary).append("\n");
            }
        } else if (model.suppressRoleNarration) {
            sb.append("- Harmed party: this pass does not resolve that role cleanly enough to publish it.\n");
            sb.append("- Publication note: offence and role labels stay out of this section unless the sealed record anchors them separately.\n");
        } else {
            if (!model.primaryHarmedParty.isEmpty()) {
                sb.append("- Harmed party: ").append(model.primaryHarmedParty).append("\n");
            }
            if (!model.offenceFindings.isEmpty()) {
                sb.append("- Direct offence finding: ").append(model.offenceFindings.get(0)).append("\n");
            } else if (!model.actorConclusion.isEmpty()) {
                sb.append("- Primary adverse actor: ").append(model.actorConclusion).append("\n");
            }
        }
        if (!model.otherAffectedParties.isEmpty()) {
            sb.append("- Other affected parties: ").append(joinStrings(model.otherAffectedParties)).append("\n");
        }
        if (!model.behaviouralFindings.isEmpty()) {
            sb.append("- Behavioural aggravation: ").append(model.behaviouralFindings.get(0)).append("\n");
        }
        if (!model.visualFindings.isEmpty()) {
            sb.append("- Visual forensic finding: ").append(model.visualFindings.get(0)).append("\n");
        }
        sb.append("\n");

        sb.append("Certified Findings\n");
        if (model.certifiedFindings.isEmpty()) {
            sb.append("- No guardian-approved certified finding summary was available in this pass.\n");
        } else {
            for (ReadableBriefModel.FindingEntry entry : model.certifiedFindings) {
                String rendered = renderFinding(entry);
                if (rendered.isEmpty()) {
                    continue;
                }
                sb.append("- ").append(rendered).append("\n");
                if (!entry.whyItMatters.isEmpty()) {
                    sb.append("  Why this matters: ").append(entry.whyItMatters).append("\n");
                }
            }
        }
        sb.append("\n");

        sb.append("What Still Needs Proof or Review\n");
        if (model.reviewItems.isEmpty()) {
            sb.append("- Review the sealed audit report and findings package for unresolved items and supporting gaps.\n");
        } else {
            for (String item : model.reviewItems) {
                sb.append("- ").append(item).append("\n");
            }
        }
        sb.append("\n");

        sb.append("Pages to Read First\n");
        if (!model.readFirstPages.isEmpty()) {
            sb.append("- Start with ").append(joinStrings(model.readFirstPages)).append(".\n");
        }
        if (model.evidencePageHints.isEmpty()) {
            sb.append("- Start with the executive summary and the certified findings in the sealed human report.\n");
        } else {
            for (String hint : model.evidencePageHints) {
                sb.append("- ").append(hint).append("\n");
            }
        }
        sb.append("\n");

        sb.append("Recommended Next Steps\n");
        if (model.immediateNextActions.isEmpty()) {
            sb.append("- Read the sealed evidence pages listed above before external escalation.\n");
            sb.append("- Check the audit report for the full contradiction chain and any failure disclosures.\n");
            sb.append("- If a fact is not anchored in the sealed record, treat it as context until supporting evidence is added.\n");
        } else {
            for (String action : model.immediateNextActions) {
                sb.append("- ").append(action).append("\n");
            }
        }
        sb.append("\n");

        sb.append("Record Integrity\n");
        sb.append("This brief is a reading aid, not the controlling record. ");
        sb.append("The audit report, findings package, visual memo, and original sealed evidence remain available in the vault for full review.\n");
        sb.append("Contradiction posture: ").append(model.contradictionPosture).append("\n");
        if (!model.visualExcerpt.isEmpty()) {
            sb.append("\nVisual findings memo excerpt:\n").append(model.visualExcerpt).append("\n");
        }
        if (!model.auditExcerpt.isEmpty()) {
            sb.append("\nAudit excerpt:\n").append(model.auditExcerpt).append("\n");
        }
        return sb.toString().trim();
    }

    private static ReadableBriefModel.Input sanitize(ReadableBriefModel.Input source) {
        ReadableBriefModel.Input out = source == null ? new ReadableBriefModel.Input() : source;
        out.caseId = safeText(out.caseId);
        out.jurisdictionName = safeText(out.jurisdictionName);
        out.jurisdictionCode = safeText(out.jurisdictionCode);
        out.evidenceHashPrefix = safeText(out.evidenceHashPrefix);
        out.truthSummary = cleanNarrativeText(out.truthSummary);
        out.conclusionWhatHappened = cleanNarrativeText(out.conclusionWhatHappened);
        out.conclusionPrimaryImplicatedActor = safeText(out.conclusionPrimaryImplicatedActor);
        out.conclusionWhy = sanitizeStrings(out.conclusionWhy, 4, 220);
        out.conclusionTimelineHighlights = sanitizeStrings(out.conclusionTimelineHighlights, 4, 220);
        out.conclusionOtherLinkedActors = sanitizeStrings(out.conclusionOtherLinkedActors, 6, 120);
        out.conclusionProven = sanitizeStrings(out.conclusionProven, 6, 220);
        out.conclusionBoundary = cleanNarrativeText(out.conclusionBoundary);
        out.conclusionPages = sanitizeStrings(out.conclusionPages, 8, 40);
        out.patternLine = cleanNarrativeText(out.patternLine);
        out.completedHarmLine = cleanNarrativeText(out.completedHarmLine);
        out.patternOriginLine = cleanNarrativeText(out.patternOriginLine);
        out.fallbackSummary = clipText(safeText(out.fallbackSummary), 1200);
        out.primaryHarmedParty = safeText(out.primaryHarmedParty);
        out.actorConclusion = cleanNarrativeText(out.actorConclusion);
        out.contradictionPosture = cleanNarrativeText(out.contradictionPosture);
        out.otherAffectedParties = sanitizeStrings(out.otherAffectedParties, 12, 120);
        out.offenceFindings = sanitizeStrings(out.offenceFindings, 6, 220);
        out.behaviouralFindings = sanitizeStrings(out.behaviouralFindings, 6, 220);
        out.visualFindings = sanitizeStrings(out.visualFindings, 6, 220);
        out.reviewItems = sanitizeStrings(out.reviewItems, 6, 220);
        out.readFirstPages = sanitizeStrings(out.readFirstPages, 10, 40);
        out.evidencePageHints = sanitizeStrings(out.evidencePageHints, 10, 220);
        out.immediateNextActions = sanitizeStrings(out.immediateNextActions, 6, 220);
        out.visualExcerpt = clipText(safeText(out.visualExcerpt), 1200);
        out.auditExcerpt = clipText(safeText(out.auditExcerpt), 1200);

        List<ReadableBriefModel.FindingEntry> sanitizedFindings = new ArrayList<>();
        if (out.certifiedFindings != null) {
            for (ReadableBriefModel.FindingEntry finding : out.certifiedFindings) {
                if (finding == null) {
                    continue;
                }
                sanitizedFindings.add(new ReadableBriefModel.FindingEntry(
                        cleanNarrativeText(finding.title),
                        cleanNarrativeText(finding.summary),
                        cleanNarrativeText(finding.whyItMatters),
                        finding.anchorPages
                ));
                if (sanitizedFindings.size() >= 10) {
                    break;
                }
            }
        }
        out.certifiedFindings = sanitizedFindings;
        return out;
    }

    private static List<String> sanitizeStrings(List<String> source, int limit, int clipLimit) {
        List<String> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (String value : source) {
            String cleaned = clipText(cleanNarrativeText(value), clipLimit);
            if (cleaned.isEmpty()) {
                continue;
            }
            out.add(cleaned);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private static String renderFinding(ReadableBriefModel.FindingEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!entry.title.isEmpty()) {
            sb.append(entry.title);
        }
        if (!entry.summary.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(": ");
            }
            sb.append(entry.summary);
        }
        if (!entry.anchorPages.isEmpty()) {
            sb.append(" (pages ").append(joinPages(entry.anchorPages)).append(")");
        }
        return sb.toString().trim();
    }

    private static boolean endsWithSectionOnly(StringBuilder sb, String sectionHeader) {
        return sb != null && sb.toString().endsWith(sectionHeader);
    }

    private static String firstNonEmpty(String first, String second) {
        if (!safeText(first).isEmpty()) {
            return first;
        }
        return safeText(second).isEmpty() ? "" : second;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanNarrativeText(String value) {
        String cleaned = safeText(value).replace('\n', ' ').replace('\r', ' ').trim();
        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ");
        }
        cleaned = cleaned.replace("[truncated for on-device prompt]", "")
                .replace("...[truncated for on-device prompt]...", "")
                .replace("...[truncated for on-device prompt]", "")
                .trim();
        return clipText(cleaned, 220);
    }

    private static String clipText(String value, int limit) {
        String safe = safeText(value);
        if (safe.length() <= limit) {
            return safe;
        }
        return safe.substring(0, Math.max(0, limit - 3)).trim() + "...";
    }

    private static String joinStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " and " + values.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(i == values.size() - 1 ? ", and " : ", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static String joinPages(List<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            int page = pages.get(i) == null ? 0 : pages.get(i);
            if (page <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(page);
        }
        return sb.toString();
    }
}
