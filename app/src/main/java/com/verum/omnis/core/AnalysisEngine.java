package com.verum.omnis.core;

import android.content.Context;

import com.verum.omnis.ai.RulesEngine;
import com.verum.omnis.ai.RnDController;
import com.verum.omnis.ai.BusinessFraudExtractor;
import com.verum.legal.LegalGrounding;
import com.verum.omnis.forensic.NativeEvidencePipeline;
import com.verum.omnis.forensic.NativeEvidenceResult;
import com.verum.omnis.forensic.RecoveryLedger;
import com.verum.omnis.promotion.PromotionCoordinator;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.time.OffsetDateTime;

public class AnalysisEngine {

    private static final String ENGINE_VERSION = "vo-forensic-engine-2026.03.26";
    private static final String RULES_VERSION = "vo-rules-2026.03.26";

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static class ForensicReport {
        public String caseId;
        public String evidenceHash;
        public String evidenceHashShort;
        public double riskScore;
        public String jurisdiction;
        public String jurisdictionName;
        public String jurisdictionAnchor;
        public String[] legalReferences;
        public String[] topLiabilities;
        public String blockchainAnchor;
        public JSONObject behavioralProfile;
        public JSONObject diagnostics;
        public JSONObject nativeEvidence;
        public JSONObject constitutionalExtraction;
        public JSONObject truthContinuityAnalysis;
        public JSONObject patternAnalysis;
        public JSONObject vulnerabilityAnalysis;
        public JSONObject rndAnalysis;
        public JSONObject legalBrainContext;
        public JSONObject jurisdictionResolution;
        public JSONObject legalAttorneyAnalysis;
        public JSONObject consensusReview;
        public JSONObject investigatorContext;
        public JSONArray investigatorSuppliedFacts;
        public JSONObject brainAnalysis;
        public JSONObject forensicSynthesis;
        public JSONObject tripleVerification;
        public JSONObject forensicConclusion;
        public JSONArray certifiedFindings;
        public JSONArray normalizedCertifiedFindings;
        public int normalizedCertifiedFindingCount;
        public String evidenceBundleHash;
        public String deterministicRunId;
        public JSONObject guardianDecision;
        public boolean sealOnlyMode;
        public String summary;
        public RecoveryLedger.Entry ledgerEntry; // optional
        public String engineVersion;
        public String rulesVersion;
        public String generatedAt;

        // Dishonesty Detection Fields
        public double truthScore;
        public int dishonestyThreshold;
        public String dishonestyStatus;
        public String[] dishonestyFindings;
    }

    public static ForensicReport analyze(Context context, File file) {
        return analyze(context, file, null);
    }

