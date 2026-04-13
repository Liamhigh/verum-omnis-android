package com.verum.omnis.core;

import com.verum.omnis.forensic.EvidenceAnchor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FindingAudit {
    public final String findingId;
    public final List<String> brainIds;
    public final String findingType;
    public final List<String> actors;
    public final List<EvidenceAnchor> sourceAnchors;
    public final List<String> sourceEvidenceIds;
    public final List<String> supportingExcerpts;
    public final List<String> ruleHits;
    public final List<String> contradictionChecks;
    public final List<String> corroborationChecks;
    public final List<String> exclusionChecks;
    public final List<String> uncertainties;
    public final Map<String, Double> internalScores;
    public final String promotionReason;
    public final String demotionReason;

    public FindingAudit(
            String findingId,
            List<String> brainIds,
            String findingType,
            List<String> actors,
            List<EvidenceAnchor> sourceAnchors,
            List<String> sourceEvidenceIds,
            List<String> supportingExcerpts,
            List<String> ruleHits,
            List<String> contradictionChecks,
            List<String> corroborationChecks,
            List<String> exclusionChecks,
            List<String> uncertainties,
            Map<String, Double> internalScores,
            String promotionReason,
            String demotionReason
    ) {
        this.findingId = findingId;
        this.brainIds = brainIds == null ? new ArrayList<>() : brainIds;
        this.findingType = findingType;
        this.actors = actors == null ? new ArrayList<>() : actors;
        this.sourceAnchors = sourceAnchors == null ? new ArrayList<>() : sourceAnchors;
        this.sourceEvidenceIds = sourceEvidenceIds == null ? new ArrayList<>() : sourceEvidenceIds;
        this.supportingExcerpts = supportingExcerpts == null ? new ArrayList<>() : supportingExcerpts;
        this.ruleHits = ruleHits == null ? new ArrayList<>() : ruleHits;
        this.contradictionChecks = contradictionChecks == null ? new ArrayList<>() : contradictionChecks;
        this.corroborationChecks = corroborationChecks == null ? new ArrayList<>() : corroborationChecks;
        this.exclusionChecks = exclusionChecks == null ? new ArrayList<>() : exclusionChecks;
        this.uncertainties = uncertainties == null ? new ArrayList<>() : uncertainties;
        this.internalScores = internalScores;
        this.promotionReason = promotionReason;
        this.demotionReason = demotionReason;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("findingId", findingId);
        out.put("brainIds", toArray(brainIds));
        out.put("findingType", findingType);
        out.put("actors", toArray(actors));
        JSONArray anchors = new JSONArray();
        for (EvidenceAnchor anchor : sourceAnchors) {
            JSONObject item = new JSONObject();
            item.put("evidenceId", anchor.evidenceId);
            item.put("file", anchor.fileName);
            item.put("page", anchor.page);
            item.put("excerpt", anchor.excerpt);
            item.put("type", anchor.type);
            anchors.put(item);
        }
        out.put("sourceAnchors", anchors);
        out.put("sourceEvidenceIds", toArray(sourceEvidenceIds));
        out.put("supportingExcerpts", toArray(supportingExcerpts));
        out.put("ruleHits", toArray(ruleHits));
        out.put("contradictionChecks", toArray(contradictionChecks));
        out.put("corroborationChecks", toArray(corroborationChecks));
        out.put("exclusionChecks", toArray(exclusionChecks));
        out.put("uncertainties", toArray(uncertainties));
        JSONObject scores = new JSONObject();
        if (internalScores != null) {
            for (Map.Entry<String, Double> entry : internalScores.entrySet()) {
                scores.put(entry.getKey(), entry.getValue());
            }
        }
        out.put("internalScores", scores);
        out.put("promotionReason", promotionReason);
        if (demotionReason != null && !demotionReason.trim().isEmpty()) {
            out.put("demotionReason", demotionReason);
        }
        return out;
    }

    private static JSONArray toArray(List<String> items) {
        JSONArray array = new JSONArray();
        for (String item : items) {
            array.put(item);
        }
        return array;
    }
}
