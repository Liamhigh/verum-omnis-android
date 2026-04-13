package com.verum.omnis.rules;

import android.content.Context;
import android.content.res.AssetManager;
import com.verum.omnis.R;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class RulesVerifier {

    public static class Result {
        public boolean ok;
        public String reason;
        public byte[] rulesJson;
        public int version;
    }

    private static final int MIN_ALLOWED_VERSION = 1; // anti-rollback baseline

    public static Result verifyAndLoad(Context ctx) {
        Result res = new Result();
        try {
            // 1) Load rules pack (sealed blob)
            AssetManager am = ctx.getAssets();
            byte[] rulesPack = readAllBytes(am.open("rules.vop"));

            // 2) Load public key

            InputStream keyStream = ctx.getResources().openRawResource(R.raw.public_key);

            byte[] keyBytes = parsePemPublicKey(readAllBytes(keyStream));

            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            PublicKey pubKey = KeyFactory.getInstance("RSA").generatePublic(spec);

            // 3) Verify signature (very simplified — assumes [data|sig] split)
            int sigLen = 256; // adjust to actual key size (bytes)
            if (rulesPack.length <= sigLen) {
                res.ok = false;
                res.reason = "Invalid rules pack (too small)";
                return res;
            }
            byte[] data = Arrays.copyOfRange(rulesPack, 0, rulesPack.length - sigLen);
            byte[] sig = Arrays.copyOfRange(rulesPack, rulesPack.length - sigLen, rulesPack.length);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(pubKey);
            verifier.update(data);
            boolean valid = verifier.verify(sig);
            if (!valid) {
                res.ok = false;
                res.reason = "Signature invalid";
                return res;
            }

            // 4) Extract payload (here just treat "data" as rules JSON for demo)
            res.rulesJson = data;
            res.version = 1; // TODO: parse version from JSON if embedded

            if (res.version < MIN_ALLOWED_VERSION) {
                res.ok = false;
                res.reason = "Rules version too old";
                return res;
            }

            res.ok = true;
            res.reason = "OK";
            return res;

        } catch (Exception e) {
            res.ok = false;
            res.reason = "Verifier error: " + e.getMessage();
            return res;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        try (in) {
            byte[] buf = new byte[8192];
            int len;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            while ((len = in.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        }
    }

    private static byte[] parsePemPublicKey(byte[] raw) {
        String text = new String(raw, StandardCharsets.UTF_8).trim();
        if (!text.startsWith("-----BEGIN")) {
            return raw;
        }
        String normalized = text
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(normalized);
    }
}