    public static ForensicReport analyze(Context context, File file, ProgressListener progressListener) {
        ForensicReport report = new ForensicReport();

        // 1. Hash
        notifyProgress(progressListener, "Hashing evidence...");
        report.engineVersion = ENGINE_VERSION;
        report.rulesVersion = RULES_VERSION;
        report.generatedAt = OffsetDateTime.now().toString();
        try {
            report.evidenceHash = HashUtil.sha512File(file);
            report.evidenceHashShort = HashUtil.truncate(report.evidenceHash, 16);
            report.caseId = "case-" + HashUtil.truncate(report.evidenceHash, 24);
        } catch (Exception e) {
            report.evidenceHash = "HASH_ERROR";
            report.evidenceHashShort = "HASH_ERROR";
            report.caseId = "case-hash-error";
        }

        // 2. Behavioral heuristics (quick stub + mock profile)
        report.riskScore = BehavioralAnalyzer.quickScore(file.getName());
        report.behavioralProfile = BehavioralAnalyzer.mockProfile();

        NativeEvidenceResult nativeResult = null;
        try {
            notifyProgress(progressListener, "Starting shredder engine...");
            nativeResult = NativeEvidencePipeline.process(context, file, progressListener);
            report.nativeEvidence = nativeResult.toJson();
        } catch (Throwable e) {
            report.nativeEvidence = new JSONObject();
            try {
                report.nativeEvidence.put("nativePipelineError", e.getMessage());
            } catch (JSONException ignored) {
            }
        }

        // 3. Jurisdiction (initial fallback, later refined from the evidence itself)
        JurisdictionManager.JurisdictionConfig jurisdiction =
                JurisdictionManager.getCurrentJurisdiction(context);
        report.jurisdiction = jurisdiction.code;
        report.jurisdictionName = jurisdiction.name;
        report.jurisdictionAnchor = jurisdiction.anchor;
        report.legalReferences = jurisdiction.legalReferences.toArray(new String[0]);

        // 4. Blockchain anchor (stubbed eth:// URI)
        report.blockchainAnchor = BlockchainService.anchor(report.evidenceHash);

        // 5. Rules engine + R&D feedback
        try {
            notifyProgress(progressListener, "Extracting constitutional findings...");
            RulesEngine.Result rr = RulesEngine.analyzeEvidence(context, file, nativeResult);
            report.riskScore = rr.riskScore;
            report.topLiabilities = rr.topLiabilities;
            report.diagnostics = rr.diagnostics;
            report.constitutionalExtraction = rr.constitutionalExtraction;

            // R&D experimental layer: weight boost and JSON diagnostics
            RnDController.Feedback fb = RnDController.synthesize(context, rr);
            report.riskScore = Math.min(1.0, rr.riskScore + fb.suggestedRiskWeightBoost);

            // Merge diagnostics into behavioralProfile if none set yet
            report.rndAnalysis = fb.report;

            if (report.behavioralProfile == null || report.behavioralProfile.length() == 0) {
                report.behavioralProfile = fb.report;
            }

        } catch (Exception e) {
            report.topLiabilities = new String[]{"Rules engine failed: " + e.getMessage()};
        }

        applyEvidenceJurisdiction(context, report, file);
        applyContextualLegalNormalization(report);

        // Populate Dishonesty Detection Matrix data
        populateDishonestyMatrix(report);
        report.truthContinuityAnalysis = buildTruthContinuityAnalysis(report);
        report.patternAnalysis = buildPatternAnalysis(context, report);
        report.vulnerabilityAnalysis = buildVulnerabilityAnalysis(context, report);
        report.brainAnalysis = NineBrainSystem.build(context, file, report);
        report.forensicSynthesis = ForensicSynthesisEngine.build(report);
        PromotionCoordinator.run(context, report);
        report.legalBrainContext = buildLegalBrainContext(context, report);
        report.jurisdictionResolution = JurisdictionResolver.resolve(context, report);
        report.legalAttorneyAnalysis = LegalAttorneyAnalyzer.analyze(context, report);
        report.consensusReview = ConsensusEngine.build(report, report.legalAttorneyAnalysis);
        report.brainAnalysis = NineBrainSystem.build(context, file, report);
        report.forensicSynthesis = ForensicSynthesisEngine.build(report);
        if (report.brainAnalysis != null) {
            try {
                report.brainAnalysis.put("b1Synthesis", report.forensicSynthesis);
            } catch (JSONException ignored) {
            }
        }
        FindingPublicationNormalizer.applyToReport(report);
        report.tripleVerification = buildTripleVerification(context, report);
        FindingPublicationNormalizer.applyToReport(report);
        report.forensicConclusion = ForensicConclusionEngine.buildJson(report);

        report.sealOnlyMode = report.riskScore < 0.15d && (report.topLiabilities == null || report.topLiabilities.length <= 1);
        report.summary = buildSummary(report, file);

        // 6. Fraud extraction + recovery ledger
        try {
            BusinessFraudExtractor.Extraction ex = BusinessFraudExtractor.parse(file);
            if (ex != null && ex.isBusiness) {
                double amountUsd = BusinessFraudExtractor.toBaseUsd(context, ex.currency, ex.amount);

                report.ledgerEntry = RecoveryLedger.create(
                        context,
                        report.caseId,
                        ex.amount,            // original amount
                        amountUsd,            // normalized USD amount
                        ex.currency,
                        ex.company,
                        report.jurisdiction,
                        report.evidenceHash,
                        "v5.2.6"
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        // 7. R&D (reserved for future)
        // TODO: RnDController.runExperimental(file)

        return report;
    }

    public static ForensicReport analyzeEvidenceSet(
            Context context,
            List<File> files,
            ProgressListener progressListener
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one evidence file is required.");
        }
        if (files.size() == 1) {
            return analyze(context, files.get(0), progressListener);
        }

        notifyProgress(progressListener, "Building merged case analysis across " + files.size() + " evidence file(s)...");

        ForensicReport merged = new ForensicReport();
        merged.engineVersion = ENGINE_VERSION;
        merged.rulesVersion = RULES_VERSION;
        merged.generatedAt = OffsetDateTime.now().toString();
        merged.behavioralProfile = BehavioralAnalyzer.mockProfile();
        merged.nativeEvidence = new JSONObject();
        merged.diagnostics = new JSONObject();
        merged.constitutionalExtraction = new JSONObject();

        JSONArray sourceFiles = new JSONArray();
        JSONArray childSummaries = new JSONArray();
        List<ForensicReport> childReports = new ArrayList<>();
        LinkedHashSet<String> legalRefs = new LinkedHashSet<>();
        LinkedHashSet<String> liabilities = new LinkedHashSet<>();
        StringBuilder bundleBuilder = new StringBuilder();
        double maxRisk = 0.0d;
        File representativeFile = files.get(0);

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            if (file == null || !file.exists()) {
                continue;
            }
            final int position = i + 1;
            notifyProgress(progressListener, "Merged case: analysing source " + position + " of " + files.size() + " (" + file.getName() + ")...");
            ForensicReport child = analyze(context, file, message ->
                    notifyProgress(progressListener, "Merged case [" + position + "/" + files.size() + "]: " + message));
            childReports.add(child);
            maxRisk = Math.max(maxRisk, child.riskScore);
            if (child.legalReferences != null) {
                for (String item : child.legalReferences) {
                    if (item != null && !item.trim().isEmpty()) {
                        legalRefs.add(item.trim());
                    }
                }
            }
            if (child.topLiabilities != null) {
                for (String item : child.topLiabilities) {
                    if (item != null && !item.trim().isEmpty()) {
                        liabilities.add(item.trim());
                    }
                }
            }
            bundleBuilder.append(file.getName()).append('|').append(child.evidenceHash).append('\n');

            JSONObject childSummary = new JSONObject();
            putSafe(childSummary, "fileName", file.getName());
            putSafe(childSummary, "evidenceHash", child.evidenceHash);
            putSafe(childSummary, "caseId", child.caseId);
            putSafe(childSummary, "summary", child.summary);
            childSummaries.put(childSummary);

            JSONObject sourceItem = new JSONObject();
            putSafe(sourceItem, "fileName", file.getName());
            putSafe(sourceItem, "path", file.getAbsolutePath());
            putSafe(sourceItem, "sizeBytes", file.length());
            try {
                putSafe(sourceItem, "sha512", HashUtil.sha512File(file));
            } catch (Exception e) {
                putSafe(sourceItem, "sha512", "HASH_ERROR");
            }
            sourceFiles.put(sourceItem);

            if (looksLikeAudioOrVideoFile(file)) {
                representativeFile = file;
            }

            mergeNativeEvidence(merged.nativeEvidence, child.nativeEvidence);
            mergeDiagnostics(merged.diagnostics, child.diagnostics);
            mergeExtraction(merged.constitutionalExtraction, child.constitutionalExtraction);
        }

        try {
            merged.evidenceHash = HashUtil.sha512(bundleBuilder.toString().getBytes(StandardCharsets.UTF_8));
            merged.evidenceHashShort = HashUtil.truncate(merged.evidenceHash, 16);
            merged.caseId = "case-bundle-" + HashUtil.truncate(merged.evidenceHash, 24);
        } catch (Exception e) {
            merged.evidenceHash = "HASH_ERROR";
            merged.evidenceHashShort = "HASH_ERROR";
            merged.caseId = "case-bundle-hash-error";
        }

        JurisdictionManager.JurisdictionConfig jurisdiction = JurisdictionManager.getCurrentJurisdiction(context);
        merged.jurisdiction = jurisdiction.code;
        merged.jurisdictionName = jurisdiction.name;
        merged.jurisdictionAnchor = jurisdiction.anchor;
        merged.legalReferences = legalRefs.toArray(new String[0]);
        merged.topLiabilities = liabilities.toArray(new String[0]);
        merged.riskScore = maxRisk;
        merged.blockchainAnchor = BlockchainService.anchor(merged.evidenceHash);

        putSafe(merged.nativeEvidence, "sourceFiles", sourceFiles);
        putSafe(merged.nativeEvidence, "scanFileCount", childReports.size());
        putSafe(merged.nativeEvidence, "childReports", childSummaries);
        putSafe(merged.nativeEvidence, "pipelineStatus", "merged");

        putSafe(merged.diagnostics, "analysisSource", "merged-evidence-set");
        putSafe(merged.diagnostics, "verifiedContradictionCount",
                countStatus(merged.diagnostics.optJSONArray("contradictionRegister"), "VERIFIED"));
        putSafe(merged.diagnostics, "candidateContradictionCount",
                countStatus(merged.diagnostics.optJSONArray("contradictionRegister"), "CANDIDATE"));
        putSafe(merged.diagnostics, "rejectedContradictionCount",
                countStatus(merged.diagnostics.optJSONArray("contradictionRegister"), "REJECTED"));
        if (!merged.diagnostics.has("processingStatus")) {
            putSafe(merged.diagnostics, "processingStatus", mergedProcessingStatus(childReports));
        }

        putSafe(merged.constitutionalExtraction, "sourceFiles", sourceFiles);
        putSafe(merged.constitutionalExtraction, "sourceFileCount", childReports.size());

        applyEvidenceJurisdiction(context, merged, representativeFile);

        populateDishonestyMatrix(merged);
        merged.truthContinuityAnalysis = buildTruthContinuityAnalysis(merged);
        merged.patternAnalysis = buildPatternAnalysis(context, merged);
        merged.vulnerabilityAnalysis = buildVulnerabilityAnalysis(context, merged);
        merged.brainAnalysis = NineBrainSystem.build(context, representativeFile, merged);
        merged.forensicSynthesis = ForensicSynthesisEngine.build(merged);
        PromotionCoordinator.run(context, merged);
        merged.legalBrainContext = buildLegalBrainContext(context, merged);
        merged.jurisdictionResolution = JurisdictionResolver.resolve(context, merged);
        merged.legalAttorneyAnalysis = LegalAttorneyAnalyzer.analyze(context, merged);
        merged.consensusReview = ConsensusEngine.build(merged, merged.legalAttorneyAnalysis);
        merged.brainAnalysis = NineBrainSystem.build(context, representativeFile, merged);
        merged.forensicSynthesis = ForensicSynthesisEngine.build(merged);
        if (merged.brainAnalysis != null) {
            try {
                merged.brainAnalysis.put("b1Synthesis", merged.forensicSynthesis);
            } catch (JSONException ignored) {
            }
        }
        FindingPublicationNormalizer.applyToReport(merged);
        merged.tripleVerification = buildTripleVerification(context, merged);
        FindingPublicationNormalizer.applyToReport(merged);
        merged.forensicConclusion = ForensicConclusionEngine.buildJson(merged);
        merged.sealOnlyMode = merged.riskScore < 0.15d
                && (merged.topLiabilities == null || merged.topLiabilities.length <= 1);
        merged.summary = buildMergedSummary(merged, files);
        return merged;
    }

    private static void applyEvidenceJurisdiction(Context context, ForensicReport report, File representativeFile) {
        if (context == null || report == null) {
            return;
        }
        String corpus = buildJurisdictionEvidenceCorpus(report, representativeFile);
        String inferredCode = inferJurisdictionCodeFromEvidence(corpus, report.jurisdiction);
        JurisdictionManager.JurisdictionConfig inferred = JurisdictionManager.getJurisdiction(context, inferredCode);
        report.jurisdiction = inferred.code;
        report.jurisdictionName = inferred.name;
        report.jurisdictionAnchor = buildJurisdictionAnchorFromEvidence(corpus, inferred.code, inferred.anchor);
        report.legalReferences = mergeUniqueStrings(
                report.legalReferences,
                inferred.legalReferences
        );
    }

    private static String buildJurisdictionEvidenceCorpus(ForensicReport report, File representativeFile) {
        StringBuilder corpus = new StringBuilder();
        if (representativeFile != null) {
            corpus.append(representativeFile.getName()).append('\n');
        }
        if (report.summary != null) {
            corpus.append(report.summary).append('\n');
        }
        if (report.nativeEvidence != null) {
            corpus.append(report.nativeEvidence.toString()).append('\n');
        }
        if (report.constitutionalExtraction != null) {
            corpus.append(report.constitutionalExtraction.toString()).append('\n');
        }
        if (report.diagnostics != null) {
            corpus.append(report.diagnostics.toString()).append('\n');
        }
        if (report.legalBrainContext != null) {
            corpus.append(report.legalBrainContext.toString()).append('\n');
        }
        if (report.topLiabilities != null) {
            for (String liability : report.topLiabilities) {
                if (liability != null) {
                    corpus.append(liability).append('\n');
                }
            }
        }
        return corpus.toString().toLowerCase(Locale.US);
    }

    private static String inferJurisdictionCodeFromEvidence(String corpus, String fallbackCode) {
        if (corpus == null) {
            return fallbackCode == null || fallbackCode.trim().isEmpty() ? "ZAF" : fallbackCode;
        }
        int uaeScore = countJurisdictionMatches(corpus,
                "uae", "united arab emirates", "rakez", "ras al khaimah", "federal law no. 32",
                "article 84", "article 110", "fz-llc", "fze", "dmcc", "dubai", "abu dhabi",
                "emirates", "commercial companies law", "uae law no. 34", "rak economic zone");
        int zafScore = countJurisdictionMatches(corpus,
                "south africa", "saps", "hawks", "dpci", "ect act", "cybercrimes act",
                "companies act 71 of 2008", "precca", "lpc", "johannesburg", "durban",
                "port elizabeth", "margate", "south african");
        if (uaeScore >= 2 && zafScore >= 2) {
            return "MULTI";
        }
        if (uaeScore >= 3 && uaeScore > zafScore) {
            return "UAE";
        }
        if (zafScore >= 3 && zafScore > uaeScore) {
            return "ZAF";
        }
        if (uaeScore >= 2 && zafScore >= 1) {
            return "MULTI";
        }
        if (zafScore >= 2 && uaeScore >= 1) {
            return "MULTI";
        }
        return fallbackCode == null || fallbackCode.trim().isEmpty() ? "ZAF" : fallbackCode;
    }

    private static int countJurisdictionMatches(String corpus, String... markers) {
        if (corpus == null || markers == null) {
            return 0;
        }
        int count = 0;
        for (String marker : markers) {
            if (marker != null && !marker.trim().isEmpty() && corpus.contains(marker.toLowerCase(Locale.US))) {
                count++;
            }
        }
        return count;
    }

    private static String buildJurisdictionAnchorFromEvidence(String corpus, String code, String fallbackAnchor) {
        if (corpus == null) {
            return fallbackAnchor;
        }
        if ("MULTI".equalsIgnoreCase(code)) {
            return "evidence-detected:uae+zaf";
        }
        if ("UAE".equalsIgnoreCase(code)) {
            if (corpus.contains("rakez")) {
                return "evidence-detected:rakez";
            }
            if (corpus.contains("fz-llc") || corpus.contains("fze") || corpus.contains("dmcc")) {
                return "evidence-detected:uae-company";
            }
            return "evidence-detected:uae";
        }
        if ("ZAF".equalsIgnoreCase(code)) {
            if (corpus.contains("saps") || corpus.contains("hawks")) {
                return "evidence-detected:saps-hawks";
            }
            return "evidence-detected:zaf";
        }
        return fallbackAnchor;
    }

    private static JSONObject buildLegalBrainContext(Context context, ForensicReport report) {
        try {
            LegalGrounding grounding = new LegalGrounding(context);
            JSONObject legalBrain = grounding.buildBrainSevenContext(report);
            mergeLegalOutputsIntoReport(report, legalBrain);
            return legalBrain;
        } catch (Exception e) {
            JSONObject fallback = new JSONObject();
            putSafe(fallback, "jurisdictionCode", report.jurisdiction);
            putSafe(fallback, "jurisdictionName", report.jurisdictionName);
            putSafe(fallback, "summary", "B7 legal mapping could not be expanded: " + safeMessage(e));
            putSafe(fallback, "recommendedActions", new JSONArray());
            putSafe(fallback, "authorities", new JSONArray());
            return fallback;
        }
    }

    private static void mergeLegalOutputsIntoReport(ForensicReport report, JSONObject legalBrain) {
        if (report == null || legalBrain == null) {
            return;
        }
        String jurisdictionCode = report.jurisdiction == null ? "" : report.jurisdiction.trim();
        report.legalReferences = mergeUniqueStrings(
                report.legalReferences,
                filterLegalTitlesForJurisdiction(jurisdictionCode, extractTitles(legalBrain.optJSONArray("matchedStatutes"), 3)),
                filterLegalTitlesForJurisdiction(jurisdictionCode, extractTitles(legalBrain.optJSONArray("matchedProceduralRules"), 3)),
                filterLegalTitlesForJurisdiction(jurisdictionCode, extractTitles(legalBrain.optJSONArray("matchedInstitutions"), 2))
        );
        report.topLiabilities = mergeUniqueStrings(
                report.topLiabilities,
                filterLegalTitlesForJurisdiction(jurisdictionCode, extractTitles(legalBrain.optJSONArray("matchedOffenceFrameworks"), 3))
        );
    }

    private static void applyContextualLegalNormalization(ForensicReport report) {
        if (report == null) {
            return;
        }
        String corpus = buildLegalContextCorpus(report).toLowerCase(Locale.US);
        boolean petroleumContext = containsAnyJurisdictionTitle(corpus,
                "all fuels",
                "petroleum products act",
                "goodwill",
                "brand fee",
                "unsigned mou",
                "countersigned",
                "desmond smith",
                "gary highcock",
                "wayne nel",
                "port edward");
        if (!petroleumContext) {
            return;
        }

        report.topLiabilities = normalizePetroleumLiabilities(report.topLiabilities);
        report.legalReferences = normalizePetroleumReferences(report.legalReferences);
    }

    private static String buildLegalContextCorpus(ForensicReport report) {
        StringBuilder sb = new StringBuilder();
        if (report.summary != null) {
            sb.append(report.summary).append(' ');
        }
        appendStringArray(sb, report.topLiabilities);
        appendStringArray(sb, report.legalReferences);
        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : null;
        if (extraction != null) {
            appendJsonArrayField(sb, extraction.optJSONArray("criticalLegalSubjects"), "subject", "excerpt");
            appendJsonArrayField(sb, extraction.optJSONArray("anchoredFindings"), "category", "excerpt");
            appendJsonArrayField(sb, extraction.optJSONArray("incidentRegister"), "summary", "narrative", "description");
            appendJsonArrayField(sb, extraction.optJSONArray("timelineAnchorRegister"), "summary", "excerpt");
            appendJsonArrayField(sb, extraction.optJSONArray("financialExposureRegister"), "summary", "excerpt");
        }
        return sb.toString();
    }

    private static void appendStringArray(StringBuilder sb, String[] values) {
        if (sb == null || values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                sb.append(value.trim()).append(' ');
            }
        }
    }

