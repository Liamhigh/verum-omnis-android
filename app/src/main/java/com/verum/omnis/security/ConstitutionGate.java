package com.verum.omnis.security;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConstitutionGate {
    public static class VerificationResult {
        public final boolean ok;
        public final Map<String, String> details;
        public VerificationResult(boolean ok, Map<String, String> details) {
            this.ok = ok;
            this.details = details;
        }
    }

    private static String sha512(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        byte[] d = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] load(Context ctx, String path) throws Exception {
        try (InputStream is = ctx.getAssets().open(path);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }


    public static VerificationResult verifyAll(Context ctx) {
        Map<String, String> details = new LinkedHashMap<>();
        try {
            String manifest = new String(load(ctx, "verum_constitution/hash_manifest.json"));
            Pattern rx = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([0-9a-fA-F]{128})\"");
            Matcher m = rx.matcher(manifest);
            Map<String, String> expected = new LinkedHashMap<>();
            while (m.find()) expected.put(m.group(1), m.group(2));

            boolean ok = true;
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String rel = e.getKey();
                String want = e.getValue();
                String got = sha512(load(ctx, rel));
                details.put(rel, got);
                if (!got.equalsIgnoreCase(want)) ok = false;
            }
            return new VerificationResult(ok, details);
        } catch (Exception ex) {
            details.put("error", ex.getMessage() == null ? "unknown" : ex.getMessage());
            return new VerificationResult(false, details);
        }
    }
}