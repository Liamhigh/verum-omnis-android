package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralizes the post-engine reporting sequence so UI code does not directly
 * orchestrate narrative generation, sealing, and vault publication.
 */
public final class AnalyzeCaseUseCase {

    public enum Mode {
        FULL_SCAN,
        PDF_ONLY
    }

    public interface Gateway {
        String buildAuditReport(AnalysisEngine.ForensicReport report, Map<String, String> integrityResults) throws Exception;

        String generateHumanReadableReport(
                AnalysisEngine.ForensicReport report,
                Map<String, String> integrityResults,
                String findingsJson,
                String auditReport
        ) throws Exception;

        Context getApplicationContext();

        String buildLegacyLegalAdvisory(AnalysisEngine.ForensicReport report) throws Exception;

        String appendLegalAdvisorySection(String humanReadableReport, String legalAdvisory);

        File writeModelAuditLedgerToVault(AnalysisEngine.ForensicReport report, String modelAuditLedger) throws Exception;

        String buildVisualFindingsMemo(AnalysisEngine.ForensicReport report);

        String generateReadableFindingsBriefReport(
                AnalysisEngine.ForensicReport report,
                String findingsJson,
                String auditReport,
                String humanReadableReport,
                String legalAdvisory,
                String visualFindingsMemo
        ) throws Exception;

        String generatePoliceReadyReport(AnalysisEngine.ForensicReport report) throws Exception;

        String generateConstitutionalVaultReport(
                AnalysisEngine.ForensicReport report,
                Map<String, String> integrityResults,
                Map<String, String> primaryEvidenceMeta,
                String auditReport,
                String humanReadableReport,
                String readableBriefReport
        ) throws Exception;

        String generateContradictionEngineReport(AnalysisEngine.ForensicReport report) throws Exception;

        File writeAuditReportToVault(AnalysisEngine.ForensicReport report, String auditReport) throws Exception;

        File writeForensicReportToVault(AnalysisEngine.ForensicReport report, String humanReadableReport) throws Exception;

        File writeReadableFindingsBriefToVault(AnalysisEngine.ForensicReport report, String readableBriefReport) throws Exception;

        File writePoliceReadyReportToVault(AnalysisEngine.ForensicReport report, String policeReadyReport) throws Exception;

        File writeConstitutionalVaultReportToVault(
                AnalysisEngine.ForensicReport report,
                String constitutionalNarrativeReport
        ) throws Exception;

        File writeContradictionEngineReportToVault(
                AnalysisEngine.ForensicReport report,
                String contradictionEngineReport
        ) throws Exception;

        File writeLegalAdvisoryToVault(AnalysisEngine.ForensicReport report, String legalAdvisory) throws Exception;

        File writeVisualFindingsMemoToVault(AnalysisEngine.ForensicReport report, String visualFindingsMemo) throws Exception;
    }

    public static final class Request {
        public Mode mode = Mode.FULL_SCAN;
        public AnalysisEngine.ForensicReport report;
        public Map<String, String> integrityResults = new LinkedHashMap<>();
        public Map<String, String> primaryEvidenceMeta = new LinkedHashMap<>();
        public JSONObject findingsPayload = new JSONObject();
        public boolean includeLegalAdvisory;
        public boolean boundedHumanBriefEnabled;
        public boolean boundedPoliceSummaryEnabled;
        public boolean boundedLegalStandingEnabled;
        public boolean boundedRenderAuditRequired = true;
        public boolean boundedRenderFailClosed = true;
    }

    public static final class Result {
        public AnalysisEngine.ForensicReport report;
        public JSONObject findingsPayload = new JSONObject();
        public String auditReport = "";
        public String humanReadableReport = "";
        public String readableBriefReport = "";
        public String policeReadyReport = "";
        public String constitutionalNarrativeReport = "";
        public String contradictionEngineReport = "";
        public String legalAdvisory = "";
        public String visualFindingsMemo = "";
        public File auditorPdf;
        public File forensicPdf;
        public File readableBriefFile;
        public File policeReadyReportFile;
        public File constitutionalNarrativeFile;
        public File contradictionEngineFile;
        public File legalAdvisoryFile;
        public File modelAuditLedgerFile;
        public File visualFindingsFile;
        public final Map<String, File> vaultReferences = new LinkedHashMap<>();
    }

