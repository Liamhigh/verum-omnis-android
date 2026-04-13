package com.verum.omnis.forensic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ExtractedProposition {
    public final String text;
    public final String actor;
    public final String target;
    public final String dateOrRange;
    public final String amount;
    public final String currency;
    public final boolean isNegated;
    public final String confidence;
    public final String category;
    public final String sourceType;
    public final List<EvidenceAnchor> anchors = new ArrayList<>();

    public ExtractedProposition(String text, String actor, String category, String sourceType) {
        this(text, actor, "", "", "", "", false, "", category, sourceType);
    }

    public ExtractedProposition(
            String text,
            String actor,
            String target,
            String dateOrRange,
            String amount,
            String currency,
            boolean isNegated,
            String confidence,
            String category,
            String sourceType
    ) {
        this.text = text;
        this.actor = actor;
        this.target = target;
        this.dateOrRange = dateOrRange;
        this.amount = amount;
        this.currency = currency;
        this.isNegated = isNegated;
        this.confidence = confidence;
        this.category = category;
        this.sourceType = sourceType;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject item = new JSONObject();
        item.put("text", text);
        item.put("actor", actor);
        item.put("target", target);
        item.put("dateOrRange", dateOrRange);
        item.put("amount", amount);
        item.put("currency", currency);
        item.put("isNegated", isNegated);
        item.put("confidence", confidence);
        item.put("category", category);
        item.put("sourceType", sourceType);
        JSONArray anchorArray = new JSONArray();
        for (EvidenceAnchor anchor : anchors) {
            anchorArray.put(anchor.toJson());
        }
        item.put("anchors", anchorArray);
        return item;
    }
}
