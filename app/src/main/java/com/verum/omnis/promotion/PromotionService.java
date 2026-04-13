package com.verum.omnis.promotion;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.PromotionDecision;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class PromotionService {

    public PromotionDecision decide(JSONObject finding, AnalysisEngine.ForensicReport report) {
        List<String> failed = new ArrayList<>();
        if (finding == null) {
            failed.add("P0_NO_FINDING");
            return new PromotionDecision(false, "No finding object was available for promotion.", failed);
        }

        JSONArray anchors = finding.optJSONArray("anchors");
        if (anchors == null || anchors.length() == 0) {
            failed.add("P1_ANCHOR_RULE");
        }

        String actor = finding.optString("actor", "").trim();
        if (finding.has("actor") && !isPromotableActor(actor)) {
            failed.add("P2_ACTOR_RULE");
        }

        boolean contradictionFinding = isContradictionFinding(finding);
        String findingType = finding.optString("findingType", "");
        boolean profitShareFinding = "UNPAID_PROFIT_SHARE".equalsIgnoreCase(findingType);
        boolean unpaidInvoiceFinding = "UNPAID_INVOICE".equalsIgnoreCase(findingType);
        boolean financialFinding = profitShareFinding || unpaidInvoiceFinding;
        boolean timelineFinding = "TIMELINE_EVENT".equalsIgnoreCase(finding.optString("findingType", ""));
        boolean oversightFinding = "OVERSIGHT_PATTERN".equalsIgnoreCase(finding.optString("findingType", ""));
        boolean incidentFinding = "INCIDENT_EVENT".equalsIgnoreCase(finding.optString("findingType", ""));

        if (contradictionFinding) {
            if (!finding.has("propositionA") || !finding.has("propositionB")) {
                failed.add("P3_EVIDENTIARY_SUFFICIENCY");
            }
        } else if (financialFinding) {
            if (profitShareFinding) {
                if (finding.optString("amount", "").trim().isEmpty()
                        || finding.optString("basis", "").trim().isEmpty()
                        || finding.optString("expectedShare", "").trim().isEmpty()) {
                    failed.add("P3_EVIDENTIARY_SUFFICIENCY");
                }
            } else if (unpaidInvoiceFinding) {
                if (finding.optString("amount", "").trim().isEmpty()
                        || finding.optString("basis", "").trim().isEmpty()
                        || finding.optString("counterparty", "").trim().isEmpty()) {
                    failed.add("P3_EVIDENTIARY_SUFFICIENCY");
                }
            }
        } else if (timelineFinding) {
            if (!finding.optBoolean("primaryEvidence", false)) {
                failed.add("P3_EVIDENTIARY_SUFFICIENCY");
            }
            if (finding.optString("eventType", finding.optString("timelineType", "")).trim().isEmpty()
                    && finding.optString("date", "").trim().isEmpty()) {
                failed.add("P3_EVIDENTIARY_SUFFICIENCY");
            }
        } else if (oversightFinding) {
            if (!finding.optBoolean("primaryEvidence", false)) {
                failed.add("P3_EVIDENTIARY_SUFFICIENCY");
            }
            if (finding.optString("conductType", finding.optString("oversightType", "")).trim().isEmpty()) {
                failed.add("P3_EVIDENTIARY_SUFFICIENCY");
            }
        } else if (incidentFinding) {
            if (finding.optString("narrative", finding.optString("summary", finding.optString("description", ""))).trim().isEmpty()) {
                failed.add("P3_EVIDENTIARY_SUFFICIENCY");
            }
            if (anchors == null || anchors.length() == 0) {
                failed.add("P3_EVIDENTIARY_SUFFICIENCY");
            }
        } else if (finding.optString("summary", "").trim().isEmpty()
                && finding.optString("narrative", "").trim().isEmpty()
                && finding.optString("excerpt", "").trim().isEmpty()) {
            failed.add("P3_EVIDENTIARY_SUFFICIENCY");
        }

        LinkedHashSet<String> uniqueSupports = new LinkedHashSet<>();
        if (anchors != null) {
            for (int i = 0; i < anchors.length(); i++) {
                JSONObject anchor = anchors.optJSONObject(i);
                if (anchor == null) continue;
                int page = anchor.optInt("page", 0);
                if (page > 0) {
                    uniqueSupports.add("page:" + page);
                }
            }
        }
        if (contradictionFinding && finding.has("propositionA") && finding.has("propositionB")) {
            uniqueSupports.add("proposition:a");
            uniqueSupports.add("proposition:b");
        }
        if (incidentFinding) {
            String pageAnchor = finding.optString("pageAnchor", "").trim();
            String source = finding.optString("source", "").trim();
            String incidentType = finding.optString("incidentType", "").trim();
            if (!pageAnchor.isEmpty()) {
                uniqueSupports.add("pageAnchor:" + pageAnchor);
            }
            if (!source.isEmpty()) {
                uniqueSupports.add("source:" + source);
            }
            if (!incidentType.isEmpty()) {
                uniqueSupports.add("incidentType:" + incidentType);
            }
        }
        addCrossBrainSupports(uniqueSupports, finding, report, contradictionFinding);
        if (uniqueSupports.size() < 2) {
            failed.add("P4_CORROBORATION_RULE");
        }

        if ("REJECTED".equalsIgnoreCase(finding.optString("status", ""))) {
            failed.add("P5_CONTRADICTION_CHECK");
        }

        if (report == null || report.evidenceHash == null || report.evidenceHash.trim().isEmpty()) {
            failed.add("P6_PROVENANCE_RULE");
        }

        if (finding.optString("summary", "").trim().isEmpty()
                && finding.optString("whyItConflicts", "").trim().isEmpty()
                && finding.optString("narrative", "").trim().isEmpty()) {
            failed.add("P7_EXPLAINABILITY_RULE");
        }

        boolean promoted = failed.isEmpty();
        String reason = promoted
                ? "Promotion rules P1-P7 passed."
                : "Promotion blocked because " + failed.size() + " rule(s) failed.";
        return new PromotionDecision(promoted, reason, failed);
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

    private void addCrossBrainSupports(
            LinkedHashSet<String> uniqueSupports,
            JSONObject finding,
            AnalysisEngine.ForensicReport report,
            boolean contradictionFinding
    ) {
        if (!contradictionFinding || uniqueSupports == null || finding == null || report == null) {
            return;
        }
        JSONObject synthesis = report.forensicSynthesis;
        if (synthesis == null || synthesis.length() == 0) {
            return;
        }
        JSONArray crossBrain = synthesis.optJSONArray("crossBrainContradictions");
        if (crossBrain == null || crossBrain.length() == 0) {
            return;
        }

        String actor = finding.optString("actor", "").trim();
        LinkedHashSet<Integer> findingPages = collectFindingPages(finding);
        for (int i = 0; i < crossBrain.length(); i++) {
            JSONObject item = crossBrain.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (!actor.isEmpty() && !actor.equalsIgnoreCase(item.optString("actor", "").trim())) {
                continue;
            }
            JSONArray brains = item.optJSONArray("supportingBrains");
            if (brains == null || brains.length() < 2) {
                continue;
            }
            if (!findingPages.isEmpty() && !hasPageOverlap(findingPages, item.optJSONArray("anchorPages"))) {
                continue;
            }
            for (int j = 0; j < brains.length(); j++) {
                String brain = brains.optString(j, "").trim();
                if (!brain.isEmpty()) {
                    uniqueSupports.add("brain:" + brain);
                }
            }
        }
    }

    private LinkedHashSet<Integer> collectFindingPages(JSONObject finding) {
        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        if (finding == null) {
            return pages;
        }
        JSONArray anchors = finding.optJSONArray("anchors");
        if (anchors != null) {
            for (int i = 0; i < anchors.length(); i++) {
                JSONObject anchor = anchors.optJSONObject(i);
                if (anchor == null) {
                    continue;
                }
                int page = anchor.optInt("page", 0);
                if (page > 0) {
                    pages.add(page);
                }
            }
        }
        collectPropositionPage(pages, finding.optJSONObject("propositionA"));
        collectPropositionPage(pages, finding.optJSONObject("propositionB"));
        return pages;
    }

    private void collectPropositionPage(LinkedHashSet<Integer> pages, JSONObject proposition) {
        if (pages == null || proposition == null) {
            return;
        }
        JSONObject anchor = proposition.optJSONObject("anchor");
        if (anchor == null) {
            return;
        }
        int page = anchor.optInt("page", 0);
        if (page > 0) {
            pages.add(page);
        }
    }

    private boolean hasPageOverlap(LinkedHashSet<Integer> findingPages, JSONArray anchorPages) {
        if (findingPages == null || findingPages.isEmpty() || anchorPages == null || anchorPages.length() == 0) {
            return false;
        }
        for (int i = 0; i < anchorPages.length(); i++) {
            int page = anchorPages.optInt(i, 0);
            if (page > 0 && findingPages.contains(page)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPromotableActor(String actor) {
        if (actor == null || actor.trim().isEmpty()) {
            return false;
        }
        String lower = actor.trim().toLowerCase();
        return !"unresolved actor".equals(lower)
                && !"the".equals(lower)
                && !"you".equals(lower)
                && !"this".equals(lower)
                && !"all".equals(lower)
                && !"march".equals(lower)
                && !"april".equals(lower)
                && !"may".equals(lower)
                && !"june".equals(lower)
                && !"july".equals(lower)
                && !"august".equals(lower)
                && !"september".equals(lower)
                && !"october".equals(lower)
                && !"november".equals(lower)
                && !"december".equals(lower)
                && !"january".equals(lower)
                && !"february".equals(lower)
                && !"fraud".equals(lower)
                && !"complaint".equals(lower)
                && !"agreement".equals(lower)
                && !"contract".equals(lower)
                && !"business".equals(lower)
                && !"deal".equals(lower)
                && !"invoice".equals(lower)
                && !"records".equals(lower)
                && !"shipment".equals(lower);
    }
}
