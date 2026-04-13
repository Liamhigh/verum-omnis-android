package com.verum.omnis.core;

import java.util.ArrayList;
import java.util.List;

public final class HumanFindingsReportBuilder {

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
        public String sourceFileName = "";
        public List<Section> sections = new ArrayList<>();
    }

    private HumanFindingsReportBuilder() {}

    public static String render(Input input) {
        Input safe = input == null ? new Input() : input;
        StringBuilder sb = new StringBuilder();
        sb.append("VERUM OMNIS - HUMAN FINDINGS REPORT\n");
        if (!safeText(safe.sourceFileName).isEmpty()) {
            sb.append(safeText(safe.sourceFileName)).append("\n");
        }
        sb.append("This report is for a human reviewer. It keeps the findings readable while staying anchored to the sealed audit report, findings package, and vault record.\n\n");
        for (Section section : safe.sections) {
            if (section == null) {
                continue;
            }
            String heading = safeText(section.heading);
            String body = safeBlock(section.body);
            if (heading.isEmpty() || body.isEmpty()) {
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append("\n");
            }
            sb.append(heading).append("\n");
            sb.append(body).append("\n\n");
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
