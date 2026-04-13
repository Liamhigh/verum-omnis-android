package com.verum.omnis.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuiltGate {

    public static final class Result {
        public boolean allowed;
        public String reason = "";
        public List<String> missingElements = new ArrayList<>();
    }

    private GuiltGate() {}

    public static Result evaluate(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            String primaryImplicatedActor
    ) {
        Result out = new Result();
        if (report == null || assembled == null) {
            out.reason = "The engine cannot publish a final guilt conclusion because the core report object was incomplete.";
            out.missingElements.add("Complete report context");
            return out;
        }

        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
        boolean guardianApproved = report.guardianDecision != null
                && report.guardianDecision.optBoolean("approved", false);
        boolean concealmentFlag = diagnostics.optBoolean("indeterminateDueToConcealment", false);
        boolean hasJurisdiction = !trimToEmpty(report.jurisdiction).isEmpty()
                && !trimToEmpty(report.jurisdictionName).isEmpty();
        boolean hasVerifiedContradiction = assembled.verifiedContradictionCount > 0;
        boolean hasCertifiedAnchoredPattern = assembled.guardianApprovedCertifiedFindingCount > 0
                && !assembled.issueGroups.isEmpty();
        boolean hasPrimaryActor = !trimToEmpty(primaryImplicatedActor).isEmpty();
        boolean hasOffenceSupport = !assembled.directOffenceFindings.isEmpty();

        if (!hasVerifiedContradiction) {
            out.missingElements.add("Verified paired contradiction threshold");
        }
        if (!hasCertifiedAnchoredPattern) {
            out.missingElements.add("Guardian-approved certified fact pattern");
        }
        if (!hasPrimaryActor) {
            out.missingElements.add("Clean primary implicated actor anchor");
        }
        if (!hasOffenceSupport) {
            out.missingElements.add("Conduct category supported by primary evidence");
        }
        if (concealmentFlag) {
            out.missingElements.add("Unresolved concealment or exclusion flags");
        }
        if (!guardianApproved) {
            out.missingElements.add("Guardian approval for adjudicative publication");
        }
        if (!hasJurisdiction) {
            out.missingElements.add("Jurisdiction mapping for adjudicative publication");
        }

        out.allowed = out.missingElements.isEmpty();
        if (out.allowed) {
            out.reason = "The engine can publish guilt-ready language because the contradiction threshold, actor linkage, guardian approval, and jurisdiction gate all passed in the same run.";
        } else {
            out.reason = "The engine cannot yet publish a final guilt conclusion because "
                    + joinMissing(out.missingElements)
                    + ".";
        }
        return out;
    }

    private static String joinMissing(List<String> missing) {
        if (missing == null || missing.isEmpty()) {
            return "the required proof object was incomplete";
        }
        if (missing.size() == 1) {
            return lowerUs(missing.get(0));
        }
        if (missing.size() == 2) {
            return lowerUs(missing.get(0)) + " and " + lowerUs(missing.get(1));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                sb.append(i == missing.size() - 1 ? ", and " : ", ");
            }
            sb.append(lowerUs(missing.get(i)));
        }
        return sb.toString();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lowerUs(String value) {
        return trimToEmpty(value).toLowerCase(Locale.US);
    }
}
