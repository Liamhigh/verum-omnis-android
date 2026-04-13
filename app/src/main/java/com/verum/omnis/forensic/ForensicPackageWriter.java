package com.verum.omnis.forensic;

import android.content.Context;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.EvidenceIntakeCapture;
import com.verum.omnis.core.FindingPublicationNormalizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;

public final class ForensicPackageWriter {

    private ForensicPackageWriter() {}

    public static File write(
            Context context,
            File sourceFile,
            AnalysisEngine.ForensicReport report,
            EvidenceIntakeCapture.Snapshot intake,
            String humanReadableReport
    ) throws Exception {
        JSONObject root = buildPayload(sourceFile, report, intake);
        if (humanReadableReport != null && !humanReadableReport.trim().isEmpty()) {
            root.put("humanReadableReport", humanReadableReport.trim());
        }
        return write(context, root);
    }

    public static JSONObject buildPayload(
            File sourceFile,
            AnalysisEngine.ForensicReport report,
            EvidenceIntakeCapture.Snapshot intake
    ) throws Exception {
        FindingPublicationNormalizer.applyToReport(report);
        JSONObject root = new JSONObject();
        root.put("caseId", report.caseId);
        root.put("evidenceHash", report.evidenceHash);
        root.put("jurisdiction", report.jurisdiction);
        root.put("jurisdictionName", report.jurisdictionName);
        root.put("jurisdictionAnchor", report.jurisdictionAnchor);
        root.put("summary", report.summary);
        root.put("blockchainAnchor", report.blockchainAnchor);
        root.put("sourceFile", sourceFile != null ? sourceFile.getName() : "unknown");
        root.put("engineVersion", report.engineVersion);
        root.put("rulesVersion", report.rulesVersion);
        root.put("generatedAt", report.generatedAt);
        root.put("evidenceBundleHash", report.evidenceBundleHash);
        root.put("deterministicRunId", report.deterministicRunId);
        root.put("guardianDecision", report.guardianDecision != null ? report.guardianDecision : new JSONObject());
        root.put("tripleVerification", report.tripleVerification != null ? report.tripleVerification : new JSONObject());
        root.put("certifiedFindings", report.certifiedFindings != null ? report.certifiedFindings : new JSONArray());
        root.put("normalizedCertifiedFindings",
                report.normalizedCertifiedFindings != null ? report.normalizedCertifiedFindings : new JSONArray());
        root.put("normalizedCertifiedFindingCount", report.normalizedCertifiedFindingCount);
        JSONObject forensicSynthesis = report.forensicSynthesis != null
                ? new JSONObject(report.forensicSynthesis.toString())
                : new JSONObject();
        normalizeActorFields(forensicSynthesis);
        root.put("forensicSynthesis", forensicSynthesis);
        root.put("findings", buildFindingsArray(report));

        JSONArray refs = new JSONArray();
        if (report.legalReferences != null) {
            for (String ref : report.legalReferences) {
                refs.put(ref);
            }
        }
        root.put("legalReferences", refs);

        JSONArray liabilities = new JSONArray();
        if (report.topLiabilities != null) {
            for (String liability : report.topLiabilities) {
                liabilities.put(liability);
            }
        }
        root.put("topLiabilities", liabilities);

        if (report.diagnostics != null) {
            root.put("diagnostics", report.diagnostics);
        }
        if (report.behavioralProfile != null) {
            root.put("behavioralProfile", report.behavioralProfile);
        }
        if (report.nativeEvidence != null) {
            root.put("nativeEvidence", report.nativeEvidence);
        }
        if (report.constitutionalExtraction != null) {
            root.put("constitutionalExtraction", report.constitutionalExtraction);
        }
        if (report.truthContinuityAnalysis != null) {
            root.put("truthContinuityAnalysis", report.truthContinuityAnalysis);
        }
        if (report.patternAnalysis != null) {
            root.put("patternAnalysis", report.patternAnalysis);
        }
        if (report.vulnerabilityAnalysis != null) {
            root.put("vulnerabilityAnalysis", report.vulnerabilityAnalysis);
        }
        if (report.rndAnalysis != null) {
            root.put("rndAnalysis", report.rndAnalysis);
        }
        if (report.legalBrainContext != null) {
            root.put("legalBrainContext", report.legalBrainContext);
        }
        if (report.investigatorContext != null) {
            root.put("investigatorContext", report.investigatorContext);
        }
        if (report.investigatorSuppliedFacts != null) {
            root.put("investigatorSuppliedFacts", report.investigatorSuppliedFacts);
        }
        if (report.brainAnalysis != null) {
            if (report.forensicSynthesis != null && !report.brainAnalysis.has("b1Synthesis")) {
                report.brainAnalysis.put("b1Synthesis", report.forensicSynthesis);
            }
            root.put("brainAnalysis", report.brainAnalysis);
        }

        if (intake != null) {
            JSONObject intakeJson = new JSONObject();
            intakeJson.put("capturedAtUtc", intake.capturedAtUtc);
            intakeJson.put("localTime", intake.localTime);
            intakeJson.put("timezoneId", intake.timezoneId);
            intakeJson.put("coordinates", intake.coordinatesLabel());
            intakeJson.put("locationStatus", intake.locationStatus);
            root.put("intake", intakeJson);
        }
        normalizeCaseEnvelope(root);
        ForensicSchemaValidator.validateCaseEnvelope(root);
        return root;
    }