    private static void appendJsonArrayField(StringBuilder sb, JSONArray array, String... keys) {
        if (sb == null || array == null || keys == null) {
            return;
        }
        for (int i = 0; i < array.length() && i < 16; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            for (String key : keys) {
                String value = item.optString(key, "").trim();
                if (!value.isEmpty()) {
                    sb.append(value).append(' ');
                }
            }
        }
    }

    private static String[] normalizePetroleumLiabilities(String[] base) {
        List<String> filtered = new ArrayList<>();
        if (base != null) {
            for (String item : base) {
                if (item == null || item.trim().isEmpty()) {
                    continue;
                }
                String lower = item.trim().toLowerCase(Locale.US);
                if (containsAnyJurisdictionTitle(lower,
                        "unauthorized account access or digital interference",
                        "shareholder oppression",
                        "breach of fiduciary duty",
                        "companies act",
                        "emotional exploitation")) {
                    continue;
                }
                filtered.add(item.trim());
            }
        }
        if (!filtered.contains("Fraud and theft pattern")) {
            filtered.add("Fraud and theft pattern");
        }
        if (!filtered.contains("Unlawful enrichment / goodwill taking")) {
            filtered.add("Unlawful enrichment / goodwill taking");
        }
        if (!filtered.contains("Financial irregularity signals")) {
            filtered.add("Financial irregularity signals");
        }
        return filtered.toArray(new String[0]);
    }

