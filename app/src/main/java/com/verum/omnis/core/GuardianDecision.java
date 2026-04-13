package com.verum.omnis.core;

import org.json.JSONException;
import org.json.JSONObject;

public class GuardianDecision {
    public final boolean approved;
    public final String reason;

    public GuardianDecision(boolean approved, String reason) {
        this.approved = approved;
        this.reason = reason;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("approved", approved);
        out.put("reason", reason);
        return out;
    }
}
