package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class AnchorBoundNarrativeBuilderTest {

    @Test
    public void buildPrefersAnchoredPropositionsAndTimelineHighlights() throws Exception {
        JSONObject forensicConclusion = new JSONObject()
                .put("publicationBoundary", "This is a forensic conclusion, not a judicial verdict.")
                .put("forensicPropositions", new JSONArray()
                        .put(new JSONObject()
                                .put("actor", "All Fuels")
                                .put("conduct", "non-renewal, vacate, or eviction-pressure events")
                                .put("anchorPages", new JSONArray().put(128).put(129).put(130).put(133)))
                        .put(new JSONObject()
                                .put("actor", "Desmond Smith")
                                .put("conduct", "primary evidence that a lease, MOU, or renewal was not countersigned or returned")
                                .put("anchorPages", new JSONArray().put(28).put(48))))
                .put("implicatedActors", new JSONArray()
                        .put(new JSONObject().put("actor", "Desmond Smith").put("role", "AFFECTED_PARTY"))
                        .put(new JSONObject().put("actor", "All Fuels").put("role", "PRIMARY_IMPLICATED")));
        JSONObject truthFrame = new JSONObject()
                .put("whatHappened", "The record shows the same unsigned-document and eviction-pressure pattern recurring across the sealed material.")
                .put("when", new JSONArray()
                        .put("31 Dec: All Fuels is linked to non-renewal, vacate, or eviction-pressure events (pages 128, 129, 130, 133)")
                        .put("31 Jul 2016: Desmond Smith is linked to lease-expiry and forced-out language (pages 28, 48)"));

        AnchorBoundNarrativeBuilder.Narrative narrative =
                AnchorBoundNarrativeBuilder.build(forensicConclusion, truthFrame);

        assertEquals(
                "The record shows the same unsigned-document and eviction-pressure pattern recurring across the sealed material.",
                narrative.summary
        );
        assertEquals(2, narrative.keyFindings.size());
        assertTrue(narrative.keyFindings.get(0).contains("All Fuels is linked in the sealed record to non-renewal"));
        assertTrue(narrative.keyFindings.get(0).contains("Pages: 128, 129, 130, and 133."));
        assertEquals(2, narrative.timelineHighlights.size());
        assertTrue(narrative.implicationSummary.contains("All Fuels"));
        assertTrue(narrative.implicationSummary.contains("Desmond Smith"));
        assertEquals("This is a forensic conclusion, not a judicial verdict.", narrative.proofBoundary);
    }
}
