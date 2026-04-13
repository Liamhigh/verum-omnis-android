package com.verum.omnis.core;

import java.util.ArrayList;
import java.util.List;

public final class VaultReportBuilder {

    public static final class Section {
        public String heading = "";
        public String body = "";

        public Section() {}

        public Section(String heading, String body) {
            this.heading = heading;
            this.body = body;
        }
    }

    public static final class Input {
        public String constitutionalVersion = "";
        public String reportType = "";
        public String inputArtifact = "";
        public String reportDateUtc = "";
        public String engineMode = "";
        public List<Section> sections = new ArrayList<>();
    }

    private VaultReportBuilder() {}

    public static String render(Input input) {
        Input safe = input == null ? new Input() : input;
        StringBuilder sb = new StringBuilder();
        sb.append("VERUM OMNIS - CONSTITUTIONAL VAULT REPORT\n");
        if (!safeText(safe.constitutionalVersion).isEmpty()) {
            sb.append("Constitutional Version: ").append(safeText(safe.constitutionalVersion)).append("\n");
        }
        if (!safeText(safe.reportType).isEmpty()) {
            sb.append("Report Type: ").append(safeText(safe.reportType)).append("\n");
        }
        if (!safeText(safe.inputArtifact).isEmpty()) {
            sb.append("Input Artifact: ").append(safeText(safe.inputArtifact)).append("\n");
        }
        if (!safeText(safe.reportDateUtc).isEmpty()) {
            sb.append("Report Date (UTC): ").append(safeText(safe.reportDateUtc)).append("\n");
        }
        if (!safeText(safe.engineMode).isEmpty()) {
            sb.append("Engine Mode: ").append(safeText(safe.engineMode)).append("\n");
        }
        sb.append("This archival record preserves the sealed evidence position, the contradiction ledger, and any downstream non-core handoff material without flattening them into one narrative.\n");
        for (Section section : safe.sections) {
            if (section == null) {
                continue;
            }
            String heading = safeText(section.heading);
            String body = safeBlock(section.body);
            if (heading.isEmpty() || body.isEmpty()) {
                continue;
            }
            sb.append("---\n");
            sb.append(heading).append("\n");
            sb.append(body).append("\n");
        }
        return sb.toString().trim();
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeBlock(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }
}
