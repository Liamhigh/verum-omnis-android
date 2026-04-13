package com.verum.omnis.security;

import android.content.Context;
import android.content.res.AssetManager;

import com.verum.omnis.BuildConfig;
import com.verum.omnis.core.HashUtil;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrityChecker {

    private IntegrityChecker() {}

    /** Run checks and return map: assetPath → status message */
    public static Map<String, String> runChecks(Context ctx) {
        Map<String, String> results = new LinkedHashMap<>();
        try {
            AssetManager am = ctx.getAssets();
            Map<String, String> checks = loadExpectedHashes(am);

            for (Map.Entry<String, String> e : checks.entrySet()) {
                String assetPath = e.getKey();
                String expected = e.getValue();
                try {
                    byte[] data = readAsset(am, assetPath);
                    String actual = HashUtil.sha512(data);
                    if (expected != null && expected.equalsIgnoreCase(actual)) {
                        results.put(assetPath, "✔ OK");
                    } else {
                        if (BuildConfig.DEBUG) {
                            results.put(assetPath, "⚠ Local development asset differs from expected release hash.\nExpected: " +
                                    HashUtil.truncate(expected, 12) +
                                    "...\nActual: " +
                                    HashUtil.truncate(actual, 12) + "...");
                        } else {
                            results.put(assetPath, "❌ Tampered!\nExpected: " +
                                    HashUtil.truncate(expected, 12) +
                                    "...\nActual: " +
                                    HashUtil.truncate(actual, 12) + "...");
                        }
                    }
                } catch (Exception ex) {
                    results.put(assetPath, "⚠ Missing or unreadable (" + ex.getMessage() + ")");
                }
            }
        } catch (Exception ex) {
            results.put("GLOBAL", "Integrity check error: " + ex.getMessage());
        }
        return results;
    }

    private static byte[] readAsset(AssetManager am, String path) throws Exception {
        try (InputStream is = am.open(path)) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = is.read(tmp)) != -1) buffer.write(tmp, 0, n);
            return buffer.toByteArray();
        }
    }

    private static Map<String, String> loadExpectedHashes(AssetManager am) throws Exception {
        Map<String, String> checks = new LinkedHashMap<>();
        try {
            String manifestJson = new String(
                    readAsset(am, "verum_constitution/hash_manifest.json"),
                    StandardCharsets.UTF_8
            );
            JSONObject manifest = new JSONObject(manifestJson);
            Iterator<String> keys = manifest.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                checks.put(key, manifest.optString(key));
            }
        } catch (Exception e) {
            // Manifest might be missing during dev
        }

        // Keep these additional checks until they are also moved into the manifest.
        checks.put("docs/Verum_Omnis_Constitution_Core.pdf",
                ExpectedHashes.TEMPLATES_VERUM_OMNIS_CONSTITUTION_CORE_PDF);
        checks.put("docs/VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320.PDF",
                ExpectedHashes.DOCS_VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320_PDF);
        checks.put("docs/VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320.PDF.ots",
                ExpectedHashes.DOCS_VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320_PDF_OTS);
        return checks;
    }
}
