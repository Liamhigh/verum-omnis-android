package com.verum.omnis.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HumanAndVaultReportBuilderTest {

    @Test
    public void humanReportBuilderUsesDistinctHumanFacingFrame() {
        HumanFindingsReportBuilder.Input input = new HumanFindingsReportBuilder.Input();
        input.sourceFileName = "greensky.zip";
        input.sections.add(new HumanFindingsReportBuilder.Section("1. WHAT THIS REPORT IS ABOUT", "This section explains the case."));
        input.sections.add(new HumanFindingsReportBuilder.Section("2. WHAT THE SEALED RECORD CURRENTLY SHOWS", "This section explains the record."));

        String rendered = HumanFindingsReportBuilder.render(input);

        assertTrue(rendered.startsWith("VERUM OMNIS - HUMAN FINDINGS REPORT"));
        assertTrue(rendered.contains("This report is for a human reviewer."));
        assertFalse(rendered.contains("VERUM OMNIS - CONSTITUTIONAL VAULT REPORT"));
        assertFalse(rendered.contains("Case Snapshot"));
    }

    @Test
    public void vaultReportBuilderUsesDistinctArchivalFrame() {
        VaultReportBuilder.Input input = new VaultReportBuilder.Input();
        input.constitutionalVersion = "v5.2.7";
        input.reportType = "Sealed Evidence Analysis";
        input.inputArtifact = "greensky.zip";
        input.reportDateUtc = "2026-04-11";
        input.engineMode = "Offline-first";
        input.sections.add(new VaultReportBuilder.Section("EXECUTIVE SUMMARY", "Formal archival summary."));

        String rendered = VaultReportBuilder.render(input);

        assertTrue(rendered.startsWith("VERUM OMNIS - CONSTITUTIONAL VAULT REPORT"));
        assertTrue(rendered.contains("This archival record preserves the sealed evidence position"));
        assertFalse(rendered.contains("This report is for a human reviewer."));
        assertFalse(rendered.contains("Case Snapshot"));
    }
}