    private static String[] normalizePetroleumReferences(String[] base) {
        List<String> filtered = new ArrayList<>();
        if (base != null) {
            for (String item : base) {
                if (item == null || item.trim().isEmpty()) {
                    continue;
                }
                String lower = item.trim().toLowerCase(Locale.US);
                if (containsAnyJurisdictionTitle(lower,
                        "companies act 71 of 2008",
                        "separate company-law relief from criminal referral",
                        "unauthorized account access or digital interference")) {
                    continue;
                }
                filtered.add(item.trim());
            }
        }
        if (!filtered.contains("Petroleum Products Act 120 of 1977: section 12B")) {
            filtered.add("Petroleum Products Act 120 of 1977: section 12B");
        }
        if (!filtered.contains("SAPS or Hawks criminal referral brief")) {
            filtered.add("SAPS or Hawks criminal referral brief");
        }
        return filtered.toArray(new String[0]);
    }

    private static List<String> filterLegalTitlesForJurisdiction(String jurisdictionCode, List<String> titles) {
        List<String> filtered = new ArrayList<>();
        if (titles == null) {
            return filtered;
        }
        for (String title : titles) {
            if (title == null || title.trim().isEmpty()) {
                continue;
            }
            if (isLegalTitleCompatibleWithJurisdiction(jurisdictionCode, title)) {
                filtered.add(title.trim());
            }
        }
        return filtered;
    }

    private static boolean isLegalTitleCompatibleWithJurisdiction(String jurisdictionCode, String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        String jurisdiction = jurisdictionCode == null ? "" : jurisdictionCode.trim().toUpperCase(Locale.US);
        if (jurisdiction.isEmpty() || "MULTI".equals(jurisdiction)) {
            return true;
        }
        String lower = title.toLowerCase(Locale.US);
        if ("ZAF".equals(jurisdiction)) {
            return !containsAnyJurisdictionTitle(lower,
                    "rakez",
                    "uae",
                    "united arab emirates",
                    "emirates",
                    "dubai",
                    "abu dhabi");
        }
        if ("UAE".equals(jurisdiction)) {
            return !containsAnyJurisdictionTitle(lower,
                    "saps",
                    "hawks",
                    "south africa",
                    "south african",
                    "precca",
                    "cybercrimes act");
        }
        return true;
    }

    private static boolean containsAnyJurisdictionTitle(String corpus, String... needles) {
        if (corpus == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && corpus.contains(needle.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static String[] mergeUniqueStrings(String[] base, List<String>... extraLists) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (base != null) {
            for (String item : base) {
                if (item != null && !item.trim().isEmpty()) {
                    values.add(item.trim());
                }
            }
        }
        if (extraLists != null) {
            for (List<String> list : extraLists) {
                if (list == null) {
                    continue;
                }
                for (String item : list) {
                    if (item != null && !item.trim().isEmpty()) {
                        values.add(item.trim());
                    }
                }
            }
        }
        return values.toArray(new String[0]);
    }

    private static List<String> extractTitles(JSONArray array, int limit) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length() && values.size() < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = item.optString("title", "").trim();
            if (!title.isEmpty()) {
                values.add(title);
            }
        }
        return values;
    }

    private static void notifyProgress(ProgressListener progressListener, String message) {
        if (progressListener != null && message != null && !message.trim().isEmpty()) {
            progressListener.onProgress(message);
        }
    }

