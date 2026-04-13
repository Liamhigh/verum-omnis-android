package com.verum.omnis.core;

import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the deterministic pre-publication analysis phase.
 *
 * This separates evidence analysis and finding-package preparation from the
 * downstream publication flow handled by {@link AnalyzeCaseUseCase}.
 */
public final class AnalysisCoordinator {

    public interface Gateway {
        Map<String, String> runIntegrityChecks() throws Exception;

        AnalysisEngine.ForensicReport analyzeEvidenceSet(List<File> evidenceFiles) throws Exception;

        AnalysisEngine.ForensicReport analyzeSingleEvidence(File evidenceFile) throws Exception;

        void applyInvestigatorContext(AnalysisEngine.ForensicReport report) throws Exception;

        Map<String, String> inspectPrimaryEvidence(File primaryEvidenceFile) throws Exception;

        JSONObject buildFindingsPayload(File primaryEvidenceFile, AnalysisEngine.ForensicReport report) throws Exception;
    }

    public static final class Request {
        public List<File> evidenceFiles;
        public File primaryEvidenceFile;
    }

    public static final class Result {
        public AnalysisEngine.ForensicReport report;
        public Map<String, String> integrityResults = new LinkedHashMap<>();
        public Map<String, String> primaryEvidenceMeta = new LinkedHashMap<>();
        public JSONObject findingsPayload = new JSONObject();
        public File primaryEvidenceFile;
    }

    public Result run(Request request, Gateway gateway) throws Exception {
        if (request == null || gateway == null) {
            throw new IllegalArgumentException("AnalysisCoordinator requires a request and gateway.");
        }
        File primaryEvidenceFile = request.primaryEvidenceFile;
        if (primaryEvidenceFile == null) {
            throw new IllegalArgumentException("AnalysisCoordinator requires a primary evidence file.");
        }

        Result result = new Result();
        result.primaryEvidenceFile = primaryEvidenceFile;
        result.integrityResults = safeMap(gateway.runIntegrityChecks());
        result.report = shouldAnalyzeEvidenceSet(request.evidenceFiles)
                ? gateway.analyzeEvidenceSet(request.evidenceFiles)
                : gateway.analyzeSingleEvidence(primaryEvidenceFile);
        gateway.applyInvestigatorContext(result.report);
        result.primaryEvidenceMeta = safeMap(gateway.inspectPrimaryEvidence(primaryEvidenceFile));
        result.findingsPayload = safeJson(gateway.buildFindingsPayload(primaryEvidenceFile, result.report));
        return result;
    }

    private static boolean shouldAnalyzeEvidenceSet(List<File> evidenceFiles) {
        return evidenceFiles != null && evidenceFiles.size() > 1;
    }

    private static Map<String, String> safeMap(Map<String, String> map) {
        return map != null ? new LinkedHashMap<>(map) : new LinkedHashMap<>();
    }

    private static JSONObject safeJson(JSONObject object) {
        return object != null ? object : new JSONObject();
    }
}
