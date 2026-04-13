package com.verum.omnis.promotion;

import com.verum.omnis.core.FindingAudit;
import com.verum.omnis.core.PromotionDecision;
import com.verum.omnis.forensic.EvidenceAnchor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class AuditService {

    public FindingAudit build(JSONObject finding, PromotionDecision decision) {
        List<String> brainIds = new ArrayList<>();
        String findingType = finding.optString("findingType", "");
        String explicitBrainId = finding.optString("brainId", "").trim();
        if (!explicitBrainId.isEmpty()) {
            brainIds.add(explicitBrainId);
        }
        if (isContradictionFinding(finding)) {
            addBrainId(brainIds, "B1");
        }
        if ("UNPAID_PROFIT_SHARE".equalsIgnoreCase(findingType)
                || "UNPAID_INVOICE".equalsIgnoreCase(findingType)) {
            addBrainId(brainIds, "B6");
        }
        if ("TIMELINE_EVENT".equalsIgnoreCase(findingType)) {
            addBrainId(brainIds, "B5");
        }
        if ("INCIDENT_EVENT".equalsIgnoreCase(findingType)) {
            addBrainId(brainIds, "B5");
        }
        if ("OVERSIGHT_PATTERN".equalsIgnoreCase(findingType)) {
            addBrainId(brainIds, explicitBrainId.isEmpty() ? "B8" : explicitBrainId);
        }
        if (brainIds.isEmpty()) {
            brainIds.add("B9");
        }

        List<String> actors = new ArrayList<>();
        String actor = finding.optString("actor", "").trim();
        if (!actor.isEmpty()) {
            actors.add(actor);
        }

        List<EvidenceAnchor> anchors = new ArrayList<>();
        List<String> sourceEvidenceIds = new ArrayList<>();
        LinkedHashSet<String> uniqueEvidenceIds = new LinkedHashSet<>();
        JSONArray anchorArray = finding.optJSONArray("anchors");
        if (anchorArray != null) {
            for (int i = 0; i < anchorArray.length(); i++) {
                JSONObject anchor = anchorArray.optJSONObject(i);
                if (anchor == null) continue;
                int page = anchor.optInt("page", 0);
                String evidenceId = "page:" + page;
                anchors.add(new EvidenceAnchor(
                        evidenceId,
                        "sealed-evidence",
                        page,
                        anchor.optInt("lineStart", 0),
                        anchor.optInt("lineEnd", 0),
                        anchor.optString("messageId", ""),
                        anchor.optString("timestamp", ""),
                        anchor.optString("blockId", ""),
                        anchor.has("fileOffset") ? anchor.optLong("fileOffset", -1L) : -1L,
                        anchor.optString("exhibitId", ""),
                        finding.optString("excerpt", ""),
                        finding.optString("findingType", finding.optString("conflictType", "FINDING"))
                ));
                if (uniqueEvidenceIds.add(evidenceId)) {
                    sourceEvidenceIds.add(evidenceId);
                }
            }
        }

        List<String> excerpts = new ArrayList<>();
        addIfPresent(excerpts, finding.optString("excerpt", ""));
        addIfPresent(excerpts, finding.optString("summary", ""));
        addIfPresent(excerpts, finding.optString("whyItConflicts", ""));
        if (finding.has("propositionA")) {
            addIfPresent(excerpts, finding.optJSONObject("propositionA").optString("text", ""));
        }
        if (finding.has("propositionB")) {
            addIfPresent(excerpts, finding.optJSONObject("propositionB").optString("text", ""));
        }

        List<String> ruleHits = new ArrayList<>();
        ruleHits.add("P1_ANCHOR_RULE");
        if (!actor.isEmpty()) {
            ruleHits.add("P2_ACTOR_RULE");
        }
        if (isContradictionFinding(finding)) {
            ruleHits.add("CONTRADICTION_REGISTER");
        }
        if ("UNPAID_PROFIT_SHARE".equalsIgnoreCase(findingType)
                || "UNPAID_INVOICE".equalsIgnoreCase(findingType)) {
            ruleHits.add("FINANCIAL_RECONCILIATION_REGISTER");
        }
        if ("TIMELINE_EVENT".equalsIgnoreCase(findingType)) {
            ruleHits.add("TIMELINE_ANCHOR_REGISTER");
        }
        if ("INCIDENT_EVENT".equalsIgnoreCase(findingType)) {
            ruleHits.add("INCIDENT_REGISTER");
        }
        if ("OVERSIGHT_PATTERN".equalsIgnoreCase(findingType)) {
            ruleHits.add("ACTOR_CONDUCT_REGISTER");
        }

        List<String> contradictionChecks = new ArrayList<>();
        contradictionChecks.add("status=" + finding.optString("status", "UNKNOWN"));
        contradictionChecks.add("conflictType=" + finding.optString("conflictType", "NONE"));

        List<String> corroborationChecks = new ArrayList<>();
        corroborationChecks.add("distinctAnchorCount=" + anchors.size());
        corroborationChecks.add("promotionDecision=" + decision.promoted);

        List<String> exclusionChecks = new ArrayList<>();
        if (!decision.promoted) {
            exclusionChecks.add("failedRules=" + String.join(",", decision.failedRules));
        }

        List<String> uncertainties = new ArrayList<>();
        if ("unresolved actor".equalsIgnoreCase(actor)) {
            uncertainties.add("Actor remains unresolved.");
        }
        if (!decision.promoted) {
            uncertainties.add("Promotion failed constitutional gate.");
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("anchorCount", (double) anchors.size());
        scores.put("evidenceIdCount", (double) sourceEvidenceIds.size());
        scores.put("failedRuleCount", (double) decision.failedRules.size());

        return new FindingAudit(
                buildFindingId(finding),
                brainIds,
                findingType.isEmpty() ? finding.optString("conflictType", "FINDING") : findingType,
                actors,
                anchors,
                sourceEvidenceIds,
                excerpts,
                ruleHits,
                contradictionChecks,
                corroborationChecks,
                exclusionChecks,
                uncertainties,
                scores,
                decision.reason,
                decision.promoted ? null : decision.reason
        );
    }

    private static String buildFindingId(JSONObject finding) {
        return finding.optString("findingType", finding.optString("conflictType", "finding"))
                + "|"
                + finding.optString("actor", "unresolved actor")
                + "|"
                + finding.optInt("page", 0);
    }

    private static void addIfPresent(List<String> target, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.add(value.trim());
        }
    }

    private static void addBrainId(List<String> brainIds, String brainId) {
        if (brainId == null || brainId.trim().isEmpty() || brainIds.contains(brainId.trim())) {
            return;
        }
        brainIds.add(brainId.trim());
    }

    private static boolean isContradictionFinding(JSONObject finding) {
        if (finding == null) {
            return false;
        }
        String conflictType = finding.optString("conflictType", "");
        return "PROPOSITION_CONFLICT".equalsIgnoreCase(conflictType)
                || "NEGATION".equalsIgnoreCase(conflictType)
                || "NUMERIC".equalsIgnoreCase(conflictType)
                || "TIMELINE".equalsIgnoreCase(conflictType)
                || "LOCATION".equalsIgnoreCase(conflictType)
                || "CONTRADICTION".equalsIgnoreCase(finding.optString("findingType", ""));
    }
}
