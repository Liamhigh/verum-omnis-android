package com.verum.omnis.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReadableBriefModel {

    public static final class FindingEntry {
        public final String title;
        public final String summary;
        public final String whyItMatters;
        public final List<Integer> anchorPages;

        public FindingEntry(String title, String summary, String whyItMatters, List<Integer> anchorPages) {
            this.title = title == null ? "" : title;
            this.summary = summary == null ? "" : summary;
            this.whyItMatters = whyItMatters == null ? "" : whyItMatters;
            this.anchorPages = Collections.unmodifiableList(copyIntegers(anchorPages));
        }
    }

    public static final class Input {
        public String caseId = "";
        public String jurisdictionName = "";
        public String jurisdictionCode = "";
        public String evidenceHashPrefix = "";
        public int guardianApprovedCertifiedFindingCount;
        public int verifiedContradictionCount;
        public int candidateContradictionCount;
        public String truthSummary = "";
        public String conclusionWhatHappened = "";
        public String conclusionPrimaryImplicatedActor = "";
        public List<String> conclusionWhy = new ArrayList<>();
        public List<String> conclusionTimelineHighlights = new ArrayList<>();
        public List<String> conclusionOtherLinkedActors = new ArrayList<>();
        public List<String> conclusionProven = new ArrayList<>();
        public String conclusionBoundary = "";
        public List<String> conclusionPages = new ArrayList<>();
        public String patternLine = "";
        public String completedHarmLine = "";
        public String patternOriginLine = "";
        public String fallbackSummary = "";
        public boolean suppressRoleNarration;
        public String primaryHarmedParty = "";
        public String actorConclusion = "";
        public String contradictionPosture = "";
        public List<String> otherAffectedParties = new ArrayList<>();
        public List<String> offenceFindings = new ArrayList<>();
        public List<String> behaviouralFindings = new ArrayList<>();
        public List<String> visualFindings = new ArrayList<>();
        public List<FindingEntry> certifiedFindings = new ArrayList<>();
        public List<String> reviewItems = new ArrayList<>();
        public List<String> readFirstPages = new ArrayList<>();
        public List<String> evidencePageHints = new ArrayList<>();
        public List<String> immediateNextActions = new ArrayList<>();
        public String visualExcerpt = "";
        public String auditExcerpt = "";
    }

    public final String caseId;
    public final String jurisdictionName;
    public final String jurisdictionCode;
    public final String evidenceHashPrefix;
    public final int guardianApprovedCertifiedFindingCount;
    public final int verifiedContradictionCount;
    public final int candidateContradictionCount;
    public final String truthSummary;
    public final String conclusionWhatHappened;
    public final String conclusionPrimaryImplicatedActor;
    public final List<String> conclusionWhy;
    public final List<String> conclusionTimelineHighlights;
    public final List<String> conclusionOtherLinkedActors;
    public final List<String> conclusionProven;
    public final String conclusionBoundary;
    public final List<String> conclusionPages;
    public final String patternLine;
    public final String completedHarmLine;
    public final String patternOriginLine;
    public final String fallbackSummary;
    public final boolean suppressRoleNarration;
    public final String primaryHarmedParty;
    public final String actorConclusion;
    public final String contradictionPosture;
    public final List<String> otherAffectedParties;
    public final List<String> offenceFindings;
    public final List<String> behaviouralFindings;
    public final List<String> visualFindings;
    public final List<FindingEntry> certifiedFindings;
    public final List<String> reviewItems;
    public final List<String> readFirstPages;
    public final List<String> evidencePageHints;
    public final List<String> immediateNextActions;
    public final String visualExcerpt;
    public final String auditExcerpt;

    ReadableBriefModel(Input input) {
        this.caseId = input.caseId == null ? "" : input.caseId;
        this.jurisdictionName = input.jurisdictionName == null ? "" : input.jurisdictionName;
        this.jurisdictionCode = input.jurisdictionCode == null ? "" : input.jurisdictionCode;
        this.evidenceHashPrefix = input.evidenceHashPrefix == null ? "" : input.evidenceHashPrefix;
        this.guardianApprovedCertifiedFindingCount = input.guardianApprovedCertifiedFindingCount;
        this.verifiedContradictionCount = input.verifiedContradictionCount;
        this.candidateContradictionCount = input.candidateContradictionCount;
        this.truthSummary = input.truthSummary == null ? "" : input.truthSummary;
        this.conclusionWhatHappened = input.conclusionWhatHappened == null ? "" : input.conclusionWhatHappened;
        this.conclusionPrimaryImplicatedActor = input.conclusionPrimaryImplicatedActor == null ? "" : input.conclusionPrimaryImplicatedActor;
        this.conclusionWhy = Collections.unmodifiableList(copyStrings(input.conclusionWhy));
        this.conclusionTimelineHighlights = Collections.unmodifiableList(copyStrings(input.conclusionTimelineHighlights));
        this.conclusionOtherLinkedActors = Collections.unmodifiableList(copyStrings(input.conclusionOtherLinkedActors));
        this.conclusionProven = Collections.unmodifiableList(copyStrings(input.conclusionProven));
        this.conclusionBoundary = input.conclusionBoundary == null ? "" : input.conclusionBoundary;
        this.conclusionPages = Collections.unmodifiableList(copyStrings(input.conclusionPages));
        this.patternLine = input.patternLine == null ? "" : input.patternLine;
        this.completedHarmLine = input.completedHarmLine == null ? "" : input.completedHarmLine;
        this.patternOriginLine = input.patternOriginLine == null ? "" : input.patternOriginLine;
        this.fallbackSummary = input.fallbackSummary == null ? "" : input.fallbackSummary;
        this.suppressRoleNarration = input.suppressRoleNarration;
        this.primaryHarmedParty = input.primaryHarmedParty == null ? "" : input.primaryHarmedParty;
        this.actorConclusion = input.actorConclusion == null ? "" : input.actorConclusion;
        this.contradictionPosture = input.contradictionPosture == null ? "" : input.contradictionPosture;
        this.otherAffectedParties = Collections.unmodifiableList(copyStrings(input.otherAffectedParties));
        this.offenceFindings = Collections.unmodifiableList(copyStrings(input.offenceFindings));
        this.behaviouralFindings = Collections.unmodifiableList(copyStrings(input.behaviouralFindings));
        this.visualFindings = Collections.unmodifiableList(copyStrings(input.visualFindings));
        this.certifiedFindings = Collections.unmodifiableList(copyFindingEntries(input.certifiedFindings));
        this.reviewItems = Collections.unmodifiableList(copyStrings(input.reviewItems));
        this.readFirstPages = Collections.unmodifiableList(copyStrings(input.readFirstPages));
        this.evidencePageHints = Collections.unmodifiableList(copyStrings(input.evidencePageHints));
        this.immediateNextActions = Collections.unmodifiableList(copyStrings(input.immediateNextActions));
        this.visualExcerpt = input.visualExcerpt == null ? "" : input.visualExcerpt;
        this.auditExcerpt = input.auditExcerpt == null ? "" : input.auditExcerpt;
    }

    private static List<String> copyStrings(List<String> source) {
        List<String> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (String value : source) {
            out.add(value == null ? "" : value);
        }
        return out;
    }

    private static List<Integer> copyIntegers(List<Integer> source) {
        List<Integer> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (Integer value : source) {
            out.add(value == null ? 0 : value);
        }
        return out;
    }

    private static List<FindingEntry> copyFindingEntries(List<FindingEntry> source) {
        List<FindingEntry> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        out.addAll(source);
        return out;
    }
}
