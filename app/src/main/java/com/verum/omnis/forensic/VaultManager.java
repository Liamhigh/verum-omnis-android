package com.verum.omnis.forensic;

import android.content.Context;

import com.verum.omnis.core.HashUtil;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class VaultManager {

    private VaultManager() {}

    public static File getVaultDir(Context context) {
        File dir = context.getExternalFilesDir("vault");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "vault");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File createVaultFile(Context context, String prefix, String extension) {
        return createVaultFile(context, prefix, extension, null);
    }

    public static File createVaultFile(
            Context context,
            String artifactLabel,
            String extension,
            String sourceFileName
    ) {
        String label = sanitizeSegment(artifactLabel);
        String source = sanitizeSegment(stripExtension(sourceFileName));
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());

        String baseName = source.isEmpty()
                ? label + "-" + stamp
                : source + "-" + label + "-" + stamp;

        File dir = getVaultDir(context);
        File candidate = new File(dir, baseName + extension);
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(dir, baseName + "-" + suffix + extension);
            suffix++;
        }
        return candidate;
    }

    public static File writeSealManifest(
            Context context,
            File artifactFile,
            String artifactLabel,
            String caseId,
            String sourceEvidenceHash
    ) throws Exception {
        if (artifactFile == null || !artifactFile.exists()) {
            return null;
        }
        String artifactSha512 = HashUtil.sha512File(artifactFile);
        JSONObject manifest = new JSONObject();
        manifest.put("artifactFileName", artifactFile.getName());
        manifest.put("artifactPath", artifactFile.getAbsolutePath());
        manifest.put("artifactLabel", artifactLabel != null ? artifactLabel : "vault-artifact");
        manifest.put("caseId", caseId != null ? caseId : "unknown");
        manifest.put("sealedAtUtc", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));
        manifest.put("artifactSha512", artifactSha512);
        if (sourceEvidenceHash != null && !sourceEvidenceHash.trim().isEmpty()) {
            manifest.put("sourceEvidenceSha512", sourceEvidenceHash.trim());
        }
        manifest.put("verificationHint", "Recompute SHA-512 for the vault artifact and confirm it matches this manifest.");

        File manifestFile = new File(
                artifactFile.getParentFile(),
                stripExtension(artifactFile.getName()) + ".seal.json"
        );
        try (FileOutputStream fos = new FileOutputStream(manifestFile)) {
            fos.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return manifestFile;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    private static String sanitizeSegment(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }
}
