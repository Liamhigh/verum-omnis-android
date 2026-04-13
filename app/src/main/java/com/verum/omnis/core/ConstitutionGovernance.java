package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONObject;

public final class ConstitutionGovernance {
    private static final int DEFAULT_QUORUM_MIN = 3;
    private static final String DEFAULT_TIE_BREAKER = "B1_REQUEST_MORE_EVIDENCE";
    private static final String DEFAULT_CONCEALMENT_OUTPUT = "INDETERMINATE DUE TO CONCEALMENT";

    public final int quorumMin;
    public final String tieBreaker;
    public final String concealmentOutput;
    public final boolean deterministic;
    public final boolean rndVoting;
    public final boolean sealedPdfRequired;

    private ConstitutionGovernance(
            int quorumMin,
            String tieBreaker,
            String concealmentOutput,
            boolean deterministic,
            boolean rndVoting,
            boolean sealedPdfRequired
    ) {
        this.quorumMin = quorumMin > 0 ? quorumMin : DEFAULT_QUORUM_MIN;
        this.tieBreaker = safeText(tieBreaker, DEFAULT_TIE_BREAKER);
        this.concealmentOutput = normalizeStatus(safeText(concealmentOutput, DEFAULT_CONCEALMENT_OUTPUT));
        this.deterministic = deterministic;
        this.rndVoting = rndVoting;
        this.sealedPdfRequired = sealedPdfRequired;
    }

    public static ConstitutionGovernance load(Context context) {
        JSONObject constitution = new JSONObject();
        JSONObject brains = new JSONObject();
        try {
            constitution = new JSONObject(RulesProvider.getConstitution(context));
        } catch (Exception ignored) {
        }
        try {
            brains = new JSONObject(RulesProvider.getBrains(context));
        } catch (Exception ignored) {
        }

        JSONObject governance = constitution.optJSONObject("governance");
        JSONObject decisionProtocol = constitution.optJSONObject("decision_protocol");

        int quorumMin = optPositiveInt(brains, "quorum_min",
                optPositiveInt(governance, "quorum_min", DEFAULT_QUORUM_MIN));
        String tieBreaker = safeText(
                brains.optString("tie_breaker", ""),
                decisionProtocol != null ? decisionProtocol.optString("tie_breaker", DEFAULT_TIE_BREAKER) : DEFAULT_TIE_BREAKER
        );
        String concealmentOutput = decisionProtocol != null
                ? safeText(decisionProtocol.optString("concealment_output", ""), DEFAULT_CONCEALMENT_OUTPUT)
                : DEFAULT_CONCEALMENT_OUTPUT;
        boolean deterministic = governance != null && governance.optBoolean("deterministic", true);
        boolean rndVoting = governance != null && governance.optBoolean("r_and_d_voting", false);
        boolean sealedPdfRequired = governance != null && governance.optBoolean("sealed_pdf_required", true);

        return new ConstitutionGovernance(
                quorumMin,
                tieBreaker,
                concealmentOutput,
                deterministic,
                rndVoting,
                sealedPdfRequired
        );
    }

    public JSONObject toJson() {
        JSONObject out = new JSONObject();
        putSafe(out, "quorumMin", quorumMin);
        putSafe(out, "tieBreaker", tieBreaker);
        putSafe(out, "concealmentOutput", concealmentOutput);
        putSafe(out, "deterministic", deterministic);
        putSafe(out, "rndVoting", rndVoting);
        putSafe(out, "sealedPdfRequired", sealedPdfRequired);
        return out;
    }

    private static int optPositiveInt(JSONObject object, String key, int fallback) {
        if (object == null) {
            return fallback;
        }
        int value = object.optInt(key, fallback);
        return value > 0 ? value : fallback;
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeStatus(String value) {
        return value == null ? DEFAULT_CONCEALMENT_OUTPUT : value.trim().replace('_', ' ');
    }

    private static void putSafe(JSONObject object, String key, Object value) {
        if (object == null || key == null) {
            return;
        }
        try {
            object.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
