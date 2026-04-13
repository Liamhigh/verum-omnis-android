package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Final publication assembly layer.
 *
 * This class converts the structured engine outputs into a single, shared
 * case picture that the human report, readable brief, and constitutional vault
 * report can all render consistently.
 */
public final class ForensicReportAssembler {

    public static final class FindingCard {
        public final String actor;
        public final String label;
        public final String summary;
        public final int primaryPage;
        public final List<Integer> anchorPages;
        public final String publicationStatus;
        public final String rawFindingStatus;
        public final String contradictionStatus;
        public final List<String> renderWarnings;

        FindingCard(
                String actor,
                String label,
                String summary,
                int primaryPage,
                List<Integer> anchorPages,
                String publicationStatus,
                String rawFindingStatus,
                String contradictionStatus,
                List<String> renderWarnings
        ) {
            this.actor = actor;
            this.label = label;
            this.summary = summary;
            this.primaryPage = primaryPage;
            this.anchorPages = anchorPages;
            this.publicationStatus = publicationStatus;
            this.rawFindingStatus = rawFindingStatus;
            this.contradictionStatus = contradictionStatus;
            this.renderWarnings = renderWarnings;
        }

        public String toPlainLine() {
            StringBuilder sb = new StringBuilder();
            if (!actor.isEmpty()) {
                sb.append(actor).append(": ");
            }
            sb.append(summary);
            if (primaryPage > 0) {
                sb.append(" (page ").append(primaryPage).append(")");
            } else if (!anchorPages.isEmpty()) {
                sb.append(" (pages ").append(joinPages(anchorPages)).append(")");
            }
            return sb.toString().trim();
        }

        public String toTechnicalLine() {
            StringBuilder sb = new StringBuilder(toPlainLine());
            if (!label.isEmpty()) {
                sb.append(" [").append(label).append("]");
            }
            if (!publicationStatus.isEmpty()) {
                sb.append(" [publication ").append(publicationStatus).append("]");
            }
            if (!rawFindingStatus.isEmpty()) {
                sb.append(" [raw ").append(rawFindingStatus).append("]");
            }
            return sb.toString();
        }
    }

    public static final class IssueCard {
        public final String title;
        public final String summary;
        public final String whyItMatters;
        public final List<String> actors;
        public final List<Integer> evidencePages;
        public final String confidence;
        public final List<FindingCard> findings;

        IssueCard(
                String title,
                String summary,
                String whyItMatters,
                List<String> actors,
                List<Integer> evidencePages,
                String confidence,
                List<FindingCard> findings
        ) {
            this.title = title;
            this.summary = summary;
            this.whyItMatters = whyItMatters;
            this.actors = actors;
            this.evidencePages = evidencePages;
            this.confidence = confidence;
            this.findings = findings;
        }

        public String toPlainLine() {
            StringBuilder sb = new StringBuilder();
            sb.append(title);
            if (!summary.isEmpty()) {
                sb.append(": ").append(summary);
            }
            if (!evidencePages.isEmpty()) {
                sb.append(" (pages ").append(joinPages(evidencePages)).append(")");
            }
            return sb.toString().trim();
        }

        public String toTechnicalLine() {
            StringBuilder sb = new StringBuilder(toPlainLine());
            if (!actors.isEmpty()) {
                sb.append(" | actors ").append(joinStrings(actors));
            }
            if (!confidence.isEmpty()) {
                sb.append(" | ").append(confidence);
            }
            return sb.toString();
        }
    }

    public static final class ChronologyEvent {
        public final String dateLabel;
        public final String summary;
        public final List<String> actors;
        public final List<Integer> evidencePages;
        public final String status;

        ChronologyEvent(
                String dateLabel,
                String summary,
                List<String> actors,
                List<Integer> evidencePages,
                String status
        ) {
            this.dateLabel = dateLabel;
            this.summary = summary;
            this.actors = actors;
            this.evidencePages = evidencePages;
            this.status = status;
        }
    }

    private static final class PartyCandidate {
        final String name;
        String role = "";
        int occurrences = 0;
        int headerEvidenceCount = 0;
        int victimCueCount = 0;
        int offenceCueCount = 0;
        int certifiedSupportCount = 0;
        int directHarmSupportCount = 0;
        int adverseSupportCount = 0;
        int synthesisSupportCount = 0;

        PartyCandidate(String name) {
            this.name = name;
        }
    }

    public static final class Assembly {
        public String primaryHarmedParty = "";
        public List<String> otherAffectedParties = new ArrayList<>();
        public String actorConclusion = "";
        public String contradictionPosture = "";
        public List<CanonicalFindingBridge.DirectOffenceFinding> directOffenceFindings = new ArrayList<>();
        public List<CanonicalFindingBridge.BehaviouralAggravationFinding> behaviouralAggravationFindings = new ArrayList<>();
        public List<CanonicalFindingBridge.VisualForgeryFinding> directVisualFindings = new ArrayList<>();
        public List<String> offenceFindings = new ArrayList<>();
        public List<String> behaviouralFindings = new ArrayList<>();
        public List<String> visualFindings = new ArrayList<>();
        public int guardianApprovedCertifiedFindingCount;
        public int verifiedContradictionCount;
        public int candidateContradictionCount;
        public boolean actorConclusionCandidateOnly;
        public List<String> readFirstPages = new ArrayList<>();
        public List<FindingCard> certifiedFindings = new ArrayList<>();
        public List<IssueCard> issueGroups = new ArrayList<>();
        public List<ChronologyEvent> chronology = new ArrayList<>();
    }

    private ForensicReportAssembler() {}

