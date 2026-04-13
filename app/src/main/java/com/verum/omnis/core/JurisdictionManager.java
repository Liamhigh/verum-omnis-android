package com.verum.omnis.core;

import android.content.Context;
import android.content.res.Resources;

import com.verum.omnis.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JurisdictionManager {

    public static final class JurisdictionConfig {
        public final String code;
        public final String name;
        public final List<String> legalReferences;
        public final List<String> authorities;
        public final String anchor;

        public JurisdictionConfig(
                String code,
                String name,
                List<String> legalReferences,
                List<String> authorities,
                String anchor
        ) {
            this.code = code;
            this.name = name;
            this.legalReferences = legalReferences;
            this.authorities = authorities;
            this.anchor = anchor;
        }
    }

    private static JurisdictionConfig current;

    private JurisdictionManager() {}

    public static synchronized void initialize(Context context) {
        initialize(context, inferJurisdictionCode());
    }

    public static synchronized void initialize(Context context, String code) {
        current = load(context.getResources(), normalizeCode(code));
    }

    public static synchronized JurisdictionConfig getCurrentJurisdiction(Context context) {
        if (current == null) {
            initialize(context);
        }
        return current;
    }

    public static synchronized JurisdictionConfig getJurisdiction(Context context, String code) {
        if (context == null) {
            return getCurrentJurisdiction();
        }
        return load(context.getResources(), normalizeCode(code));
    }

    public static synchronized JurisdictionConfig getCurrentJurisdiction() {
        if (current == null) {
            current = new JurisdictionConfig(
                    "ZAF",
                    "South Africa",
                    new ArrayList<>(),
                    new ArrayList<>(),
                    "local-only"
            );
        }
        return current;
    }

    public static synchronized String getCurrentJurisdictionCode(Context context) {
        return getCurrentJurisdiction(context).code;
    }

    public static synchronized String getCurrentJurisdictionCode() {
        return getCurrentJurisdiction().code;
    }

    private static JurisdictionConfig load(Resources resources, String code) {
        if ("MULTI".equals(code)) {
            JurisdictionConfig zaf = load(resources, "ZAF");
            JurisdictionConfig uae = load(resources, "UAE");
            List<String> legalReferences = new ArrayList<>();
            legalReferences.addAll(zaf.legalReferences);
            for (String reference : uae.legalReferences) {
                if (!legalReferences.contains(reference)) {
                    legalReferences.add(reference);
                }
            }
            List<String> authorities = new ArrayList<>();
            authorities.addAll(zaf.authorities);
            for (String authority : uae.authorities) {
                if (!authorities.contains(authority)) {
                    authorities.add(authority);
                }
            }
            return new JurisdictionConfig(
                    "MULTI",
                    readableName("MULTI"),
                    legalReferences,
                    authorities,
                    "composite:UAE+ZAF"
            );
        }
        int resId;
        switch (code) {
            case "UAE":
                resId = R.raw.uae_jurisdiction;
                break;
            case "EU":
                resId = R.raw.eu_jurisdiction;
                break;
            case "ZAF":
            default:
                resId = R.raw.sa_jurisdiction;
                break;
        }

        try (InputStream inputStream = resources.openRawResource(resId);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int read;
            while ((read = inputStream.read(data)) != -1) {
                buffer.write(data, 0, read);
            }

            JSONObject json = new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
            return new JurisdictionConfig(
                    json.optString("code", code),
                    json.optString("name", readableName(code)),
                    jsonArrayToList(json.optJSONArray("legalReferences"), json.optJSONArray("rules")),
                    jsonArrayToList(json.optJSONArray("authorities"), null),
                    json.optString("anchor", "local-only")
            );
        } catch (Exception e) {
            return new JurisdictionConfig(
                    code,
                    readableName(code),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    "local-only"
            );
        }
    }

    private static List<String> jsonArrayToList(JSONArray primary, JSONArray fallback) {
        JSONArray source = primary != null ? primary : fallback;
        List<String> values = new ArrayList<>();
        if (source == null) {
            return values;
        }
        for (int i = 0; i < source.length(); i++) {
            String value = source.optString(i, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static String inferJurisdictionCode() {
        String country = Locale.getDefault().getCountry().toUpperCase(Locale.ROOT);
        if ("AE".equals(country)) {
            return "UAE";
        }
        if (country.matches("AT|BE|BG|HR|CY|CZ|DK|EE|FI|FR|DE|GR|HU|IE|IT|LV|LT|LU|MT|NL|PL|PT|RO|SK|SI|ES|SE")) {
            return "EU";
        }
        return "ZAF";
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return "ZAF";
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(normalized)) {
            return "ZAF";
        }
        if ("MULTI_JURISDICTION".equals(normalized)) {
            return "MULTI";
        }
        return normalized;
    }

    private static String readableName(String code) {
        switch (normalizeCode(code)) {
            case "MULTI":
                return "Multi-jurisdiction (UAE and South Africa)";
            case "UAE":
                return "United Arab Emirates";
            case "EU":
                return "European Union";
            case "ZAF":
            default:
                return "South Africa";
        }
    }
}