    private static void populateDishonestyMatrix(ForensicReport report) {
        JSONObject synthesis = report != null ? report.forensicSynthesis : null;
        JSONArray actorScores = synthesis != null ? synthesis.optJSONArray("actorDishonestyScores") : null;
        JSONObject leadActor = actorScores != null && actorScores.length() > 0 ? actorScores.optJSONObject(0) : null;
        double leadDishonesty = leadActor != null ? leadActor.optDouble("dishonestyScore", 0.0d) : 0.0d;
        report.truthScore = Math.max(0, 100 - (int) Math.round(Math.max(report.riskScore * 100.0d, leadDishonesty)));
        report.dishonestyThreshold = 20; // Default threshold

        AssistanceRestrictionManager.Snapshot preview = AssistanceRestrictionManager.preview(report);
        report.dishonestyStatus = preview.restricted ? "RESTRICTED" : "CLEAR";

        List<String> findings = new ArrayList<>();
        if (leadActor != null && leadActor.length() > 0) {
            findings.add("Lead actor: " + leadActor.optString("actor", "unknown"));
            findings.add("Lead dishonesty score: " + Math.round(leadDishonesty * 10.0d) / 10.0d);
        }
        if (report.diagnostics != null) {
            int con = report.diagnostics.optInt("verifiedContradictionCount", report.diagnostics.optInt("contradictions", 0));
            int hid = report.diagnostics.optInt("concealment", 0);
            int ev = report.diagnostics.optInt("evasion", 0);
            if (con > 0) findings.add("Verified contradictions: " + con);
            if (hid > 0) findings.add("Concealment markers: " + hid);
            if (ev > 0) findings.add("Evasion indicators: " + ev);
        }
        if (preview.restricted && !preview.reason.isEmpty()) {
            findings.add("Assistance suspension triggered: severe dishonesty detected.");
        }
        report.dishonestyFindings = findings.toArray(new String[0]);
    }

    private static String buildSummary(ForensicReport report, File file) {
        StringBuilder sb = new StringBuilder();
        boolean indeterminateDueToConcealment = report.diagnostics != null
                && report.diagnostics.optBoolean("indeterminateDueToConcealment", false);
        int certifiedFindingCount = report.normalizedCertifiedFindingCount;
        boolean guardianApproved = report.guardianDecision != null
                && report.guardianDecision.optBoolean("approved", false);
        String guardianReason = report.guardianDecision != null
                ? report.guardianDecision.optString("reason", report.guardianDecision.optString("error", ""))
                : "";
        String processingStatus = qualifiedPublicationStatus(report);
        sb.append("Constitutional intake completed for ")
                .append(file.getName())
                .append(". ");
        sb.append("Case ID ").append(report.caseId).append(". ");
        sb.append("Processing status: ").append(processingStatus).append(". ");
        if (report.tripleVerification != null) {
            JSONObject overall = report.tripleVerification.optJSONObject("overall");
            if (overall != null) {
                String verificationStatus = overall.optString("status", "").trim();
                String verificationReason = overall.optString("reason", "").trim();
                if (!verificationStatus.isEmpty()) {
                    sb.append("Triple verification: ").append(verificationStatus).append(". ");
                }
                if (!verificationReason.isEmpty()) {
                    sb.append(verificationReason).append(" ");
                }
            }
        }
        if (!guardianApproved && report.guardianDecision != null && report.guardianDecision.length() > 0) {
            sb.append("Guardian review denied certification; this report contains candidate findings only. ");
            if (guardianReason != null && !guardianReason.trim().isEmpty()) {
                sb.append(guardianReason.trim()).append(" ");
            }
            sb.append("Detailed findings are listed section by section in the report body.");
        } else if ("COMPLETED".equalsIgnoreCase(processingStatus) && certifiedFindingCount > 0) {
            sb.append("Certified anchored findings survived contradiction or transaction-reconciliation review. ");
            if (report.diagnostics != null && report.diagnostics.optBoolean("evidenceManipulationDetected", false)) {
                sb.append("The record still shows manipulation or concealment markers, but those did not block a determinate outcome. ");
            }
            sb.append("Detailed findings are listed section by section in the report body.");
        } else if (indeterminateDueToConcealment) {
            sb.append("Current extraction is too weak or materially obstructed to support a mature offence conclusion. ");
            sb.append("Detailed findings remain limited and should be treated cautiously until fuller source disclosure or stronger extraction is available.");
        } else if (report.sealOnlyMode) {
            sb.append("Current pass supports integrity preservation and low-signal review only. ");
            sb.append("Detailed findings are listed in the report body.");
        } else {
            sb.append("Detailed findings are listed section by section in the report body.");
        }
        if (report.truthContinuityAnalysis != null) {
            sb.append(" Continuity: ")
                    .append(report.truthContinuityAnalysis.optString("overallAssessment", "not assessed"))
                    .append(".");
        }
        if (report.brainAnalysis != null) {
            JSONObject consensus = report.brainAnalysis.optJSONObject("consensus");
            if (consensus != null) {
                int engagedBrains = consensus.optInt("engagedBrainCount", 0);
                int engagedVotingBrains = consensus.optInt("engagedVotingBrainCount", 0);
                sb.append(" Nine-brain status: ")
                        .append(engagedBrains)
                        .append("/9 brains engaged, ")
                        .append(engagedVotingBrains)
                        .append(" voting.");
            }
        }
        if (certifiedFindingCount > 0) {
            sb.append(" Guardian-approved certified findings: ").append(certifiedFindingCount).append(".");
        }
        return sb.toString();
    }

    private static String qualifiedPublicationStatus(ForensicReport report) {
        JSONObject diagnostics = report != null && report.diagnostics != null
                ? report.diagnostics
                : new JSONObject();
        JSONObject overall = report != null && report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("overall")
                : null;
        boolean indeterminateDueToConcealment = diagnostics.optBoolean("indeterminateDueToConcealment", false);
        if (indeterminateDueToConcealment) {
            return "INDETERMINATE DUE TO CONCEALMENT";
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
            return "DETERMINATE WITH MATERIAL COVERAGE GAPS";
        }
        String raw = diagnostics.optString("processingStatus", "").trim();
        if (!raw.isEmpty() && !"COMPLETED".equalsIgnoreCase(raw)) {
            return raw;
        }
        return "DETERMINATE";
    }