    public static Assembly assemble(AnalysisEngine.ForensicReport report) {
        Assembly out = new Assembly();
        if (report == null) {
            return out;
        }

        FindingPublicationNormalizer.applyToReport(report);
        out.guardianApprovedCertifiedFindingCount = report.normalizedCertifiedFindingCount;

        JSONObject antithesis = report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("antithesis")
                : null;
        out.verifiedContradictionCount = antithesis != null ? antithesis.optInt("verifiedCount", 0) : 0;
        out.candidateContradictionCount = antithesis != null ? antithesis.optInt("candidateCount", 0) : 0;

        out.certifiedFindings = buildFindingCards(report);
        out.issueGroups = buildIssueCards(out.certifiedFindings);
        LinkedHashMap<String, PartyCandidate> partyCandidates = collectPartyCandidates(report, out.certifiedFindings);
        out.primaryHarmedParty = derivePrimaryHarmedParty(report, out.certifiedFindings, partyCandidates);
        out.otherAffectedParties = deriveOtherAffectedParties(report, out.primaryHarmedParty, partyCandidates);
        deriveActorConclusion(report, out, partyCandidates);
        out.contradictionPosture = buildContradictionPosture(out.verifiedContradictionCount, out.candidateContradictionCount);
        String leadActor = resolveLeadAdverseActor(report, out, partyCandidates);
        CanonicalFindingBridge.FindingsBundle typedFindings = CanonicalFindingBridge.build(report, out.issueGroups, leadActor);
        out.directOffenceFindings = typedFindings.offenceFindings;
        out.behaviouralAggravationFindings = typedFindings.behaviouralFindings;
        out.directVisualFindings = typedFindings.visualFindings;
        out.offenceFindings = renderOffenceFindingLines(out.directOffenceFindings);
        out.behaviouralFindings = renderBehaviouralFindingLines(out.behaviouralAggravationFindings);
        out.visualFindings = renderVisualFindingLines(out.directVisualFindings);
        out.chronology = buildChronology(report, out.issueGroups);
        out.readFirstPages = collectReadFirstPages(out.issueGroups, 6);
        return out;
    }

