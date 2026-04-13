package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalysisCoordinatorTest {

    @Test
    public void coordinatorOwnsDeterministicAnalysisPackaging() throws Exception {
        AnalysisCoordinator coordinator = new AnalysisCoordinator();
        RecordingGateway gateway = new RecordingGateway();

        AnalysisCoordinator.Request request = new AnalysisCoordinator.Request();
        request.primaryEvidenceFile = new File("primary.pdf");
        request.evidenceFiles = Arrays.asList(request.primaryEvidenceFile, new File("extra.pdf"));

        AnalysisCoordinator.Result result = coordinator.run(request, gateway);

        assertEquals("set", gateway.analysisMode);
        assertTrue(gateway.investigatorContextApplied);
        assertEquals("primary.pdf", gateway.inspectedPrimaryEvidenceName);
        assertEquals("primary.pdf", gateway.findingsPayloadSourceName);
        assertEquals("case-analysis", result.report.caseId);
        assertEquals("ok", result.integrityResults.get("integrity"));
        assertEquals("primary.pdf", result.findingsPayload.optString("sourceFileName"));
        assertEquals("true", result.primaryEvidenceMeta.get("scanned"));
    }

    @Test
    public void coordinatorFallsBackToSingleEvidenceMode() throws Exception {
        AnalysisCoordinator coordinator = new AnalysisCoordinator();
        RecordingGateway gateway = new RecordingGateway();

        AnalysisCoordinator.Request request = new AnalysisCoordinator.Request();
        request.primaryEvidenceFile = new File("single.pdf");
        request.evidenceFiles = Collections.singletonList(request.primaryEvidenceFile);

        coordinator.run(request, gateway);

        assertEquals("single", gateway.analysisMode);
    }

    private static final class RecordingGateway implements AnalysisCoordinator.Gateway {
        String analysisMode = "";
        boolean investigatorContextApplied;
        String inspectedPrimaryEvidenceName = "";
        String findingsPayloadSourceName = "";

        @Override
        public Map<String, String> runIntegrityChecks() {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("integrity", "ok");
            return out;
        }

        @Override
        public AnalysisEngine.ForensicReport analyzeEvidenceSet(List<File> evidenceFiles) {
            analysisMode = "set";
            return buildReport();
        }

        @Override
        public AnalysisEngine.ForensicReport analyzeSingleEvidence(File evidenceFile) {
            analysisMode = "single";
            return buildReport();
        }

        @Override
        public void applyInvestigatorContext(AnalysisEngine.ForensicReport report) {
            investigatorContextApplied = true;
        }

        @Override
        public Map<String, String> inspectPrimaryEvidence(File primaryEvidenceFile) {
            inspectedPrimaryEvidenceName = primaryEvidenceFile.getName();
            Map<String, String> out = new LinkedHashMap<>();
            out.put("scanned", "true");
            return out;
        }

        @Override
        public JSONObject buildFindingsPayload(File primaryEvidenceFile, AnalysisEngine.ForensicReport report) throws Exception {
            findingsPayloadSourceName = primaryEvidenceFile.getName();
            return new JSONObject().put("sourceFileName", primaryEvidenceFile.getName());
        }

        private AnalysisEngine.ForensicReport buildReport() {
            AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
            report.caseId = "case-analysis";
            return report;
        }
    }
}
