package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PromotionDecision {
    public final boolean promoted;
    public final String reason;
    public final List<String> failedRules;

    public PromotionDecision(boolean promoted, String reason, List<String> failedRules) {
        this.promoted = promoted;
        this.reason = reason;
        this.failedRules = failedRules == null ? new ArrayList<>() : failedRules;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("promoted", promoted);
        out.put("reason", reason);
        JSONArray failed = new JSONArray();
        for (String rule : failedRules) {
            failed.put(rule);
        }
        out.put("failedRules", failed);
        return out;
    }
}
