package com.verum.omnis.core;

import java.util.regex.Pattern;

/**
 * Purely presentational classifier that assigns a colour and weight to a report
 * line based on its shape, so the deterministic report text produced by the
 * engine renders in an easy-to-read, colour-coded layout (section headers,
 * findings, evidence anchors, statutes, body).
 *
 * <p>This is a renderer aid only: it never changes report content, wording, or
 * meaning. It merely styles text the engine already produced.</p>
 */
public final class ReportLineStyle {

    public enum Kind { H1, H2, FINDING, SEVERITY, CONTRADICTION, EVIDENCE, STATUTE, BODY }

    // Colours chosen for a white report page.
    private static final int COLOR_H1 = 0xFF0C254D;       // deep blue
    private static final int COLOR_H2 = 0xFF1E5AA8;       // medium blue
    private static final int COLOR_FINDING = 0xFF9A6A00;  // amber
    private static final int COLOR_EVIDENCE = 0xFF1B6B3A; // green
    private static final int COLOR_STATUTE = 0xFF6A1B9A;  // purple
    private static final int COLOR_CONTRADICTION = 0xFF00695C; // teal
    private static final int COLOR_BODY = 0xFF1E1E1E;     // near-black

    // Severity colours by ordinal level.
    private static final int COLOR_SEV_VERY_HIGH = 0xFFB00020; // red
    private static final int COLOR_SEV_HIGH = 0xFFC85A00;      // orange
    private static final int COLOR_SEV_MODERATE = 0xFF9A6A00;  // amber
    private static final int COLOR_SEV_LOW = 0xFF5F6A7A;       // slate

    private static final Pattern SECTION = Pattern.compile("^\\d+\\.\\s+\\S.*");
    private static final Pattern SUBSECTION = Pattern.compile("^\\d+\\.\\d+\\S*\\s+\\S.*");
    private static final Pattern FINDING = Pattern.compile("^(FINDING|CORE FINDING|KEY FINDING)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEVERITY = Pattern.compile("^SEVERITY\\s*[:\\-].*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTRADICTION = Pattern.compile("^Contradiction\\s+C-?\\d+\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVIDENCE = Pattern.compile("^(Evidence|Anchor|Anchored evidence|Source)\\s*[:\\-].*", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUTE = Pattern.compile(
            ".*\\b(Act\\s+\\d+\\s+of\\s+\\d{4}|(Federal\\s+)?(Decree-)?Law\\s+No\\.?\\s+\\d+\\s+of\\s+\\d{4}"
                    + "|Section\\s+\\d+|s\\.?\\s?\\d+\\(|Article\\s+\\d+|POCA|ECT Act|Cybercrimes Act"
                    + "|Companies Law|Petroleum Products Act)\\b.*");

    public final Kind kind;
    public final int colorArgb;
    public final float textSize;
    public final boolean bold;

    private ReportLineStyle(Kind kind, int colorArgb, float textSize, boolean bold) {
        this.kind = kind;
        this.colorArgb = colorArgb;
        this.textSize = textSize;
        this.bold = bold;
    }

    public static ReportLineStyle classify(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return new ReportLineStyle(Kind.BODY, COLOR_BODY, 11f, false);
        }
        if (FINDING.matcher(line).matches()) {
            return new ReportLineStyle(Kind.FINDING, COLOR_FINDING, 12f, true);
        }
        if (SEVERITY.matcher(line).matches()) {
            return new ReportLineStyle(Kind.SEVERITY, severityColor(line), 11f, true);
        }
        if (CONTRADICTION.matcher(line).matches()) {
            return new ReportLineStyle(Kind.CONTRADICTION, COLOR_CONTRADICTION, 11.5f, true);
        }
        if (EVIDENCE.matcher(line).matches()) {
            return new ReportLineStyle(Kind.EVIDENCE, COLOR_EVIDENCE, 11f, false);
        }
        if (SUBSECTION.matcher(line).matches()) {
            return new ReportLineStyle(Kind.H2, COLOR_H2, 13f, true);
        }
        if (SECTION.matcher(line).matches() && line.length() <= 80) {
            return new ReportLineStyle(Kind.H1, COLOR_H1, 15f, true);
        }
        if (isHeadingCaps(line)) {
            return new ReportLineStyle(Kind.H1, COLOR_H1, 14f, true);
        }
        // Statute detection is last so headings/findings win first.
        if (STATUTE.matcher(line).matches()) {
            return new ReportLineStyle(Kind.STATUTE, COLOR_STATUTE, 11f, false);
        }
        return new ReportLineStyle(Kind.BODY, COLOR_BODY, 11f, false);
    }

    private static int severityColor(String line) {
        String upper = line.toUpperCase(java.util.Locale.US);
        if (upper.contains("VERY HIGH")) {
            return COLOR_SEV_VERY_HIGH;
        }
        if (upper.contains("HIGH")) {
            return COLOR_SEV_HIGH;
        }
        if (upper.contains("MODERATE")) {
            return COLOR_SEV_MODERATE;
        }
        return COLOR_SEV_LOW; // LOW / INSUFFICIENT / unspecified
    }

    /** True for short, mostly-uppercase heading lines like "EXECUTIVE SUMMARY". */
    private static boolean isHeadingCaps(String line) {
        if (line.length() < 4 || line.length() > 60) {
            return false;
        }
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        return letters >= 3 && upper >= (int) Math.ceil(letters * 0.85);
    }
}
