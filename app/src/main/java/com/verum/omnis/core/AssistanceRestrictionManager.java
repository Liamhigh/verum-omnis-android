package com.verum.omnis.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class AssistanceRestrictionManager {
    private static final String PREFS = "verum_assistance_restriction";
    private static final String KEY_RESTRICTED = "restricted";
    private static final String KEY_REASON = "reason";
    private static final String KEY_CASE_ID = "caseId";
    private static final String KEY_HASH_SHORT = "hashShort";
    private static final String KEY_TRIGGERED_AT = "triggeredAt";
    private static final String LEGACY_REASON_PREFIX =
            "Dishonesty threshold reached after full forensic completion.";

    private AssistanceRestrictionManager() {}

    public static final class Snapshot {
        public final boolean restricted;
        public final String reason;
        public final String caseId;
        public final String evidenceHashShort;
        public final long triggeredAt;

        public Snapshot(boolean restricted, String reason, String caseId, String evidenceHashShort, long triggeredAt) {
            this.restricted = restricted;
            this.reason = reason;
            this.caseId = caseId;
            this.evidenceHashShort = evidenceHashShort;
            this.triggeredAt = triggeredAt;
        }
    }

    public static Snapshot load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean restricted = prefs.getBoolean(KEY_RESTRICTED, false);
        String reason = prefs.getString(KEY_REASON, "");
        if (restricted && reason != null && reason.startsWith(LEGACY_REASON_PREFIX)) {
            clear(context);
            return new Snapshot(false, "", "", "", 0L);
        }
        return new Snapshot(
                restricted,
                reason,
                prefs.getString(KEY_CASE_ID, ""),
                prefs.getString(KEY_HASH_SHORT, ""),
                prefs.getLong(KEY_TRIGGERED_AT, 0L)
        );
    }

    public static Snapshot preview(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return new Snapshot(false, "", "", "", 0L);
        }

        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();

        int contradictions = diagnostics.optInt("contradictions", 0);
        int concealment = diagnostics.optInt("concealment", 0);
        int evasion = diagnostics.optInt("evasion", 0);
        int financial = diagnostics.optInt("financial", 0);
        int namedPartyCount = extraction.optJSONArray("namedParties") != null
                ? extraction.optJSONArray("namedParties").length() : 0;
        int anchoredFindingCount = extraction.optJSONArray("anchoredFindings") != null
                ? extraction.optJSONArray("anchoredFindings").length() : 0;
        int incidentCount = extraction.optJSONArray("incidentRegister") != null
                ? extraction.optJSONArray("incidentRegister").length() : 0;

        boolean fraudulentEvidenceSubject = hasSubject(extraction.optJSONArray("criticalLegalSubjects"), "Fraudulent Evidence");
        boolean directForgeryIncident = hasDirectForgeryIncident(extraction.optJSONArray("incidentRegister"));
        boolean extractionThin = namedPartyCount < 2 || anchoredFindingCount < 3 || incidentCount < 4;
        boolean userDirectedDeception =
                directForgeryIncident
                        && extractionThin
                        && concealment >= 2
                        && (contradictions >= 2 || evasion >= 1)
                        && report.riskScore >= 0.92d;

        userDirectedDeception = userDirectedDeception
                || (fraudulentEvidenceSubject
                && directForgeryIncident
                && extractionThin
                && concealment >= 3
                && report.riskScore >= 0.96d);

        if (!userDirectedDeception) {
            return new Snapshot(false, "", report.caseId, report.evidenceHashShort, 0L);
        }

        String reason = String.format(
                Locale.US,
                "Potential attempt to deceive the forensic system. contradictions=%d, concealment=%d, evasion=%d, financial=%d, directForgeryIndicators=%s, extractionThin=%s.",
                contradictions,
                concealment,
                evasion,
                financial,
                directForgeryIncident ? "yes" : "no",
                extractionThin ? "yes" : "no"
        );
        return new Snapshot(true, reason, report.caseId, report.evidenceHashShort, 0L);
    }

    public static Snapshot persistIfRestricted(Context context, AnalysisEngine.ForensicReport report) {
        Snapshot snapshot = preview(report);
        if (!snapshot.restricted) {
            clear(context);
            return snapshot;
        }

        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RESTRICTED, true)
                .putString(KEY_REASON, snapshot.reason)
                .putString(KEY_CASE_ID, snapshot.caseId)
                .putString(KEY_HASH_SHORT, snapshot.evidenceHashShort)
                .putLong(KEY_TRIGGERED_AT, now)
                .apply();
        return new Snapshot(true, snapshot.reason, snapshot.caseId, snapshot.evidenceHashShort, now);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private static boolean hasSubject(JSONArray subjects, String wanted) {
        if (subjects == null) {
            return false;
        }
        for (int i = 0; i < subjects.length(); i++) {
            JSONObject subject = subjects.optJSONObject(i);
            if (subject == null) continue;
            if (wanted.equalsIgnoreCase(subject.optString("subject", ""))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDirectForgeryIncident(JSONArray incidents) {
        if (incidents == null) {
            return false;
        }
        for (int i = 0; i < incidents.length(); i++) {
            JSONObject incident = incidents.optJSONObject(i);
            if (incident == null) continue;
            String type = incident.optString("incidentType", "").toUpperCase(Locale.US);
            String description = incident.optString("description", "").toLowerCase(Locale.US);
            if (type.contains("TAMPER")
                    || type.contains("FORGERY")
                    || type.contains("SIGNATURE_ZONE_OVERLAY_SUSPECTED")
                    || type.contains("SIGNATURE_MISMATCH")
                    || type.contains("EXECUTION_PAGE_MISMATCH")
                    || description.contains("altered")
                    || description.contains("forg")
                    || description.contains("back-dated")
                    || description.contains("signature mismatch")
                    || description.contains("counterfeit")) {
                return true;
            }
        }
        return false;
    }
}