    private static void normalizeCaseEnvelope(JSONObject root) throws Exception {
        JSONObject diagnostics = ensureObject(root, "diagnostics");
        putIfAbsent(diagnostics, "keywords", 0);
        putIfAbsent(diagnostics, "entities", 0);
        putIfAbsent(diagnostics, "evasion", 0);
        putIfAbsent(diagnostics, "contradictions", 0);
        putIfAbsent(diagnostics, "concealment", 0);
        putIfAbsent(diagnostics, "financial", 0);
        putIfAbsent(diagnostics, "processingStatus", "COMPLETED");
        putIfAbsent(diagnostics, "contradictionRegister", new JSONArray());

        JSONObject behavioralProfile = ensureObject(root, "behavioralProfile");
        if (behavioralProfile.length() == 0) {
            behavioralProfile.put("status", "AVAILABLE");
        }

        JSONObject nativeEvidence = ensureObject(root, "nativeEvidence");
        putIfAbsent(nativeEvidence, "fileName", root.optString("sourceFile", "unknown"));
        putIfAbsent(nativeEvidence, "evidenceHash", root.optString("evidenceHash", ""));
        putIfAbsent(nativeEvidence, "pageCount", 0);
        putIfAbsent(nativeEvidence, "sourcePageCount", nativeEvidence.optInt("pageCount", 0));
        putIfAbsent(nativeEvidence, "pipelineStatus", diagnostics.optString("processingStatus", "COMPLETED"));
        putIfAbsent(nativeEvidence, "documentTextBlocks", new JSONArray());

        JSONObject tripleVerification = ensureObject(root, "tripleVerification");
        ensureTripleVerificationNode(tripleVerification, "thesis");
        ensureTripleVerificationNode(tripleVerification, "antithesis");
        ensureTripleVerificationNode(tripleVerification, "synthesis");
        ensureTripleVerificationNode(tripleVerification, "overall");

        JSONObject forensicSynthesis = ensureObject(root, "forensicSynthesis");
        putIfAbsent(forensicSynthesis, "engineId", root.optString("engineVersion", "unknown"));
        putIfAbsent(forensicSynthesis, "crossBrainContradictions", new JSONArray());
        putIfAbsent(forensicSynthesis, "actorDishonestyScores", new JSONObject());
        putIfAbsent(forensicSynthesis, "actorHeatmap", new JSONObject());
        putIfAbsent(forensicSynthesis, "promotionSupportMatrix", new JSONObject());
        putIfAbsent(forensicSynthesis, "summary", root.optString("summary", "No synthesis summary available."));

        JSONObject wrongfulActorProfile = ensureObject(forensicSynthesis, "wrongfulActorProfile");
        putIfAbsent(wrongfulActorProfile, "actor", "UNRESOLVED");
        putIfAbsent(wrongfulActorProfile, "factualFaultAssessment", "No wrongful actor could be resolved from the current anchored record.");
        putIfAbsent(wrongfulActorProfile, "supportingContradictions", new JSONArray());
        putIfAbsent(wrongfulActorProfile, "supportingBrains", new JSONArray());
        putIfAbsent(wrongfulActorProfile, "anchorPages", new JSONArray());
    }

    private static JSONObject ensureObject(JSONObject parent, String key) throws Exception {
        JSONObject object = parent.optJSONObject(key);
        if (object == null) {
            object = new JSONObject();
            parent.put(key, object);
        }
        return object;
    }

    private static void putIfAbsent(JSONObject object, String key, Object value) throws Exception {
        if (!object.has(key) || object.isNull(key)) {
            object.put(key, value);
        }
    }

    private static void ensureTripleVerificationNode(JSONObject tripleVerification, String key) throws Exception {
        JSONObject node = tripleVerification.optJSONObject(key);
        if (node == null) {
            node = new JSONObject();
            tripleVerification.put(key, node);
        }
        putIfAbsent(node, "status", "UNAVAILABLE");
        putIfAbsent(node, "reason", "No constitutional verification result was recorded for this branch.");
    }

