package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class GemmaReportOrchestratorTest {

    @Test
    public void bundleFactoryBuildsGuardianBoundedBundle() throws Exception {
        ConstitutionalReportBundleFactory factory = new ConstitutionalReportBundleFactory();
        AnalysisEngine.ForensicReport report = buildReport();

        ReportRenderInput bundle = factory.build(report);

        assertEquals("PASS", bundle.getTripleVerificationStatus());
        assertTrue(bundle.getCertifiedFindings().stream().allMatch(
                it -> "CERTIFIED".equalsIgnoreCase(it.getPublicationStatus())
        ));
        assertTrue(bundle.getBoundaryNote().contains("judicial verdict"));
        assertTrue(bundle.getLegalIssueHints().stream().anyMatch(it -> it.contains("goodwill")));
        assertTrue(bundle.getLegalIssueHints().stream().anyMatch(it -> it.contains("countersigned")));
    }

    @Test
    public void orchestratorPublishesRenderedSectionsOnlyWhenAuditPasses() {
        LocalModelEngine renderEngine = new FakeEngine(
                "{\"report\":\"What happened section\"}",
                "{\"report\":\"Legal standing section\"}",
                "{\"report\":\"Police summary section\"}"
        );
        LocalModelEngine auditEngine = new FakeEngine(
                "{\"auditPassed\":true,\"notes\":[\"bounded\"]}"
        );

        GemmaReportOrchestrator orchestrator = new GemmaReportOrchestrator(renderEngine, auditEngine);
        RenderedReport result = orchestrator.render(sampleBundle());

        assertTrue(result.getAuditPassed());
        assertEquals("What happened section", result.getHumanBrief());
        assertEquals("Legal standing section", result.getLegalStanding());
        assertEquals("Police summary section", result.getPoliceSummary());
    }

    @Test
    public void orchestratorReturnsFailedAuditNotesWhenAuditRejects() {
        LocalModelEngine renderEngine = new FakeEngine(
                "{\"report\":\"What happened section\"}",
                "{\"report\":\"Legal standing section\"}",
                "{\"report\":\"Police summary section\"}"
        );
        LocalModelEngine auditEngine = new FakeEngine(
                "{\"auditPassed\":false,\"notes\":[\"missing constitutional boundary\"]}"
        );

        GemmaReportOrchestrator orchestrator = new GemmaReportOrchestrator(renderEngine, auditEngine);
        RenderedReport result = orchestrator.render(sampleBundle());

        assertFalse(result.getAuditPassed());
        assertTrue(result.getAuditNotes().contains("missing constitutional boundary"));
    }

    private ReportRenderInput sampleBundle() {
        return new ReportRenderInput(
                "case-id",
                "hash",
                "engine",
                "run",
                "South Africa",
                "PASS",
                "thesis",
                "antithesis",
                "synthesis",
                null,
                java.util.Collections.singletonList(
                        new RenderCertifiedFinding(
                                "Actor",
                                "Summary",
                                8,
                                java.util.Collections.singletonList(8),
                                "CANDIDATE",
                                "CERTIFIED"
                        )
                ),
                java.util.Collections.singletonList("PRECCA Section 34"),
                java.util.Collections.singletonList("Do not state lawful transfer occurred."),
                "This is a forensic conclusion, not a judicial verdict."
        );
    }

    private AnalysisEngine.ForensicReport buildReport() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "case-current";
        report.evidenceHash = "hash-current";
        report.engineVersion = "engine-v";
        report.deterministicRunId = "run-v";
        report.jurisdictionName = "South Africa";
        report.summary = "The governed record shows unsigned papers, continuing money movement, and later pressure to vacate.";
        report.legalReferences = new String[] {"PRECCA Section 34"};
        report.tripleVerification = new JSONObject()
                .put("overall", new JSONObject().put("status", "PASS"))
                .put("thesis", new JSONObject().put("reason", "thesis reason"))
                .put("antithesis", new JSONObject().put("reason", "antithesis reason"))
                .put("synthesis", new JSONObject().put("reason", "synthesis reason"));
        report.forensicConclusion = new JSONObject()
                .put("whatHappened", new JSONArray()
                        .put("A repeated pattern appears in the record where agreements were signed but not countersigned, money continued to move, and pressure to vacate followed.")
                        .put("If goodwill wasn't of any value why did they add a clause for my dad to sign it away?"));
        report.normalizedCertifiedFindings = new JSONArray()
                .put(new JSONObject()
                        .put("actor", "All Fuels")
                        .put("summary", "No countersigned transfer is present in the governed record.")
                        .put("page", 8)
                        .put("primaryPage", 8)
                        .put("anchorPages", new JSONArray().put(8))
                        .put("publicationStatus", "CERTIFIED")
                        .put("contradictionStatus", "CANDIDATE"))
                .put(new JSONObject()
                        .put("actor", "Desmond Smith")
                        .put("summary", "Pressure to vacate followed unresolved document execution.")
                        .put("page", 128)
                        .put("primaryPage", 128)
                        .put("anchorPages", new JSONArray().put(128).put(129))
                        .put("publicationStatus", "CERTIFIED")
                        .put("contradictionStatus", "CANDIDATE"))
                .put(new JSONObject()
                        .put("actor", "Gary Highcock")
                        .put("summary", "The record carries live risk around the same pattern.")
                        .put("page", 130)
                        .put("primaryPage", 130)
                        .put("anchorPages", new JSONArray().put(130))
                        .put("publicationStatus", "CERTIFIED")
                        .put("contradictionStatus", "CANDIDATE"));
        report.certifiedFindings = report.normalizedCertifiedFindings;
        report.normalizedCertifiedFindingCount = 3;
        return report;
    }

    private static final class FakeEngine implements LocalModelEngine {
        private final String[] responses;
        private int index = 0;

        private FakeEngine(String... responses) {
            this.responses = responses;
        }

        @Override
        public void warmUp() {
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public ModelResponse run(ModelRequest request) {
            String response = responses[Math.min(index, responses.length - 1)];
            index++;
            return new ModelResponse(true, "fake", response, 5L, null);
        }
    }
}