    private static JSONObject buildTripleVerification(Context context, ForensicReport report) {
        JSONObject out = new JSONObject();
        try {
            ConstitutionGovernance governance = ConstitutionGovernance.load(context);
            JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
            JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
            JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
            JSONObject consensus = report.brainAnalysis != null
                    ? report.brainAnalysis.optJSONObject("consensus")
                    : null;
            if (consensus == null) {
                consensus = new JSONObject();
            }

            JSONObject thesis = new JSONObject();
            int anchoredFindings = extraction.optJSONArray("anchoredFindings") != null
                    ? extraction.optJSONArray("anchoredFindings").length() : 0;
            int incidents = extraction.optJSONArray("incidentRegister") != null
                    ? extraction.optJSONArray("incidentRegister").length() : 0;
            int timelineAnchors = extraction.optJSONArray("timelineAnchorRegister") != null
                    ? extraction.optJSONArray("timelineAnchorRegister").length() : 0;
            int financialExposure = extraction.optJSONArray("financialExposureRegister") != null
                    ? extraction.optJSONArray("financialExposureRegister").length() : 0;
            int integrityFindings = extraction.optJSONArray("documentIntegrityFindings") != null
                    ? extraction.optJSONArray("documentIntegrityFindings").length() : 0;
            int textBlocks = nativeEvidence.optJSONArray("documentTextBlocks") != null
                    ? nativeEvidence.optJSONArray("documentTextBlocks").length() : 0;
            int primarySignalCount = anchoredFindings + incidents + timelineAnchors + financialExposure + integrityFindings;
            boolean thesisPass = primarySignalCount > 0 || textBlocks > 0;
            thesis.put("status", thesisPass ? "PASS" : "FAIL");
            thesis.put("reason", thesisPass
                    ? "Primary extraction produced anchored material across "
                    + primarySignalCount + " register entries and " + textBlocks + " document text blocks."
                    : "No usable primary-evidence register or extracted text block was available for a safe thesis.");
            thesis.put("primarySignalCount", primarySignalCount);
            thesis.put("documentTextBlockCount", textBlocks);

            JSONObject antithesis = new JSONObject();
            JSONArray contradictionRegister = diagnostics.optJSONArray("contradictionRegister");
            int verifiedContradictions = diagnostics.optInt("verifiedContradictionCount", 0);
            int candidateContradictions = diagnostics.optInt("candidateContradictionCount", 0);
            int rejectedContradictions = diagnostics.optInt("rejectedContradictionCount", 0);
            boolean contradictionReviewExecuted = contradictionRegister != null;
            antithesis.put("status", contradictionReviewExecuted ? "PASS" : "FAIL");
            antithesis.put("reason", contradictionReviewExecuted
                    ? "Contradiction review executed with "
                    + verifiedContradictions + " verified, "
                    + candidateContradictions + " candidate, and "
                    + rejectedContradictions + " rejected contradiction entries."
                    : "No contradiction register was available, so the antithesis stage could not be completed.");
            antithesis.put("verifiedCount", verifiedContradictions);
            antithesis.put("candidateCount", candidateContradictions);
            antithesis.put("rejectedCount", rejectedContradictions);

            JSONObject synthesis = new JSONObject();
            String processingStatus = diagnostics.optString("processingStatus", "UNKNOWN");
            boolean guardianApproved = report.guardianDecision != null
                    && report.guardianDecision.optBoolean("approved", false);
            String guardianReason = report.guardianDecision != null
                    ? report.guardianDecision.optString("reason", report.guardianDecision.optString("error", ""))
                    : "";
            boolean quorumSatisfied = consensus.optBoolean("quorumSatisfied", false);
            int quorumMin = consensus.optInt("quorumMin", governance.quorumMin);
            int contributingVotingBrains = consensus.optInt("contributingVotingBrainCount", 0);
            int certifiedFindingCount = report.normalizedCertifiedFindingCount;

            boolean synthesisPass = guardianApproved
                    && certifiedFindingCount > 0
                    && quorumSatisfied
                    && !governance.concealmentOutput.equalsIgnoreCase(processingStatus);
            String synthesisReason;
            if (governance.concealmentOutput.equalsIgnoreCase(processingStatus)) {
                synthesisReason = "Safe synthesis is blocked because the constitutional outcome remains "
                        + governance.concealmentOutput + ".";
            } else if (!quorumSatisfied) {
                synthesisReason = "Safe synthesis is blocked because only "
                        + contributingVotingBrains + " contributing voting brains engaged; quorum requires "
                        + quorumMin + ". Tie-breaker: " + governance.tieBreaker + ".";
            } else if (!guardianApproved || certifiedFindingCount <= 0) {
                synthesisReason = "Safe synthesis is blocked because guardian review did not approve a certified finding. "
                        + guardianReason;
            } else {
                synthesisReason = "Safe synthesis passed with guardian-approved certified findings and constitutional quorum satisfied.";
            }
            synthesis.put("status", synthesisPass ? "PASS" : "FAIL");
            synthesis.put("reason", synthesisReason.trim());
            synthesis.put("quorumSatisfied", quorumSatisfied);
            synthesis.put("quorumMin", quorumMin);
            synthesis.put("contributingVotingBrainCount", contributingVotingBrains);
            synthesis.put("guardianApproved", guardianApproved);
            synthesis.put("certifiedFindingCount", certifiedFindingCount);
            synthesis.put("tieBreaker", governance.tieBreaker);
            synthesis.put("constitutionalOutcome", consensus.optString("constitutionalOutcome", processingStatus));

            JSONObject overall = new JSONObject();
            boolean overallPass = thesisPass && contradictionReviewExecuted && synthesisPass;
            overall.put("status", overallPass ? "PASS" : "FAIL");
            overall.put("reason", overallPass
                    ? "All triple-verification stages passed."
                    : firstFailureReason(thesis, antithesis, synthesis));

            out.put("thesis", thesis);
            out.put("antithesis", antithesis);
            out.put("synthesis", synthesis);
            out.put("overall", overall);
            out.put("quorumMin", governance.quorumMin);
            out.put("tieBreaker", governance.tieBreaker);
            out.put("concealmentOutput", governance.concealmentOutput);
        } catch (Exception e) {
            putSafe(out, "thesis", buildVerificationFailure("FAIL", "Triple verification failed during thesis assembly: " + safeMessage(e)));
            putSafe(out, "antithesis", buildVerificationFailure("FAIL", "Triple verification failed during antithesis assembly: " + safeMessage(e)));
            putSafe(out, "synthesis", buildVerificationFailure("FAIL", "Triple verification failed during synthesis assembly: " + safeMessage(e)));
            putSafe(out, "overall", buildVerificationFailure("FAIL", "Triple verification could not be completed: " + safeMessage(e)));
        }
        return out;
    }

    private static JSONObject buildVerificationFailure(String status, String reason) {
        JSONObject out = new JSONObject();
        putSafe(out, "status", status);
        putSafe(out, "reason", reason);
        return out;
    }

    private static String firstFailureReason(JSONObject... stages) {
        if (stages == null) {
            return "One or more triple-verification stages failed.";
        }
        for (JSONObject stage : stages) {
            if (stage == null) {
                continue;
            }
            if (!"PASS".equalsIgnoreCase(stage.optString("status", ""))) {
                String reason = stage.optString("reason", "").trim();
                if (!reason.isEmpty()) {
                    return reason;
                }
            }
        }
        return "One or more triple-verification stages failed.";
    }

