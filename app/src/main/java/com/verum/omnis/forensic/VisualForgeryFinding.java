package com.verum.omnis.forensic;

public final class VisualForgeryFinding {
    public final int pageIndex;
    public final String type;
    public final String severity;
    public final String description;
    public final String region;

    public VisualForgeryFinding(int pageIndex, String type, String severity, String description) {
        this(pageIndex, type, severity, description, "full-page");
    }

    public VisualForgeryFinding(int pageIndex, String type, String severity, String description, String region) {
        this.pageIndex = pageIndex;
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.region = region;
    }
}
