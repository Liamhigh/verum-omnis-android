package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ForensicSynthesisEngine {

    private ForensicSynthesisEngine() {}

    public static JSONObject build(AnalysisEngine.ForensicReport report) {
        JSONObject root = new JSONObject();
        try {
            JSONObject diagnostics = safeObject(report != null ? report.diagnostics : null);
            JSONObject extraction = safeObject(report != null ? report.constitutionalExtraction : null);

            JSONArray contradictionRegister = diagnostics.optJSONArray("contradictionRegister");
            JSONArray propositionRegister = extraction.optJSONArray("propositionRegister");
            JSONArray documentIntegrity = extraction.optJSONArray("documentIntegrityFindings");
            JSONArray timeline = extraction.optJSONArray("timelineAnchorRegister");
            JSONArray conduct = extraction.optJSONArray("actorConductRegister");
            JSONArray financial = extraction.optJSONArray("financialExposureRegister");
            JSONArray namedParties = extraction.optJSONArray("namedParties");

            LinkedHashMap<String, ActorMetrics> metricsByActor = buildActorMetrics(
                    namedParties,
                    propositionRegister,
                    contradictionRegister,
                    documentIntegrity,
                    timeline,
                    conduct,
                    financial
            );
            JSONArray victimActors = buildVictimActorList(namedParties, timeline, conduct, financial, metricsByActor);
            LinkedHashMap<String, ActorMetrics> wrongfulMetrics = filterWrongfulActorMetrics(metricsByActor, victimActors);

            JSONArray actorScores = buildActorDishonestyScores(wrongfulMetrics);
            JSONObject actorHeatmap = buildActorHeatmap(wrongfulMetrics);
            JSONArray crossBrainContradictions = buildCrossBrainContradictions(wrongfulMetrics, contradictionRegister);
            JSONArray promotionSupportMatrix = buildPromotionSupportMatrix(crossBrainContradictions);
            JSONObject wrongfulActorProfile = buildWrongfulActorProfile(actorScores, crossBrainContradictions);

            root.put("engineId", "vo_b1_synthesis_v1");
            root.put("crossBrainContradictions", crossBrainContradictions);
            root.put("actorDishonestyScores", actorScores);
            root.put("actorHeatmap", actorHeatmap);
            root.put("promotionSupportMatrix", promotionSupportMatrix);
            root.put("wrongfulActorProfile", wrongfulActorProfile);
            root.put("victimActors", victimActors);
            root.put("victimActorCount", victimActors.length());
            root.put("summary", buildSummary(actorScores, crossBrainContradictions, victimActors));
            root.put("topActor", actorScores.length() > 0 ? actorScores.optJSONObject(0) : new JSONObject());
            root.put("verifiedCrossBrainCount", countByStatus(crossBrainContradictions, "VERIFIED"));
            root.put("candidateCrossBrainCount", countByStatus(crossBrainContradictions, "CANDIDATE"));
            root.put("severeActorCount", countBySeverity(actorScores, "SEVERE"));
        } catch (Exception e) {
            putSafe(root, "error", "Forensic synthesis failed: " + safeMessage(e));
            putSafe(root, "engineId", "vo_b1_synthesis_v1");
            putSafe(root, "crossBrainContradictions", new JSONArray());
            putSafe(root, "actorDishonestyScores", new JSONArray());
            putSafe(root, "actorHeatmap", new JSONObject());
            putSafe(root, "promotionSupportMatrix", new JSONArray());
            putSafe(root, "wrongfulActorProfile", buildEmptyWrongfulActorProfile());
            putSafe(root, "summary", "B1 synthesis failed and returned an empty synthesis record.");
        }
        return root;
    }

    private static LinkedHashMap<String, ActorMetrics> buildActorMetrics(
            JSONArray namedParties,
            JSONArray propositionRegister,
            JSONArray contradictionRegister,
            JSONArray documentIntegrity,
            JSONArray timeline,
            JSONArray conduct,
            JSONArray financial
    ) {
        LinkedHashMap<String, ActorMetrics> out = new LinkedHashMap<>();
        seedNamedParties(out, namedParties);
        collectPropositionMetrics(out, propositionRegister, namedParties);
        collectContradictionMetrics(out, contradictionRegister);
        collectIntegrityMetrics(out, documentIntegrity);
        collectTimelineMetrics(out, timeline);
        collectConductMetrics(out, conduct);
        collectFinancialMetrics(out, financial);
        mergeCanonicalNamedPartyActors(out, namedParties);
        pruneNarrativeActors(out);
        dropEmptyActors(out);
        return out;
    }

    private static void collectPropositionMetrics(
            LinkedHashMap<String, ActorMetrics> out,
            JSONArray source,
            JSONArray namedParties
    ) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String corpus = buildCorpus(item);
            if (corpus.trim().isEmpty()) {
                continue;
            }
            String actor = resolvePropositionActor(item, namedParties);
            if (actor == null || isComplainantSideContext(actor, corpus)) {
                continue;
            }
            String category = trimmed(item.optString("category", ""));
            ActorMetrics metrics = ensureActor(out, actor);
            boolean touched = false;
            if (containsAny(category, "Contradiction")
                    || containsAny(corpus,
                    "issued a termination notice",
                    "termination notice",
                    "completed order",
                    "proceeded with the deal",
                    "did the deal",
                    "no exclusivity",
                    "exclusive",
                    "refused to",
                    "selective screenshots")) {
                metrics.contradictions++;
                metrics.flags.add("contradiction-led");
                metrics.supportingBrains.add("B1");
                touched = true;
            }
            if (containsAny(category, "Cybercrime")
                    || containsAny(corpus,
                    "archive request",
                    "scaquaculture",
                    "unauthorised",
                    "unauthorized",
                    "hack",
                    "device")) {
                metrics.concealment++;
                metrics.flags.add("cyber");
                metrics.supportingBrains.add("B3");
                touched = true;
            }
            if (containsAny(category, "Emotional Exploitation", "Behavioral")
                    || containsAny(corpus,
                    "selective screenshots",
                    "attack call",
                    "must i do the marketing",
                    "pressure",
                    "gaslight",
                    "emotional")) {
                metrics.evasion++;
                metrics.flags.add("pressure");
                metrics.supportingBrains.add("B4");
                touched = true;
            }
            if (containsAny(category, "Financial")
                    || shouldScoreFinancialItem(corpus)
                    || containsAny(corpus, "greensky is owed", "client confirmation", "invoice accepted", "hong kong")) {
                metrics.financial++;
                metrics.flags.add("financial");
                metrics.supportingBrains.add("B6");
                touched = true;
            }
            if (containsAny(corpus,
                    "refused to communicate",
                    "refused to assist",
                    "failed to reply",
                    "must i do the marketing",
                    "pricing communication",
                    "completed order")) {
                metrics.omissions++;
                metrics.flags.add("execution");
                metrics.supportingBrains.add("B5");
                touched = true;
            }
            if (!touched) {
                continue;
            }
            metrics.adverseSignals++;
            if (containsHardForensicSignal(corpus)) {
                metrics.hardEvidenceSignals++;
            }
            addPages(metrics.pages, item.optJSONArray("anchors"), item.optInt("page", 0));
            metrics.evidenceNotes.add(trimmed(firstNonBlank(
                    item.optString("summary", ""),
                    item.optString("text", "")
            )));
        }
    }

    private static void seedNamedParties(LinkedHashMap<String, ActorMetrics> out, JSONArray namedParties) {
        if (namedParties == null) {
            return;
        }
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject item = namedParties.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = normalizeActor(item.optString("name", ""));
            if (actor == null) {
                continue;
            }
            out.putIfAbsent(actor, new ActorMetrics(actor));
        }
    }

    private static void collectContradictionMetrics(LinkedHashMap<String, ActorMetrics> out, JSONArray source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = normalizeActor(item.optString("actor", ""));
            if (actor == null) {
                continue;
            }
            String status = item.optString("status", "");
            String conflictType = item.optString("conflictType", "");
            String confidence = trimmed(item.optString("confidence", ""));
            String corpus = buildCorpus(item);
            if (!shouldScoreContradictionItem(corpus)) {
                continue;
            }
            boolean verified = "VERIFIED".equalsIgnoreCase(status);
            boolean complainantContext = isComplainantSideContext(actor, corpus);
            if (complainantContext) {
                continue;
            }
            ActorMetrics metrics = ensureActor(out, actor);
            if ("INTER_ACTOR_CONFLICT".equalsIgnoreCase(conflictType)) {
                metrics.contradictions += verified ? 4 : 2;
            } else {
                metrics.contradictions += verified ? 2 : 1;
            }
            if (verified) {
                metrics.verifiedContradictions++;
            } else if ("CANDIDATE".equalsIgnoreCase(status)) {
                metrics.candidateContradictions++;
            }
            if ("HIGH".equalsIgnoreCase(confidence)) {
                metrics.highConfidenceContradictions++;
            } else if ("VERY_HIGH".equalsIgnoreCase(confidence)) {
                metrics.highConfidenceContradictions += 2;
            }
            if ("NEGATION".equalsIgnoreCase(conflictType)
                    || "NUMERIC".equalsIgnoreCase(conflictType)
                    || "INTER_ACTOR_CONFLICT".equalsIgnoreCase(conflictType)) {
                metrics.criticalContradictions++;
            }
            if ("NUMERIC".equalsIgnoreCase(conflictType)) {
                metrics.financial += verified ? 2 : 1;
                metrics.flags.add("financial");
            }
            if ("TIMELINE".equalsIgnoreCase(conflictType)) {
                if (verified) {
                    metrics.omissions++;
                }
                metrics.flags.add("timeline");
            }
            if ("NEGATION".equalsIgnoreCase(conflictType) && verified) {
                metrics.evasion++;
            }
            if (containsAny(corpus, "ignored", "refused", "blocked", "delay", "withheld", "gaslight", "silence")) {
                metrics.evasion++;
            }
            if (containsAny(corpus, "forg", "cropped", "manipulat", "backdat", "tamper", "metadata")) {
                metrics.concealment++;
                metrics.flags.add("concealment");
            }
            if (containsAny(corpus, "fraud", "misrepresentation", "false")) {
                metrics.flags.add("contradiction");
            }
            metrics.adverseSignals += verified ? 2 : 1;
            if (containsHardForensicSignal(corpus)) {
                metrics.hardEvidenceSignals++;
            }
            metrics.supportingBrains.add("B1");
            addPages(metrics.pages, item.optJSONArray("anchors"), item.optInt("page", 0));
            metrics.evidenceNotes.add(trimmed(firstNonBlank(
                    item.optString("summary", ""),
                    item.optString("whyItConflicts", ""),
                    conflictType
            )));
        }
    }

    private static void collectIntegrityMetrics(LinkedHashMap<String, ActorMetrics> out, JSONArray source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = normalizeActor(item.optString("actor", ""));
            if (actor == null) {
                continue;
            }
            String type = item.optString("type", "");
            String corpus = buildCorpus(item);
            if (isComplainantSideContext(actor, corpus)) {
                continue;
            }
            ActorMetrics metrics = ensureActor(out, actor);
            if (containsAny(type, "MISSING_COUNTERSIGNATURE", "MISSING_EXECUTION_EVIDENCE")) {
                metrics.omissions++;
                metrics.flags.add("omissions");
            }
            if (containsAny(type, "BACKDATING_RISK", "METADATA_ANOMALY")) {
                metrics.concealment++;
                metrics.flags.add("concealment");
            }
            if (containsAny(type, "SIGNATURE_MISMATCH", "SIGNATURE_MARKS_NOT_FOUND", "SIGNATURE_ZONE_OVERLAY", "POSSIBLE_OVERLAY_REGION")) {
                metrics.concealment++;
                metrics.flags.add("forgery");
                metrics.flags.add("document_integrity");
            }
            metrics.adverseSignals++;
            metrics.hardEvidenceSignals++;
            metrics.supportingBrains.add("B2");
            addPages(metrics.pages, item.optJSONArray("anchors"), item.optInt("page", 0));
            metrics.evidenceNotes.add(trimmed(item.optString("excerpt", "")));
        }
    }

    private static void collectTimelineMetrics(LinkedHashMap<String, ActorMetrics> out, JSONArray source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = normalizeActor(item.optString("actor", ""));
            if (actor == null) {
                continue;
            }
            String eventType = item.optString("eventType", "");
            String corpus = buildCorpus(item);
            if (!shouldScoreTimelineItem(eventType, corpus)) {
                continue;
            }
            if (isComplainantSideContext(actor, corpus)) {
                continue;
            }
            ActorMetrics metrics = ensureActor(out, actor);
            if ("EXECUTION_STATUS".equalsIgnoreCase(eventType)) {
                metrics.omissions++;
                metrics.flags.add("execution");
            }
            if ("EVICTION_PRESSURE".equalsIgnoreCase(eventType)) {
                metrics.evasion++;
                metrics.flags.add("pressure");
            }
            if (containsAny(corpus, "forg", "manipulat", "cropped", "unauthorised", "deleted", "hidden")) {
                metrics.concealment++;
                metrics.flags.add("concealment");
            }
            if (containsAny(corpus, "ignored", "refused", "blocked", "delay", "withheld")) {
                metrics.evasion++;
            }
            metrics.adverseSignals++;
            if (containsHardForensicSignal(corpus)) {
                metrics.hardEvidenceSignals++;
            }
            if (item.optBoolean("primaryEvidence", false)) {
                metrics.supportingBrains.add("B5");
            } else if (!item.optBoolean("supportOnly", false)) {
                metrics.supportingBrains.add("B3");
            }
            addPages(metrics.pages, item.optJSONArray("anchors"), item.optInt("page", 0));
            metrics.evidenceNotes.add(trimmed(item.optString("summary", "")));
        }
    }

    private static void collectConductMetrics(LinkedHashMap<String, ActorMetrics> out, JSONArray source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = normalizeActor(item.optString("actor", ""));
            if (actor == null) {
                continue;
            }
            ActorMetrics metrics = ensureActor(out, actor);
            String conductType = item.optString("conductType", "");
            String corpus = buildCorpus(item);
            if (!shouldScoreConductItem(conductType, corpus)) {
                continue;
            }
            if (containsAny(conductType, "CONTRACT_EXECUTION_POSITION", "DOCUMENT_EXECUTION_STATE")) {
                metrics.omissions++;
            }
            if (containsAny(conductType, "EVICTION_OR_PRESSURE_POSITION")) {
                metrics.evasion++;
            }
            if (containsAny(conductType, "FINANCIAL_POSITION")) {
                metrics.financial++;
            }
            metrics.adverseSignals++;
            if (containsHardForensicSignal(corpus)) {
                metrics.hardEvidenceSignals++;
            }
            metrics.supportingBrains.add(supportingBrainForConductType(conductType));
            addPages(metrics.pages, item.optJSONArray("anchors"), item.optInt("page", 0));
            metrics.evidenceNotes.add(trimmed(item.optString("summary", "")));
        }
    }

    private static String supportingBrainForConductType(String conductType) {
        String type = trimmed(conductType).toUpperCase(Locale.ROOT);
        if (containsAny(type,
                "CONTRACT_EXECUTION_POSITION",
                "DOCUMENT_EXECUTION_STATE",
                "MISSING_COUNTERSIGNATURE",
                "EXECUTION_STATUS")) {
            return "B2";
        }
        if (containsAny(type,
                "FINANCIAL_POSITION",
                "UNLAWFUL_CONTROL_POSITION",
                "FINANCIAL_REFERENCE",
                "CONTINUED_VALUE_EXTRACTION")) {
            return "B6";
        }
        if (containsAny(type,
                "EVICTION_OR_PRESSURE_POSITION",
                "NOTICE_AND_ESCALATION",
                "AUTHORITY_AND_STANDING",
                "RETALIATION_OR_PRESSURE")) {
            return "B5";
        }
        return "B5";
    }

    private static void collectFinancialMetrics(LinkedHashMap<String, ActorMetrics> out, JSONArray source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String corpus = buildCorpus(item);
            if (!shouldScoreFinancialItem(corpus)) {
                continue;
            }
            String actor = normalizeActor(item.optString("actor", ""));
            if (actor != null) {
                ActorMetrics metrics = ensureActor(out, actor);
                metrics.financial += "VERIFIED".equalsIgnoreCase(item.optString("status", "")) ? 2 : 1;
                metrics.flags.add("financial");
                metrics.adverseSignals++;
                metrics.hardEvidenceSignals++;
                metrics.supportingBrains.add("B6");
                addPages(metrics.pages, item.optJSONArray("anchors"), item.optInt("page", 0));
                metrics.evidenceNotes.add(trimmed(item.optString("summary", "")));
            }
        }
    }

    private static JSONArray buildActorDishonestyScores(LinkedHashMap<String, ActorMetrics> metricsByActor) throws JSONException {
        List<ActorMetrics> ranked = new ArrayList<>(metricsByActor.values());
        Collections.sort(ranked, (left, right) -> {
            int contradictionPriorityCompare = Integer.compare(
                    right.contradictionPriority(),
                    left.contradictionPriority()
            );
            if (contradictionPriorityCompare != 0) {
                return contradictionPriorityCompare;
            }
            int scoreCompare = Double.compare(right.score(), left.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Integer.compare(right.hardEvidenceSignals, left.hardEvidenceSignals);
        });
        JSONArray out = new JSONArray();
        for (ActorMetrics metrics : ranked) {
            JSONObject item = new JSONObject();
            item.put("actor", metrics.actor);
            item.put("dishonestyScore", metrics.score());
            item.put("contradictionPriority", metrics.contradictionPriority());
            item.put("severity", metrics.severity());
            item.put("flags", toJsonArray(new ArrayList<>(metrics.flagSet())));
            item.put("supportingBrains", toJsonArray(new ArrayList<>(metrics.supportingBrains)));
            item.put("anchorPages", toJsonArray(new ArrayList<>(metrics.pages)));
            item.put("anchorCount", metrics.pages.size());
            item.put("verifiedContradictionCount", metrics.verifiedContradictions);
            item.put("candidateContradictionCount", metrics.candidateContradictions);
            item.put("highConfidenceContradictionCount", metrics.highConfidenceContradictions);
            item.put("criticalContradictionCount", metrics.criticalContradictions);
            item.put("evidenceNotes", toJsonArray(metrics.topEvidenceNotes(3)));
            item.put("heatmap", metrics.heatmapJson());
            item.put("likelyWrongfulParty", true);
            out.put(item);
        }
        return out;
    }

    private static JSONObject buildActorHeatmap(LinkedHashMap<String, ActorMetrics> metricsByActor) throws JSONException {
        JSONObject out = new JSONObject();
        for (ActorMetrics metrics : metricsByActor.values()) {
            out.put(metrics.actor, metrics.heatmapJson());
        }
        return out;
    }

    private static JSONArray buildCrossBrainContradictions(
            LinkedHashMap<String, ActorMetrics> metricsByActor,
            JSONArray contradictionRegister
    ) throws JSONException {
        JSONArray out = new JSONArray();
        for (ActorMetrics metrics : metricsByActor.values()) {
            if (metrics.supportingBrains.size() < 2) {
                continue;
            }
            boolean hasVerifiedContradiction = hasContradictionForActor(contradictionRegister, metrics.actor, "VERIFIED");
            boolean hasCandidateContradiction = hasVerifiedContradiction
                    || hasContradictionForActor(contradictionRegister, metrics.actor, "CANDIDATE");
            maybeAppend(out, buildCrossBrainItem(
                    metrics,
                    "EXECUTION_MISMATCH",
                    "B1,B2",
                    metrics.contradictions > 0 && metrics.omissions > 0,
                    "Cross-brain execution mismatch: contradiction signals align with missing or incomplete execution evidence.",
                    hasVerifiedContradiction,
                    hasCandidateContradiction
            ));
            maybeAppend(out, buildCrossBrainItem(
                    metrics,
                    "UNLAWFUL_EXTRACTION",
                    "B2,B6",
                    metrics.omissions > 0 && metrics.financial > 0,
                    "Cross-brain unlawful extraction pattern: execution defects align with anchored financial exposure.",
                    hasVerifiedContradiction,
                    hasCandidateContradiction
            ));
            maybeAppend(out, buildCrossBrainItem(
                    metrics,
                    "CORROBORATED_DECEPTION",
                    "B1,B4",
                    metrics.contradictions > 0 && metrics.evasion > 0,
                    "Contradiction and evasion markers cluster around the same actor.",
                    hasVerifiedContradiction,
                    hasCandidateContradiction
            ));
            maybeAppend(out, buildCrossBrainItem(
                    metrics,
                    "EVIDENCE_CONCEALMENT_PATTERN",
                    "B1,B3",
                    metrics.contradictions > 0 && metrics.concealment > 0,
                    "Contradiction signals align with concealment or manipulation markers.",
                    hasVerifiedContradiction,
                    hasCandidateContradiction
            ));
            maybeAppend(out, buildCrossBrainItem(
                    metrics,
                    "RETROACTIVE_TERMINATION",
                    "B5,B6",
                    metrics.financial > 0 && metrics.evasion > 0,
                    "Timeline pressure and financial exposure combine into a retroactive termination or coercion pattern.",
                    hasVerifiedContradiction,
                    hasCandidateContradiction
            ));
        }
        return sortByActorAndStatus(out);
    }

    private static boolean hasContradictionForActor(JSONArray contradictionRegister, String actor, String status) {
        String normalizedActor = normalizeActor(actor);
        if (contradictionRegister == null || normalizedActor == null) {
            return false;
        }
        for (int i = 0; i < contradictionRegister.length(); i++) {
            JSONObject item = contradictionRegister.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String itemStatus = trimmed(item.optString("status", ""));
            if (!status.isEmpty() && !status.equalsIgnoreCase(itemStatus)) {
                continue;
            }
            if (actorMatchesContradictionItem(normalizedActor, item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean actorMatchesContradictionItem(String normalizedActor, JSONObject item) {
        if (normalizedActor == null || item == null) {
            return false;
        }
        String[] directFields = new String[] {
                item.optString("actor", ""),
                item.optString("leftActor", ""),
                item.optString("rightActor", ""),
                item.optString("actorA", ""),
                item.optString("actorB", ""),
                item.optString("subject", ""),
                item.optString("target", "")
        };
        for (String candidate : directFields) {
            if (normalizedActor.equalsIgnoreCase(normalizeActor(candidate))) {
                return true;
            }
        }
        JSONArray arrays = item.optJSONArray("actors");
        if (jsonArrayContainsActor(arrays, normalizedActor)) {
            return true;
        }
        arrays = item.optJSONArray("parties");
        if (jsonArrayContainsActor(arrays, normalizedActor)) {
            return true;
        }
        String corpus = buildCorpus(item);
        if (corpus.trim().isEmpty()) {
            return false;
        }
        String actorToken = " " + normalizedActor.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        if ("  ".equals(actorToken)) {
            return false;
        }
        String normalizedCorpus = " " + corpus.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        return normalizedCorpus.contains(actorToken);
    }

    private static boolean jsonArrayContainsActor(JSONArray source, String normalizedActor) {
        if (source == null || normalizedActor == null) {
            return false;
        }
        for (int i = 0; i < source.length(); i++) {
            Object entry = source.opt(i);
            if (entry instanceof JSONObject) {
                JSONObject item = (JSONObject) entry;
                if (normalizedActor.equalsIgnoreCase(normalizeActor(item.optString("actor", "")))
                        || normalizedActor.equalsIgnoreCase(normalizeActor(item.optString("name", "")))) {
                    return true;
                }
            } else if (entry instanceof String) {
                if (normalizedActor.equalsIgnoreCase(normalizeActor((String) entry))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JSONObject buildCrossBrainItem(
            ActorMetrics metrics,
            String type,
            String brainPair,
            boolean active,
            String reason,
            boolean hasVerifiedContradiction,
            boolean hasCandidateContradiction
    ) throws JSONException {
        if (!active || metrics.pages.isEmpty()) {
            return null;
        }
        String[] pair = brainPair.split(",");
        LinkedHashSet<String> supportingBrains = new LinkedHashSet<>();
        for (String value : pair) {
            String trimmed = value.trim();
            if (metrics.supportingBrains.contains(trimmed)) {
                supportingBrains.add(trimmed);
            }
        }
        if (supportingBrains.size() < 2) {
            return null;
        }
        JSONObject item = new JSONObject();
        item.put("actor", metrics.actor);
        item.put("contradictionType", type);
        String status = hasVerifiedContradiction && metrics.pages.size() >= 2
                ? "VERIFIED"
                : (hasCandidateContradiction ? "CANDIDATE" : "SUPPORT_ONLY");
        item.put("status", status);
        item.put("supportingBrains", toJsonArray(new ArrayList<>(supportingBrains)));
        item.put("reason", hasCandidateContradiction
                ? reason
                : reason + " No stored contradiction pair survived verification for this actor, so this remains support-only.");
        item.put("anchorPages", toJsonArray(new ArrayList<>(metrics.pages)));
        item.put("heatmap", metrics.heatmapJson());
        item.put("supportCount", supportingBrains.size());
        item.put("anchorCount", metrics.pages.size());
        item.put("evidenceNotes", toJsonArray(metrics.topEvidenceNotes(3)));
        return item;
    }

    private static JSONArray buildPromotionSupportMatrix(JSONArray contradictions) throws JSONException {
        JSONArray out = new JSONArray();
        if (contradictions == null) {
            return out;
        }
        for (int i = 0; i < contradictions.length(); i++) {
            JSONObject item = contradictions.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("actor", item.optString("actor", ""));
            row.put("findingType", item.optString("contradictionType", ""));
            row.put("supportingBrains", item.optJSONArray("supportingBrains"));
            row.put("anchorPages", item.optJSONArray("anchorPages"));
            row.put("promotionEligible", "VERIFIED".equalsIgnoreCase(item.optString("status", "")));
            row.put("p1AnchorRule", item.optInt("anchorCount", 0) > 0);
            row.put("p2ActorRule", !item.optString("actor", "").trim().isEmpty());
            row.put("p3EvidentiarySufficiency", item.optInt("supportCount", 0) >= 2);
            row.put("p4CorroborationByBrain", item.optInt("supportCount", 0) >= 2);
            row.put("explainability", item.optString("reason", ""));
            out.put(row);
        }
        return out;
    }

    private static JSONObject buildWrongfulActorProfile(JSONArray actorScores, JSONArray contradictions) throws JSONException {
        JSONObject out = buildEmptyWrongfulActorProfile();
        if (actorScores == null || actorScores.length() == 0) {
            return out;
        }
        JSONObject lead = actorScores.optJSONObject(0);
        String actor = lead != null ? lead.optString("actor", "") : "";
        JSONArray supportingContradictions = new JSONArray();
        LinkedHashSet<String> supportingBrains = new LinkedHashSet<>();
        LinkedHashSet<Integer> anchorPages = new LinkedHashSet<>();
        if (contradictions != null) {
            for (int i = 0; i < contradictions.length(); i++) {
                JSONObject item = contradictions.optJSONObject(i);
                if (item == null || !actor.equalsIgnoreCase(item.optString("actor", ""))) {
                    continue;
                }
                supportingContradictions.put(item.optString("contradictionType", ""));
                JSONArray brains = item.optJSONArray("supportingBrains");
                if (brains != null) {
                    for (int j = 0; j < brains.length(); j++) {
                        String brain = item.optJSONArray("supportingBrains").optString(j, "").trim();
                        if (!brain.isEmpty()) {
                            supportingBrains.add(brain);
                        }
                    }
                }
                JSONArray pages = item.optJSONArray("anchorPages");
                if (pages != null) {
                    for (int j = 0; j < pages.length(); j++) {
                        int page = pages.optInt(j, 0);
                        if (page > 0) {
                            anchorPages.add(page);
                        }
                    }
                }
            }
        }
        out.put("actor", actor);
        int verifiedContradictionCount = lead != null ? lead.optInt("verifiedContradictionCount", 0) : 0;
        out.put("factualFaultAssessment", actor.isEmpty()
                ? "No factual-fault profile could be stated."
                : verifiedContradictionCount > 0
                ? actor + " currently carries the strongest contradiction-led factual-fault profile on the anchored evidence."
                : actor + " currently carries the strongest candidate contradiction-led factual-fault profile on the anchored evidence, but this remains candidate-level until a verified contradiction matures.");
        out.put("leadScore", lead != null ? lead.optDouble("dishonestyScore", 0.0d) : 0.0d);
        out.put("contradictionPriority", lead != null ? lead.optInt("contradictionPriority", 0) : 0);
        out.put("verifiedContradictionCount", verifiedContradictionCount);
        out.put("severity", lead != null ? lead.optString("severity", "") : "");
        out.put("evidenceNotes", lead != null && lead.optJSONArray("evidenceNotes") != null
                ? lead.optJSONArray("evidenceNotes")
                : new JSONArray());
        JSONArray secondaryActors = new JSONArray();
        if (actorScores != null) {
            for (int i = 1; i < actorScores.length() && secondaryActors.length() < 2; i++) {
                JSONObject item = actorScores.optJSONObject(i);
                if (item == null || item.optDouble("dishonestyScore", 0.0d) < 15.0d) {
                    continue;
                }
                JSONObject secondary = new JSONObject();
                secondary.put("actor", item.optString("actor", ""));
                secondary.put("dishonestyScore", item.optDouble("dishonestyScore", 0.0d));
                secondary.put("severity", item.optString("severity", ""));
                secondary.put("anchorPages", item.optJSONArray("anchorPages"));
                secondary.put("evidenceNotes", item.optJSONArray("evidenceNotes"));
                secondaryActors.put(secondary);
            }
        }
        out.put("secondaryActors", secondaryActors);
        out.put("supportingContradictions", supportingContradictions);
        out.put("supportingBrains", toJsonArray(new ArrayList<>(supportingBrains)));
        out.put("anchorPages", toJsonArray(new ArrayList<>(anchorPages)));
        return out;
    }

    private static JSONObject buildEmptyWrongfulActorProfile() {
        JSONObject out = new JSONObject();
        putSafe(out, "actor", "");
        putSafe(out, "factualFaultAssessment", "No actor-level synthesis result was mature enough to assign a wrongful-actor profile.");
        putSafe(out, "leadScore", 0.0d);
        putSafe(out, "contradictionPriority", 0);
        putSafe(out, "verifiedContradictionCount", 0);
        putSafe(out, "severity", "");
        putSafe(out, "evidenceNotes", new JSONArray());
        putSafe(out, "secondaryActors", new JSONArray());
        putSafe(out, "supportingContradictions", new JSONArray());
        putSafe(out, "supportingBrains", new JSONArray());
        putSafe(out, "anchorPages", new JSONArray());
        return out;
    }

    private static String buildSummary(JSONArray actorScores, JSONArray contradictions, JSONArray victimActors) {
        int severeCount = countBySeverity(actorScores, "SEVERE");
        int verifiedCount = countByStatus(contradictions, "VERIFIED");
        if (actorScores == null || actorScores.length() == 0) {
            if (victimActors != null && victimActors.length() > 0) {
                return "B1 synthesis ran, but no actor-linked synthesis output was mature enough to score. Victim-linked actors were still detected in the sealed record: "
                        + joinStringArray(victimActors, 4) + ".";
            }
            return "B1 synthesis ran, but no actor-linked synthesis output was mature enough to score.";
        }
        JSONObject leadActor = actorScores.optJSONObject(0);
        String actorName = leadActor != null ? leadActor.optString("actor", "the leading actor") : "the leading actor";
        double score = leadActor != null ? leadActor.optDouble("dishonestyScore", 0.0d) : 0.0d;
        int verifiedContradictionCount = leadActor != null ? leadActor.optInt("verifiedContradictionCount", 0) : 0;
        String summary;
        if (verifiedCount <= 0 || verifiedContradictionCount <= 0) {
            summary = "B1 synthesis has not yet matured a verified cross-brain contradiction in this pass. The highest current candidate contradiction-led dishonesty score points to "
                    + actorName + " at " + score + ", but that lead remains candidate-only until a verified contradiction is produced.";
        } else {
            summary = "B1 synthesis produced " + verifiedCount + " verified cross-brain contradiction(s) and "
                    + severeCount + " severe actor profile(s). The highest current contradiction-led dishonesty score belongs to "
                    + actorName + " at " + score + " with " + verifiedContradictionCount + " verified contradiction(s).";
        }
        JSONObject secondActor = findNextMaterialActor(actorScores, actorName);
        if (secondActor != null) {
            summary += " The next material actor profile is "
                    + secondActor.optString("actor", "the next actor")
                    + " at "
                    + secondActor.optDouble("dishonestyScore", 0.0d)
                    + ".";
        }
        if (victimActors != null && victimActors.length() > 1) {
            summary += " The record also reflects a multi-victim pattern involving " + joinStringArray(victimActors, 5) + ".";
        }
        return summary;
    }

    private static LinkedHashMap<String, ActorMetrics> filterWrongfulActorMetrics(LinkedHashMap<String, ActorMetrics> metricsByActor, JSONArray victimActors) {
        LinkedHashMap<String, ActorMetrics> filtered = new LinkedHashMap<>();
        LinkedHashMap<String, ActorMetrics> institutional = new LinkedHashMap<>();
        if (metricsByActor == null) {
            return filtered;
        }
        for (Map.Entry<String, ActorMetrics> entry : metricsByActor.entrySet()) {
            ActorMetrics metrics = entry.getValue();
            if (metrics != null && isWrongfulActorCandidate(metrics) && !isVictimActor(entry.getKey(), victimActors)) {
                if (isInstitutionalActor(entry.getKey())) {
                    institutional.put(entry.getKey(), metrics);
                } else {
                    filtered.put(entry.getKey(), metrics);
                }
            }
        }
        if (filtered.isEmpty()) {
            filtered.putAll(institutional);
        }
        return filtered;
    }

    private static boolean isVictimActor(String actor, JSONArray victimActors) {
        String canonicalActor = canonicalVictimActor(actor);
        if (canonicalActor.isEmpty() || victimActors == null) {
            return false;
        }
        for (int i = 0; i < victimActors.length(); i++) {
            String victim = canonicalVictimActor(victimActors.optString(i, ""));
            if (!victim.isEmpty() && canonicalActor.equalsIgnoreCase(victim)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWrongfulActorCandidate(ActorMetrics metrics) {
        if (metrics == null || metrics.score() <= 0.0d) {
            return false;
        }
        if (metrics.hardEvidenceSignals <= 0 || metrics.adverseSignals <= 0) {
            return false;
        }
        if (metrics.concealment > 0 || metrics.omissions > 0) {
            return true;
        }
        if (metrics.financial > 0 && (metrics.contradictions > 0 || metrics.evasion > 0)) {
            return true;
        }
        if (metrics.contradictions > 0 && metrics.supportingBrains.size() >= 2) {
            return true;
        }
        return metrics.financial > 0 && metrics.supportingBrains.contains("B6");
    }

    private static String buildCorpus(JSONObject item) {
        if (item == null) {
            return "";
        }
        return item.optString("summary", "") + " "
                + item.optString("excerpt", "") + " "
                + item.optString("whyItConflicts", "") + " "
                + item.optString("narrative", "") + " "
                + item.optString("text", "") + " "
                + item.optString("label", "");
    }

    private static boolean isComplainantSideContext(String actor, String corpus) {
        String actorLower = actor != null ? actor.toLowerCase(Locale.ROOT) : "";
        String corpusLower = corpus != null ? corpus.toLowerCase(Locale.ROOT) : "";
        if (actorLower.isEmpty() || corpusLower.isEmpty()) {
            return false;
        }
        boolean institutionalActor = containsAny(actorLower,
                "legal",
                "attorney",
                "law",
                "ltd",
                "llc",
                "pty",
                "inc",
                "ministry",
                "department",
                "police",
                "council",
                "association",
                "court",
                "bank",
                "trust",
                "government",
                "office",
                "rakez",
                "saps",
                "ohchr",
                "fcdo",
                "fatf");
        if (institutionalActor) {
            return false;
        }
        boolean authoredComplaint = containsAny(corpusLower,
                "formal complaint",
                "complaint file",
                "criminal complaint",
                "legal practice council",
                "follow-up",
                "follow up",
                "meeting request",
                "private meeting",
                "formal notice",
                "law-enforcement notice",
                "escalation",
                "prepared by:",
                "prepared for",
                "submitted to",
                "i am writing",
                "i write",
                "i confirm",
                "we write",
                "my company",
                "my case",
                "our case",
                "our evidence",
                "regards,",
                "kind regards",
                "sincerely,",
                "dear ",
                "subject:",
                "from:",
                "to:",
                "cc:",
                "bcc:");
        boolean selfMarkedActor = containsAny(actorLower,
                "complainant",
                "claimant",
                "applicant",
                "reporting party",
                "founder",
                "liam",
                "highcock");
        return authoredComplaint || selfMarkedActor;
    }

    private static boolean shouldScoreContradictionItem(String corpus) {
        if (corpus == null || corpus.trim().isEmpty()) {
            return false;
        }
        if (isSupportOnlyNarrative(corpus) && !containsHardForensicSignal(corpus)) {
            return false;
        }
        return containsHardForensicSignal(corpus)
                || containsAny(corpus,
                "proceeded with the deal",
                "termination",
                "no exclusivity",
                "profit share",
                "shareholder agreement",
                "countersigned",
                "forged",
                "archive request",
                "scaquaculture");
    }

    private static boolean shouldScoreTimelineItem(String eventType, String corpus) {
        if (corpus == null || corpus.trim().isEmpty()) {
            return false;
        }
        if (isSupportOnlyNarrative(corpus) && !containsHardForensicSignal(corpus)) {
            return false;
        }
        if ("EXECUTION_STATUS".equalsIgnoreCase(eventType) || "EVICTION_PRESSURE".equalsIgnoreCase(eventType)) {
            return true;
        }
        return containsHardForensicSignal(corpus)
                && !containsAny(corpus,
                "meeting request",
                "private meeting",
                "formal complaint",
                "legal practice council",
                "follow-up",
                "goodwill payment");
    }

    private static boolean shouldScoreConductItem(String conductType, String corpus) {
        if (corpus == null || corpus.trim().isEmpty()) {
            return false;
        }
        if (isSupportOnlyNarrative(corpus) && !containsHardForensicSignal(corpus)) {
            return false;
        }
        if (containsAny(conductType, "CONTRACT_EXECUTION_POSITION", "DOCUMENT_EXECUTION_STATE")) {
            return containsHardForensicSignal(corpus) || containsAny(corpus, "not countersigned", "never signed back", "unsigned", "execution");
        }
        if (containsAny(conductType, "EVICTION_OR_PRESSURE_POSITION")) {
            return containsAny(corpus, "evict", "vacate", "pressure", "excluded", "forced");
        }
        if (containsAny(conductType, "FINANCIAL_POSITION")) {
            return shouldScoreFinancialItem(corpus);
        }
        return false;
    }

    private static boolean shouldScoreFinancialItem(String corpus) {
        if (corpus == null || corpus.trim().isEmpty()) {
            return false;
        }
        if (isSupportOnlyNarrative(corpus) && !containsHardForensicSignal(corpus)) {
            return false;
        }
        return containsAny(corpus,
                "unpaid",
                "withheld",
                "profit share",
                "30%",
                "invoice",
                "salary",
                "payment",
                "owed",
                "invoiced",
                "goodwill",
                "loss",
                "stolen",
                "charter",
                "registration",
                "permit",
                "forced off site",
                "million",
                "deal",
                "order",
                "proceeds",
                "export");
    }

    private static JSONArray buildVictimActorList(
            JSONArray namedParties,
            JSONArray timeline,
            JSONArray conduct,
            JSONArray financial,
            LinkedHashMap<String, ActorMetrics> metricsByActor
    ) throws JSONException {
        JSONArray out = new JSONArray();
        if (namedParties != null) {
            for (int i = 0; i < namedParties.length(); i++) {
                JSONObject party = namedParties.optJSONObject(i);
                if (party == null) {
                    continue;
                }
                String role = party.optString("role", party.optString("actorClass", "")).trim();
                String name = party.optString("name", "").trim();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!name.isEmpty()
                        && "VICTIM".equalsIgnoreCase(role)
                        && isNarrativeUsableActorName(name)
                        && !isVictimActorBlocked(name, metricsByActor)
                        && !containsAny(lower,
                        "all fuels",
                        "individuals",
                        "matters",
                        "common purpose",
                        "account",
                        "screenshot",
                        "trade license",
                        "dual liability",
                        "commit fraud",
                        "cyber forgery",
                        "investigating officer",
                        "south african",
                        "south african law",
                        "google drive",
                        "doc ref",
                        "fabricate liability",
                        "retail licence",
                        "franchisor retail program",
                        "complainant",
                        "first respondent",
                        "second respondent",
                        "respondent",
                        "applicant",
                        "claimant",
                        "ancillary profit centre",
                        "operator",
                        "franchisee",
                        "franchisor",
                        "equipment lease",
                        "trade marks",
                        "trade dress",
                        "creditor",
                        "debtor",
                        "surety",
                        "kerosene",
                        "outlets",
                        "tue",
                        "motor fuel",
                        "motor fuels",
                        "astron product",
                        "astron products",
                        "astron motor fuels",
                        "approved supplier",
                        "consumer price index",
                        "dispute resolution procedure",
                        "designated area",
                        "seton smith",
                        "associates tel",
                        " his ",
                        " forced ",
                        " clause ",
                        "differences matter",
                        "civil disputes")) {
                    addVictimActor(out, name);
                }
            }
        }
        if (out.length() == 0) {
            appendVictimActorFallbacks(out, financial, metricsByActor);
            appendVictimActorFallbacks(out, timeline, metricsByActor);
            appendVictimActorFallbacks(out, conduct, metricsByActor);
        }
        return out;
    }

    private static void appendVictimActorFallbacks(
            JSONArray out,
            JSONArray register,
            LinkedHashMap<String, ActorMetrics> metricsByActor
    ) throws JSONException {
        if (out == null || register == null) {
            return;
        }
        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        for (int i = 0; i < register.length(); i++) {
            JSONObject item = register.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String corpus = buildCorpus(item).toLowerCase(Locale.ROOT);
            if (!looksLikeVictimEvidence(corpus)) {
                continue;
            }
            String actor = canonicalVictimActor(item.optString("actor", ""));
            String counterparty = canonicalVictimActor(item.optString("counterparty", ""));
            if (isVictimFallbackActor(actor) && !isVictimActorBlocked(actor, metricsByActor)) {
                incrementVictimCandidateScore(scores, actor, victimSignalWeight(corpus, false));
            }
            if (isVictimFallbackActor(counterparty)
                    && !counterparty.equalsIgnoreCase(actor)
                    && !isVictimActorBlocked(counterparty, metricsByActor)
                    && looksLikeCounterpartyVictimEvidence(corpus)) {
                incrementVictimCandidateScore(scores, counterparty, victimSignalWeight(corpus, true));
            }
        }
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(scores.entrySet());
        Collections.sort(ranked, (left, right) -> {
            int scoreCompare = Integer.compare(right.getValue(), left.getValue());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return left.getKey().compareToIgnoreCase(right.getKey());
        });
        for (Map.Entry<String, Integer> entry : ranked) {
            if (entry.getValue() <= 0) {
                continue;
            }
            addVictimActor(out, entry.getKey());
        }
    }

    private static boolean isVictimFallbackActor(String actor) {
        return actor != null
                && !actor.trim().isEmpty()
                && !"unresolved actor".equalsIgnoreCase(actor)
                && isNarrativeUsableActorName(actor)
                && !isInstitutionalActor(actor);
    }

    private static boolean isVictimFallbackBlocked(String actor) {
        String lower = actor == null ? "" : actor.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "all fuels",
                "greensky ornamentals",
                "greensky solutions",
                "individuals",
                "matters",
                "common purpose",
                "account",
                "screenshot");
    }

    private static boolean isVictimActorBlocked(String actor, LinkedHashMap<String, ActorMetrics> metricsByActor) {
        if (isVictimFallbackBlocked(actor)) {
            return true;
        }
        return carriesAdverseContradictionWeight(actor, metricsByActor);
    }

    private static boolean carriesAdverseContradictionWeight(String actor, LinkedHashMap<String, ActorMetrics> metricsByActor) {
        String canonicalActor = canonicalVictimActor(actor);
        if (canonicalActor.isEmpty() || metricsByActor == null || metricsByActor.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, ActorMetrics> entry : metricsByActor.entrySet()) {
            String canonicalEntryActor = canonicalVictimActor(entry.getKey());
            if (canonicalEntryActor.isEmpty() || !canonicalActor.equalsIgnoreCase(canonicalEntryActor)) {
                continue;
            }
            ActorMetrics metrics = entry.getValue();
            if (metrics == null) {
                return false;
            }
            if (metrics.verifiedContradictions > 0) {
                return true;
            }
            if (metrics.candidateContradictions > 0 && metrics.contradictionPriority() >= 70) {
                return true;
            }
            if (metrics.criticalContradictions > 0 && metrics.contradictionPriority() >= 50) {
                return true;
            }
            return false;
        }
        return false;
    }

    private static boolean looksLikeVictimEvidence(String corpus) {
        return containsAny(corpus,
                "unpaid share",
                "payment withheld",
                "withheld payment",
                "indebted to",
                "owed to",
                "repayment of my initial investment",
                "wanted to withdraw from the business",
                "withdraw from the business",
                "shareholder exclusion",
                "shareholder oppression",
                "excluded",
                "refused to communicate",
                "blocked both",
                "harmed",
                "loss",
                "stolen",
                "forced off site",
                "forced off");
    }

    private static boolean looksLikeCounterpartyVictimEvidence(String corpus) {
        return containsAny(corpus,
                "indebted to",
                "owed to",
                "repayment of my initial investment",
                "wanted to withdraw from the business",
                "withdraw from the business",
                "shareholder exclusion",
                "shareholder oppression",
                "refused to communicate",
                "blocked both");
    }

    private static int victimSignalWeight(String corpus, boolean counterparty) {
        int score = 1;
        if (containsAny(corpus, "indebted to", "owed to", "repayment of my initial investment")) {
            score += counterparty ? 4 : 1;
        }
        if (containsAny(corpus, "wanted to withdraw from the business", "withdraw from the business")) {
            score += counterparty ? 4 : 1;
        }
        if (containsAny(corpus, "shareholder exclusion", "shareholder oppression", "excluded")) {
            score += counterparty ? 3 : 2;
        }
        if (containsAny(corpus, "blocked both", "refused to communicate")) {
            score += counterparty ? 2 : 1;
        }
        if (containsAny(corpus, "unpaid share", "payment withheld", "withheld payment")) {
            score += counterparty ? 1 : 2;
        }
        return score;
    }

    private static void incrementVictimCandidateScore(Map<String, Integer> scores, String actor, int delta) {
        if (scores == null || actor == null || actor.trim().isEmpty()) {
            return;
        }
        scores.put(actor, scores.getOrDefault(actor, 0) + Math.max(1, delta));
    }

    private static void addVictimActor(JSONArray out, String value) throws JSONException {
        String canonical = canonicalVictimActor(value);
        if (canonical.isEmpty()) {
            return;
        }
        for (int i = 0; i < out.length(); i++) {
            if (canonical.equalsIgnoreCase(out.optString(i, "").trim())) {
                return;
            }
        }
        out.put(canonical);
    }

    private static String canonicalVictimActor(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return "";
        }
        String knownPerson = canonicalKnownPersonName(value);
        if (knownPerson != null) {
            return knownPerson;
        }
        if (lower.endsWith(" yes")
                || lower.endsWith(" no")
                || "his".equals(lower)
                || "forced".equals(lower)
                || "clause".equals(lower)
                || "equipment lease".equals(lower)
                || "trade marks".equals(lower)
                || "trade dress".equals(lower)
                || "creditor".equals(lower)
                || "debtor".equals(lower)
                || "surety".equals(lower)
                || "complainant".equals(lower)
                || "first respondent".equals(lower)
                || "second respondent".equals(lower)
                || "respondent".equals(lower)
                || "applicant".equals(lower)
                || "claimant".equals(lower)
                || "ancillary profit centre".equals(lower)
                || "kerosene".equals(lower)
                || "outlets".equals(lower)
                || "tue".equals(lower)
                || "motor fuel".equals(lower)
                || "motor fuels".equals(lower)
                || "astron product".equals(lower)
                || "astron products".equals(lower)
                || "astron motor fuels".equals(lower)
                || "approved supplier".equals(lower)
                || "consumer price index".equals(lower)
                || "dispute resolution procedure".equals(lower)
                || "designated area".equals(lower)
                || "seton smith".equals(lower)
                || "associates tel".equals(lower)
                || "screenshot".equals(lower)
                || "account".equals(lower)
                || "common purpose".equals(lower)
                || "your phone number".equals(lower)
                || "april liam highcock".equals(lower)
                || "emphasis added".equals(lower)
                || "austrian law".equals(lower)
                || "austrian trade agents".equals(lower)
                || "australian law".equals(lower)
                || "australian competition".equals(lower)
                || "differences matter".equals(lower)
                || "civil disputes".equals(lower)
                || lower.contains(" his ")
                || lower.contains(" forced ")
                || lower.contains(" clause ")) {
            return "";
        }
        return value == null ? "" : value.trim();
    }

    private static boolean isInstitutionalActor(String actor) {
        String lower = actor == null ? "" : actor.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "police",
                "saps",
                "hawks",
                "ministry",
                "department",
                "council",
                "authority",
                "office",
                "government",
                "legal practice council",
                "rakez",
                "samsa",
                "dffe",
                "bank",
                "court",
                "ohchr",
                "fcdo");
    }

    private static String joinStringArray(JSONArray values, int limit) {
        if (values == null || values.length() == 0) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (int i = 0; i < values.length() && items.size() < limit; i++) {
            String value = values.optString(i, "").trim();
            if (!value.isEmpty() && !items.contains(value)) {
                items.add(value);
            }
        }
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + ", and " + items.get(items.size() - 1);
    }

    private static boolean containsHardForensicSignal(String corpus) {
        return containsAny(corpus,
                "proceeded with the deal",
                "thanks for the invoice",
                "thank you for the invoice",
                "invoice",
                "shareholder agreement",
                "agreement states",
                "30%",
                "profit share",
                "termination",
                "sealife",
                "forged",
                "whatsapp",
                "screenshot",
                "archive request",
                "scaquaculture",
                "unauthorised",
                "unauthorized",
                "issued a termination notice",
                "termination notice",
                "completed order",
                "client confirmation",
                "invoice accepted",
                "must i do the marketing",
                "selective screenshots",
                "attack call",
                "hong kong",
                "countersigned",
                "not countersigned",
                "never countersigned",
                "group exit",
                "metadata",
                "tamper",
                "deal completed",
                "same financial or quantitative event",
                "same event or claim",
                "same event chain");
    }

    private static boolean isSupportOnlyNarrative(String corpus) {
        return containsAny(corpus,
                "formal complaint",
                "complaint file",
                "legal practice council",
                "follow-up",
                "follow up",
                "meeting request",
                "private meeting",
                "goodwill payment",
                "settlement",
                "formal notice",
                "law-enforcement notice",
                "escalation",
                "dear ",
                "subject:",
                "from:",
                "to:",
                "cc:",
                "bcc:");
    }

    private static JSONArray sortByActorAndStatus(JSONArray source) throws JSONException {
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null) {
                items.add(item);
            }
        }
        Collections.sort(items, (left, right) -> {
            int statusCompare = Integer.compare(
                    "VERIFIED".equalsIgnoreCase(right.optString("status", "")) ? 1 : 0,
                    "VERIFIED".equalsIgnoreCase(left.optString("status", "")) ? 1 : 0
            );
            if (statusCompare != 0) {
                return statusCompare;
            }
            return left.optString("actor", "").compareToIgnoreCase(right.optString("actor", ""));
        });
        JSONArray out = new JSONArray();
        for (JSONObject item : items) {
            out.put(item);
        }
        return out;
    }

    private static JSONObject findNextMaterialActor(JSONArray actorScores, String leadActor) {
        if (actorScores == null) {
            return null;
        }
        for (int i = 0; i < actorScores.length(); i++) {
            JSONObject item = actorScores.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = normalizeActor(item.optString("actor", ""));
            if (actor == null || actor.equalsIgnoreCase(leadActor)) {
                continue;
            }
            if (item.optDouble("dishonestyScore", 0.0d) < 15.0d) {
                continue;
            }
            return item;
        }
        return null;
    }

    private static String resolvePropositionActor(JSONObject item, JSONArray namedParties) {
        String actor = normalizeActor(item.optString("actor", ""));
        if (actor != null && isNarrativeUsableActorName(actor)) {
            return actor;
        }
        String target = normalizeActor(item.optString("target", ""));
        if (target != null && isNarrativeUsableActorName(target)) {
            return target;
        }
        return findNamedActorInCorpus(buildCorpus(item), namedParties);
    }

    private static String findNamedActorInCorpus(String corpus, JSONArray namedParties) {
        if (corpus == null || corpus.trim().isEmpty() || namedParties == null) {
            return null;
        }
        String normalizedCorpus = " " + corpus.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        String best = null;
        int bestLength = -1;
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject item = namedParties.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String candidate = normalizeActor(item.optString("name", ""));
            if (candidate == null || !isNarrativeUsableActorName(candidate)) {
                continue;
            }
            String normalizedCandidate = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            if (normalizedCandidate.isEmpty()) {
                continue;
            }
            boolean matched = normalizedCorpus.contains(" " + normalizedCandidate + " ");
            if (!matched) {
                String[] parts = normalizedCandidate.split("\\s+");
                if (parts.length >= 1 && parts[0].length() >= 4) {
                    matched = normalizedCorpus.contains(" " + parts[0] + " ");
                }
            }
            if (matched && candidate.length() > bestLength) {
                best = candidate;
                bestLength = candidate.length();
            }
        }
        return best;
    }

    private static void mergeCanonicalNamedPartyActors(
            LinkedHashMap<String, ActorMetrics> out,
            JSONArray namedParties
    ) {
        if (out == null || out.isEmpty() || namedParties == null || namedParties.length() == 0) {
            return;
        }
        List<String> actors = new ArrayList<>(out.keySet());
        for (String actor : actors) {
            ActorMetrics aliasMetrics = out.get(actor);
            if (aliasMetrics == null) {
                continue;
            }
            String canonical = canonicalizeNamedPartyActor(actor, namedParties);
            if (canonical == null || canonical.equalsIgnoreCase(actor)) {
                continue;
            }
            ActorMetrics canonicalMetrics = out.get(canonical);
            if (canonicalMetrics == null) {
                canonicalMetrics = new ActorMetrics(canonical);
                out.put(canonical, canonicalMetrics);
            }
            mergeActorMetrics(canonicalMetrics, aliasMetrics);
            out.remove(actor);
        }
    }

    private static void pruneNarrativeActors(LinkedHashMap<String, ActorMetrics> out) {
        if (out == null || out.isEmpty()) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        for (String actor : out.keySet()) {
            if (!isNarrativeUsableActorName(actor)) {
                toRemove.add(actor);
            }
        }
        for (String actor : toRemove) {
            out.remove(actor);
        }
    }

    private static String canonicalizeNamedPartyActor(String actor, JSONArray namedParties) {
        String normalizedActor = normalizeActor(actor);
        if (normalizedActor == null || namedParties == null) {
            return normalizedActor;
        }
        String actorToken = normalizedActor.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (actorToken.isEmpty() || actorToken.contains(" ")) {
            return normalizedActor;
        }
        String resolved = null;
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject item = namedParties.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String candidate = normalizeActor(item.optString("name", ""));
            if (candidate == null || !isNarrativeUsableActorName(candidate)) {
                continue;
            }
            String normalizedCandidate = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            if (!normalizedCandidate.contains(" ")) {
                continue;
            }
            String[] parts = normalizedCandidate.split("\\s+");
            boolean matched = actorToken.equals(parts[0]) || actorToken.equals(parts[parts.length - 1]);
            if (!matched && normalizedCandidate.startsWith(actorToken + " ")) {
                matched = true;
            }
            if (!matched) {
                continue;
            }
            if (resolved != null && !resolved.equalsIgnoreCase(candidate)) {
                return normalizedActor;
            }
            resolved = candidate;
        }
        return resolved != null ? resolved : normalizedActor;
    }

    private static void mergeActorMetrics(ActorMetrics target, ActorMetrics source) {
        if (target == null || source == null || target == source) {
            return;
        }
        target.contradictions += source.contradictions;
        target.omissions += source.omissions;
        target.evasion += source.evasion;
        target.concealment += source.concealment;
        target.financial += source.financial;
        target.adverseSignals += source.adverseSignals;
        target.hardEvidenceSignals += source.hardEvidenceSignals;
        target.verifiedContradictions += source.verifiedContradictions;
        target.candidateContradictions += source.candidateContradictions;
        target.highConfidenceContradictions += source.highConfidenceContradictions;
        target.criticalContradictions += source.criticalContradictions;
        target.pages.addAll(source.pages);
        target.supportingBrains.addAll(source.supportingBrains);
        target.flags.addAll(source.flags);
        target.evidenceNotes.addAll(source.evidenceNotes);
    }

    private static boolean isNarrativeUsableActorName(String actor) {
        String normalized = normalizeActor(actor);
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower,
                "south african",
                "general manager",
                "legal department",
                "permit holder",
                "complainant",
                "first respondent",
                "second respondent",
                "respondent",
                "applicant",
                "claimant",
                "ancillary profit centre",
                "equipment lease",
                "trade marks",
                "trade dress",
                "creditor",
                "debtor",
                "surety",
                "kerosene",
                "outlets",
                "tue",
                "motor fuel",
                "motor fuels",
                "astron product",
                "astron products",
                "astron motor fuels",
                "approved supplier",
                "consumer price index",
                "dispute resolution procedure",
                "designated area",
                "seton smith",
                "associates tel",
                "section",
                "negative evidence",
                "asset forfeiture unit",
                "natal south",
                "south africa",
                "canonical spec",
                "master forensic archive",
                "deployment validator",
                "guardianship treaty",
                "triple verification doctrine",
                "mail delivery subsystem",
                "unified call center",
                "rakpp info",
                "urgent cross",
                "institutional silence despite",
                "immediate instruction opportunity",
                "sealed files",
                "greensky fraud cases",
                "southbridge misconduct",
                "integrity lock",
                "selective omissions",
                "fiduciary duty key",
                "fraudulent evidence key",
                "document execution state",
                "gaslighting refusal",
                "explanation thank",
                "let kevin",
                "liam give kevin",
                "immediate engagement required",
                "legal violation",
                "order issues",
                "march email",
                "accused cite",
                "screenshot",
                "account",
                "common purpose",
                "your phone number",
                "april liam highcock",
                "emphasis added",
                "austrian law",
                "austrian trade agents",
                "australian law",
                "australian competition",
                "fabricate liability",
                "sealed fraud",
                "registered address",
                "shareholder agreement")) {
            return false;
        }
        return !("ref".equals(lower)
                || "and".equals(lower)
                || "not".equals(lower)
                || "his".equals(lower)
                || "forced".equals(lower)
                || "clause".equals(lower)
                || "that".equals(lower)
                || "which".equals(lower)
                || "goodwill".equals(lower)
                || "outcome".equals(lower)
                || "pattern".equals(lower)
                || "behind".equals(lower)
                || "exposing".equals(lower)
                || "racketeering".equals(lower)
                || "arbitration".equals(lower)
                || "access".equals(lower)
                || "action".equals(lower)
                || "legal".equals(lower)
                || "each".equals(lower)
                || "evidence".equals(lower)
                || "gold standard".equals(lower)
                || "integrity lock".equals(lower)
                || "selective omissions".equals(lower)
                || "fiduciary duty key".equals(lower)
                || "fraudulent evidence key".equals(lower)
                || "document execution state".equals(lower)
                || "gaslighting refusal".equals(lower)
                || "for".equals(lower)
                || "any".equals(lower)
                || "case".equals(lower)
                || "what".equals(lower)
                || "legal violation".equals(lower)
                || "order issues".equals(lower)
                || "march email".equals(lower)
                || "accused cite".equals(lower)
                || "immediate engagement required".equals(lower)
                || "client".equals(lower)
                || "operator".equals(lower)
                || "franchisee".equals(lower)
                || "franchisor".equals(lower)
                || "franchisor retail program".equals(lower)
                || "retail licence".equals(lower)
                || "screenshot".equals(lower)
                || "account".equals(lower)
                || "common purpose".equals(lower)
                || "gmail".equals(lower)
                || "google".equals(lower)
                || "google drive".equals(lower)
                || "hong kong".equals(lower)
                || "greensky".equals(lower)
                || "breach".equals(lower)
                || "company".equals(lower)
                || "federal law".equals(lower)
                || "even".equals(lower)
                || "legal relevance".equals(lower)
                || "was".equals(lower)
                || "shareholder".equals(lower)
                || "transfer".equals(lower)
                || "oppression".equals(lower)
                || "unauthorized".equals(lower)
                || "unauthorised".equals(lower)
                || "request".equals(lower)
                || "attempt".equals(lower)
                || "the company".equals(lower)
                || lower.endsWith(" yes")
                || lower.endsWith(" no")
                || lower.contains(" his ")
                || lower.contains(" forced ")
                || lower.contains(" clause ")
                || lower.contains("differences matter")
                || lower.contains("civil disputes"));
    }

    private static int countByStatus(JSONArray source, String requiredStatus) {
        if (source == null || requiredStatus == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && requiredStatus.equalsIgnoreCase(item.optString("status", ""))) {
                count++;
            }
        }
        return count;
    }

    private static int countBySeverity(JSONArray source, String severity) {
        if (source == null || severity == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && severity.equalsIgnoreCase(item.optString("severity", ""))) {
                count++;
            }
        }
        return count;
    }

    private static void dropEmptyActors(LinkedHashMap<String, ActorMetrics> out) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ActorMetrics> entry : out.entrySet()) {
            if (entry.getValue().isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        for (String actor : toRemove) {
            out.remove(actor);
        }
    }

    private static ActorMetrics ensureActor(LinkedHashMap<String, ActorMetrics> out, String actor) {
        ActorMetrics metrics = out.get(actor);
        if (metrics == null) {
            metrics = new ActorMetrics(actor);
            out.put(actor, metrics);
        }
        return metrics;
    }

    private static void addPages(LinkedHashSet<Integer> target, JSONArray anchors, int fallbackPage) {
        if (anchors != null) {
            for (int i = 0; i < anchors.length(); i++) {
                JSONObject anchor = anchors.optJSONObject(i);
                if (anchor == null) {
                    continue;
                }
                int page = anchor.optInt("page", 0);
                if (page > 0) {
                    target.add(page);
                }
            }
        }
        if (fallbackPage > 0) {
            target.add(fallbackPage);
        }
    }

    private static JSONObject safeObject(JSONObject value) {
        return value != null ? value : new JSONObject();
    }

    private static JSONArray toJsonArray(List<?> values) throws JSONException {
        JSONArray out = new JSONArray();
        if (values == null) {
            return out;
        }
        for (Object value : values) {
            out.put(value);
        }
        return out;
    }

    private static String normalizeActor(String actor) {
        if (actor == null) {
            return null;
        }
        String trimmed = actor.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String knownPerson = canonicalKnownPersonName(trimmed);
        if (knownPerson != null) {
            return knownPerson;
        }
        if ("unresolved actor".equals(lower)
                || "the".equals(lower)
                || "and".equals(lower)
                || "not".equals(lower)
                || "that".equals(lower)
                || "which".equals(lower)
                || "you".equals(lower)
                || "this".equals(lower)
                || "all".equals(lower)
                || "record".equals(lower)
                || "the record".equals(lower)
                || "financial irregularities".equals(lower)
                || "shareholder oppression".equals(lower)
                || "fraudulent evidence".equals(lower)
                || "march".equals(lower)
                || "april".equals(lower)
                || "may".equals(lower)
                || "june".equals(lower)
                || "july".equals(lower)
                || "august".equals(lower)
                || "september".equals(lower)
                || "october".equals(lower)
                || "november".equals(lower)
                || "december".equals(lower)
                || "january".equals(lower)
                || "february".equals(lower)
                || "fraud".equals(lower)
                || "complaint".equals(lower)
                || "agreement".equals(lower)
                || "contract".equals(lower)
                || "business".equals(lower)
                || "deal".equals(lower)
                || "invoice".equals(lower)
                || "operator".equals(lower)
                || "franchisee".equals(lower)
                || "franchisor".equals(lower)
                || "franchisor retail program".equals(lower)
                || "retail licence".equals(lower)
                || "section".equals(lower)
                || "negative evidence".equals(lower)
                || "asset forfeiture unit".equals(lower)
                || "natal south".equals(lower)
                || "south africa".equals(lower)
                || "screenshot".equals(lower)
                || "account".equals(lower)
                || "common purpose".equals(lower)
                || "your phone number".equals(lower)
                || "april liam highcock".equals(lower)
                || "emphasis added".equals(lower)
                || "austrian law".equals(lower)
                || "austrian trade agents".equals(lower)
                || "australian law".equals(lower)
                || "australian competition".equals(lower)
                || "goodwill".equals(lower)
                || "outcome".equals(lower)
                || "pattern".equals(lower)
                || "behind".equals(lower)
                || "exposing".equals(lower)
                || "racketeering".equals(lower)
                || "arbitration".equals(lower)
                || "ref".equals(lower)
                || "client".equals(lower)
                || "gmail".equals(lower)
                || "google".equals(lower)
                || "google drive".equals(lower)
                || "hong kong".equals(lower)
                || "shareholder".equals(lower)
                || "transfer".equals(lower)
                || "oppression".equals(lower)
                || "unauthorized".equals(lower)
                || "unauthorised".equals(lower)
                || "request".equals(lower)
                || "attempt".equals(lower)
                || "records".equals(lower)
                || "shipment".equals(lower)
                || "thanks".equals(lower)
                || "what".equals(lower)
                || "even".equals(lower)
                || "was".equals(lower)
                || "breach".equals(lower)
                || "company".equals(lower)
                || "federal law".equals(lower)
                || "legal violation".equals(lower)
                || "order issues".equals(lower)
                || "march email".equals(lower)
                || "accused cite".equals(lower)
                || "immediate engagement required".equals(lower)) {
            return null;
        }
        return trimmed;
    }

    private static String canonicalKnownPersonName(String value) {
        String normalized = normalizeNameTokens(value);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("des".equals(normalized)
                || "des smith".equals(normalized)
                || "desmond smith".equals(normalized)
                || normalized.startsWith("desmond owen smith")) {
            return "Desmond Smith";
        }
        if ("wayne".equals(normalized)
                || "wayne nel".equals(normalized)
                || "wayne nell".equals(normalized)) {
            return "Wayne Nel";
        }
        if ("your dad".equals(normalized)
                || "gary".equals(normalized)
                || "gary highcock".equals(normalized)) {
            return "Gary Highcock";
        }
        if ("marius".equals(normalized)
                || "marius nor".equals(normalized)
                || "marius nortje".equals(normalized)) {
            return "Marius Nortje";
        }
        if ("liam".equals(normalized)
                || "liam highcock".equals(normalized)) {
            return "Liam Highcock";
        }
        if ("kevin".equals(normalized)
                || "kevin lappeman".equals(normalized)) {
            return "Kevin Lappeman";
        }
        return null;
    }

    private static String normalizeNameTokens(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return ascii
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String corpus, String... needles) {
        if (corpus == null || needles == null) {
            return false;
        }
        String lower = corpus.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && lower.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
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

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static void maybeAppend(JSONArray target, JSONObject item) {
        if (target != null && item != null) {
            target.put(item);
        }
    }

    private static void putSafe(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }

    private static String safeMessage(Exception e) {
        return e == null || e.getMessage() == null ? "unknown error" : e.getMessage();
    }

    private static final class ActorMetrics {
        final String actor;
        int contradictions;
        int omissions;
        int evasion;
        int concealment;
        int financial;
        int adverseSignals;
        int hardEvidenceSignals;
        int verifiedContradictions;
        int candidateContradictions;
        int highConfidenceContradictions;
        int criticalContradictions;
        final LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        final LinkedHashSet<String> supportingBrains = new LinkedHashSet<>();
        final LinkedHashSet<String> flags = new LinkedHashSet<>();
        final LinkedHashSet<String> evidenceNotes = new LinkedHashSet<>();

        ActorMetrics(String actor) {
            this.actor = actor;
        }

        boolean isEmpty() {
            return contradictions <= 0
                    && omissions <= 0
                    && evasion <= 0
                    && concealment <= 0
                    && financial <= 0
                    && supportingBrains.isEmpty();
        }

        LinkedHashSet<String> flagSet() {
            LinkedHashSet<String> out = new LinkedHashSet<>(flags);
            if (contradictions > 0) out.add("contradictions");
            if (verifiedContradictions > 0) out.add("verified_contradictions");
            if (candidateContradictions > 0) out.add("candidate_contradictions");
            if (omissions > 0) out.add("omissions");
            if (evasion > 0) out.add("evasion");
            if (concealment > 0) out.add("concealment");
            if (financial > 0) out.add("financial");
            return out;
        }

        int contradictionPriority() {
            int priority = (verifiedContradictions * 100)
                    + (candidateContradictions * 35)
                    + (highConfidenceContradictions * 15)
                    + (criticalContradictions * 20);
            if (contradictions >= 4) {
                priority += 20;
            }
            if (contradictions >= 6) {
                priority += 15;
            }
            return priority;
        }

        double score() {
            double contradictionLeadScore = Math.min(55.0d,
                    (verifiedContradictions * 18.0d)
                            + (candidateContradictions * 8.0d)
                            + (highConfidenceContradictions * 4.0d)
                            + (criticalContradictions * 6.0d));
            if (contradictions >= 4) {
                contradictionLeadScore += 6.0d;
            }
            if (contradictions >= 6) {
                contradictionLeadScore += 4.0d;
            }
            double omissionScore = Math.min(12.0d, omissions * 4.0d);
            double evasionScore = Math.min(10.0d, evasion * 3.0d);
            double concealmentScore = Math.min(14.0d, concealment * 5.0d);
            double financialScore = Math.min(9.0d, financial * 3.0d);
            double corroborationBonus = Math.min(10.0d, hardEvidenceSignals * 2.0d);
            double total = contradictionLeadScore + omissionScore + evasionScore + concealmentScore + financialScore + corroborationBonus;
            return Math.round(Math.min(100.0d, total) * 10.0d) / 10.0d;
        }

        String severity() {
            double score = score();
            if (score >= 81.0d) {
                return "SEVERE";
            }
            if (score >= 51.0d) {
                return "HIGH";
            }
            if (score >= 21.0d) {
                return "MODERATE";
            }
            return "LOW";
        }

        JSONObject heatmapJson() throws JSONException {
            JSONObject out = new JSONObject();
            out.put("contradictions", contradictions);
            out.put("verifiedContradictions", verifiedContradictions);
            out.put("candidateContradictions", candidateContradictions);
            out.put("highConfidenceContradictions", highConfidenceContradictions);
            out.put("criticalContradictions", criticalContradictions);
            out.put("omissions", omissions);
            out.put("evasion", evasion);
            out.put("concealment", concealment);
            out.put("financial", financial);
            out.put("contradictionPriority", contradictionPriority());
            out.put("severity", severity());
            out.put("dishonestyScore", score());
            return out;
        }

        List<String> topEvidenceNotes(int limit) {
            List<String> out = new ArrayList<>();
            for (String note : evidenceNotes) {
                if (note != null && !note.trim().isEmpty()) {
                    out.add(note.trim());
                }
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        }
    }
}
