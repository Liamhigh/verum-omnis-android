package com.verum.legal;

import com.verum.omnis.core.AnalysisEngine;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegalGroundingTest {

    @Test
    public void loadJurisdictionPacks_loadsPrimaryLawMaterials() throws Exception {
        LegalGrounding grounding = new LegalGrounding(findLegalPackRoot());
        LegalGrounding.JurisdictionPack pack = grounding.loadJurisdictionPacks("UAE");

        assertEquals("UAE", pack.code);
        assertFalse(pack.statutes.isEmpty());
        assertFalse(pack.offenceElements.isEmpty());
        assertFalse(pack.proceduralRules.isEmpty());
        assertFalse(pack.institutions.isEmpty());
    }

    @Test
    public void buildRetrievalQueries_isDeterministic() throws Exception {
        LegalGrounding grounding = new LegalGrounding(findLegalPackRoot());
        AnalysisEngine.ForensicReport report = sampleReport();

        LegalGrounding.GroundingData data = grounding.extractGroundingData(report);
        assertEquals(
                grounding.buildRetrievalQueries(data),
                grounding.buildRetrievalQueries(data)
        );
    }

    @Test
    public void buildPromptPackage_greenskyLikeCaseUsesGroundedInputs() throws Exception {
        LegalGrounding grounding = new LegalGrounding(findLegalPackRoot());
        AnalysisEngine.ForensicReport report = sampleReport();

        LegalGrounding.PromptPackage promptPackage = grounding.buildPromptPackage(report);

        assertTrue(promptPackage.prompt.contains("United Arab Emirates"));
        assertTrue(promptPackage.prompt.contains("Federal Decree-Law No. (32) of 2021"));
        assertTrue(promptPackage.prompt.contains("Shareholder was excluded from company decisions"));
        assertTrue(promptPackage.docs.stream().anyMatch(doc -> doc.title.contains("Commercial Companies")));
        assertFalse(promptPackage.prompt.contains("RAW-EVIDENCE-SHOULD-NOT-LEAK-1234567890"));
    }

    private AnalysisEngine.ForensicReport sampleReport() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.caseId = "greensky-like-case";
        report.jurisdiction = "UAE";
        report.jurisdictionName = "United Arab Emirates";
        report.summary = "Sealed findings indicate exclusion from company decisions, a document execution dispute, and an access-control concern.";
        report.legalReferences = new String[]{
                "Federal Decree-Law No. (32) of 2021 Regarding Commercial Companies",
                "Federal Decree-Law No. (34) of 2021 On Countering Rumors and Cybercrimes"
        };
        report.topLiabilities = new String[]{
                "Shareholder oppression or exclusion",
                "Unauthorized account access or digital interference"
        };
        report.diagnostics = new JSONObject()
                .put("processingStatus", "DETERMINATE")
                .put("verifiedContradictionCount", 2)
                .put("candidateContradictionCount", 1)
                .put("anchoredFindingCount", 3)
                .put("namedPartyCount", 4);
        report.certifiedFindings = new JSONArray()
                .put(new JSONObject()
                        .put("summary", "Shareholder was excluded from company decisions")
                        .put("excerpt", "Formal meeting and signature control were withheld from the reporting shareholder.")
                        .put("page", 12))
                .put(new JSONObject()
                        .put("summary", "Account credential access was disputed")
                        .put("excerpt", "The sealed record links the account dispute to a credential-use concern without exposing raw logs.")
                        .put("anchor", new JSONObject().put("page", 27)))
                .put(new JSONObject()
                        .put("summary", "A long certified excerpt was safely truncated")
                        .put("excerpt", repeat("hidden raw evidence ", 30))
                        .put("page", 44));
        return report;
    }

    private File findLegalPackRoot() {
        for (String candidate : Arrays.asList(
                "src/main/assets/legal_packs",
                "app/src/main/assets/legal_packs",
                "VerumOmnisV1/app/src/main/assets/legal_packs"
        )) {
            File file = new File(System.getProperty("user.dir"), candidate);
            if (file.isDirectory()) {
                return file;
            }
        }
        throw new IllegalStateException("Unable to locate legal_packs test fixture directory.");
    }

    private String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }
}
