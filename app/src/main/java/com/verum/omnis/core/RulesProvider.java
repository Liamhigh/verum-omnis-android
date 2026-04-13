package com.verum.omnis.core;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class RulesProvider {
    private RulesProvider() {}

    private static String readAsset(Context ctx, String path) throws Exception {
        try (InputStream is = ctx.getAssets().open(path)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toString("UTF-8");
        }
    }


    public static String getConstitution(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/constitution.json");
    }

    public static String getBrains(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/brains.json");
    }

    public static String getDetectionRules(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/detection_rules.json");
    }

    public static String getSubjectKeywords(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/subject_keywords.json");
    }

    public static String getIntegrityKeywords(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/integrity_keywords.json");
    }

    public static String getJurisdictionPacks(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/jurisdiction_packs.json");
    }

    public static String getBusinessConstitution(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/business_constitution.json");
    }

    public static String getContradictionRules(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/contradiction_rules.json");
    }

    public static String getGemmaPolicy(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/gemma_policy.json");
    }

    public static String getBehaviourPatternLibrary(Context ctx) throws Exception {
        return readAsset(ctx, "verum_constitution/behaviour_pattern_library.json");
    }

    public static String getLegalPackJurisdictionRules(Context ctx) throws Exception {
        return readAsset(ctx, "legal_packs/jurisdiction_rules.json");
    }

    public static String getLegalPackOffenceFrameworks(Context ctx) throws Exception {
        return readAsset(ctx, "legal_packs/offence_frameworks.json");
    }

    public static String getLegalPackInstitutionPlaybooks(Context ctx) throws Exception {
        return readAsset(ctx, "legal_packs/institution_playbooks.json");
    }

    public static String getLegalPackGemmaStyleExamples(Context ctx) throws Exception {
        return readAsset(ctx, "legal_packs/gemma_style_examples.jsonl");
    }

    public static String getLegalPackAsset(Context ctx, String relativePath) throws Exception {
        return readAsset(ctx, "legal_packs/" + relativePath);
    }
}
