package com.verum.omnis.casefile;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScanFolderManager {
    private static final String METADATA_FILE = "scan_folder.json";

    private final Context context;

    public ScanFolderManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public File getScanRootDir() {
        File dir = context.getExternalFilesDir("scans");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "scans");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public List<ScanFolder> listScanFolders() {
        File root = getScanRootDir();
        File[] children = root.listFiles(File::isDirectory);
        if (children == null || children.length == 0) {
            return new ArrayList<>();
        }
        List<File> folders = Arrays.asList(children);
        Collections.sort(folders, Comparator.comparing(File::getName).reversed());
        List<ScanFolder> result = new ArrayList<>();
        for (File folder : folders) {
            result.add(loadScanFolder(folder));
        }
        return collapseRestoredDuplicates(result);
    }

    public ScanFolder createScanFolder(String name) throws Exception {
        String safeName = sanitizeName(name);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.US));
        File dir = new File(getScanRootDir(), stamp + "_" + safeName);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create scan folder: " + dir.getAbsolutePath());
        }
        ScanFolder folder = new ScanFolder();
        folder.setFolderPath(dir.getAbsolutePath());
        folder.setName(name == null || name.trim().isEmpty() ? safeName : name.trim());
        folder.setScanDate(LocalDateTime.now());
        folder.setFilePaths(listFilesRecursive(dir));
        saveScanFolder(folder);
        return folder;
    }

    public boolean validateScanFolder(ScanFolder folder) {
        if (folder == null || folder.getFolderPath() == null || folder.getFolderPath().trim().isEmpty()) {
            return false;
        }
        File dir = new File(folder.getFolderPath());
        return dir.exists() && dir.isDirectory() && !listFilesRecursive(dir).isEmpty();
    }

    public File copyEvidenceIntoScanFolder(File sourceFile, ScanFolder folder) throws Exception {
        if (sourceFile == null || folder == null) {
            throw new IllegalArgumentException("Source file and scan folder are required.");
        }
        File targetDir = new File(folder.getFolderPath(), "evidence");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File target = uniqueFile(targetDir, sourceFile.getName());
        copyFile(sourceFile, target);
        folder.setFilePaths(listFilesRecursive(new File(folder.getFolderPath())));
        saveScanFolder(folder);
        return target;
    }

    public File createArtifactFile(ScanFolder folder, String baseName, String extension) {
        File dir = new File(folder.getFolderPath(), "artifacts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return uniqueFile(dir, sanitizeName(baseName) + extension);
    }

    public void saveScanFolder(ScanFolder folder) throws Exception {
        if (folder == null || folder.getFolderPath() == null) {
            return;
        }
        File dir = new File(folder.getFolderPath());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File metadata = new File(dir, METADATA_FILE);
        try (FileOutputStream fos = new FileOutputStream(metadata)) {
            fos.write(folder.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private ScanFolder loadScanFolder(File dir) {
        File metadata = new File(dir, METADATA_FILE);
        if (metadata.exists()) {
            try (FileInputStream fis = new FileInputStream(metadata)) {
                byte[] data = fis.readAllBytes();
                ScanFolder folder = ScanFolder.fromJson(new JSONObject(new String(data, StandardCharsets.UTF_8)));
                folder.setFolderPath(dir.getAbsolutePath());
                if (folder.getScanDate() == null) {
                    folder.setScanDate(LocalDateTime.now());
                }
                folder.setFilePaths(listFilesRecursive(dir));
                return folder;
            } catch (Exception ignored) {
            }
        }
        ScanFolder folder = new ScanFolder();
        folder.setFolderPath(dir.getAbsolutePath());
        folder.setName(dir.getName());
        folder.setScanDate(LocalDateTime.now());
        folder.setFilePaths(listFilesRecursive(dir));
        return folder;
    }

    private List<ScanFolder> collapseRestoredDuplicates(List<ScanFolder> folders) {
        if (folders == null || folders.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, ScanFolder> restoredByBase = new LinkedHashMap<>();
        for (ScanFolder folder : folders) {
            if (folder == null || !isRestoredScanFolder(folder)) {
                continue;
            }
            restoredByBase.put(normalizedRestoredBaseName(folder), folder);
        }
        List<ScanFolder> filtered = new ArrayList<>();
        for (ScanFolder folder : folders) {
            if (folder == null) {
                continue;
            }
            if (!isRestoredScanFolder(folder)) {
                ScanFolder restored = restoredByBase.get(normalizedRestoredBaseName(folder));
                if (restored != null && shouldHideOriginalFolder(folder, restored)) {
                    continue;
                }
            }
            filtered.add(folder);
        }
        return filtered;
    }

    private boolean shouldHideOriginalFolder(ScanFolder original, ScanFolder restored) {
        if (original == null || restored == null) {
            return false;
        }
        JSONObject originalManifest = loadManifest(original);
        JSONObject restoredManifest = loadManifest(restored);
        if (originalManifest != null && restoredManifest != null) {
            String originalCaseId = originalManifest.optString("sourceCaseId", "").trim();
            String restoredCaseId = restoredManifest.optString("sourceCaseId", "").trim();
            String originalHash = originalManifest.optString("sourceEvidenceHash", "").trim();
            String restoredHash = restoredManifest.optString("sourceEvidenceHash", "").trim();
            if (!originalCaseId.isEmpty() && originalCaseId.equals(restoredCaseId)) {
                return true;
            }
            if (!originalHash.isEmpty() && originalHash.equals(restoredHash)) {
                return true;
            }
        }
        if (original.getScanDate() != null && restored.getScanDate() != null) {
            long minutes = java.time.Duration.between(original.getScanDate(), restored.getScanDate()).abs().toMinutes();
            return minutes <= 15;
        }
        return false;
    }

    private boolean isRestoredScanFolder(ScanFolder folder) {
        if (folder == null || folder.getName() == null) {
            return false;
        }
        if (folder.getName().trim().toLowerCase(Locale.US).endsWith("_restored")) {
            return true;
        }
        JSONObject manifest = loadManifest(folder);
        return manifest != null && manifest.optBoolean("recoveredFromPendingNarrative", false);
    }

    private String normalizedRestoredBaseName(ScanFolder folder) {
        if (folder == null || folder.getName() == null) {
            return "";
        }
        String name = folder.getName().trim();
        if (name.toLowerCase(Locale.US).endsWith("_restored")) {
            name = name.substring(0, name.length() - "_restored".length());
        }
        return sanitizeName(name).toLowerCase(Locale.US);
    }

    private JSONObject loadManifest(ScanFolder folder) {
        if (folder == null || folder.getFolderPath() == null || folder.getFolderPath().trim().isEmpty()) {
            return null;
        }
        File manifest = new File(folder.getFolderPath(), "scan_manifest.json");
        if (!manifest.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(manifest)) {
            byte[] data = fis.readAllBytes();
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> listFilesRecursive(File root) {
        List<String> files = new ArrayList<>();
        if (root == null || !root.exists()) {
            return files;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return files;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(listFilesRecursive(child));
            } else {
                files.add(child.getAbsolutePath());
            }
        }
        Collections.sort(files);
        return files;
    }

    static void copyFile(File source, File target) throws Exception {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static File uniqueFile(File parent, String name) {
        File candidate = new File(parent, name);
        if (!candidate.exists()) {
            return candidate;
        }
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(parent, base + "-" + suffix + ext);
            suffix++;
        }
        return candidate;
    }

    static String sanitizeName(String value) {
        String safe = value == null ? "" : value.trim();
        safe = safe.replaceAll("[^a-zA-Z0-9._-]+", "_");
        safe = safe.replaceAll("_+", "_");
        safe = safe.replaceAll("^_+|_+$", "");
        return safe.isEmpty() ? "scan" : safe;
    }
}
