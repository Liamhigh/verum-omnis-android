package com.verum.omnis.core;

public final class ContradictionReportModel {

    public static final class Input {
        public String constitutionalVersion = "v5.2.7";
        public String reportType = "Direct contradiction-engine output";
        public String inputArtifact = "unknown";
        public String reportDateUtc = "";
        public String engineMode = "Offline-first | Contradiction-first | Deterministic extraction";
        public int verifiedContradictions;
        public int candidateContradictions;
        public String processingStatus = "";
        public String ordinalConfidence = "";
        public String executiveSummary = "";
        public String contradictionPosture = "";
        public String truthSection = "";
        public String evidenceManifest = "";
        public String chainOfCustody = "";
        public String contradictionLedger = "";
        public String anchoredTimeline = "";
        public String nineBrainOutputs = "";
        public String tripleVerification = "";
        public String resolutionGuidance = "";
        public String coverageGaps = "";
        public String sealBlock = "";
        public String limitations = "";
    }

    public final String constitutionalVersion;
    public final String reportType;
    public final String inputArtifact;
    public final String reportDateUtc;
    public final String engineMode;
    public final int verifiedContradictions;
    public final int candidateContradictions;
    public final String processingStatus;
    public final String ordinalConfidence;
    public final String executiveSummary;
    public final String contradictionPosture;
    public final String truthSection;
    public final String evidenceManifest;
    public final String chainOfCustody;
    public final String contradictionLedger;
    public final String anchoredTimeline;
    public final String nineBrainOutputs;
    public final String tripleVerification;
    public final String resolutionGuidance;
    public final String coverageGaps;
    public final String sealBlock;
    public final String limitations;

    ContradictionReportModel(Input input) {
        this.constitutionalVersion = safe(input.constitutionalVersion);
        this.reportType = safe(input.reportType);
        this.inputArtifact = safe(input.inputArtifact);
        this.reportDateUtc = safe(input.reportDateUtc);
        this.engineMode = safe(input.engineMode);
        this.verifiedContradictions = input.verifiedContradictions;
        this.candidateContradictions = input.candidateContradictions;
        this.processingStatus = safe(input.processingStatus);
        this.ordinalConfidence = safe(input.ordinalConfidence);
        this.executiveSummary = safe(input.executiveSummary);
        this.contradictionPosture = safe(input.contradictionPosture);
        this.truthSection = safe(input.truthSection);
        this.evidenceManifest = safe(input.evidenceManifest);
        this.chainOfCustody = safe(input.chainOfCustody);
        this.contradictionLedger = safe(input.contradictionLedger);
        this.anchoredTimeline = safe(input.anchoredTimeline);
        this.nineBrainOutputs = safe(input.nineBrainOutputs);
        this.tripleVerification = safe(input.tripleVerification);
        this.resolutionGuidance = safe(input.resolutionGuidance);
        this.coverageGaps = safe(input.coverageGaps);
        this.sealBlock = safe(input.sealBlock);
        this.limitations = safe(input.limitations);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
