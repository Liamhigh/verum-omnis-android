package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ForensicSynthesisEngineTest {

    @Test
    public void verifiedContradictionActorIsNotTreatedAsVictimFallback() throws Exception {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        report.diagnostics = new JSONObject()
                .put("contradictionRegister", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "Kevin Lappeman")
                                .put("status", "VERIFIED")
                                .put("confidence", "HIGH")
                                .put("conflictType", "INTER_ACTOR_CONFLICT")
                                .put("summary", "Kevin denied that any exclusivity agreement existed after Marius said Kevin's Export proceeded with the deal.")
                                .put("page", 89)
                                .put("anchors", anchors(89))
                                .put("propositionA", proposition("No exclusivity agreement ever existed", 89))
                                .put("propositionB", proposition("Kevin's Export proceeded with the deal", 215))));
        report.constitutionalExtraction = new JSONObject()
                .put("namedParties", new JSONArray()
                        .put(namedParty("Marius Nortje", "VICTIM"))
                        .put(namedParty("Kevin Lappeman", "VICTIM")))
                .put("propositionRegister", new JSONArray())
                .put("documentIntegrityFindings", new JSONArray())
                .put("timelineAnchorRegister", new JSONArray())
                .put("actorConductRegister", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "Kevin Lappeman")
                                .put("conductType", "CONTRACT_EXECUTION_POSITION")
                                .put("summary", "Kevin's Export proceeded with the deal and then denied the agreement states exclusivity.")
                                .put("page", 215)
                                .put("anchors", anchors(215))))
                .put("financialExposureRegister", new JSONArray());

        JSONObject synthesis = ForensicSynthesisEngine.build(report);

        JSONArray victimActors = synthesis.getJSONArray("victimActors");
        assertEquals(1, victimActors.length());
        assertEquals("Marius Nortje", victimActors.getString(0));

        JSONObject wrongfulActorProfile = synthesis.getJSONObject("wrongfulActorProfile");
        assertEquals("Kevin Lappeman", wrongfulActorProfile.getString("actor"));
        assertTrue(wrongfulActorProfile.getInt("verifiedContradictionCount") > 0);
    }

    private JSONObject namedParty(String name, String role) throws Exception {
        return new JSONObject()
                .put("name", name)
                .put("role", role);
    }

    private JSONObject proposition(String text, int page) throws Exception {
        return new JSONObject()
                .put("text", text)
                .put("anchor", new JSONObject().put("page", page));
    }

    private JSONArray anchors(int page) throws Exception {
        return new JSONArray().put(new JSONObject().put("page", page));
    }
}
