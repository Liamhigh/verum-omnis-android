package com.verum.omnis.forensic;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ForensicSchemaValidator {

    private ForensicSchemaValidator() {}

    public static void validateCaseEnvelope(JSONObject root) {
        require(root, "caseId");
        require(root, "evidenceHash");
        require(root, "jurisdiction");
        require(root, "jurisdictionName");
        require(root, "summary");
        require(root, "diagnostics");
        require(root, "behavioralProfile");
        require(root, "nativeEvidence");
        require(root, "tripleVerification");
        require(root, "forensicSynthesis");

        JSONObject diagnostics = root.optJSONObject("diagnostics");
        require(diagnostics, "keywords");
        require(diagnostics, "entities");
        require(diagnostics, "evasion");
        require(diagnostics, "contradictions");
        require(diagnostics, "concealment");
        require(diagnostics, "financial");
        require(diagnostics, "processingStatus");

        JSONArray contradictionRegister = diagnostics.optJSONArray("contradictionRegister");
        if (contradictionRegister != null) {
            for (int i = 0; i < contradictionRegister.length(); i++) {
                JSONObject item = contradictionRegister.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                require(item, "status");
                require(item, "confidence");
                require(item, "anchors");
                if ("VERIFIED".equalsIgnoreCase(item.optString("status", ""))) {
                    require(item, "propositionA");
                    require(item, "propositionB");
                }
            }
        }

        JSONObject nativeEvidence = root.optJSONObject("nativeEvidence");
        require(nativeEvidence, "fileName");
        require(nativeEvidence, "evidenceHash");
        require(nativeEvidence, "pageCount");
        require(nativeEvidence, "sourcePageCount");
        require(nativeEvidence, "pipelineStatus");
        require(nativeEvidence, "documentTextBlocks");

        JSONObject brainAnalysis = root.optJSONObject("brainAnalysis");
        if (brainAnalysis != null) {
            require(brainAnalysis, "brains");
            require(brainAnalysis, "consensus");
            require(brainAnalysis, "b1Synthesis");
        }

        JSONObject forensicSynthesis = root.optJSONObject("forensicSynthesis");
        require(forensicSynthesis, "engineId");
        require(forensicSynthesis, "crossBrainContradictions");
        require(forensicSynthesis, "actorDishonestyScores");
        require(forensicSynthesis, "actorHeatmap");
        require(forensicSynthesis, "promotionSupportMatrix");
        require(forensicSynthesis, "wrongfulActorProfile");
        require(forensicSynthesis, "summary");

        JSONObject wrongfulActorProfile = forensicSynthesis.optJSONObject("wrongfulActorProfile");
        require(wrongfulActorProfile, "actor");
        require(wrongfulActorProfile, "factualFaultAssessment");
        require(wrongfulActorProfile, "supportingContradictions");
        require(wrongfulActorProfile, "supportingBrains");
        require(wrongfulActorProfile, "anchorPages");

        JSONObject tripleVerification = root.optJSONObject("tripleVerification");
        require(tripleVerification, "thesis");
        require(tripleVerification, "antithesis");
        require(tripleVerification, "synthesis");
        require(tripleVerification, "overall");
        require(tripleVerification.optJSONObject("thesis"), "status");
        require(tripleVerification.optJSONObject("thesis"), "reason");
        require(tripleVerification.optJSONObject("antithesis"), "status");
        require(tripleVerification.optJSONObject("antithesis"), "reason");
        require(tripleVerification.optJSONObject("synthesis"), "status");
        require(tripleVerification.optJSONObject("synthesis"), "reason");
        require(tripleVerification.optJSONObject("overall"), "status");
        require(tripleVerification.optJSONObject("overall"), "reason");

        JSONArray certifiedFindings = root.optJSONArray("certifiedFindings");
        if (certifiedFindings != null) {
            for (int i = 0; i < certifiedFindings.length(); i++) {
                JSONObject item = certifiedFindings.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                require(item, "finding");
                require(item, "audit");
                require(item, "certification");
                JSONObject certification = item.optJSONObject("certification");
                require(certification, "constitutionHash");
                require(certification, "rulePackVersion");
                require(certification, "engineVersion");
                require(certification, "deterministicRunId");
                require(certification, "evidenceBundleHash");
                require(certification, "findingHash");
                require(certification, "promotionHash");
                require(certification, "guardianApproval");
            }
        }
    }

    private static void require(JSONObject object, String key) {
        if (object == null) {
            throw new IllegalStateException("Schema validation failed: missing object for required key " + key);
        }
        if (!object.has(key) || object.isNull(key)) {
            throw new IllegalStateException("Schema validation failed: missing required key " + key);
        }
        Object value = object.opt(key);
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            throw new IllegalStateException("Schema validation failed: empty required key " + key);
        }
    }
}