    private static List<FindingCard> buildFindingCards(AnalysisEngine.ForensicReport report) {
        List<FindingCard> out = new ArrayList<>();
        JSONArray findings = FindingPublicationNormalizer.renderableCertifiedFindings(report);
        if (findings == null) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < findings.length(); i++) {
            JSONObject normalized = findings.optJSONObject(i);
            if (!FindingPublicationNormalizer.isPublicationCertified(normalized)) {
                continue;
            }
            FindingCard card = toFindingCard(normalized);
            if (card == null) {
                continue;
            }
            String key = lowerUs(card.actor) + "|" + lowerUs(card.label) + "|" + card.primaryPage + "|" + lowerUs(card.summary);
            if (seen.add(key)) {
                out.add(card);
            }
        }
        return out;
    }

    private static FindingCard toFindingCard(JSONObject normalized) {
        if (normalized == null) {
            return null;
        }
        JSONObject finding = normalized.optJSONObject("finding");
        String actor = canonicalizeName(firstNonEmpty(
                finding != null ? finding.optString("actor", null) : null,
                normalized.optString("actor", null)
        ));
        if (isDiscardedName(actor)) {
            actor = "";
        }

        String label = humanizeType(firstNonEmpty(
                normalized.optString("type", null),
                finding != null ? finding.optString("findingType", null) : null,
                finding != null ? finding.optString("timelineType", null) : null,
                finding != null ? finding.optString("oversightType", null) : null,
                finding != null ? finding.optString("conflictType", null) : null,
                "Certified finding"
        ));

        int primaryPage = firstPositive(
                normalized.optInt("primaryPage", 0),
                finding != null ? finding.optInt("page", 0) : 0
        );
        List<Integer> anchorPages = collectAnchorPages(normalized.optJSONArray("anchorPages"),
                finding != null ? finding.optJSONArray("anchors") : null);

        String summary = buildSummary(normalized, finding, actor, label);
        if (summary.isEmpty()) {
            summary = "Guardian-approved finding drawn from the sealed record.";
        }

        List<String> warnings = collectWarnings(normalized.optJSONArray("renderWarnings"));
        return new FindingCard(
                actor,
                label,
                summary,
                primaryPage,
                anchorPages,
                trimToEmpty(normalized.optString("publicationStatus", "")),
                trimToEmpty(normalized.optString("rawFindingStatus", "")),
                trimToEmpty(normalized.optString("contradictionStatus", "")),
                warnings
        );
    }

    private static String buildSummary(JSONObject normalized, JSONObject finding, String actor, String label) {
        String conflictType = finding != null ? trimToEmpty(finding.optString("conflictType", "")) : "";
        if (!conflictType.isEmpty()) {
            String propositionA = propositionText(finding != null ? finding.optJSONObject("propositionA") : null);
            String propositionB = propositionText(finding != null ? finding.optJSONObject("propositionB") : null);
            if (!propositionA.isEmpty() && !propositionB.isEmpty()) {
                String topic = inferTopic(propositionA + " " + propositionB);
                return sentenceCase(firstNonEmpty(
                        "The record contains a verified contradiction about " + topic + ": \"" + clipText(propositionA, 80)
                                + "\" and \"" + clipText(propositionB, 80) + "\" cannot both stand.",
                        ""
                ));
            }
        }

        String summary = firstNonEmpty(
                normalized.optString("primarySummary", null),
                normalized.optString("summary", null),
                finding != null ? finding.optString("summary", null) : null,
                finding != null ? finding.optString("narrative", null) : null,
                normalized.optString("excerpt", null),
                finding != null ? finding.optString("excerpt", null) : null
        );
        summary = cleanNarrative(summary);
        if (summary.isEmpty()) {
            return "";
        }
        if (!actor.isEmpty()) {
            String lowerSummary = lowerUs(summary);
            String lowerActor = lowerUs(actor);
            if (lowerSummary.startsWith(lowerActor + ":")
                    || lowerSummary.startsWith(lowerActor + " |")
                    || lowerSummary.startsWith(lowerActor + " ")) {
                return sentenceCase(summary);
            }
            if (lowerSummary.contains("current actor picture")
                    || lowerSummary.contains("candidate contradiction-led profile")) {
                return sentenceCase(summary.replace("current actor picture:", "").trim());
            }
        }
        if (!label.isEmpty() && lowerUs(summary).startsWith(lowerUs(label) + " |")) {
            summary = summary.substring(label.length() + 1).trim();
        }
        return sentenceCase(summary);
    }

    private static List<IssueCard> buildIssueCards(List<FindingCard> cards) {
        LinkedHashMap<String, List<FindingCard>> groups = new LinkedHashMap<>();
        for (FindingCard card : cards) {
            String key = buildIssueKey(card);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(card);
        }
        List<IssueCard> out = new ArrayList<>();
        for (List<FindingCard> members : groups.values()) {
            if (members.isEmpty()) {
                continue;
            }
            out.add(toIssueCard(members));
        }
        return out;
    }

    private static IssueCard toIssueCard(List<FindingCard> findings) {
        FindingCard lead = findings.get(0);
        LinkedHashSet<String> actors = new LinkedHashSet<>();
        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        boolean hasVerifiedLanguage = false;
        boolean hasCandidateLanguage = false;
        for (FindingCard finding : findings) {
            if (!finding.actor.isEmpty()) {
                actors.add(finding.actor);
            }
            if (finding.primaryPage > 0) {
                pages.add(finding.primaryPage);
            }
            pages.addAll(finding.anchorPages);
            if (containsAny(finding.summary, "verified contradiction", "resolved inter actor conflict", "cannot both stand")) {
                hasVerifiedLanguage = true;
            }
            if ("CANDIDATE".equalsIgnoreCase(finding.rawFindingStatus)) {
                hasCandidateLanguage = true;
            }
        }
        String topic = inferTopic(lead.label + " " + lead.summary);
        String title = buildIssueTitle(lead, topic);
        String summary = buildIssueSummary(lead, topic, findings.size());
        String whyItMatters = buildWhyItMatters(lead, topic);
        String confidence = hasVerifiedLanguage ? "VERIFIED" : (hasCandidateLanguage ? "CANDIDATE-ROOTED" : lead.publicationStatus);
        return new IssueCard(
                title,
                summary,
                whyItMatters,
                new ArrayList<>(actors),
                new ArrayList<>(pages),
                confidence,
                new ArrayList<>(findings)
        );
    }

    private static String buildIssueKey(FindingCard card) {
        String label = lowerUs(card.label);
        String topic = inferTopic(card.label + " " + card.summary);
        if (containsAny(label, "inter-actor contradiction", "contradiction")) {
            return label + "|" + topic;
        }
        String actor = lowerUs(card.actor);
        if (containsAny(label, "oversight", "document integrity", "execution")) {
            return actor + "|" + label + "|" + topic;
        }
        List<Integer> pages = new ArrayList<>();
        if (card.primaryPage > 0) {
            pages.add(card.primaryPage);
        }
        pages.addAll(card.anchorPages);
        String pageFamily = pageFamilyKey(pages);
        return actor + "|" + label + "|" + topic + "|" + pageFamily;
    }

    private static String buildIssueTitle(FindingCard lead, String topic) {
        String lowerLabel = lowerUs(lead.label);
        if (containsAny(lowerLabel, "inter-actor contradiction", "contradiction")) {
            return "Contradiction on " + sentenceCase(topic);
        }
        if (containsAny(lowerLabel, "financial")) {
            return "Financial signal";
        }
        if (containsAny(lowerLabel, "oversight", "document integrity", "execution")) {
            return "Document execution issue";
        }
        if (containsAny(lowerLabel, "timeline", "chronology")) {
            return "Chronology point";
        }
        return lead.label.isEmpty() ? "Certified issue" : lead.label;
    }

    private static String buildIssueSummary(FindingCard lead, String topic, int duplicatesCollapsed) {
        String summary = cleanNarrative(lead.summary);
        if (containsAny(summary, "verified contradiction", "cannot both stand")) {
            summary = "Two anchored accounts conflict on " + topic + ". This issue card brings that contradiction together in one place.";
        } else if (duplicatesCollapsed > 1) {
            summary = summary + " This report groups " + duplicatesCollapsed + " related anchored findings here so the same point is not repeated.";
        }
        return summary;
    }

    private static String buildWhyItMatters(FindingCard lead, String topic) {
        String lowerLabel = lowerUs(lead.label);
        String lowerSummary = lowerUs(lead.summary);
        if (containsAny(lowerLabel, "inter-actor contradiction", "contradiction") || containsAny(lowerSummary, "cannot both stand")) {
            return "If both accounts cannot be true, this contradiction helps show who needs closer scrutiny on " + topic + ".";
        }
        if (containsAny(lowerLabel, "financial")) {
            return "It helps explain where money, liability, or loss may sit in the record.";
        }
        if (containsAny(lowerSummary, "countersigned", "unsigned", "not countersigned", "not validly executed")) {
            return "It helps answer whether the agreement was ever properly completed and capable of being relied on.";
        }
        if (containsAny(lowerSummary, "vacate", "eviction", "non-renewal", "termination")) {
            return "It links the case to a concrete pressure, removal, or non-renewal event.";
        }
        return "It is one of the clearest anchored issues carried into the published report.";
    }

    private static List<ChronologyEvent> buildChronology(AnalysisEngine.ForensicReport report, List<IssueCard> issueGroups) {
        List<ChronologyEvent> out = new ArrayList<>();
        JSONObject extraction = report != null && report.constitutionalExtraction != null
                ? report.constitutionalExtraction
                : new JSONObject();
        JSONArray timeline = extraction.optJSONArray("timelineAnchorRegister");
        LinkedHashMap<String, ChronologyEvent> seen = new LinkedHashMap<>();
        if (timeline != null) {
            for (int i = 0; i < timeline.length() && seen.size() < 12; i++) {
                JSONObject item = timeline.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String date = trimToEmpty(item.optString("date", ""));
                String summary = cleanNarrative(firstNonEmpty(
                        item.optString("summary", null),
                        item.optString("text", null),
                        item.optString("label", null)
                ));
                if (summary.isEmpty() || isDiscardedNarrative(summary)) {
                    continue;
                }
                if (date.isEmpty() && containsAny(summary,
                        "cryptographically sealed evidence",
                        "national priority indicators",
                        "law-enforcement notice reference",
                        "this isn't just documents")) {
                    continue;
                }
                List<String> actors = new ArrayList<>();
                String actor = canonicalizeName(item.optString("actor", ""));
                if (!isDiscardedName(actor) && isAllowedAffectedPartyName(actor)) {
                    actors.add(actor);
                }
                List<Integer> pages = collectAnchorPages(null, item.optJSONArray("anchors"));
                int page = item.optInt("page", 0);
                if (page > 0 && !pages.contains(page)) {
                    pages.add(0, page);
                }
                String status = date.isEmpty() ? "INFERRED-LIMITED" : "ANCHORED";
                String key = lowerUs(date) + "|" + lowerUs(summary);
                seen.putIfAbsent(key, new ChronologyEvent(
                        date.isEmpty() ? "Date not fixed in this extract" : date,
                        summary,
                        actors,
                        pages,
                        status
                ));
            }
        }
        if (seen.isEmpty()) {
            for (IssueCard issue : issueGroups) {
                if (seen.size() >= 8) {
                    break;
                }
                if (issue.evidencePages.isEmpty()) {
                    continue;
                }
                seen.putIfAbsent(issue.title + "|" + issue.evidencePages.get(0), new ChronologyEvent(
                        "Date not fixed in this extract",
                        issue.summary,
                        issue.actors,
                        issue.evidencePages,
                        "INFERRED-LIMITED"
                ));
            }
        }
        out.addAll(seen.values());
        return out;
    }

    private static String derivePrimaryHarmedParty(
            AnalysisEngine.ForensicReport report,
            List<FindingCard> cards,
            LinkedHashMap<String, PartyCandidate> partyCandidates
    ) {
        PartyCandidate best = bestVictimCandidate(partyCandidates, "");
        if (isStrongVictimCandidate(best)) {
            return best.name;
        }

        JSONObject synthesis = report.forensicSynthesis != null ? report.forensicSynthesis : new JSONObject();
        List<String> victims = collectCanonicalNames(synthesis.optJSONArray("victimActors"));
        if (victims.size() == 1) {
            String synthesisVictim = victims.get(0);
            PartyCandidate synthesisCandidate = partyCandidates.get(lowerUs(synthesisVictim));
            if (!isDiscardedName(synthesisVictim)
                    && ((isStrongVictimCandidate(synthesisCandidate)
                    && (best == null || synthesisVictim.equalsIgnoreCase(best.name)
                    || scoreVictimCandidate(synthesisCandidate) >= scoreVictimCandidate(best)))
                    || (!isStrongVictimCandidate(best) && (synthesisCandidate == null || scoreVictimCandidate(synthesisCandidate) >= 0)))) {
                return synthesisVictim;
            }
        }

        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONArray namedParties = extraction.optJSONArray("namedParties");
        if (namedParties != null) {
            for (int i = 0; i < namedParties.length(); i++) {
                JSONObject party = namedParties.optJSONObject(i);
                if (party == null) {
                    continue;
                }
                String role = trimToEmpty(firstNonEmpty(
                        party.optString("role", null),
                        party.optString("actorClass", null)
                ));
                if (!"VICTIM".equalsIgnoreCase(role) && !"COMPLAINANT".equalsIgnoreCase(role)) {
                    continue;
                }
                String name = canonicalizeName(party.optString("name", ""));
                PartyCandidate candidate = partyCandidates.get(lowerUs(name));
                if (!isDiscardedName(name) && isStrongVictimCandidate(candidate)) {
                    return name;
                }
            }
        }
        return "";
    }

    private static boolean isStrongVictimCandidate(PartyCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        if (scoreVictimCandidate(candidate) < 12) {
            return false;
        }
        return candidate.directHarmSupportCount > 0
                || candidate.victimCueCount > 1
                || candidate.certifiedSupportCount > 0
                || candidate.synthesisSupportCount > 1;
    }

    private static List<String> deriveOtherAffectedParties(
            AnalysisEngine.ForensicReport report,
            String primary,
            LinkedHashMap<String, PartyCandidate> partyCandidates
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        List<PartyCandidate> ranked = new ArrayList<>(partyCandidates.values());
        ranked.sort(Comparator
                .comparingInt(ForensicReportAssembler::scoreVictimCandidate)
                .reversed()
                .thenComparing(candidate -> lowerUs(candidate.name)));
        for (PartyCandidate candidate : ranked) {
            if (out.size() >= 4) {
                break;
            }
            if (!isPublishableAffectedPartyCandidate(candidate, primary)) {
                continue;
            }
            out.add(candidate.name);
        }

        JSONObject synthesis = report.forensicSynthesis != null ? report.forensicSynthesis : new JSONObject();
        for (String victim : collectCanonicalNames(synthesis.optJSONArray("victimActors"))) {
            PartyCandidate candidate = partyCandidates.get(lowerUs(victim));
            if (isPublishableAffectedPartyCandidate(candidate, primary)) {
                out.add(victim);
            }
        }
        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONArray namedParties = extraction.optJSONArray("namedParties");
        if (namedParties != null) {
            for (int i = 0; i < namedParties.length() && out.size() < 4; i++) {
                JSONObject party = namedParties.optJSONObject(i);
                if (party == null) {
                    continue;
                }
                String role = trimToEmpty(firstNonEmpty(
                        party.optString("role", null),
                        party.optString("actorClass", null)
                ));
                if (!"VICTIM".equalsIgnoreCase(role) && !"COMPLAINANT".equalsIgnoreCase(role)) {
                    continue;
                }
                String name = canonicalizeName(party.optString("name", ""));
                PartyCandidate candidate = partyCandidates.get(lowerUs(name));
                if (isPublishableAffectedPartyCandidate(candidate, primary)) {
                    out.add(name);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean isPublishableAffectedPartyCandidate(PartyCandidate candidate, String primary) {
        if (candidate == null || candidate.name.equalsIgnoreCase(primary)) {
            return false;
        }
        if (!isAllowedAffectedPartyName(candidate.name) || scoreVictimCandidate(candidate) < 40) {
            return false;
        }
        if ("ACTOR".equalsIgnoreCase(candidate.role)) {
            return false;
        }
        return candidate.directHarmSupportCount > 0
                || candidate.victimCueCount > 0
                || candidate.synthesisSupportCount > 1
                || candidate.certifiedSupportCount > 0;
    }

    private static void deriveActorConclusion(
            AnalysisEngine.ForensicReport report,
            Assembly out,
            LinkedHashMap<String, PartyCandidate> partyCandidates
    ) {
        JSONObject synthesis = report.forensicSynthesis != null ? report.forensicSynthesis : new JSONObject();
        JSONObject wrongfulActorProfile = synthesis.optJSONObject("wrongfulActorProfile");
        String actor = wrongfulActorProfile != null
                ? canonicalizeName(wrongfulActorProfile.optString("actor", ""))
                : "";
        if (!isUsableActorName(actor, out.primaryHarmedParty)) {
            actor = "";
        }
        int verifiedForActor = wrongfulActorProfile != null
                ? wrongfulActorProfile.optInt("verifiedContradictionCount", 0)
                : 0;
        if (actor.isEmpty()) {
            PartyCandidate adverse = bestAdverseActorCandidate(partyCandidates, out.primaryHarmedParty);
            if (adverse != null && scoreActorCandidate(adverse) > 0) {
                actor = adverse.name;
            }
        }
        out.actorConclusionCandidateOnly = verifiedForActor <= 0;
        if (actor.isEmpty()) {
            out.actorConclusion = "The record supports a structured case theory, but this pass does not justify naming one main adverse actor yet.";
            out.actorConclusionCandidateOnly = true;
            return;
        }
        if (verifiedForActor > 0 || out.verifiedContradictionCount > 0) {
            out.actorConclusion = "The record currently points most strongly to " + actor + " as the main adverse actor.";
            out.actorConclusionCandidateOnly = false;
            return;
        }
        out.actorConclusion = "The record currently points mainly to " + actor + ", but that remains a working conclusion until contradiction review matures further.";
    }

    private static String buildContradictionPosture(int verifiedContradictions, int candidateContradictions) {
        if (verifiedContradictions > 0) {
            return "The contradiction review has matured: verified contradictions are present in the sealed record.";
        }
        if (candidateContradictions > 0) {
            return "The record already shows contradiction pressure, but it has not yet matured into a verified contradiction. Keep the actor picture provisional.";
        }
        return "No verified contradiction matured in this pass. Read the certified findings as the main evidence base and keep conclusions cautious.";
    }

    private static List<String> renderOffenceFindingLines(List<CanonicalFindingBridge.DirectOffenceFinding> findings) {
        List<String> out = new ArrayList<>();
        if (findings == null) {
            return out;
        }
        for (CanonicalFindingBridge.DirectOffenceFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            String line = trimToEmpty(finding.toReportLine());
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static List<String> renderBehaviouralFindingLines(List<CanonicalFindingBridge.BehaviouralAggravationFinding> findings) {
        List<String> out = new ArrayList<>();
        if (findings == null) {
            return out;
        }
        for (CanonicalFindingBridge.BehaviouralAggravationFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            String line = trimToEmpty(finding.toReportLine());
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static List<String> renderVisualFindingLines(List<CanonicalFindingBridge.VisualForgeryFinding> findings) {
        List<String> out = new ArrayList<>();
        if (findings == null) {
            return out;
        }
        for (CanonicalFindingBridge.VisualForgeryFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            String line = trimToEmpty(finding.toReportLine());
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static List<String> buildOffenceFindings(
            AnalysisEngine.ForensicReport report,
            Assembly out,
            LinkedHashMap<String, PartyCandidate> partyCandidates
    ) {
        LinkedHashSet<String> findings = new LinkedHashSet<>();
        String actor = resolveLeadAdverseActor(report, out, partyCandidates);
        if (report.topLiabilities != null) {
            for (String item : report.topLiabilities) {
                String offence = normalizeOffenceLabel(item);
                if (offence.isEmpty()) {
                    continue;
                }
                IssueCard support = findSupportingIssueForOffence(offence, out.issueGroups);
                StringBuilder line = new StringBuilder();
                if (!actor.isEmpty()) {
                    line.append(actor).append(" is linked in the sealed record to the offence of ").append(offence);
                } else {
                    line.append("The sealed record supports the offence of ").append(offence);
                }
                if (support != null && !trimToEmpty(support.summary).isEmpty()) {
                    line.append(": ").append(trimToEmpty(support.summary));
                } else {
                    line.append(".");
                }
                if (support != null && !support.evidencePages.isEmpty()) {
                    line.append(" (pages ").append(joinPages(support.evidencePages)).append(")");
                }
                findings.add(ensureSentence(line.toString()));
                if (findings.size() >= 6) {
                    break;
                }
            }
        }
        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONArray subjects = extraction.optJSONArray("criticalLegalSubjects");
        if (subjects != null) {
            for (int i = 0; i < subjects.length() && findings.size() < 8; i++) {
                JSONObject subject = subjects.optJSONObject(i);
                if (subject == null) {
                    continue;
                }
                String offence = normalizeOffenceLabel(subject.optString("subject", ""));
                if (offence.isEmpty()) {
                    continue;
                }
                String excerpt = cleanNarrative(firstNonEmpty(
                        subject.optString("summary", null),
                        subject.optString("excerpt", null),
                        subject.optString("description", null)
                ));
                String line = (!actor.isEmpty() ? actor + " is linked in the sealed record to the offence of " + offence
                        : "The sealed record supports the offence of " + offence)
                        + (excerpt.isEmpty() ? "." : ": " + excerpt);
                findings.add(ensureSentence(line));
            }
        }
        return new ArrayList<>(findings);
    }

    private static List<String> buildBehaviouralFindings(AnalysisEngine.ForensicReport report) {
        LinkedHashSet<String> findings = new LinkedHashSet<>();
        collectBehaviouralMatches(findings, report.patternAnalysis != null ? report.patternAnalysis.optJSONArray("matches") : null);
        collectBehaviouralMatches(findings, report.vulnerabilityAnalysis != null ? report.vulnerabilityAnalysis.optJSONArray("matches") : null);
        JSONObject vulnerability = report.vulnerabilityAnalysis != null ? report.vulnerabilityAnalysis : new JSONObject();
        JSONArray indicators = vulnerability.optJSONArray("indicators");
        if (indicators != null) {
            for (int i = 0; i < indicators.length() && findings.size() < 6; i++) {
                String indicator = trimToEmpty(indicators.optString(i, ""));
                if (!indicator.isEmpty()) {
                    findings.add(ensureSentence("Behavioural aggravation finding: " + indicator));
                }
            }
        }
        return new ArrayList<>(findings);
    }

    private static void collectBehaviouralMatches(Set<String> findings, JSONArray matches) {
        if (matches == null) {
            return;
        }
        for (int i = 0; i < matches.length() && findings.size() < 6; i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match == null) {
                continue;
            }
            String reportLanguage = trimToEmpty(match.optString("reportLanguage", ""));
            String evidenceNote = trimToEmpty(match.optString("evidenceNote", ""));
            String line = firstNonEmpty(reportLanguage, evidenceNote);
            if (!line.isEmpty()) {
                findings.add(ensureSentence("Behavioural aggravation finding: " + line));
            }
        }
    }

    private static List<String> buildVisualFindings(AnalysisEngine.ForensicReport report) {
        LinkedHashSet<String> findings = new LinkedHashSet<>();
        JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
        JSONArray visualFindings = nativeEvidence.optJSONArray("visualFindings");
        if (visualFindings == null) {
            return new ArrayList<>(findings);
        }
        for (int i = 0; i < visualFindings.length() && findings.size() < 6; i++) {
            JSONObject item = visualFindings.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = trimToEmpty(item.optString("type", ""));
            String severity = lowerUs(item.optString("severity", ""));
            int page = item.optInt("page", 0);
            if ("SIGNATURE_ZONE_OVERLAY_SUSPECTED".equals(type) && ("high".equals(severity) || "critical".equals(severity))) {
                findings.add(ensureSentence("Forgery finding: page " + page + " contains a signature-zone overlay anomaly consistent with a pasted, masked, or whiteout signature panel"));
            } else if ("SIGNATURE_REGION_EMPTY".equals(type) && ("medium".equals(severity) || "high".equals(severity))) {
                findings.add(ensureSentence("Execution finding: page " + page + " carries a materially blank signature region where a signature block would be expected"));
            } else if ("SIGNATURE_MARKS_NOT_FOUND".equals(type) && ("medium".equals(severity) || "high".equals(severity))) {
                findings.add(ensureSentence("Execution finding: page " + page + " shows no strong signature stroke pattern in the expected signing zone"));
            }
        }
        return new ArrayList<>(findings);
    }

    private static String resolveLeadAdverseActor(
            AnalysisEngine.ForensicReport report,
            Assembly out,
            LinkedHashMap<String, PartyCandidate> partyCandidates
    ) {
        JSONObject synthesis = report.forensicSynthesis != null ? report.forensicSynthesis : new JSONObject();
        JSONObject wrongfulActorProfile = synthesis.optJSONObject("wrongfulActorProfile");
        String actor = wrongfulActorProfile != null
                ? canonicalizeName(wrongfulActorProfile.optString("actor", ""))
                : "";
        if (isUsableActorName(actor, out.primaryHarmedParty)) {
            return actor;
        }
        PartyCandidate adverse = bestAdverseActorCandidate(partyCandidates, out.primaryHarmedParty);
        return adverse != null && scoreActorCandidate(adverse) > 0 ? adverse.name : "";
    }

    private static IssueCard findSupportingIssueForOffence(String offence, List<IssueCard> issueGroups) {
        if (issueGroups == null || issueGroups.isEmpty()) {
            return null;
        }
        String lower = lowerUs(offence);
        for (IssueCard issue : issueGroups) {
            String corpus = lowerUs(issue.title + " " + issue.summary + " " + issue.whyItMatters);
            if ("fraud".equals(lower) && containsAny(corpus, "fraud", "misrepresentation", "false", "contradiction", "cannot both stand")) {
                return issue;
            }
            if (containsAny(lower, "cybercrime", "unauthorized access") && containsAny(corpus, "google archive", "archive request", "unauthorized", "cyber")) {
                return issue;
            }
            if (containsAny(lower, "fiduciary", "shareholder oppression", "unlawful exclusion") && containsAny(corpus, "shareholder", "proceeded with the deal", "private meeting", "excluded")) {
                return issue;
            }
            if (containsAny(lower, "tampering", "spoliation", "forgery") && containsAny(corpus, "forg", "tamper", "cropped", "overlay", "signature")) {
                return issue;
            }
            if (containsAny(lower, "financial diversion", "unlawful enrichment", "theft") && containsAny(corpus, "invoice", "profit", "payment", "unpaid share", "goodwill")) {
                return issue;
            }
        }
        return issueGroups.get(0);
    }

    private static String normalizeOffenceLabel(String raw) {
        String lower = lowerUs(raw);
        if (lower.isEmpty()) {
            return "";
        }
        if (containsAny(lower, "fraud", "fraudulent purpose")) {
            return "fraud";
        }
        if (containsAny(lower, "cybercrime", "unauthorized access", "digital interference")) {
            return "unauthorized access or cyber interference";
        }
        if (containsAny(lower, "fiduciary")) {
            return "breach of fiduciary duty";
        }
        if (containsAny(lower, "shareholder oppression", "unlawful exclusion")) {
            return "shareholder oppression or unlawful exclusion";
        }
        if (containsAny(lower, "tamper", "spoliation", "fraudulent evidence", "forgery")) {
            return "evidence tampering or forgery";
        }
        if (containsAny(lower, "unlawful enrichment", "financial irregularity", "goodwill", "theft")) {
            return "unlawful enrichment or financial diversion";
        }
        return trimToEmpty(raw);
    }

    private static List<String> collectReadFirstPages(List<IssueCard> cards, int limit) {
        LinkedHashSet<String> pages = new LinkedHashSet<>();
        for (IssueCard card : cards) {
            if (pages.size() >= limit) {
                break;
            }
            for (Integer page : card.evidencePages) {
                if (pages.size() >= limit) {
                    break;
                }
                if (page != null && page > 0) {
                    pages.add("p. " + page);
                }
            }
        }
        return new ArrayList<>(pages);
    }

    private static List<String> collectCanonicalNames(JSONArray names) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (names == null) {
            return new ArrayList<>(out);
        }
        for (int i = 0; i < names.length(); i++) {
            String canonical = canonicalizeName(names.optString(i, ""));
            if (!isDiscardedName(canonical) && isAllowedAffectedPartyName(canonical)) {
                out.add(canonical);
            }
        }
        return new ArrayList<>(out);
    }

    private static LinkedHashMap<String, PartyCandidate> collectPartyCandidates(
            AnalysisEngine.ForensicReport report,
            List<FindingCard> cards
    ) {
        LinkedHashMap<String, PartyCandidate> out = new LinkedHashMap<>();

        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONArray namedParties = extraction.optJSONArray("namedParties");
        if (namedParties != null) {
            for (int i = 0; i < namedParties.length(); i++) {
                JSONObject party = namedParties.optJSONObject(i);
                if (party == null) {
                    continue;
                }
                String rawName = trimToEmpty(party.optString("name", ""));
                if (isRejectedRawPublicationName(rawName)) {
                    continue;
                }
                String name = canonicalizeName(rawName);
                if (!isLikelyPublicationPartyName(name)) {
                    continue;
                }
                PartyCandidate candidate = out.computeIfAbsent(lowerUs(name), ignored -> new PartyCandidate(name));
                candidate.role = firstNonEmpty(candidate.role,
                        party.optString("role", null),
                        party.optString("actorClass", null));
                candidate.occurrences += Math.max(1, party.optInt("occurrences", 1));
                candidate.headerEvidenceCount += party.optInt("headerEvidenceCount", 0);
                candidate.victimCueCount += party.optInt("victimCueCount", 0);
                candidate.offenceCueCount += party.optInt("offenceCueCount", 0);
            }
        }

        JSONObject synthesis = report.forensicSynthesis != null ? report.forensicSynthesis : new JSONObject();
        JSONArray victims = synthesis.optJSONArray("victimActors");
        if (victims != null) {
            for (int i = 0; i < victims.length(); i++) {
                String rawName = trimToEmpty(victims.optString(i, ""));
                if (isRejectedRawPublicationName(rawName)) {
                    continue;
                }
                String name = canonicalizeName(rawName);
                if (!isLikelyPublicationPartyName(name)) {
                    continue;
                }
                PartyCandidate candidate = out.computeIfAbsent(lowerUs(name), ignored -> new PartyCandidate(name));
                candidate.synthesisSupportCount += 1;
                if (candidate.role.isEmpty()) {
                    candidate.role = "VICTIM";
                }
            }
        }

        for (FindingCard card : cards) {
            String actor = canonicalizeName(card.actor);
            if (!isLikelyPublicationPartyName(actor)) {
                continue;
            }
            PartyCandidate candidate = out.computeIfAbsent(lowerUs(actor), ignored -> new PartyCandidate(actor));
            if (looksLikeContradictionFinding(card)) {
                candidate.adverseSupportCount += 1;
            } else {
                candidate.certifiedSupportCount += 1;
                if (looksLikeDirectHarmFinding(card)) {
                    candidate.directHarmSupportCount += 1;
                }
            }
        }

        return out;
    }

    private static PartyCandidate bestVictimCandidate(Map<String, PartyCandidate> candidates, String excludedName) {
        PartyCandidate best = null;
        int bestScore = Integer.MIN_VALUE;
        for (PartyCandidate candidate : candidates.values()) {
            if (!excludedName.isEmpty() && candidate.name.equalsIgnoreCase(excludedName)) {
                continue;
            }
            int score = scoreVictimCandidate(candidate);
            if (score > bestScore || (score == bestScore && best != null
                    && lowerUs(candidate.name).compareTo(lowerUs(best.name)) < 0)) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static PartyCandidate bestAdverseActorCandidate(Map<String, PartyCandidate> candidates, String harmedParty) {
        PartyCandidate best = null;
        int bestScore = Integer.MIN_VALUE;
        for (PartyCandidate candidate : candidates.values()) {
            if (candidate.name.equalsIgnoreCase(harmedParty)) {
                continue;
            }
            int score = scoreActorCandidate(candidate);
            if (score > bestScore || (score == bestScore && best != null
                    && lowerUs(candidate.name).compareTo(lowerUs(best.name)) < 0)) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static int scoreVictimCandidate(PartyCandidate candidate) {
        if (candidate == null || !isLikelyPublicationPartyName(candidate.name)) {
            return Integer.MIN_VALUE;
        }
        int score = (candidate.certifiedSupportCount * 40)
                + (candidate.victimCueCount * 10)
                + (candidate.directHarmSupportCount * 60)
                + (candidate.synthesisSupportCount * 6)
                + candidate.occurrences
                - candidate.headerEvidenceCount
                - (candidate.adverseSupportCount * 20);
        if ("VICTIM".equalsIgnoreCase(candidate.role) || "COMPLAINANT".equalsIgnoreCase(candidate.role)) {
            score += 20;
        }
        if (candidate.victimCueCount == 0) {
            score -= 10;
        }
        if (candidate.directHarmSupportCount == 0
                && candidate.victimCueCount == 0
                && candidate.synthesisSupportCount == 0) {
            score -= 35;
        }
        if ("ACTOR".equalsIgnoreCase(candidate.role)) {
            score -= 20;
        }
        if (!"VICTIM".equalsIgnoreCase(candidate.role)
                && !"COMPLAINANT".equalsIgnoreCase(candidate.role)
                && candidate.synthesisSupportCount == 0) {
            score -= 35;
        }
        return score;
    }

    private static int scoreActorCandidate(PartyCandidate candidate) {
        if (candidate == null || !isLikelyPublicationPartyName(candidate.name)) {
            return Integer.MIN_VALUE;
        }
        int score = (candidate.certifiedSupportCount * 35)
                + (candidate.adverseSupportCount * 45)
                + (candidate.offenceCueCount * 6)
                + (candidate.occurrences * 2)
                - (candidate.headerEvidenceCount * 3);
        if ("ACTOR".equalsIgnoreCase(candidate.role)) {
            score += 20;
        } else {
            score -= 15;
        }
        if (candidate.victimCueCount > candidate.offenceCueCount) {
            score -= 15;
        }
        return score;
    }

    private static boolean looksLikeContradictionFinding(FindingCard card) {
        if (card == null) {
            return false;
        }
        return containsAny(
                lowerUs(card.label + " " + card.summary + " " + card.contradictionStatus),
                "inter-actor contradiction",
                "contradiction",
                "cannot both stand",
                "admission-versus-denial",
                "admission versus denial",
                "conflicting",
                "conflict"
        );
    }

    private static boolean looksLikeDirectHarmFinding(FindingCard card) {
        if (card == null) {
            return false;
        }
        String corpus = lowerUs(card.label + " " + card.summary);
        return containsAny(corpus,
                "unpaid share",
                "profit-share reconciliation gap",
                "payment withheld",
                "withheld payment",
                "owed",
                "loss",
                "damages",
                "vacate",
                "eviction",
                "non-renewal",
                "terminated",
                "termination notice",
                "taken",
                "stolen",
                "data theft",
                "attempted access",
                "not countersigned",
                "not signed",
                "not returned",
                "not validly executed");
    }

    private static boolean isUsableActorName(String actor, String harmedParty) {
        return isLikelyPublicationPartyName(actor)
                && !actor.equalsIgnoreCase(harmedParty)
                && !containsAny(lowerUs(actor), "tel", "side at the");
    }

    private static boolean looksLikeAdverseActor(String actor, AnalysisEngine.ForensicReport report) {
        JSONObject synthesis = report.forensicSynthesis != null ? report.forensicSynthesis : new JSONObject();
        JSONObject wrongfulActorProfile = synthesis.optJSONObject("wrongfulActorProfile");
        if (wrongfulActorProfile == null) {
            return false;
        }
        String wrongfulActor = canonicalizeName(wrongfulActorProfile.optString("actor", ""));
        return !wrongfulActor.isEmpty() && wrongfulActor.equalsIgnoreCase(actor);
    }

    private static List<Integer> collectAnchorPages(JSONArray normalizedAnchorPages, JSONArray findingAnchors) {
        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        if (normalizedAnchorPages != null) {
            for (int i = 0; i < normalizedAnchorPages.length(); i++) {
                int page = normalizedAnchorPages.optInt(i, 0);
                if (page > 0) {
                    pages.add(page);
                }
            }
        }
        if (findingAnchors != null) {
            for (int i = 0; i < findingAnchors.length(); i++) {
                JSONObject anchor = findingAnchors.optJSONObject(i);
                if (anchor == null) {
                    continue;
                }
                int page = anchor.optInt("page", 0);
                if (page > 0) {
                    pages.add(page);
                }
            }
        }
        return new ArrayList<>(pages);
    }

    private static List<String> collectWarnings(JSONArray warnings) {
        List<String> out = new ArrayList<>();
        if (warnings == null) {
            return out;
        }
        for (int i = 0; i < warnings.length(); i++) {
            String warning = trimToEmpty(warnings.optString(i, ""));
            if (!warning.isEmpty()) {
                out.add(warning);
            }
        }
        return out;
    }

    private static String propositionText(JSONObject proposition) {
        if (proposition == null) {
            return "";
        }
        return trimToEmpty(proposition.optString("text", ""));
    }

    private static String inferTopic(String corpus) {
        String lower = lowerUs(corpus);
        if (containsAny(lower, "exclusivity", "agreement", "contract")) {
            return "the agreement";
        }
        if (containsAny(lower, "deal", "proceeded with the deal", "export")) {
            return "the deal";
        }
        if (containsAny(lower, "invoice", "payment", "profit", "share")) {
            return "the financial arrangement";
        }
        if (containsAny(lower, "access", "account", "email", "drive")) {
            return "access and correspondence";
        }
        return "the same material fact";
    }

    private static String cleanNarrative(String value) {
        String trimmed = trimToEmpty(value)
                .replace('\n', ' ')
                .replace('\r', ' ');
        while (trimmed.contains("  ")) {
            trimmed = trimmed.replace("  ", " ");
        }
        trimmed = trimmed.replace("[truncated for on-device prompt]", "")
                .replace("...[truncated for on-device prompt]...", "")
                .replace("...[truncated for on-device prompt]", "")
                .replace("[...truncated...]", "")
                .replace("...", "...");
        trimmed = trimmed.replace("Current actor picture:", "")
                .replace("current actor picture:", "")
                .replace("Contradiction posture:", "")
                .trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1).trim();
        }
        return clipText(trimmed, 220);
    }

    private static String sentenceCase(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private static String ensureSentence(String value) {
        String cleaned = cleanNarrative(value);
        if (cleaned.isEmpty()) {
            return "";
        }
        char last = cleaned.charAt(cleaned.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return cleaned;
        }
        return cleaned + ".";
    }

    private static String humanizeType(String rawType) {
        String cleaned = trimToEmpty(rawType).replace('_', ' ');
        if (cleaned.isEmpty()) {
            return "";
        }
        String lower = cleaned.toLowerCase(Locale.US);
        if ("inter actor conflict".equals(lower)) {
            return "Inter-actor contradiction";
        }
        if ("proposition conflict".equals(lower)) {
            return "Contradiction";
        }
        return sentenceCase(lower);
    }

    private static String canonicalizeName(String value) {
        return ActorNameNormalizer.canonicalizePublicationActor(value);
    }

    private static boolean isRejectedRawPublicationName(String value) {
        String lower = lowerUs(value);
        return containsAny(lower, "founder", "modus operandi", "side at the");
    }

    private static boolean isDiscardedName(String value) {
        String lower = lowerUs(value);
        if (lower.isEmpty()) {
            return true;
        }
        return containsAny(lower,
                "legal violation",
                "order issues",
                "federal law",
                "accused cite",
                "march email",
                "development office",
                "verifiable global exposure",
                "fcdo correspondence",
                "afghan relocations",
                "assistance policy",
                "southbridge legal",
                "bar association",
                "marine living resources",
                "criminal restitution",
                "quantified loss",
                "damaged equipment",
                "fish finder",
                "total direct financial",
                "common law",
                "protection order",
                "living resources",
                "common purpose",
                "trade license",
                "dual liability",
                "data theft",
                "commit fraud",
                "cyber forgery",
                "investigating officer",
                "screenshot",
                "account",
                "immediate engagement required",
                "thanks",
                "this",
                "you",
                "what",
                "was",
                "even",
                "tel",
                "company",
                "breach",
                "matters",
                "individuals",
                "order",
                "deal memo",
                "january",
                "march",
                "april",
                "founder",
                "modus operandi",
                "port edward",
                "unlawful enrichment",
                "south africa",
                "fraud",
                "yours",
                "that",
                "side at the",
                "page");
    }

    private static boolean isAllowedAffectedPartyName(String value) {
        String lower = lowerUs(value);
        if (lower.isEmpty()) {
            return false;
        }
        if (isDiscardedName(value)) {
            return false;
        }
        return !containsAny(lower,
                "correspondence",
                "office",
                "department",
                "policy",
                "exposure",
                "relocations",
                "association",
                "resources",
                "restitution",
                "quantified loss",
                "damaged equipment",
                "fish finder",
                "direct financial",
                "common law",
                "protection order",
                "legal",
                "issues",
                "law",
                "memo",
                "report");
    }

    private static boolean isLikelyPublicationPartyName(String value) {
        String canonical = canonicalizeName(value);
        String lower = lowerUs(canonical);
        if (lower.isEmpty() || isDiscardedName(canonical)) {
            return false;
        }
        if (containsAny(lower, "founder", "modus operandi", "side at the")) {
            return false;
        }
        if (canonical.contains("'")) {
            return false;
        }
        String[] tokens = canonical.split("\\s+");
        if (tokens.length < 2) {
            return false;
        }
        for (String token : tokens) {
            String clean = token.replaceAll("[^A-Za-z]", "");
            if (clean.isEmpty()) {
                return false;
            }
            String lowerToken = clean.toLowerCase(Locale.US);
            if ("and".equals(lowerToken)
                    || "in".equals(lowerToken)
                    || "at".equals(lowerToken)
                    || "the".equals(lowerToken)
                    || "of".equals(lowerToken)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDiscardedNarrative(String summary) {
        String lower = lowerUs(summary);
        return lower.isEmpty()
                || containsAny(lower,
                "current actor picture",
                "contradiction posture",
                "forensic correction & addendum",
                "please ensure this judgment",
                "fraud docket",
                "survived the primary extraction filter");
    }

    private static boolean containsAny(String corpus, String... terms) {
        if (corpus == null) {
            return false;
        }
        String lower = lowerUs(corpus);
        for (String term : terms) {
            if (!trimToEmpty(term).isEmpty() && lower.contains(term.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static int firstPositive(int... values) {
        if (values == null) {
            return 0;
        }
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lowerUs(String value) {
        return trimToEmpty(value).toLowerCase(Locale.US);
    }

    private static String clipText(String value, int maxChars) {
        String trimmed = trimToEmpty(value);
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private static String joinPages(List<Integer> pages) {
        List<String> out = new ArrayList<>();
        for (Integer page : pages) {
            if (page != null && page > 0) {
                out.add(String.valueOf(page));
            }
        }
        return out.isEmpty() ? "" : joinStrings(out);
    }

    private static String pageFamilyKey(List<Integer> pages) {
        List<String> out = new ArrayList<>();
        int count = 0;
        for (Integer page : pages) {
            if (page == null || page <= 0) {
                continue;
            }
            out.add(String.valueOf(page));
            count++;
            if (count >= 3) {
                break;
            }
        }
        return out.isEmpty() ? "no-pages" : joinStrings(out);
    }

    private static String joinStrings(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(i == items.size() - 1 ? ", and " : ", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}

