package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnalyzeCaseUseCaseTest {

    @Test
    public void readableBriefPathNeverConsumesLegalAdvisoryText() throws Exception {
        AnalyzeCaseUseCase useCase = new AnalyzeCaseUseCase();
        RecordingGateway gateway = new RecordingGateway();

        AnalyzeCaseUseCase.Request request = new AnalyzeCaseUseCase.Request();
        request.mode = AnalyzeCaseUseCase.Mode.FULL_SCAN;
        request.report = new AnalysisEngine.ForensicReport();
        request.report.caseId = "case-use-case";
        request.report.evidenceHash = "hash";
        request.findingsPayload = new JSONObject().put("seed", "value");
        request.includeLegalAdvisory = true;

        AnalyzeCaseUseCase.Result result = useCase.run(request, gateway);

        assertEquals("LEGAL ADVISORY", result.legalAdvisory);
        assertEquals("", gateway.readableBriefLegalAdvisorySeen);
        assertTrue(gateway.humanReadableFindingsJsonSeen.contains("\"seed\": \"value\""));
        assertNotNull(result.legalAdvisoryFile);
        assertTrue(result.vaultReferences.containsKey("legalAdvisoryPath"));
        assertFalse(result.vaultReferences.containsKey("modelAuditLedgerPath"));
    }

    @Test
    public void pdfOnlyModeSkipsAdvisoryArtifactButKeepsGovernedPublicationStable() throws Exception {
        AnalyzeCaseUseCase useCase = new AnalyzeCaseUseCase();
        RecordingGateway gateway = new RecordingGateway();

        AnalyzeCaseUseCase.Request request = new AnalyzeCaseUseCase.Request();
        request.mode = AnalyzeCaseUseCase.Mode.PDF_ONLY;
        request.report = new AnalysisEngine.ForensicReport();
        request.report.caseId = "case-pdf";
        request.report.evidenceHash = "hash";
        request.findingsPayload = new JSONObject().put("seed", "value");
        request.includeLegalAdvisory = true;

        AnalyzeCaseUseCase.Result result = useCase.run(request, gateway);

        assertEquals("LEGAL ADVISORY", result.legalAdvisory);
        assertNull(result.legalAdvisoryFile);
        assertNull(result.contradictionEngineFile);
        assertFalse(result.vaultReferences.containsKey("legalAdvisoryPath"));
        assertFalse(result.vaultReferences.containsKey("contradictionEnginePath"));
        assertEquals("AUDIT REPORT", result.auditReport);
        assertEquals("HUMAN REPORT", result.humanReadableReport);
        assertEquals("READABLE BRIEF", result.readableBriefReport);
    }

    @Test
    public void fullAndPdfOnlyModesProduceSameGovernedInputsForSharedPublicationStages() throws Exception {
        AnalyzeCaseUseCase useCase = new AnalyzeCaseUseCase();

        RecordingGateway fullGateway = new RecordingGateway();
        AnalyzeCaseUseCase.Request fullRequest = new AnalyzeCaseUseCase.Request();
        fullRequest.mode = AnalyzeCaseUseCase.Mode.FULL_SCAN;
        fullRequest.report = new AnalysisEngine.ForensicReport();
        fullRequest.report.caseId = "case-full";
        fullRequest.report.evidenceHash = "hash";
        fullRequest.findingsPayload = new JSONObject().put("seed", "value");
        fullRequest.includeLegalAdvisory = false;
        useCase.run(fullRequest, fullGateway);

        RecordingGateway pdfGateway = new RecordingGateway();
        AnalyzeCaseUseCase.Request pdfRequest = new AnalyzeCaseUseCase.Request();
        pdfRequest.mode = AnalyzeCaseUseCase.Mode.PDF_ONLY;
        pdfRequest.report = new AnalysisEngine.ForensicReport();
        pdfRequest.report.caseId = "case-full";
        pdfRequest.report.evidenceHash = "hash";
        pdfRequest.findingsPayload = new JSONObject().put("seed", "value");
        pdfRequest.includeLegalAdvisory = false;
        useCase.run(pdfRequest, pdfGateway);

        assertEquals(fullGateway.humanReadableFindingsJsonSeen, pdfGateway.humanReadableFindingsJsonSeen);
        assertEquals(fullGateway.auditReportSeenByReadableBrief, pdfGateway.auditReportSeenByReadableBrief);
        assertEquals(fullGateway.humanReportSeenByReadableBrief, pdfGateway.humanReportSeenByReadableBrief);
    }

    private static final class RecordingGateway implements AnalyzeCaseUseCase.Gateway {
        String humanReadableFindingsJsonSeen = "";
        String readableBriefLegalAdvisorySeen = "";
        String auditReportSeenByReadableBrief = "";
        String humanReportSeenByReadableBrief = "";

        @Override
        public String buildAuditReport(AnalysisEngine.ForensicReport report, Map<String, String> integrityResults) {
            return "AUDIT REPORT";
        }

        @Override
        public String generateHumanReadableReport(
                AnalysisEngine.ForensicReport report,
                Map<String, String> integrityResults,
                String findingsJson,
                String auditReport
        ) {
            this.humanReadableFindingsJsonSeen = findingsJson;
            return "HUMAN REPORT";
        }

        @Override
        public Context getApplicationContext() {
            return null;
        }

        @Override
        public String buildLegacyLegalAdvisory(AnalysisEngine.ForensicReport report) {
            return "LEGAL ADVISORY";
        }

        @Override
        public String appendLegalAdvisorySection(String humanReadableReport, String legalAdvisory) {
            return humanReadableReport;
        }

        @Override
        public File writeModelAuditLedgerToVault(AnalysisEngine.ForensicReport report, String modelAuditLedger) {
            return modelAuditLedger == null || modelAuditLedger.trim().isEmpty() ? null : fakeFile("model-audit.json");
        }

        @Override
        public String buildVisualFindingsMemo(AnalysisEngine.ForensicReport report) {
            return "VISUAL MEMO";
        }

        @Override
        public String generateReadableFindingsBriefReport(
                AnalysisEngine.ForensicReport report,
                String findingsJson,
                String auditReport,
                String humanReadableReport,
                String legalAdvisory,
                String visualFindingsMemo
        ) {
            this.readableBriefLegalAdvisorySeen = legalAdvisory;
            this.auditReportSeenByReadableBrief = auditReport;
            this.humanReportSeenByReadableBrief = humanReadableReport;
            return "READABLE BRIEF";
        }

        @Override
        public String generatePoliceReadyReport(AnalysisEngine.ForensicReport report) {
            return "POLICE READY";
        }

        @Override
        public String generateConstitutionalVaultReport(
                AnalysisEngine.ForensicReport report,
                Map<String, String> integrityResults,
                Map<String, String> primaryEvidenceMeta,
                String auditReport,
                String humanReadableReport,
                String readableBriefReport
        ) {
            return "VAULT REPORT";
        }

        @Override
        public String generateContradictionEngineReport(AnalysisEngine.ForensicReport report) {
            return "CONTRADICTION REPORT";
        }

        @Override
        public File writeAuditReportToVault(AnalysisEngine.ForensicReport report, String auditReport) {
            return fakeFile("audit.pdf");
        }

        @Override
        public File writeForensicReportToVault(AnalysisEngine.ForensicReport report, String humanReadableReport) {
            return fakeFile("human.pdf");
        }

        @Override
        public File writeReadableFindingsBriefToVault(AnalysisEngine.ForensicReport report, String readableBriefReport) {
            return fakeFile("brief.pdf");
        }

        @Override
        public File writePoliceReadyReportToVault(AnalysisEngine.ForensicReport report, String policeReadyReport) {
            return fakeFile("police.pdf");
        }

        @Override
        public File writeConstitutionalVaultReportToVault(
                AnalysisEngine.ForensicReport report,
                String constitutionalNarrativeReport
        ) {
            return fakeFile("vault.pdf");
        }

        @Override
        public File writeContradictionEngineReportToVault(
                AnalysisEngine.ForensicReport report,
                String contradictionEngineReport
        ) {
            return fakeFile("contradiction.pdf");
        }

        @Override
        public File writeLegalAdvisoryToVault(AnalysisEngine.ForensicReport report, String legalAdvisory) {
            return fakeFile("legal.pdf");
        }

        @Override
        public File writeVisualFindingsMemoToVault(AnalysisEngine.ForensicReport report, String visualFindingsMemo) {
            return fakeFile("visual.pdf");
        }

        private File fakeFile(String name) {
            return new File(name);
        }
    }
}
