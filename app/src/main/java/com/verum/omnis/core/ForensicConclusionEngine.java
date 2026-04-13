package com.verum.omnis.core;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ForensicConclusionEngine {

    public static final class ImplicatedActor {
        public String actor = "";
        public String role = "UNRESOLVED";
        public List<String> basis = new ArrayList<>();
        public List<Integer> anchorPages = new ArrayList<>();
        public String supportLevel = "INSUFFICIENT";

        JSONObject toJson() {
            JSONObject out = new JSONObject();
            putSafe(out, "actor", actor);
            putSafe(out, "role", role);
            putSafe(out, "basis", new JSONArray(basis));
            putSafe(out, "anchorPages", new JSONArray(anchorPages));
            putSafe(out, "supportLevel", supportLevel);
            return out;
        }
    }

    public static final class LegalExposure {
        public String actor = "";
        public List<String> categories = new ArrayList<>();
        public String status = "EXPOSURE_ONLY";

        JSONObject toJson() {
            JSONObject out = new JSONObject();
            putSafe(out, "actor", actor);
            putSafe(out, "categories", new JSONArray(categories));
            putSafe(out, "status", status);
            return out;
        }
    }

    public static final class ForensicProposition {
        public String actor = "";
        public String conduct = "";
        public String timestamp = "UNRESOLVED_TIME";
        public List<Integer> anchorPages = new ArrayList<>();
        public String offenceMapping = "";
        public String status = "CERTIFIED_CONDUCT";
        public String publicationBoundary = "";

        JSONObject toJson() {
            JSONObject out = new JSONObject();
            putSafe(out, "actor", actor);
            putSafe(out, "conduct", conduct);
            putSafe(out, "timestamp", timestamp);
            putSafe(out, "anchorPages", new JSONArray(anchorPages));
            putSafe(out, "offenceMapping", offenceMapping);
            putSafe(out, "status", status);
            putSafe(out, "publicationBoundary", publicationBoundary);
            return out;
        }
    }

    public static final class ForensicConclusion {
        public String processingStatus = "INDETERMINATE";
        public String narrativeType = "FACT_PATTERN";
        public List<String> whatHappened = new ArrayList<>();
        public List<ImplicatedActor> implicatedActors = new ArrayList<>();
        public String strongestConclusion = "";
        public String publicationBoundary = "";
        public List<String> proofGaps = new ArrayList<>();
        public List<String> certifiedForensicConduct = new ArrayList<>();
        public List<String> stronglyAllegedExposure = new ArrayList<>();
        public List<String> frameworkMapping = new ArrayList<>();
        public List<LegalExposure> legalExposure = new ArrayList<>();
        public List<ForensicProposition> forensicPropositions = new ArrayList<>();
        public GuiltGate.Result guiltGate = new GuiltGate.Result();

        public JSONObject toJson() {
            JSONObject out = new JSONObject();
            putSafe(out, "processingStatus", processingStatus);
            putSafe(out, "narrativeType", narrativeType);
            putSafe(out, "whatHappened", new JSONArray(whatHappened));
            JSONArray implicated = new JSONArray();
            for (ImplicatedActor actor : implicatedActors) {
                implicated.put(actor.toJson());
            }
            putSafe(out, "implicatedActors", implicated);
            putSafe(out, "strongestConclusion", strongestConclusion);
            putSafe(out, "publicationBoundary", publicationBoundary);
            putSafe(out, "proofGaps", new JSONArray(proofGaps));
            putSafe(out, "certifiedForensicConduct", new JSONArray(certifiedForensicConduct));
            putSafe(out, "stronglyAllegedExposure", new JSONArray(stronglyAllegedExposure));
            putSafe(out, "frameworkMapping", new JSONArray(frameworkMapping));
            JSONArray exposure = new JSONArray();
            for (LegalExposure item : legalExposure) {
                exposure.put(item.toJson());
            }
            putSafe(out, "legalExposure", exposure);
            JSONArray propositions = new JSONArray();
            for (ForensicProposition proposition : forensicPropositions) {
                propositions.put(proposition.toJson());
            }
            putSafe(out, "forensicPropositions", propositions);
            JSONObject gate = new JSONObject();
            putSafe(gate, "allowed", guiltGate.allowed);
            putSafe(gate, "reason", guiltGate.reason);
            putSafe(gate, "missingElements", new JSONArray(guiltGate.missingElements));
            putSafe(out, "guiltGate", gate);
            return out;
        }
    }

    private static final class ActorAccumulator {
        final String actor;
        final LinkedHashSet<String> basis = new LinkedHashSet<>();
        final LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        int issueHits;
        int offenceHits;
        int timelineHits;
        int contradictionHits;
        int affectedHits;

        ActorAccumulator(String actor) {
            this.actor = actor;
        }

        int score() {
            return issueHits * 4
                    + offenceHits * 6
                    + timelineHits * 2
                    + contradictionHits * 3
                    + affectedHits;
        }
    }

    private ForensicConclusionEngine() {}

    public static JSONObject buildJson(AnalysisEngine.ForensicReport report) {
        return build(report).toJson();
    }

    public static ForensicConclusion build(AnalysisEngine.ForensicReport report) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        return build(report, assembled);
    }

    static ForensicConclusion build(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        ForensicConclusion out = new ForensicConclusion();
        if (report == null || assembled == null) {
            out.publicationBoundary = "The engine could not build a forensic conclusion because the report object was incomplete.";
            out.proofGaps.add("Complete report context");
            return out;
        }

        JSONObject truthFrame = TruthInCodeEngine.buildTruthFrame(report, assembled, false);
        out.processingStatus = deriveProcessingStatus(report);
        out.whatHappened = deriveWhatHappened(truthFrame, assembled);
        out.implicatedActors = deriveImplicatedActors(report, assembled);
        out.guiltGate = GuiltGate.evaluate(report, assembled, primaryImplicatedActorNameFromList(out.implicatedActors));
        out.narrativeType = deriveNarrativeType(out, assembled);
        out.certifiedForensicConduct = deriveCertifiedForensicConduct(assembled);
        out.stronglyAllegedExposure = deriveStronglyAllegedExposure(report, assembled);
        out.frameworkMapping = deriveFrameworkMapping(report);
        out.legalExposure = deriveLegalExposure(assembled, out);
        out.publicationBoundary = ConclusionTemplates.buildPublicationBoundary(out);
        out.forensicPropositions = deriveForensicPropositions(report, assembled, out);
        out.strongestConclusion = ConclusionTemplates.buildStrongestConclusion(out);
        out.proofGaps = deriveProofGaps(report, assembled, truthFrame, out.guiltGate);
        return out;
    }

    public static String primaryImplicatedActorName(ForensicConclusion conclusion) {
        if (conclusion == null) {
            return "";
        }
        return primaryImplicatedActorNameFromList(conclusion.implicatedActors);
    }

    private static String primaryImplicatedActorNameFromList(List<ImplicatedActor> actors) {
        if (actors == null) {
            return "";
        }
        for (ImplicatedActor actor : actors) {
            if (actor != null && "PRIMARY_IMPLICATED".equals(actor.role)) {
                return trimToEmpty(actor.actor);
            }
        }
        return "";
    }

    private static List<String> deriveWhatHappened(JSONObject truthFrame, ForensicReportAssembler.Assembly assembled) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        String truthSummary = trimToEmpty(truthFrame.optString("whatHappened", ""));
        if (!truthSummary.isEmpty()) {
            lines.add(ensureSentence(truthSummary));
        }
        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (issue == null) {
                continue;
            }
            String summary = ensureSentence(trimToEmpty(issue.summary));
            if (!summary.isEmpty()) {
                lines.add(summary);
            }
            if (lines.size() >= 3) {
                break;
            }
        }
        if (lines.isEmpty()) {
            lines.add("The sealed record contains anchored findings, but they did not compress into one stronger fact pattern in this pass.");
        }
        return new ArrayList<>(lines);
    }

    private static List<ImplicatedActor> deriveImplicatedActors(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        LinkedHashSet<String> knownActors = collectKnownActors(assembled);
        LinkedHashMap<String, ActorAccumulator> actors = new LinkedHashMap<>();

        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (issue == null) {
                continue;
            }
            for (String rawActor : issue.actors) {
                String actor = canonicalizeActor(rawActor, knownActors);
                if (actor.isEmpty()) {
                    continue;
                }
                ActorAccumulator bucket = actors.computeIfAbsent(actor, ActorAccumulator::new);
                bucket.issueHits++;
                bucket.pages.addAll(issue.evidencePages);
                addBasis(bucket, issue.summary);
            }
        }

        for (CanonicalFindingBridge.DirectOffenceFinding finding : assembled.directOffenceFindings) {
            if (finding == null) {
                continue;
            }
            String actor = canonicalizeActor(finding.actor, knownActors);
            if (actor.isEmpty()) {
                continue;
            }
            ActorAccumulator bucket = actors.computeIfAbsent(actor, ActorAccumulator::new);
            bucket.offenceHits++;
            bucket.pages.addAll(finding.evidencePages);
            addBasis(bucket, finding.summary);
        }

        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
        JSONArray contradictions = diagnostics.optJSONArray("contradictionRegister");
        if (contradictions != null) {
            for (int i = 0; i < contradictions.length(); i++) {
                JSONObject item = contradictions.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String corpus = item.toString();
                for (Map.Entry<String, ActorAccumulator> entry : actors.entrySet()) {
                    if (corpus.toLowerCase(Locale.US).contains(entry.getKey().toLowerCase(Locale.US))) {
                        entry.getValue().contradictionHits++;
                    }
                }
            }
        }

        String affectedParty = canonicalizeActor(assembled.primaryHarmedParty, knownActors);
        if (!affectedParty.isEmpty()) {
            ActorAccumulator bucket = actors.computeIfAbsent(affectedParty, ActorAccumulator::new);
            bucket.affectedHits += 2;
            addBasis(bucket, "This party is separately carried as an affected party in the current publication layer.");
        }
        for (String item : assembled.otherAffectedParties) {
            String actor = canonicalizeActor(item, knownActors);
            if (actor.isEmpty()) {
                continue;
            }
            ActorAccumulator bucket = actors.computeIfAbsent(actor, ActorAccumulator::new);
            bucket.affectedHits++;
        }

        List<ActorAccumulator> ranked = new ArrayList<>(actors.values());
        ranked.sort(Comparator.comparingInt(ActorAccumulator::score).reversed()
                .thenComparing(acc -> acc.actor.toLowerCase(Locale.US)));

        String primaryImplicated = "";
        for (ActorAccumulator accumulator : ranked) {
            if (!accumulator.actor.equalsIgnoreCase(affectedParty) && isEligiblePrimaryImplicated(accumulator)) {
                primaryImplicated = accumulator.actor;
                break;
            }
        }

        List<ImplicatedActor> out = new ArrayList<>();
        if (!affectedParty.isEmpty()) {
            ImplicatedActor harmed = toImplicatedActor(actors.get(affectedParty), "AFFECTED_PARTY");
            if (harmed != null) {
                out.add(harmed);
            }
        }
        for (ActorAccumulator accumulator : ranked) {
            if (accumulator.actor.equalsIgnoreCase(affectedParty)) {
                continue;
            }
            String role = "UNRESOLVED";
            if (!primaryImplicated.isEmpty() && accumulator.actor.equalsIgnoreCase(primaryImplicated)) {
                role = "PRIMARY_IMPLICATED";
            } else if (isEligibleSecondaryImplicated(accumulator)) {
                role = "SECONDARY_IMPLICATED";
            }
            ImplicatedActor actor = toImplicatedActor(accumulator, role);
            if (actor != null) {
                out.add(actor);
            }
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    private static List<ForensicProposition> deriveForensicPropositions(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            ForensicConclusion conclusion
    ) {
        LinkedHashSet<String> knownActors = collectKnownActors(assembled);
        LinkedHashMap<String, ForensicProposition> propositions = new LinkedHashMap<>();

        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (issue == null) {
                continue;
            }
            String conduct = trimToEmpty(issue.summary);
            if (conduct.isEmpty()) {
                continue;
            }
            for (String rawActor : issue.actors) {
                String actor = canonicalizeActor(rawActor, knownActors);
                if (actor.isEmpty() || issue.evidencePages == null || issue.evidencePages.isEmpty()) {
                    continue;
                }
                String key = actor.toLowerCase(Locale.US) + "|" + conduct.toLowerCase(Locale.US);
                ForensicProposition proposition = propositions.computeIfAbsent(key, ignored -> new ForensicProposition());
                proposition.actor = actor;
                proposition.conduct = ensureSentence(conduct);
                proposition.anchorPages = mergeAnchorPages(proposition.anchorPages, issue.evidencePages);
                proposition.status = "CERTIFIED_CONDUCT";
                proposition.publicationBoundary = conclusion.guiltGate.allowed
                        ? conclusion.publicationBoundary
                        : "This is a forensic conclusion, not a judicial verdict.";
                if (trimToEmpty(proposition.offenceMapping).isEmpty()) {
                    proposition.offenceMapping = findOffenceMappingForActor(actor, issue.summary, assembled, conclusion);
                }
            }
        }

        if (propositions.isEmpty()) {
            for (CanonicalFindingBridge.DirectOffenceFinding finding : assembled.directOffenceFindings) {
                if (finding == null) {
                    continue;
                }
                String actor = canonicalizeActor(finding.actor, knownActors);
                String conduct = trimToEmpty(finding.summary);
                if (actor.isEmpty() || conduct.isEmpty() || finding.evidencePages == null || finding.evidencePages.isEmpty()) {
                    continue;
                }
                ForensicProposition proposition = new ForensicProposition();
                proposition.actor = actor;
                proposition.conduct = ensureSentence(conduct);
                proposition.anchorPages = mergeAnchorPages(proposition.anchorPages, finding.evidencePages);
                proposition.offenceMapping = trimToEmpty(finding.offence);
                proposition.status = "CERTIFIED_CONDUCT";
                proposition.publicationBoundary = conclusion.guiltGate.allowed
                        ? conclusion.publicationBoundary
                        : "This is a forensic conclusion, not a judicial verdict.";
                propositions.put(actor.toLowerCase(Locale.US) + "|" + conduct.toLowerCase(Locale.US), proposition);
            }
        }

        return new ArrayList<>(propositions.values());
    }

    private static List<String> deriveProofGaps(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject truthFrame,
            GuiltGate.Result guiltGate
    ) {
        LinkedHashSet<String> gaps = new LinkedHashSet<>();
        JSONArray openQuestions = truthFrame.optJSONArray("openQuestions");
        if (openQuestions != null) {
            for (int i = 0; i < openQuestions.length(); i++) {
                String line = trimToEmpty(openQuestions.optString(i, ""));
                if (!line.isEmpty()) {
                    gaps.add(ensureSentence(line));
                }
            }
        }
        for (String missing : guiltGate.missingElements) {
            if (!trimToEmpty(missing).isEmpty()) {
                gaps.add(ensureSentence(missing + " remains incomplete."));
            }
        }
        if (assembled.candidateContradictionCount > 0 && assembled.verifiedContradictionCount <= 0) {
            gaps.add("The contradiction layer still needs a verified paired conflict before the engine can publish final guilt language.");
        }
        if (gaps.isEmpty()) {
            gaps.add("No material proof gap was carried into the conclusion layer beyond the sealed audit record.");
        }
        return new ArrayList<>(gaps);
    }

    private static List<LegalExposure> deriveLegalExposure(
            ForensicReportAssembler.Assembly assembled,
            ForensicConclusion conclusion
    ) {
        LinkedHashMap<String, LinkedHashSet<String>> categoriesByActor = new LinkedHashMap<>();
        for (CanonicalFindingBridge.DirectOffenceFinding finding : assembled.directOffenceFindings) {
            if (finding == null) {
                continue;
            }
            String actor = canonicalizeActor(finding.actor, collectKnownActors(assembled));
            if (actor.isEmpty()) {
                actor = primaryImplicatedActorName(conclusion);
            }
            if (actor.isEmpty()) {
                continue;
            }
            categoriesByActor.computeIfAbsent(actor, ignored -> new LinkedHashSet<>()).add(trimToEmpty(finding.offence));
        }
        List<LegalExposure> out = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : categoriesByActor.entrySet()) {
            LegalExposure item = new LegalExposure();
            item.actor = entry.getKey();
            item.categories = new ArrayList<>(entry.getValue());
            if (conclusion.guiltGate.allowed) {
                item.status = "CHARGE_READY";
            } else if ("PROVEN_MISCONDUCT".equals(conclusion.narrativeType)) {
                item.status = "CLAIM_READY";
            } else {
                item.status = "EXPOSURE_ONLY";
            }
            out.add(item);
        }
        return out;
    }

    private static List<String> deriveCertifiedForensicConduct(ForensicReportAssembler.Assembly assembled) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (issue == null) {
                continue;
            }
            String summary = trimToEmpty(issue.summary);
            if (!summary.isEmpty()) {
                lines.add(ensureSentence(summary));
            }
            if (lines.size() >= 4) {
                break;
            }
        }
        if (lines.isEmpty() && !assembled.certifiedFindings.isEmpty()) {
            for (ForensicReportAssembler.FindingCard finding : assembled.certifiedFindings) {
                if (finding == null) {
                    continue;
                }
                lines.add(ensureSentence(finding.summary));
                if (lines.size() >= 4) {
                    break;
                }
            }
        }
        return new ArrayList<>(lines);
    }

    private static List<String> deriveStronglyAllegedExposure(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        if (report != null && report.topLiabilities != null) {
            for (String label : report.topLiabilities) {
                String cleaned = normalizeExposureLabel(label);
                if (cleaned.isEmpty() || isFrameworkLabel(cleaned) || isCertifiedConductLabel(cleaned, assembled)) {
                    continue;
                }
                lines.add(cleaned);
                if (lines.size() >= 6) {
                    break;
                }
            }
        }
        return new ArrayList<>(lines);
    }

    private static String findOffenceMappingForActor(
            String actor,
            String conduct,
            ForensicReportAssembler.Assembly assembled,
            ForensicConclusion conclusion
    ) {
        String loweredConduct = trimToEmpty(conduct).toLowerCase(Locale.US);
        for (CanonicalFindingBridge.DirectOffenceFinding finding : assembled.directOffenceFindings) {
            if (finding == null) {
                continue;
            }
            String findingActor = canonicalizeActor(finding.actor, collectKnownActors(assembled));
            if (!trimToEmpty(findingActor).equalsIgnoreCase(trimToEmpty(actor))) {
                continue;
            }
            String offence = trimToEmpty(finding.offence);
            if (!offence.isEmpty()) {
                return offence;
            }
            String summary = trimToEmpty(finding.summary).toLowerCase(Locale.US);
            if (!summary.isEmpty() && (summary.contains(loweredConduct) || loweredConduct.contains(summary))) {
                return trimToEmpty(finding.offence);
            }
        }
        for (LegalExposure exposure : conclusion.legalExposure) {
            if (exposure == null || !trimToEmpty(exposure.actor).equalsIgnoreCase(trimToEmpty(actor))) {
                continue;
            }
            for (String category : exposure.categories) {
                String trimmed = trimToEmpty(category);
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }

    private static List<Integer> mergeAnchorPages(List<Integer> existing, List<Integer> incoming) {
        LinkedHashSet<Integer> merged = new LinkedHashSet<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (incoming != null) {
            for (Integer page : incoming) {
                if (page != null && page > 0) {
                    merged.add(page);
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private static List<String> deriveFrameworkMapping(AnalysisEngine.ForensicReport report) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        if (report != null && report.topLiabilities != null) {
            for (String label : report.topLiabilities) {
                String cleaned = trimToEmpty(label);
                if (isFrameworkLabel(cleaned)) {
                    lines.add(cleaned);
                }
            }
        }
        if (report != null && report.legalReferences != null) {
            for (String item : report.legalReferences) {
                String cleaned = trimToEmpty(item);
                if (cleaned.isEmpty()) {
                    continue;
                }
                if (cleaned.toLowerCase(Locale.US).contains("precca")
                        || cleaned.toLowerCase(Locale.US).contains("company")
                        || cleaned.toLowerCase(Locale.US).contains("access")
                        || cleaned.toLowerCase(Locale.US).contains("password")) {
                    lines.add(cleaned);
                }
                if (lines.size() >= 6) {
                    break;
                }
            }
        }
        return new ArrayList<>(lines);
    }

    private static String deriveNarrativeType(
            ForensicConclusion conclusion,
            ForensicReportAssembler.Assembly assembled
    ) {
        if (conclusion.guiltGate.allowed) {
            return "GUILT_READY";
        }
        if (assembled.verifiedContradictionCount > 0
                && !assembled.directOffenceFindings.isEmpty()
                && !primaryImplicatedActorName(conclusion).isEmpty()) {
            return "PROVEN_MISCONDUCT";
        }
        if (!primaryImplicatedActorName(conclusion).isEmpty()) {
            return "IMPLICATION_PATTERN";
        }
        return "FACT_PATTERN";
    }

    private static ImplicatedActor toImplicatedActor(ActorAccumulator accumulator, String role) {
        if (accumulator == null || trimToEmpty(accumulator.actor).isEmpty()) {
            return null;
        }
        ImplicatedActor out = new ImplicatedActor();
        out.actor = accumulator.actor;
        out.role = trimToEmpty(role).isEmpty() ? "UNRESOLVED" : role;
        out.basis = new ArrayList<>(accumulator.basis);
        out.anchorPages = new ArrayList<>(accumulator.pages);
        out.supportLevel = toSupportLevel(accumulator.score());
        return out;
    }

    private static String deriveProcessingStatus(AnalysisEngine.ForensicReport report) {
        JSONObject diagnostics = report != null && report.diagnostics != null
                ? report.diagnostics
                : new JSONObject();
        JSONObject overall = report != null && report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("overall")
                : null;
        boolean indeterminateDueToConcealment = diagnostics.optBoolean("indeterminateDueToConcealment", false);
        if (indeterminateDueToConcealment) {
            return "INDETERMINATE";
        }
        JSONObject antithesis = report != null && report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("antithesis")
                : null;
        int verifiedContradictions = diagnostics.optInt("verifiedContradictionCount",
                antithesis != null ? antithesis.optInt("verifiedCount", 0) : 0);
        int candidateContradictions = diagnostics.optInt("candidateContradictionCount",
                antithesis != null ? antithesis.optInt("candidateCount", 0) : 0);
        StringBuilder corpus = new StringBuilder();
        corpus.append(" ").append(diagnostics.optString("processingStatus", ""));
        corpus.append(" ").append(diagnostics.optString("processingReason", ""));
        if (overall != null) {
            corpus.append(" ").append(overall.optString("status", ""));
            corpus.append(" ").append(overall.optString("reason", ""));
        }
        String lowered = corpus.toString().toLowerCase(Locale.US);
        boolean hasCoverageGapLanguage = lowered.contains("coverage gap")
                || lowered.contains("request_more_evidence")
                || lowered.contains("mature consensus")
                || lowered.contains("immature consensus")
                || lowered.contains("concealment");
        if ((verifiedContradictions <= 0 && candidateContradictions > 0) || hasCoverageGapLanguage) {
            return "DETERMINATE_WITH_GAPS";
        }
        return "DETERMINATE";
    }

    private static boolean isCertifiedConductLabel(String value, ForensicReportAssembler.Assembly assembled) {
        String lower = trimToEmpty(value).toLowerCase(Locale.US);
        if (lower.isEmpty()) {
            return false;
        }
        if (lower.contains("financial irregularities")
                || lower.contains("concealment")
                || lower.contains("password")
                || lower.contains("access-code")
                || lower.contains("precca")) {
            return false;
        }
        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            String corpus = (issue.title + " " + issue.summary).toLowerCase(Locale.US);
            if (corpus.contains(lower) || lower.contains("document") && corpus.contains("document")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFrameworkLabel(String value) {
        String lower = trimToEmpty(value).toLowerCase(Locale.US);
        return lower.contains("framework")
                || lower.contains("password")
                || lower.contains("access-code")
                || lower.contains("company-law")
                || lower.contains("institution track")
                || lower.contains("institutional");
    }

    private static String normalizeExposureLabel(String raw) {
        String lower = trimToEmpty(raw).toLowerCase(Locale.US);
        if (lower.isEmpty()) {
            return "";
        }
        if (lower.contains("concealment")) {
            return "concealment";
        }
        if (lower.contains("gaslighting") || lower.contains("evasion") || lower.contains("emotional exploitation")) {
            return "evasion/gaslighting";
        }
        if (lower.contains("financial irregular")) {
            return "financial irregularities";
        }
        if (lower.contains("unlawful enrichment") || lower.contains("financial diversion") || lower.contains("goodwill") || lower.contains("theft")) {
            return "unlawful enrichment or financial diversion";
        }
        if (lower.contains("precca")) {
            return "possible PRECCA Section 34 non-reporting exposure";
        }
        return trimToEmpty(raw);
    }

    private static String canonicalizeActor(String raw, LinkedHashSet<String> knownActors) {
        String actor = ActorNameNormalizer.canonicalizePublicationActor(raw);
        if (actor.isEmpty()) {
            return "";
        }
        String lower = actor.toLowerCase(Locale.US);
        if (lower.equals("unknown")
                || lower.equals("unresolved actor")
                || lower.equals("this")
                || lower.equals("you")
                || lower.equals("yours")
                || lower.equals("tel")
                || lower.equals("modus operandi")) {
            return "";
        }
        return resolveActorAlias(actor, knownActors);
    }

    private static LinkedHashSet<String> collectKnownActors(ForensicReportAssembler.Assembly assembled) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (assembled == null) {
            return out;
        }
        addKnownActor(out, assembled.primaryHarmedParty);
        for (String item : assembled.otherAffectedParties) {
            addKnownActor(out, item);
        }
        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (issue == null) {
                continue;
            }
            for (String actor : issue.actors) {
                addKnownActor(out, actor);
            }
        }
        for (ForensicReportAssembler.FindingCard finding : assembled.certifiedFindings) {
            if (finding != null) {
                addKnownActor(out, finding.actor);
            }
        }
        for (CanonicalFindingBridge.DirectOffenceFinding finding : assembled.directOffenceFindings) {
            if (finding != null) {
                addKnownActor(out, finding.actor);
            }
        }
        return out;
    }

    private static void addKnownActor(LinkedHashSet<String> sink, String actor) {
        String cleaned = trimToEmpty(actor);
        if (!cleaned.isEmpty()) {
            sink.add(cleaned);
        }
    }

    private static String resolveActorAlias(String actor, LinkedHashSet<String> knownActors) {
        String trimmed = trimToEmpty(actor);
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.US);
        for (String known : knownActors) {
            String normalizedKnown = trimToEmpty(known);
            if (normalizedKnown.isEmpty()) {
                continue;
            }
            String knownLower = normalizedKnown.toLowerCase(Locale.US);
            if (knownLower.equals(lower)) {
                return normalizedKnown;
            }
            if (looksLikeAliasMatch(lower, knownLower)) {
                return normalizedKnown;
            }
        }
        return trimmed;
    }

    private static boolean looksLikeAliasMatch(String aliasLower, String fullLower) {
        if (aliasLower.isEmpty() || fullLower.isEmpty() || aliasLower.equals(fullLower)) {
            return false;
        }
        if (Arrays.asList("des", "desmond", "dez", "gary", "liam", "marius", "kevin", "nealy", "silicia", "silicia scheppel-barnard").contains(aliasLower)) {
            return fullLower.startsWith(aliasLower + " ") || fullLower.startsWith(aliasLower);
        }
        return aliasLower.length() >= 3
                && !aliasLower.contains(" ")
                && fullLower.startsWith(aliasLower + " ");
    }

    private static boolean isEligiblePrimaryImplicated(ActorAccumulator accumulator) {
        if (accumulator == null) {
            return false;
        }
        int conductSupport = accumulator.offenceHits + accumulator.issueHits;
        return accumulator.offenceHits > 0
                || accumulator.issueHits >= 2
                || (conductSupport > 0 && accumulator.score() >= 8);
    }

    private static boolean isEligibleSecondaryImplicated(ActorAccumulator accumulator) {
        if (accumulator == null) {
            return false;
        }
        int conductSupport = accumulator.offenceHits + accumulator.issueHits;
        return conductSupport > 0 || accumulator.affectedHits > 0;
    }

    private static void addBasis(ActorAccumulator accumulator, String line) {
        String cleaned = trimToEmpty(line);
        if (accumulator == null || cleaned.isEmpty()) {
            return;
        }
        accumulator.basis.add(clipText(ensureSentence(cleaned), 220));
    }

    private static String toSupportLevel(int score) {
        if (score >= 14) return "VERY_HIGH";
        if (score >= 8) return "HIGH";
        if (score >= 4) return "MODERATE";
        if (score >= 1) return "LOW";
        return "INSUFFICIENT";
    }

    private static String ensureSentence(String value) {
        String cleaned = trimToEmpty(value)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ");
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        char last = cleaned.charAt(cleaned.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return cleaned;
        }
        return cleaned + ".";
    }

    private static String clipText(String value, int limit) {
        String cleaned = trimToEmpty(value);
        if (cleaned.length() <= limit) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(0, limit - 3)).trim() + "...";
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void putSafe(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}