    public Result run(Request request, Gateway gateway) throws Exception {
        Request safeRequest = request != null ? request : new Request();
        if (safeRequest.report == null) {
            throw new IllegalArgumentException("AnalyzeCaseUseCase requires a populated forensic report.");
        }
        if (gateway == null) {
            throw new IllegalArgumentException("AnalyzeCaseUseCase requires a gateway.");
        }

        Result result = new Result();
        result.report = safeRequest.report;
        result.findingsPayload = safeRequest.findingsPayload != null
                ? safeRequest.findingsPayload
                : new JSONObject();
        String findingsJson = result.findingsPayload.toString(2);

        result.auditReport = nonNull(gateway.buildAuditReport(safeRequest.report, safeRequest.integrityResults));
        result.auditorPdf = gateway.writeAuditReportToVault(safeRequest.report, result.auditReport);
        putReference(result, "auditReportPath", result.auditorPdf);

        result.humanReadableReport = nonNull(gateway.generateHumanReadableReport(
                safeRequest.report,
                safeRequest.integrityResults,
                findingsJson,
                result.auditReport
        ));

        if (safeRequest.includeLegalAdvisory) {
            String legacyFallback = nonNull(gateway.buildLegacyLegalAdvisory(safeRequest.report));
            result.legalAdvisory = nonNull(GemmaReportOrchestrator.renderBoundedHumanBriefOnly(
                    gateway.getApplicationContext(),
                    safeRequest.report,
                    new GemmaReportOrchestrator.BoundedFeatureFlags(
                            safeRequest.boundedHumanBriefEnabled,
                            safeRequest.boundedPoliceSummaryEnabled,
                            safeRequest.boundedLegalStandingEnabled,
                            safeRequest.boundedRenderAuditRequired,
                            safeRequest.boundedRenderFailClosed
                    ),
                    legacyFallback
            ));
            String modelAuditLedger = ModelAuditLogger.buildLedgerJson(
                    gateway.getApplicationContext(),
                    safeRequest.report.caseId != null ? safeRequest.report.caseId : ""
            );
            String modelAuditAppendix = ModelAuditLogger.buildVisibleAppendix(
                    gateway.getApplicationContext(),
                    safeRequest.report.caseId != null ? safeRequest.report.caseId : ""
            );
            if (!modelAuditAppendix.trim().isEmpty() && !result.legalAdvisory.trim().isEmpty()) {
                result.legalAdvisory = result.legalAdvisory.trim()
                        + "\n\n"
                        + modelAuditAppendix.trim();
            }
            if (!result.legalAdvisory.trim().isEmpty()) {
                result.humanReadableReport = nonNull(
                        gateway.appendLegalAdvisorySection(result.humanReadableReport, result.legalAdvisory)
                );
            }
            result.modelAuditLedgerFile = gateway.writeModelAuditLedgerToVault(safeRequest.report, modelAuditLedger);
            putReference(result, "modelAuditLedgerPath", result.modelAuditLedgerFile);
        }

        result.forensicPdf = gateway.writeForensicReportToVault(safeRequest.report, result.humanReadableReport);
        putReference(result, "humanReportPath", result.forensicPdf);

        result.visualFindingsMemo = nonNull(gateway.buildVisualFindingsMemo(safeRequest.report));
        result.readableBriefReport = nonNull(gateway.generateReadableFindingsBriefReport(
                safeRequest.report,
                findingsJson,
                result.auditReport,
                result.humanReadableReport,
                "",
                result.visualFindingsMemo
        ));
        result.readableBriefFile = gateway.writeReadableFindingsBriefToVault(
                safeRequest.report,
                result.readableBriefReport
        );
        putReference(result, "readableBriefPath", result.readableBriefFile);

        result.policeReadyReport = nonNull(gateway.generatePoliceReadyReport(safeRequest.report));
        result.policeReadyReportFile = gateway.writePoliceReadyReportToVault(
                safeRequest.report,
                result.policeReadyReport
        );
        putReference(result, "policeReadyReportPath", result.policeReadyReportFile);

        result.constitutionalNarrativeReport = nonNull(gateway.generateConstitutionalVaultReport(
                safeRequest.report,
                safeRequest.integrityResults,
                safeRequest.primaryEvidenceMeta,
                result.auditReport,
                result.humanReadableReport,
                result.readableBriefReport
        ));
        result.constitutionalNarrativeFile = gateway.writeConstitutionalVaultReportToVault(
                safeRequest.report,
                result.constitutionalNarrativeReport
        );
        putReference(result, "constitutionalNarrativePath", result.constitutionalNarrativeFile);

        result.visualFindingsFile = gateway.writeVisualFindingsMemoToVault(
                safeRequest.report,
                result.visualFindingsMemo
        );
        putReference(result, "visualFindingsPath", result.visualFindingsFile);

        if (safeRequest.mode == Mode.FULL_SCAN) {
            result.contradictionEngineReport = nonNull(gateway.generateContradictionEngineReport(safeRequest.report));
            result.contradictionEngineFile = gateway.writeContradictionEngineReportToVault(
                    safeRequest.report,
                    result.contradictionEngineReport
            );
            putReference(result, "contradictionEnginePath", result.contradictionEngineFile);

            result.legalAdvisoryFile = gateway.writeLegalAdvisoryToVault(safeRequest.report, result.legalAdvisory);
            putReference(result, "legalAdvisoryPath", result.legalAdvisoryFile);
        }

        return result;
    }

    private static void putReference(Result result, String key, File file) {
        if (result == null || key == null || key.trim().isEmpty() || file == null) {
            return;
        }
        result.vaultReferences.put(key, file);
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
