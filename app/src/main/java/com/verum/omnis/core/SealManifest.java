package com.verum.omnis.core;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The deterministic hash manifest written next to a sealed artifact
 * ({@code <artifact>.verum-seal.json}). It records the SHA-512 and SHA-256 that
 * are computed last, over the complete sealed document, so verification (in-app
 * or on the web) can read "the SHA-512 with everything in it" and so the SHA-256
 * can be anchored to Bitcoin via OpenTimestamps.
 */
public final class SealManifest {

    public static final String SCHEME = "verum-omnis-seal-manifest";
    public static final String VERSION = "1.0";
    public static final String SIDECAR_SUFFIX = ".verum-seal.json";

    public final String artifact;
    public final String sha512;
    public final String sha256;

    public SealManifest(String artifact, String sha512, String sha256) {
        this.artifact = artifact == null ? "" : artifact;
        this.sha512 = sha512 == null ? "" : sha512;
        this.sha256 = sha256 == null ? "" : sha256;
    }

    /** The conventional sidecar file for a sealed artifact ({@code <name>.verum-seal.json}). */
    public static File sidecarFor(File sealedFile) {
        if (sealedFile == null) {
            return null;
        }
        return new File(sealedFile.getParentFile(), sealedFile.getName() + SIDECAR_SUFFIX);
    }

    /**
     * Builds the manifest JSON with a fixed key order so identical inputs always
     * produce byte-identical output (determinism is a constitutional requirement).
     */
    public static String build(String artifactName, String sha512, String sha256) {
        return "{\n"
                + "  \"scheme\": \"" + SCHEME + "\",\n"
                + "  \"version\": \"" + VERSION + "\",\n"
                + "  \"artifact\": " + JSONObject.quote(artifactName == null ? "" : artifactName) + ",\n"
                + "  \"sha512\": \"" + (sha512 == null ? "" : sha512) + "\",\n"
                + "  \"sha256\": \"" + (sha256 == null ? "" : sha256) + "\",\n"
                + "  \"bitcoinAnchorDigestAlgorithm\": \"SHA-256\",\n"
                + "  \"otsSidecar\": " + JSONObject.quote((artifactName == null ? "" : artifactName) + ".ots") + ",\n"
                + "  \"note\": \"SHA-512 and SHA-256 are computed over the complete sealed document. "
                + "Anchor the SHA-256 to Bitcoin with OpenTimestamps and keep the .ots sidecar next to this file.\"\n"
                + "}\n";
    }

    public static SealManifest parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(json);
            if (!SCHEME.equals(obj.optString("scheme", ""))) {
                return null;
            }
            return new SealManifest(
                    obj.optString("artifact", ""),
                    obj.optString("sha512", ""),
                    obj.optString("sha256", "")
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static SealManifest parse(File manifestFile) {
        if (manifestFile == null || !manifestFile.exists() || !manifestFile.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(manifestFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