    private static JSONArray buildFindingsArray(AnalysisEngine.ForensicReport report) throws Exception {
        JSONArray findings = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (report == null) {
            return findings;
        }
        if (report.certifiedFindings != null) {
            for (int i = 0; i < report.certifiedFindings.length(); i++) {
                JSONObject certified = report.certifiedFindings.optJSONObject(i);
                if (certified == null) {
                    continue;
                }
                JSONObject finding = certified.optJSONObject("finding");
                JSONObject normalized = normalizeFinding(finding, "certified");
                if (normalized == null) {
                    continue;
                }
                String key = normalized.optString("type") + "|" + normalized.optString("actor") + "|" + normalized.optString("summary");
                if (seen.add(key)) {
                    findings.put(normalized);
                }
            }
        }
        if (findings.length() > 0) {
            return findings;
        }
        appendRegisterFindings(findings, seen, report.diagnostics != null ? report.diagnostics.optJSONArray("contradictionRegister") : null);
        appendRegisterFindings(findings, seen, report.constitutionalExtraction != null ? report.constitutionalExtraction.optJSONArray("financialExposureRegister") : null);
        return findings;
    }

    private static void appendRegisterFindings(JSONArray target, LinkedHashSet<String> seen, JSONArray source) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject finding = source.optJSONObject(i);
            if (finding == null) {
                continue;
            }
            if ("REJECTED".equalsIgnoreCase(finding.optString("status", ""))) {
                continue;
            }
            JSONObject normalized = normalizeFinding(finding, "register");
            if (normalized == null) {
                continue;
            }
            String key = normalized.optString("type") + "|" + normalized.optString("actor") + "|" + normalized.optString("summary");
            if (seen.add(key)) {
                target.put(normalized);
            }
        }
    }

    private static JSONObject normalizeFinding(JSONObject finding, String source) throws Exception {
        if (finding == null) {
            return null;
        }
        JSONObject normalized = new JSONObject();
        normalized.put("type", normalizeFindingType(finding));
        normalized.put("rawType", rawFindingType(finding));
        normalized.put("status", finding.optString("status", ""));
        normalized.put("layer", finding.optString("layer", finding.optString("status", "")));
        String actor = firstNonBlank(finding.optString("actor", ""), "unresolved actor");
        normalized.put("actor", actor);
        normalized.put("summary", firstNonBlank(
                finding.optString("summary", ""),
                finding.optString("whyItConflicts", ""),
                normalizeFindingType(finding)
        ));
        normalized.put("excerpt", finding.optString("excerpt", ""));
        normalized.put("page", finding.optInt("page", 0));
        normalized.put("anchors", finding.optJSONArray("anchors") != null ? finding.optJSONArray("anchors") : new JSONArray());
        if (finding.has("neededEvidence")) {
            normalized.put("neededEvidence", finding.optString("neededEvidence", ""));
        }
        normalized.put("source", source);
        return normalized;
    }

    private static String rawFindingType(JSONObject finding) {
        String findingType = finding.optString("findingType", "").trim();
        if (!findingType.isEmpty()) {
            return findingType;
        }
        return finding.optString("conflictType", "").trim();
    }

    private static String normalizeFindingType(JSONObject finding) {
        String raw = rawFindingType(finding);
        if ("PROPOSITION_CONFLICT".equalsIgnoreCase(raw)
                || "NEGATION".equalsIgnoreCase(raw)
                || "NUMERIC".equalsIgnoreCase(raw)
                || "TIMELINE".equalsIgnoreCase(raw)
                || "LOCATION".equalsIgnoreCase(raw)) {
            return "CONTRADICTION";
        }
        return raw.isEmpty() ? "FINDING" : raw;
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

    private static void normalizeActorFields(JSONObject forensicSynthesis) throws Exception {
        if (forensicSynthesis == null) {
            return;
        }
        JSONObject wrongfulActorProfile = forensicSynthesis.optJSONObject("wrongfulActorProfile");
        if (wrongfulActorProfile != null) {
            String actor = wrongfulActorProfile.optString("actor", "").trim();
            if (actor.isEmpty()) {
                wrongfulActorProfile.put("actor", "unresolved actor");
            }
        }
    }

    public static File write(Context context, JSONObject root) throws Exception {
        File out = VaultManager.createVaultFile(
                context,
                "findings-package",
                ".json",
                root.optString("sourceFile", null)
        );
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        VaultManager.writeSealManifest(
                context,
                out,
                "findings-package",
                root.optString("caseId", "unknown"),
                root.optString("evidenceHash", "")
        );
        return out;
    }
}