    private static String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return e == null ? "unknown error" : e.getClass().getSimpleName();
        }
        return e.getMessage().trim();
    }

    private static void putSafe(JSONObject object, String key, Object value) {
        if (object == null || key == null) {
            return;
        }
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
    }

    private static void mergeNativeEvidence(JSONObject target, JSONObject source) {
        if (target == null || source == null) {
            return;
        }
        appendArrayField(target, source, "documentTextBlocks");
        appendArrayField(target, source, "ocrBlocks");
        appendArrayField(target, source, "anchors");
        appendArrayField(target, source, "visualFindings");
        appendArrayField(target, source, "sourceFiles");
        addIntField(target, source, "pageCount");
        addIntField(target, source, "sourcePageCount");
        addIntField(target, source, "renderedPageCount");
        addIntField(target, source, "ocrSuccessCount");
        addIntField(target, source, "ocrFailedCount");
        addIntField(target, source, "documentTextPageCount");
        addIntField(target, source, "visualOnlyPageCount");
        addIntField(target, source, "fastPathTextPageCount");
        addIntField(target, source, "scanFileCount");
    }

    private static void mergeDiagnostics(JSONObject target, JSONObject source) {
        if (target == null || source == null) {
            return;
        }
        appendArrayField(target, source, "contradictionRegister");
        String[] counters = new String[]{
                "contradictions",
                "concealment",
                "evasion",
                "financial",
                "namedPartyCount",
                "verifiedContradictionCount",
                "candidateContradictionCount",
                "rejectedContradictionCount",
                "verifiedFindingCount",
                "candidateFindingCount",
                "rejectedFindingCount",
                "verifiedFinancialFindingCount",
                "candidateFinancialFindingCount",
                "rejectedFinancialFindingCount"
        };
        for (String key : counters) {
            addIntField(target, source, key);
        }
        if (source.optBoolean("indeterminateDueToConcealment", false)) {
            putSafe(target, "indeterminateDueToConcealment", true);
        }
        if (!target.has("processingStatus")) {
            putSafe(target, "processingStatus", source.optString("processingStatus", ""));
        } else if ("INDETERMINATE DUE TO CONCEALMENT".equalsIgnoreCase(source.optString("processingStatus", ""))) {
            putSafe(target, "processingStatus", "INDETERMINATE DUE TO CONCEALMENT");
        }
    }

    private static void mergeExtraction(JSONObject target, JSONObject source) {
        if (target == null || source == null) {
            return;
        }
        appendArrayField(target, source, "namedParties");
        appendArrayField(target, source, "criticalLegalSubjects");
        appendArrayField(target, source, "incidentRegister");
        appendArrayField(target, source, "timelineAnchorRegister");
        appendArrayField(target, source, "actorConductRegister");
        appendArrayField(target, source, "financialExposureRegister");
        appendArrayField(target, source, "narrativeThemeRegister");
        appendArrayField(target, source, "anchoredFindings");
        appendArrayField(target, source, "documentIntegrityFindings");
    }

    private static void appendArrayField(JSONObject target, JSONObject source, String key) {
        if (target == null || source == null || key == null) {
            return;
        }
        JSONArray incoming = source.optJSONArray(key);
        if (incoming == null || incoming.length() == 0) {
            return;
        }
        JSONArray targetArray = target.optJSONArray(key);
        if (targetArray == null) {
            targetArray = new JSONArray();
            putSafe(target, key, targetArray);
        }
        for (int i = 0; i < incoming.length(); i++) {
            targetArray.put(incoming.opt(i));
        }
    }

    private static void addIntField(JSONObject target, JSONObject source, String key) {
        if (target == null || source == null || key == null) {
            return;
        }
        int mergedValue = target.optInt(key, 0) + source.optInt(key, 0);
        if (mergedValue > 0 || target.has(key) || source.has(key)) {
            putSafe(target, key, mergedValue);
        }
    }

    private static int countStatus(JSONArray array, String status) {
        if (array == null || status == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (status.equalsIgnoreCase(item.optString("status", ""))) {
                count++;
            }
        }
        return count;
    }

    private static String mergedProcessingStatus(List<ForensicReport> childReports) {
        if (childReports == null || childReports.isEmpty()) {
            return "COMPLETED";
        }
        boolean concealmentIndeterminate = false;
        boolean processingError = false;
        for (ForensicReport report : childReports) {
            if (report == null || report.diagnostics == null) {
                continue;
            }
            String status = report.diagnostics.optString("processingStatus", "");
            if ("INDETERMINATE DUE TO CONCEALMENT".equalsIgnoreCase(status)
                    || report.diagnostics.optBoolean("indeterminateDueToConcealment", false)) {
                concealmentIndeterminate = true;
            } else if (status.toUpperCase(Locale.ROOT).contains("ERROR")) {
                processingError = true;
            }
        }
        if (concealmentIndeterminate) {
            return "INDETERMINATE DUE TO CONCEALMENT";
        }
        if (processingError) {
            return "COMPLETED WITH SOURCE ERRORS";
        }
        return "COMPLETED";
    }

    private static String buildMergedSummary(ForensicReport report, List<File> files) {
        int fileCount = files == null ? 0 : files.size();
        int contradictions = report != null && report.diagnostics != null
                ? report.diagnostics.optInt("contradictions", 0) : 0;
        int namedParties = report != null && report.constitutionalExtraction != null
                ? report.constitutionalExtraction.optJSONArray("namedParties") != null
                ? report.constitutionalExtraction.optJSONArray("namedParties").length() : 0
                : 0;
        int anchoredFindings = report != null && report.constitutionalExtraction != null
                ? report.constitutionalExtraction.optJSONArray("anchoredFindings") != null
                ? report.constitutionalExtraction.optJSONArray("anchoredFindings").length() : 0
                : 0;
        String processingStatus = report != null && report.diagnostics != null
                ? report.diagnostics.optString("processingStatus", "COMPLETED")
                : "COMPLETED";
        if ("INDETERMINATE DUE TO CONCEALMENT".equalsIgnoreCase(processingStatus)) {
            return "Merged case analysis completed across " + fileCount
                    + " evidence file(s), but the bundle remains indeterminate due to concealment signals in one or more source scans.";
        }
        if (contradictions > 0) {
            return "Merged case analysis completed across " + fileCount
                    + " evidence file(s) with " + contradictions
                    + " contradiction marker(s), " + anchoredFindings
                    + " anchored finding(s), and " + namedParties + " named party reference(s).";
        }
        return "Merged case analysis completed across " + fileCount
                + " evidence file(s) with " + anchoredFindings
                + " anchored finding(s) and " + namedParties + " named party reference(s).";
    }

    private static boolean looksLikeAudioOrVideoFile(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3")
                || name.endsWith(".m4a")
                || name.endsWith(".wav")
                || name.endsWith(".aac")
                || name.endsWith(".ogg")
                || name.endsWith(".mp4")
                || name.endsWith(".mov")
                || name.endsWith(".avi")
                || name.endsWith(".mkv");
    }

    private static JSONObject buildTruthContinuityAnalysis(ForensicReport report) {
        JSONObject out = new JSONObject();
        try {
            JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
            JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
            JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();

            int contradictions = diagnostics.optInt("contradictions", 0);
            int concealment = diagnostics.optInt("concealment", 0);
            int evasion = diagnostics.optInt("evasion", 0);
            int namedPartyCount = diagnostics.optInt("namedPartyCount",
                    extraction.optJSONArray("namedParties") != null ? extraction.optJSONArray("namedParties").length() : 0);
            int anchoredFindingCount = extraction.optJSONArray("anchoredFindings") != null
                    ? extraction.optJSONArray("anchoredFindings").length() : 0;
            int incidentCount = extraction.optJSONArray("incidentRegister") != null
                    ? extraction.optJSONArray("incidentRegister").length() : 0;
            int ocrSuccessCount = nativeEvidence.optInt("ocrSuccessCount", 0);
            int ocrFailedCount = nativeEvidence.optInt("ocrFailedCount", 0);

            int narrativeShiftCount = contradictions + Math.max(0, evasion - 1);
            int brokenLinkCount = Math.max(0, namedPartyCount - anchoredFindingCount);
            int retroactiveJustificationCount = contradictions > 0 && concealment > 0 ? 1 : 0;
            int selectiveContextCount = concealment;
            int corroboratedEventCount = Math.max(anchoredFindingCount, incidentCount);

            String overallAssessment;
            if (contradictions == 0 && brokenLinkCount == 0 && corroboratedEventCount >= Math.max(1, namedPartyCount)) {
                overallAssessment = "high continuity";
            } else if (contradictions <= 1 && brokenLinkCount <= 1) {
                overallAssessment = "mostly connectable with limited gaps";
            } else if (contradictions >= 3 || concealment >= 2 || brokenLinkCount >= 2) {
                overallAssessment = "material continuity concerns";
            } else {
                overallAssessment = "mixed continuity";
            }

            JSONArray stableNarratives = new JSONArray();
            if (ocrSuccessCount > 0) {
                stableNarratives.put("Primary evidence is extractable across OCR blocks and not limited to one unsupported claim.");
            }
            if (anchoredFindingCount > 0) {
                stableNarratives.put("Multiple events can be tied to anchored findings rather than bare allegation.");
            }

            JSONArray narrativeShifts = new JSONArray();
            if (contradictions > 0) {
                narrativeShifts.put("Material statement conflicts appear across the extracted record.");
            }
            if (retroactiveJustificationCount > 0) {
                narrativeShifts.put("Later explanations may be post-hoc justifications rather than contemporaneous reasons.");
            }

            JSONArray brokenLinks = new JSONArray();
            if (brokenLinkCount > 0) {
                brokenLinks.put("Named parties exceed actor-attributed findings, leaving unsupported links in the story.");
            }
            if (ocrFailedCount > 0) {
                brokenLinks.put("Some source material failed OCR and should not be treated as fully connected until rerun or manually confirmed.");
            }

            JSONArray reasoning = new JSONArray();
            reasoning.put("Continuity is assessed by whether dates, actors, acts, and evidence anchors connect without material contradiction.");
            reasoning.put("Narrative shifts increase when statement conflicts, concealment markers, or post-hoc explanations appear.");
            reasoning.put("Broken links increase when parties are named but not tied to specific anchored acts.");

            out.put("overallAssessment", overallAssessment);
            out.put("connectableStory", contradictions == 0 && brokenLinkCount == 0 && corroboratedEventCount > 0);
            out.put("narrativeShiftCount", narrativeShiftCount);
            out.put("brokenLinkCount", brokenLinkCount);
            out.put("retroactiveJustificationCount", retroactiveJustificationCount);
            out.put("selectiveContextCount", selectiveContextCount);
            out.put("corroboratedEventCount", corroboratedEventCount);
            out.put("stableNarratives", stableNarratives);
            out.put("narrativeShifts", narrativeShifts);
            out.put("brokenLinks", brokenLinks);
            out.put("reasoning", reasoning);
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static JSONObject buildPatternAnalysis(Context context, ForensicReport report) {
        JSONObject out = new JSONObject();
        try {
            JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
            JSONObject continuity = report.truthContinuityAnalysis != null ? report.truthContinuityAnalysis : new JSONObject();
            JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
            JSONObject library = new JSONObject(RulesProvider.getBehaviourPatternLibrary(context));
            JSONArray matches = new JSONArray();

            addPatternMatchIf(matches, library, "P010",
                    diagnostics.optInt("contradictions", 0) > 0 || continuity.optInt("brokenLinkCount", 0) > 0,
                    "Narrative continuity is weakened by contradictions or broken links in the extracted record.",
                    "strongly_indicated");
            addPatternMatchIf(matches, library, "P005",
                    diagnostics.optInt("concealment", 0) > 0 && diagnostics.optInt("contradictions", 0) > 0,
                    "Known context appears to have been suppressed or ignored before blame was intensified.",
                    "possible");
            addPatternMatchIf(matches, library, "P004",
                    diagnostics.optInt("concealment", 0) > 0,
                    "The record contains concealment markers consistent with selective-context presentation.",
                    "possible");
            addPatternMatchIf(matches, library, "P003",
                    containsText(nativeEvidence, "copied you in") || containsText(nativeEvidence, "bcc"),
                    "Communication evidence suggests hidden or indirect visibility into message flow.",
                    "possible");
            addPatternMatchIf(matches, library, "P006",
                    containsText(nativeEvidence, "countersigned") || containsText(nativeEvidence, "signature"),
                    "Document evidence references unsigned or countersignature-defective workflows that may later be exploited.",
                    "possible");

            out.put("matchedPatternCount", matches.length());
            out.put("matches", matches);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static JSONObject buildVulnerabilityAnalysis(Context context, ForensicReport report) {
        JSONObject out = new JSONObject();
        try {
            JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
            JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
            JSONObject continuity = report.truthContinuityAnalysis != null ? report.truthContinuityAnalysis : new JSONObject();
            JSONObject library = new JSONObject(RulesProvider.getBehaviourPatternLibrary(context));
            JSONArray matches = new JSONArray();
            JSONArray indicators = new JSONArray();

            boolean mentalHealthTargeting =
                    containsText(nativeEvidence, "mental health")
                            || containsText(nativeEvidence, "crazy")
                            || containsText(nativeEvidence, "unstable")
                            || containsText(nativeEvidence, "gaslighting");
            boolean crisisTargeting =
                    containsText(nativeEvidence, "family crisis")
                            || containsText(nativeEvidence, "vulnerable")
                            || containsText(nativeEvidence, "distress")
                            || containsText(nativeEvidence, "breakdown");
            boolean coerciveIsolation =
                    containsText(nativeEvidence, "asking everyone to leave")
                            || containsText(nativeEvidence, "leave his room")
                            || containsText(nativeEvidence, "on his own")
                            || containsText(nativeEvidence, "unannounced");

            if (mentalHealthTargeting) {
                indicators.put("Mental-health or credibility-targeting language appears in the extracted record.");
            }
            if (crisisTargeting) {
                indicators.put("The record references a known crisis, vulnerability, or distress state around the disputed events.");
            }
            if (coerciveIsolation) {
                indicators.put("The record describes unannounced in-person intervention or witness removal before a sensitive discussion.");
            }

            addPatternMatchIf(matches, library, "P011",
                    mentalHealthTargeting || crisisTargeting || (diagnostics.optInt("concealment", 0) > 0 && continuity.optInt("narrativeShiftCount", 0) > 0),
                    "The evidence suggests pressure, blame, or credibility attacks may have been directed at a known vulnerability or crisis point.",
                    "possible");
            addPatternMatchIf(matches, library, "P012",
                    coerciveIsolation,
                    "The record describes an unannounced meeting or room-clearing event consistent with coercive isolation before a sensitive discussion.",
                    "possible");

            String assessment;
            if (matches.length() >= 2) {
                assessment = "material vulnerability concerns";
            } else if (matches.length() == 1) {
                assessment = "possible vulnerability-linked coercion";
            } else {
                assessment = "no vulnerability pattern matched from current extraction";
            }

            out.put("matchedVulnerabilityCount", matches.length());
            out.put("assessment", assessment);
            out.put("indicators", indicators);
            out.put("matches", matches);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void addPatternMatchIf(
            JSONArray sink,
            JSONObject library,
            String patternId,
            boolean condition,
            String evidenceNote,
            String status
    ) throws JSONException {
        if (!condition) {
            return;
        }
        JSONArray patterns = library.optJSONArray("patterns");
        if (patterns == null) {
            return;
        }
        for (int i = 0; i < patterns.length(); i++) {
            JSONObject pattern = patterns.optJSONObject(i);
            if (pattern == null || !patternId.equals(pattern.optString("pattern_id"))) {
                continue;
            }
            JSONObject match = new JSONObject();
            match.put("patternId", patternId);
            match.put("patternName", pattern.optString("pattern_name"));
            match.put("category", pattern.optString("category"));
            match.put("status", status);
            match.put("reportLanguage", pattern.optString("report_language"));
            match.put("evidenceNote", evidenceNote);
            sink.put(match);
            return;
        }
    }

    private static boolean containsText(JSONObject value, String needle) {
        return value != null && value.toString().toLowerCase().contains(needle.toLowerCase());
    }
}
