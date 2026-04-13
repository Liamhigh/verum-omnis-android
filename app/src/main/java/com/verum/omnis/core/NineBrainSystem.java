package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class NineBrainSystem {

    private NineBrainSystem() {}

    public static JSONObject build(Context context, File sourceFile, AnalysisEngine.ForensicReport report) {
        JSONObject root = new JSONObject();
        try {
            ConstitutionGovernance governance = ConstitutionGovernance.load(context);
            JSONObject diagnostics = safeObject(report.diagnostics);
            JSONObject extraction = safeObject(report.constitutionalExtraction);
            JSONObject nativeEvidence = safeObject(report.nativeEvidence);
            JSONObject continuity = safeObject(report.truthContinuityAnalysis);
            JSONObject patternAnalysis = safeObject(report.patternAnalysis);
            JSONObject vulnerabilityAnalysis = safeObject(report.vulnerabilityAnalysis);
            JSONObject rndAnalysis = safeObject(report.rndAnalysis);

            JSONArray brains = new JSONArray();
            brains.put(buildB1(diagnostics));
            brains.put(buildB2(extraction, nativeEvidence));
            brains.put(buildB3(extraction));
            brains.put(buildB4(diagnostics, safeObject(report.behavioralProfile)));
            brains.put(buildB5(extraction, continuity));
            brains.put(buildB6(extraction, diagnostics, report.ledgerEntry != null));
            brains.put(buildB7(
                    safeObject(report.legalBrainContext),
                    extraction,
                    report.legalReferences,
                    report.topLiabilities,
                    report.jurisdiction,
                    report.jurisdictionName
            ));
            brains.put(buildB8(sourceFile, nativeEvidence));
            brains.put(buildB9(patternAnalysis, vulnerabilityAnalysis, rndAnalysis));

            root.put("systemId", "verum_omnis_nine_brain_v1");
            root.put("votingBrainCount", 8);
            root.put("advisoryBrainCount", 1);
            root.put("governance", governance.toJson());
            root.put("brains", brains);
            root.put("consensus", buildConsensus(report, brains, governance));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    private static JSONObject buildB1(JSONObject diagnostics) throws JSONException {
        int verified = diagnostics.optInt("verifiedContradictionCount", 0);
        int candidate = diagnostics.optInt("candidateContradictionCount", 0);
        int rejected = diagnostics.optInt("rejectedContradictionCount", 0);
        JSONArray contradictionRegister = diagnostics.optJSONArray("contradictionRegister");
        JSONObject leadVerified = findFirstByStatus(contradictionRegister, "VERIFIED");
        JSONObject brain = baseBrain("B1", "Contradiction Engine", true);
        brain.put("engaged", verified + candidate + rejected > 0);
        brain.put("findingCount", verified);
        brain.put("candidateCount", candidate);
        brain.put("rejectedCount", rejected);
        brain.put("keyPages", collectPages(contradictionRegister, 6));
        brain.put("themes", stringArray("contradictions", "paired propositions"));
        brain.put("status", verified > 0 ? "ACTIVE" : candidate > 0 ? "CANDIDATE_ONLY" : "NO_SIGNAL");
        brain.put("confidence", verified > 0 ? "HIGH" : candidate > 0 ? "MODERATE" : "LOW");
        brain.put("summary", verified > 0
                ? buildContradictionSummary(contradictionRegister, true)
                : candidate > 0
                ? buildContradictionSummary(contradictionRegister, false)
                : "No contradiction survived as a verified engine output in this pass.");
        brain.put("sourceRegisters", stringArray("diagnostics.contradictionRegister"));
        return brain;
    }

    private static JSONObject buildB2(JSONObject extraction, JSONObject nativeEvidence) throws JSONException {
        JSONArray integrity = extraction.optJSONArray("documentIntegrityFindings");
        JSONArray visuals = nativeEvidence.optJSONArray("visualFindings");
        int integrityCount = arrayLength(integrity);
        int visualMediumHigh = countVisualFindings(visuals, true);
        JSONObject brain = baseBrain("B2", "Document and Image Forensics", true);
        brain.put("engaged", integrityCount > 0 || visualMediumHigh > 0);
        brain.put("findingCount", integrityCount);
        brain.put("candidateCount", visualMediumHigh);
        brain.put("rejectedCount", 0);
        brain.put("keyPages", mergePages(collectPages(integrity, 4), collectVisualPages(visuals, 4)));
        brain.put("themes", stringArray("document integrity", "visual tamper review"));
        brain.put("status", integrityCount > 0 ? "ACTIVE" : visualMediumHigh > 0 ? "CANDIDATE_ONLY" : "NO_SIGNAL");
        brain.put("confidence", integrityCount > 0 ? "HIGH" : visualMediumHigh > 0 ? "MODERATE" : "LOW");
        brain.put("summary", integrityCount > 0
                ? "Primary document-integrity findings survived extraction, supported by visual review cues."
                : visualMediumHigh > 0
                ? "Visual or page-integrity anomalies were detected, but they remain candidate-level signals."
                : "No material document or image-forensic signal survived as a primary finding in this pass.");
        brain.put("sourceRegisters", stringArray("constitutionalExtraction.documentIntegrityFindings", "nativeEvidence.visualFindings"));
        return brain;
    }

    private static JSONObject buildB3(JSONObject extraction) throws JSONException {
        JSONArray timeline = extraction.optJSONArray("timelineAnchorRegister");
        JSONArray actorConduct = extraction.optJSONArray("actorConductRegister");
        int commsCount = countTimelineEvents(timeline,
                "DATED_COMMUNICATION", "COMPLAINT_ESCALATION", "LAW_ENFORCEMENT_NOTICE");
        int escalationCount = countConductTypes(actorConduct, "NOTICE_AND_ESCALATION", "AUTHORITY_AND_STANDING");
        JSONObject brain = baseBrain("B3", "Communications Integrity", true);
        brain.put("engaged", commsCount + escalationCount > 0);
        brain.put("findingCount", commsCount);
        brain.put("candidateCount", escalationCount);
        brain.put("rejectedCount", 0);
        brain.put("keyPages", mergePages(collectPages(timeline, 5), collectPages(actorConduct, 3)));
        brain.put("themes", stringArray("communications", "notices", "escalation"));
        brain.put("status", commsCount > 0 ? "ACTIVE" : escalationCount > 0 ? "CANDIDATE_ONLY" : "NO_SIGNAL");
        brain.put("confidence", commsCount > 0 ? "HIGH" : escalationCount > 0 ? "MODERATE" : "LOW");
        brain.put("summary", commsCount > 0
                ? "Dated communications and notice events were extracted into the constitutional record."
                : escalationCount > 0
                ? "Escalation-related communications were detected but remain a supporting layer."
                : "No communication-integrity signal survived as a primary finding in this pass.");
        brain.put("sourceRegisters", stringArray("constitutionalExtraction.timelineAnchorRegister", "constitutionalExtraction.actorConductRegister"));
        return brain;
    }

    private static JSONObject buildB4(JSONObject diagnostics, JSONObject behavioralProfile) throws JSONException {
        double languageAnomalies = behavioralProfile.optDouble("languageAnomalies", 0.0d);
        double timelineConflicts = behavioralProfile.optDouble("timelineConflicts", 0.0d);
        double metadataSuspicion = behavioralProfile.optDouble("metadataSuspicion", 0.0d);
        int evasion = diagnostics.optInt("evasion", 0);
        boolean engaged = languageAnomalies > 0.0d || timelineConflicts > 0.0d || metadataSuspicion > 0.0d || evasion > 0;
        JSONObject brain = baseBrain("B4", "Behavioral and Linguistic Patterns", true);
        brain.put("engaged", engaged);
        brain.put("findingCount", evasion);
        brain.put("candidateCount", engaged ? 1 : 0);
        brain.put("rejectedCount", 0);
        brain.put("themes", stringArray("behavioral profile", "language anomalies", "evasion"));
        brain.put("status", evasion > 0 ? "ACTIVE" : engaged ? "ADVISORY_SIGNAL" : "NO_SIGNAL");
        brain.put("confidence", evasion > 0 ? "MODERATE" : engaged ? "LOW" : "LOW");
        brain.put("summary", engaged
                ? "Behavioral scoring is present as a support layer and should be read as contextual rather than dispositive."
                : "No behavioral support layer was populated beyond baseline defaults.");
        brain.put("sourceRegisters", stringArray("behavioralProfile", "diagnostics.evasion"));
        return brain;
    }

    private static JSONObject buildB5(JSONObject extraction, JSONObject continuity) throws JSONException {
        JSONArray timeline = extraction.optJSONArray("timelineAnchorRegister");
        int timelineCount = arrayLength(timeline);
        String assessment = continuity.optString("overallAssessment", "");
        JSONObject brain = baseBrain("B5", "Timeline and Continuity", true);
        brain.put("engaged", timelineCount > 0 || !assessment.isEmpty());
        brain.put("findingCount", timelineCount);
        brain.put("candidateCount", continuity.optInt("brokenLinkCount", 0));
        brain.put("rejectedCount", 0);
        brain.put("keyPages", collectPages(timeline, 6));
        brain.put("themes", stringArray("timeline", "continuity", "sequence"));
        brain.put("status", timelineCount > 0 ? "ACTIVE" : !assessment.isEmpty() ? "ADVISORY_SIGNAL" : "NO_SIGNAL");
        brain.put("confidence", timelineCount > 0 ? "HIGH" : !assessment.isEmpty() ? "MODERATE" : "LOW");
        brain.put("summary", !assessment.isEmpty()
                ? "Timeline and continuity analysis reports " + assessment + "."
                : "No structured timeline or continuity analysis was available.");
        brain.put("sourceRegisters", stringArray("constitutionalExtraction.timelineAnchorRegister", "truthContinuityAnalysis"));
        return brain;
    }

    private static JSONObject buildB6(JSONObject extraction, JSONObject diagnostics, boolean hasLedgerEntry) throws JSONException {
        JSONArray financialExposure = extraction.optJSONArray("financialExposureRegister");
        int financialCount = arrayLength(financialExposure);
        int verifiedFinancialCount = diagnostics.optInt("verifiedFinancialFindingCount", 0);
        JSONObject leadVerified = findFirstByStatus(financialExposure, "VERIFIED");
        JSONObject brain = baseBrain("B6", "Financial Exposure and Transaction Patterns", true);
        brain.put("engaged", financialCount > 0 || hasLedgerEntry || diagnostics.optInt("financial", 0) > 0);
        brain.put("findingCount", verifiedFinancialCount > 0 ? verifiedFinancialCount : financialCount);
        brain.put("candidateCount", financialCount > verifiedFinancialCount ? (financialCount - verifiedFinancialCount) : (hasLedgerEntry ? 1 : 0));
        brain.put("rejectedCount", 0);
        brain.put("keyPages", collectPages(financialExposure, 6));
        brain.put("themes", stringArray("financial exposure", "transaction anchors", "commercial records"));
        brain.put("status", verifiedFinancialCount > 0 ? "ACTIVE" : financialCount > 0 ? "CANDIDATE_ONLY" : hasLedgerEntry ? "CANDIDATE_ONLY" : "NO_SIGNAL");
        brain.put("confidence", verifiedFinancialCount > 0 ? "HIGH" : financialCount > 0 ? "MODERATE" : hasLedgerEntry ? "MODERATE" : "LOW");
        brain.put("summary", verifiedFinancialCount > 0
                ? firstNonEmpty(leadVerified != null ? leadVerified.optString("summary", "") : "", "A verified transaction reconciliation gap survived extraction and now drives the financial signal.")
                : financialCount > 0
                ? "A transaction-linked financial register survived extraction and now drives the financial signal."
                : hasLedgerEntry
                ? "Business-fraud extraction produced a ledger candidate, but no mature financial register survived."
                : "No anchored financial register survived this pass strongly enough to carry the commercial finding layer.");
        brain.put("sourceRegisters", stringArray("constitutionalExtraction.financialExposureRegister", "diagnostics.financial"));
        return brain;
    }

    private static JSONObject buildB7(
            JSONObject legalBrainContext,
            JSONObject extraction,
            String[] legalReferences,
            String[] topLiabilities,
            String jurisdictionCode,
            String jurisdictionName
    ) throws JSONException {
        JSONArray legalSubjects = extraction.optJSONArray("criticalLegalSubjects");
        JSONArray matchedFrameworks = legalBrainContext.optJSONArray("matchedOffenceFrameworks");
        JSONArray matchedInstitutions = legalBrainContext.optJSONArray("matchedInstitutions");
        JSONArray matchedProcedures = legalBrainContext.optJSONArray("matchedProceduralRules");
        JSONArray authorities = legalBrainContext.optJSONArray("authorities");
        JSONArray recommendedActions = legalBrainContext.optJSONArray("recommendedActions");

        boolean engaged = arrayLength(legalSubjects) > 0
                || (legalReferences != null && legalReferences.length > 0)
                || arrayLength(matchedFrameworks) > 0
                || arrayLength(matchedInstitutions) > 0
                || arrayLength(matchedProcedures) > 0;

        JSONObject brain = baseBrain("B7", "Legal Subject Mapping", true);
        brain.put("engaged", engaged);
        brain.put("findingCount", Math.max(arrayLength(legalSubjects), arrayLength(matchedFrameworks)));
        brain.put("candidateCount", arrayLength(matchedInstitutions) + arrayLength(matchedProcedures));
        brain.put("rejectedCount", 0);
        brain.put("keyPages", collectPages(legalSubjects, 6));
        brain.put("themes", mergeJsonArrays(
                toJsonArray(topLiabilities),
                extractTitles(matchedFrameworks, 4),
                toJsonArray(legalReferences)
        ));
        brain.put("status", arrayLength(matchedFrameworks) > 0 || arrayLength(legalSubjects) > 0
                ? "ACTIVE"
                : engaged ? "ADVISORY_SIGNAL" : "NO_SIGNAL");
        brain.put("confidence", arrayLength(matchedFrameworks) > 0
                ? "HIGH"
                : arrayLength(legalSubjects) > 0 ? "MODERATE" : "LOW");
        brain.put("summary", firstNonEmpty(
                legalBrainContext.optString("summary", ""),
                arrayLength(legalSubjects) > 0
                        ? "Legal subject flags were mapped from anchored evidence categories rather than treated as final verdicts."
                        : "Only jurisdiction references were available; no mature legal-subject mapping survived extraction."
        ));
        brain.put("jurisdictionCode", jurisdictionCode == null ? "" : jurisdictionCode);
        brain.put("jurisdictionName", jurisdictionName == null ? "" : jurisdictionName);
        brain.put("offenceFrameworks", matchedFrameworks != null ? matchedFrameworks : new JSONArray());
        brain.put("institutions", matchedInstitutions != null ? matchedInstitutions : new JSONArray());
        brain.put("proceduralTracks", matchedProcedures != null ? matchedProcedures : new JSONArray());
        brain.put("authorities", authorities != null ? authorities : new JSONArray());
        brain.put("recommendedActions", recommendedActions != null ? recommendedActions : new JSONArray());
        brain.put("sourceRegisters", stringArray(
                "constitutionalExtraction.criticalLegalSubjects",
                "legalReferences",
                "legalBrainContext"
        ));
        return brain;
    }

    private static JSONObject buildB8(File sourceFile, JSONObject nativeEvidence) throws JSONException {
        boolean audioOrVideo = looksLikeAudioOrVideo(sourceFile);
        boolean engaged = audioOrVideo;
        JSONObject brain = baseBrain("B8", "Audio and Rich Media Forensics", true);
        brain.put("engaged", engaged);
        brain.put("findingCount", 0);
        brain.put("candidateCount", 0);
        brain.put("rejectedCount", 0);
        brain.put("themes", stringArray("audio", "video", "media metadata"));
        brain.put("status", engaged ? "NO_SIGNAL" : "NOT_ENGAGED");
        brain.put("confidence", "LOW");
        brain.put("summary", engaged
                ? "Rich-media evidence was present, but no dedicated audio or video-finding register is implemented yet."
                : "No audio or rich-media source was present, so B8 remained unengaged.");
        brain.put("sourceRegisters", stringArray("nativeEvidence"));
        return brain;
    }

    private static JSONObject buildB9(JSONObject patternAnalysis, JSONObject vulnerabilityAnalysis, JSONObject rndAnalysis) throws JSONException {
        JSONArray patternMatches = patternAnalysis.optJSONArray("matches");
        JSONArray vulnerabilityMatches = vulnerabilityAnalysis.optJSONArray("matches");
        int advisoryCount = arrayLength(patternMatches) + arrayLength(vulnerabilityMatches);
        JSONObject brain = baseBrain("B9", "Research and Advisory Mesh", false);
        brain.put("engaged", advisoryCount > 0 || rndAnalysis.length() > 0);
        brain.put("findingCount", advisoryCount);
        brain.put("candidateCount", 0);
        brain.put("rejectedCount", 0);
        brain.put("themes", stringArray("advisory patterns", "vulnerability review", "experimental directives"));
        brain.put("status", advisoryCount > 0 || rndAnalysis.length() > 0 ? "ADVISORY" : "NO_SIGNAL");
        brain.put("confidence", advisoryCount > 0 ? "MODERATE" : "LOW");
        brain.put("summary", advisoryCount > 0 || rndAnalysis.length() > 0
                ? "B9 remains non-voting and contributes only pattern, vulnerability, and R&D advisory output."
                : "No advisory-only experimental output was populated in this pass.");
        brain.put("sourceRegisters", stringArray("patternAnalysis", "vulnerabilityAnalysis", "rndAnalysis"));
        return brain;
    }

    private static JSONObject buildConsensus(
            AnalysisEngine.ForensicReport report,
            JSONArray brains,
            ConstitutionGovernance governance
    ) throws JSONException {
        JSONObject consensus = new JSONObject();
        Map<String, Integer> votingThemeCounts = new LinkedHashMap<>();
        JSONArray advisoryThemes = new JSONArray();
        int engagedBrains = 0;
        int engagedVotingBrains = 0;
        int contributingVotingBrains = 0;
        JSONObject guardianDecision = safeObject(report.guardianDecision);

        for (int i = 0; i < brains.length(); i++) {
            JSONObject brain = brains.optJSONObject(i);
            if (brain == null) {
                continue;
            }
            boolean voting = brain.optBoolean("voting", false);
            boolean engaged = brain.optBoolean("engaged", false);
            if (engaged) {
                engagedBrains++;
                if (voting) {
                    engagedVotingBrains++;
                    if (countsTowardQuorum(brain)) {
                        contributingVotingBrains++;
                    }
                }
            }
            JSONArray themes = brain.optJSONArray("themes");
            if (themes == null) {
                continue;
            }
            for (int t = 0; t < themes.length(); t++) {
                String theme = themes.optString(t, "").trim();
                if (theme.isEmpty()) {
                    continue;
                }
                if (voting && engaged) {
                    votingThemeCounts.put(theme, votingThemeCounts.getOrDefault(theme, 0) + 1);
                } else if (!voting && engaged) {
                    advisoryThemes.put(theme);
                }
            }
        }

        JSONArray concordantThemes = new JSONArray();
        for (Map.Entry<String, Integer> entry : votingThemeCounts.entrySet()) {
            if (entry.getValue() >= 2) {
                concordantThemes.put(entry.getKey());
            }
        }

        JSONArray coverageGaps = new JSONArray();
        JSONObject diagnostics = safeObject(report.diagnostics);
        int verifiedFindingCount = report.certifiedFindings != null
                ? report.certifiedFindings.length()
                : 0;
        String processingStatus = diagnostics.optString("processingStatus", "UNKNOWN");
        boolean guardianApproved = guardianDecision.optBoolean("approved", false);
        String guardianNote = guardianDecision.optString("reason", guardianDecision.optString("error", "")).trim();
        int quorumMin = governance != null ? governance.quorumMin : 3;
        String tieBreaker = governance != null ? governance.tieBreaker : "B1_REQUEST_MORE_EVIDENCE";
        String concealmentOutput = governance != null ? governance.concealmentOutput : "INDETERMINATE DUE TO CONCEALMENT";
        boolean quorumSatisfied = contributingVotingBrains >= quorumMin;
        String constitutionalOutcome = processingStatus;
        if (!quorumSatisfied) {
            constitutionalOutcome = concealmentOutput.equalsIgnoreCase(processingStatus)
                    ? concealmentOutput
                    : "INCONCLUSIVE";
        }
        if ("INDETERMINATE DUE TO CONCEALMENT".equalsIgnoreCase(processingStatus)) {
            coverageGaps.put("Overall outcome remains indeterminate due to concealment, contamination risk, or weak extraction.");
        }
        if (findBrain(brains, "B1").optInt("findingCount", 0) == 0) {
            coverageGaps.put("The contradiction layer has not yet produced a verified paired-proposition conflict.");
        }
        if (findBrain(brains, "B6").optInt("findingCount", 0) == 0) {
            coverageGaps.put("The financial layer still lacks a mature transaction register strong enough to carry the commercial core alone.");
        }
        if (!findBrain(brains, "B8").optBoolean("engaged", false)) {
            coverageGaps.put("No audio or rich-media evidence engaged B8 in this run.");
        }
        if (!guardianApproved && guardianDecision.length() > 0) {
            coverageGaps.put("Guardian review did not approve any certified finding in this run.");
        }
        if (!quorumSatisfied) {
            coverageGaps.put("Voting-brain quorum was not met. Tie-breaker requires " + tieBreaker + ".");
        }

        consensus.put("processingStatus", processingStatus);
        consensus.put("constitutionalOutcome", constitutionalOutcome);
        consensus.put("verifiedFindingCount", verifiedFindingCount);
        consensus.put("engagedBrainCount", engagedBrains);
        consensus.put("engagedVotingBrainCount", engagedVotingBrains);
        consensus.put("contributingVotingBrainCount", contributingVotingBrains);
        consensus.put("quorumMin", quorumMin);
        consensus.put("quorumSatisfied", quorumSatisfied);
        consensus.put("tieBreaker", tieBreaker);
        consensus.put("concealmentOutput", concealmentOutput);
        consensus.put("guardianApproved", guardianApproved);
        consensus.put("guardianApprovedCount", guardianDecision.optInt("approvedCount", 0));
        consensus.put("guardianReviewedCount", guardianDecision.optInt("reviewedCount", 0));
        consensus.put("guardianDeniedCount", guardianDecision.optInt("deniedCount", 0));
        consensus.put("guardianNote", guardianNote);
        consensus.put("concordantThemes", concordantThemes);
        consensus.put("advisoryThemes", advisoryThemes);
        consensus.put("coverageGaps", coverageGaps);
        consensus.put("summary", buildConsensusSummary(
                diagnostics,
                engagedBrains,
                engagedVotingBrains,
                verifiedFindingCount,
                concordantThemes,
                coverageGaps,
                quorumSatisfied,
                quorumMin,
                tieBreaker,
                constitutionalOutcome,
                guardianApproved,
                guardianNote
        ));
        return consensus;
    }

    private static String buildConsensusSummary(
            JSONObject diagnostics,
            int engagedBrains,
            int engagedVotingBrains,
            int verifiedFindingCount,
            JSONArray concordantThemes,
            JSONArray coverageGaps,
            boolean quorumSatisfied,
            int quorumMin,
            String tieBreaker,
            String constitutionalOutcome,
            boolean guardianApproved,
            String guardianNote
    ) {
        String status = diagnostics.optString("processingStatus", "UNKNOWN");
        if (!quorumSatisfied) {
            return "The nine-brain system engaged " + engagedBrains + "/9 brains (" + engagedVotingBrains
                    + " voting), but constitutional quorum was not met. Minimum contributing voting brains: "
                    + quorumMin + ". Tie-breaker: " + tieBreaker + ". Outcome: " + constitutionalOutcome + ".";
        }
        if (!guardianApproved && guardianNote != null && !guardianNote.trim().isEmpty()) {
            return "The nine-brain system engaged " + engagedBrains + "/9 brains (" + engagedVotingBrains
                    + " voting), but guardian review denied certification: " + guardianNote;
        }
        if ("COMPLETED".equalsIgnoreCase(status) && verifiedFindingCount > 0) {
            return "The nine-brain system engaged " + engagedBrains + "/9 brains (" + engagedVotingBrains
                    + " voting) and produced " + verifiedFindingCount
                    + " verified findings despite remaining manipulation or coverage markers.";
        }
        if ("INDETERMINATE DUE TO CONCEALMENT".equalsIgnoreCase(status)) {
            return "The nine-brain system engaged " + engagedBrains + "/9 brains (" + engagedVotingBrains
                    + " voting), but the aggregate outcome remains indeterminate due to concealment or extraction weakness.";
        }
        if (concordantThemes.length() > 0) {
            return "The nine-brain system engaged " + engagedBrains + "/9 brains with concordant themes around "
                    + joinJsonArray(concordantThemes, 4) + ".";
        }
        if (coverageGaps.length() > 0) {
            return "The nine-brain system is active, but material coverage gaps still prevent a mature consensus outcome.";
        }
        return "The nine-brain system is active, but no strong multi-brain theme convergence was recorded in this pass.";
    }

    private static boolean countsTowardQuorum(JSONObject brain) {
        if (brain == null || !brain.optBoolean("voting", false) || !brain.optBoolean("engaged", false)) {
            return false;
        }
        String status = brain.optString("status", "").trim().toUpperCase(Locale.ROOT);
        return "ACTIVE".equals(status) || "CANDIDATE_ONLY".equals(status);
    }

    private static JSONObject findFirstByStatus(JSONArray items, String status) {
        if (items == null || status == null) {
            return null;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && status.equalsIgnoreCase(item.optString("status", ""))) {
                return item;
            }
        }
        return null;
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

    private static String buildContradictionSummary(JSONArray contradictionRegister, boolean verifiedMode) {
        String actorPhrase = collectContradictionActors(contradictionRegister);
        if (!actorPhrase.isEmpty()) {
            return actorPhrase + " linked to anchored deal-failure language and later execution or admission language that materially conflict.";
        }
        return verifiedMode
                ? "Anchored proposition conflicts survived contradiction review."
                : "Contradiction-linked material was found but still lacks enough anchored pairing for verified promotion.";
    }

    private static String collectContradictionActors(JSONArray contradictionRegister) {
        if (contradictionRegister == null) {
            return "";
        }
        Map<String, Boolean> actors = new LinkedHashMap<>();
        for (int i = 0; i < contradictionRegister.length(); i++) {
            JSONObject item = contradictionRegister.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String status = item.optString("status", "");
            if (!"VERIFIED".equalsIgnoreCase(status) && !"CANDIDATE".equalsIgnoreCase(status)) {
                continue;
            }
            String actor = sanitizeContradictionActor(item.optString("actor", ""));
            if (!actor.isEmpty()) {
                actors.put(actor, Boolean.TRUE);
            }
            if (actors.size() >= 2) {
                break;
            }
        }
        if (actors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String actor : actors.keySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(actor);
        }
        return sb.toString();
    }

    private static String sanitizeContradictionActor(String actor) {
        if (actor == null) {
            return "";
        }
        String trimmed = actor.trim();
        if (trimmed.isEmpty() || "unresolved actor".equalsIgnoreCase(trimmed)) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.equals("the")
                || lower.equals("you")
                || lower.equals("april")
                || lower.equals("all")
                || lower.equals("this")
                || lower.equals("that")
                || lower.equals("page")
                || lower.equals("date")
                || lower.equals("march")
                || lower.equals("it")
                || lower.equals("there")
                || lower.equals("here")
                || lower.equals("now")
                || lower.equals("then")
                || lower.contains("verum omnis")
                || lower.contains("goodwill theft")
                || lower.contains("cryptographic sealing")
                || lower.contains("port edward garage")
                || lower.contains("final window")
                || lower.contains("registered address")) {
            return "";
        }
        return trimmed;
    }

    private static JSONObject baseBrain(String id, String name, boolean voting) throws JSONException {
        JSONObject brain = new JSONObject();
        brain.put("id", id);
        brain.put("name", name);
        brain.put("voting", voting);
        return brain;
    }

    private static JSONObject findBrain(JSONArray brains, String id) {
        if (brains == null || id == null) {
            return new JSONObject();
        }
        for (int i = 0; i < brains.length(); i++) {
            JSONObject brain = brains.optJSONObject(i);
            if (brain != null && id.equalsIgnoreCase(brain.optString("id", ""))) {
                return brain;
            }
        }
        return new JSONObject();
    }

    private static JSONObject safeObject(JSONObject object) {
        return object == null ? new JSONObject() : object;
    }

    private static int arrayLength(JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private static JSONArray stringArray(String... values) throws JSONException {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                array.put(value.trim());
            }
        }
        return array;
    }

    private static JSONArray toJsonArray(String[] values) throws JSONException {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                array.put(value.trim());
            }
        }
        return array;
    }

    private static JSONArray extractTitles(JSONArray source, int limit) throws JSONException {
        JSONArray out = new JSONArray();
        if (source == null || limit <= 0) {
            return out;
        }
        for (int i = 0; i < source.length() && out.length() < limit; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = item.optString("title", "").trim();
            if (!title.isEmpty()) {
                out.put(title);
            }
        }
        return out;
    }

    private static JSONArray mergeJsonArrays(JSONArray... arrays) throws JSONException {
        JSONArray out = new JSONArray();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        if (arrays == null) {
            return out;
        }
        for (JSONArray array : arrays) {
            if (array == null) {
                continue;
            }
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty() && !seen.containsKey(value)) {
                    seen.put(value, Boolean.TRUE);
                    out.put(value);
                }
            }
        }
        return out;
    }

    private static int countVisualFindings(JSONArray visualFindings, boolean mediumOrHigherOnly) {
        if (visualFindings == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < visualFindings.length(); i++) {
            JSONObject item = visualFindings.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String severity = item.optString("severity", "").toLowerCase(Locale.US);
            if (!mediumOrHigherOnly || "medium".equals(severity) || "high".equals(severity)) {
                count++;
            }
        }
        return count;
    }

    private static int countTimelineEvents(JSONArray timeline, String... eventTypes) {
        if (timeline == null || eventTypes == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < timeline.length(); i++) {
            JSONObject item = timeline.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String eventType = item.optString("eventType", "");
            for (String expected : eventTypes) {
                if (expected.equalsIgnoreCase(eventType)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static int countConductTypes(JSONArray actorConduct, String... conductTypes) {
        if (actorConduct == null || conductTypes == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < actorConduct.length(); i++) {
            JSONObject item = actorConduct.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String conductType = item.optString("conductType", "");
            for (String expected : conductTypes) {
                if (expected.equalsIgnoreCase(conductType)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static JSONArray collectPages(JSONArray source, int limit) throws JSONException {
        JSONArray pages = new JSONArray();
        if (source == null || limit <= 0) {
            return pages;
        }
        Map<Integer, Boolean> seen = new LinkedHashMap<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            int page = item.optInt("page", 0);
            if (page <= 0 || seen.containsKey(page)) {
                continue;
            }
            seen.put(page, Boolean.TRUE);
            pages.put(page);
            if (pages.length() >= limit) {
                break;
            }
        }
        return pages;
    }

    private static JSONArray collectVisualPages(JSONArray source, int limit) throws JSONException {
        return collectPages(source, limit);
    }

    private static JSONArray mergePages(JSONArray left, JSONArray right) throws JSONException {
        JSONArray merged = new JSONArray();
        Map<Integer, Boolean> seen = new LinkedHashMap<>();
        copyPagesInto(merged, seen, left);
        copyPagesInto(merged, seen, right);
        return merged;
    }

    private static void copyPagesInto(JSONArray target, Map<Integer, Boolean> seen, JSONArray source) throws JSONException {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            int page = source.optInt(i, 0);
            if (page > 0 && !seen.containsKey(page)) {
                seen.put(page, Boolean.TRUE);
                target.put(page);
            }
        }
    }

    private static boolean looksLikeAudioOrVideo(File sourceFile) {
        if (sourceFile == null) {
            return false;
        }
        String name = sourceFile.getName().toLowerCase(Locale.US);
        return name.endsWith(".mp3")
                || name.endsWith(".wav")
                || name.endsWith(".m4a")
                || name.endsWith(".aac")
                || name.endsWith(".mp4")
                || name.endsWith(".mov")
                || name.endsWith(".avi");
    }

    private static String joinJsonArray(JSONArray array, int limit) {
        if (array == null || array.length() == 0 || limit <= 0) {
            return "no convergent themes";
        }
        StringBuilder sb = new StringBuilder();
        int emitted = 0;
        for (int i = 0; i < array.length() && emitted < limit; i++) {
            String value = array.optString(i, "").trim();
            if (value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
            emitted++;
        }
        return sb.length() == 0 ? "no convergent themes" : sb.toString();
    }
}
