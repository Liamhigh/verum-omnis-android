package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReportLineStyleTest {

    private ReportLineStyle.Kind kind(String line) {
        return ReportLineStyle.classify(line).kind;
    }

    @Test
    public void classifiesHeadings() {
        assertEquals(ReportLineStyle.Kind.H1, kind("1. EXECUTIVE SUMMARY"));
        assertEquals(ReportLineStyle.Kind.H1, kind("EXECUTIVE SUMMARY"));
        assertEquals(ReportLineStyle.Kind.H2, kind("8.1 Desmond Smith - Umtentweni Service Station (28 Years)"));
    }

    @Test
    public void classifiesFindingsAndEvidence() {
        assertEquals(ReportLineStyle.Kind.FINDING, kind("FINDING 1: SEVEN CONFIRMED VICTIMS"));
        assertEquals(ReportLineStyle.Kind.FINDING, kind("CORE FINDING: THE GOODWILL THEFT MECHANISM"));
        assertEquals(ReportLineStyle.Kind.EVIDENCE, kind("Evidence: Clean Bundle pp.7-8 (Desmond Smith)"));
    }

    @Test
    public void classifiesStatuteAndBody() {
        assertEquals(ReportLineStyle.Kind.STATUTE,
                kind("This satisfies the Prevention of Organised Crime Act 121 of 1998 definition."));
        assertEquals(ReportLineStyle.Kind.BODY, kind("The clause itself is the confession."));
        assertEquals(ReportLineStyle.Kind.BODY, kind(""));
    }
}
