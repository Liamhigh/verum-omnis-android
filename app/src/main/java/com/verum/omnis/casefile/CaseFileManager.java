package com.verum.omnis.casefile;

import android.content.Context;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.HashUtil;
import com.verum.omnis.forensic.ForensicPackageWriter;

import org.json.JSONArray;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CaseFileManager {
    private static final String METADATA_FILE = "case_file.json";

    private final Context context;
    private final NarrativeGenerator narrativeGenerator;
    private final SealingService sealingService;

    public CaseFileManager(Context context) {
        this.context = context.getApplicationContext();
        this.narrativeGenerator = new NarrativeGenerator(this.context);
        this.sealingService = new SealingService(this.context);
    }

    public File getCaseRootDir() {
        File dir = context.getExternalFilesDir("cases");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "cases");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public CaseFile mergeScanFolders(List<ScanFolder> folders, String caseName) throws Exception {
        return mergeScanFolders(folders, caseName, null);
    }

    public CaseFile mergeScanFolders(
            List<ScanFolder> folders,
            String caseName,
            AnalysisEngine.ProgressListener progressListener
    ) throws Exception {
        if (folders == null || folders.isEmpty()) {
            throw new IllegalArgumentException("At least one scan folder is required.");
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.US));
        String folderName = stamp + "_" + ScanFolderManager.sanitizeName(caseName);
        File caseDir = new File(getCaseRootDir(), folderName);
        if (!caseDir.exists() && !caseDir.mkdirs()) {
            throw new IllegalStateException("Could not create case folder: " + caseDir.getAbsolutePath());
        }

        File scansDir = new File(caseDir, "scans");
        if (!scansDir.exists()) {
            scansDir.mkdirs();
        }

        List<File> allEvidenceFiles = new ArrayList<>();
        for (ScanFolder folder : folders) {
            if (folder == null || folder.getFolderPath() == null) {
                continue;
            }
            File sourceDir = new File(folder.getFolderPath());
            if (!sourceDir.exists()) {
                continue;
            }
            File targetDir = new File(scansDir, sourceDir.getName());
            copyFolder(sourceDir, targetDir);
            allEvidenceFiles.addAll(listEvidenceFiles(targetDir));
        }
        if (allEvidenceFiles.isEmpty()) {
            throw new IllegalStateException("No evidence files were found in the selected scan folders.");
        }

        List<File> indexedPdfFiles = new ArrayList<>();
        collectIndexedPdfFiles(scansDir, indexedPdfFiles);
        if (allEvidenceFiles.isEmpty() && !indexedPdfFiles.isEmpty()) {
            allEvidenceFiles.addAll(indexedPdfFiles);
        }
        File manifest = new File(caseDir, "evidence_manifest.json");
        JSONObject manifestJson = writeManifest(manifest, folders, allEvidenceFiles, indexedPdfFiles);

        CaseFile caseFile = new CaseFile();
        caseFile.setCaseId(UUID.randomUUID().toString());
        caseFile.setName(caseName == null || caseName.trim().isEmpty() ? folderName : caseName.trim());
        caseFile.setScanFolders(folders);
        caseFile.setMergedFolderPath(caseDir.getAbsolutePath());
        caseFile.setCreationDate(LocalDateTime.now());
        saveMetadata(caseFile);

        File mergedJson = new File(caseDir, "merged_forensic_report.json");
        try {
            AnalysisEngine.ForensicReport mergedReport = AnalysisEngine.analyzeEvidenceSet(
                    context,
                    allEvidenceFiles,
                    progressListener
            );

            JSONObject payload = ForensicPackageWriter.buildPayload(null, mergedReport, null);
            payload.put("mergedReportCaseId", mergedReport.caseId == null ? "" : mergedReport.caseId);
            payload.put("caseId", caseFile.getCaseId());
            payload.put("caseFile", caseFile.toJson());
            payload.put("sourceScanCount", folders.size());
            payload.put("sourceEvidenceCount", allEvidenceFiles.size());
            payload.put("indexedPdfCount", indexedPdfFiles.size());
            payload.put("evidenceManifest", manifestJson);
            writeMergedPayload(mergedJson, payload);
        } catch (Throwable mergeFailure) {
            JSONObject recoveryPayload = buildRecoveryPayload(
                    caseFile,
                    folders,
                    allEvidenceFiles,
                    indexedPdfFiles,
                    manifestJson,
                    mergeFailure
            );
            writeMergedPayload(mergedJson, recoveryPayload);
        }
        caseFile.setMergedForensicJsonPath(mergedJson.getAbsolutePath());
        saveMetadata(caseFile);
        sealCaseFile(caseFile);
        syncCaseFileArtifacts(caseFile, caseDir, mergedJson);
        updateMergedPayloadCaseFile(mergedJson, caseFile);
        saveMetadata(caseFile);
        return caseFile;
    }

    public CaseFile sealCaseFile(CaseFile caseFile) throws Exception {
        if (caseFile == null) {
            throw new IllegalArgumentException("Case file is required.");
        }
        File mergedJson = new File(caseFile.getMergedForensicJsonPath());
        if (!mergedJson.exists()) {
            throw new IllegalStateException("Merged forensic report not found for case file.");
        }
        try {
            String narrative = narrativeGenerator.generateNarrative(mergedJson);
            sealingService.sealNarrative(narrative, caseFile);
        } catch (Throwable primaryFailure) {
            String emergencyNarrative = buildEmergencyNarrative(mergedJson, caseFile);
            sealingService.sealNarrative(emergencyNarrative, caseFile);
        }
        syncCaseFileArtifacts(caseFile, mergedJson.getParentFile(), mergedJson);
        updateMergedPayloadCaseFile(mergedJson, caseFile);
        saveMetadata(caseFile);
        return caseFile;
    }

    public List<CaseFile> listCaseFiles() {
        File root = getCaseRootDir();
        File[] children = root.listFiles(File::isDirectory);
        if (children == null || children.length == 0) {
            return new ArrayList<>();
        }
        List<File> dirs = Arrays.asList(children);
        Collections.sort(dirs, Comparator.comparing(File::getName).reversed());
        List<CaseFile> files = new ArrayList<>();
        for (File dir : dirs) {
            CaseFile caseFile = loadCaseFile(dir);
            if (caseFile != null) {
                files.add(caseFile);
            }
        }
        return files;
    }

    public void saveMetadata(CaseFile caseFile) throws Exception {
        if (caseFile == null || caseFile.getMergedFolderPath() == null) {
            return;
        }
        File metadata = new File(caseFile.getMergedFolderPath(), METADATA_FILE);
        try (FileOutputStream fos = new FileOutputStream(metadata)) {
            fos.write(caseFile.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private CaseFile loadCaseFile(File dir) {
        File metadata = new File(dir, METADATA_FILE);
        if (metadata.exists()) {
            try (FileInputStream fis = new FileInputStream(metadata)) {
                byte[] data = fis.readAllBytes();
                CaseFile caseFile = CaseFile.fromJson(new JSONObject(new String(data, StandardCharsets.UTF_8)));
                caseFile.setMergedFolderPath(dir.getAbsolutePath());
                syncCaseFileArtifacts(caseFile, dir, new File(dir, "merged_forensic_report.json"));
                restoreScanFoldersFromCaseDirectory(caseFile, dir);
                return caseFile;
            } catch (Exception ignored) {
            }
        }
        File mergedJson = new File(dir, "merged_forensic_report.json");
        if (mergedJson.exists()) {
            try (FileInputStream fis = new FileInputStream(mergedJson)) {
                byte[] data = fis.readAllBytes();
                JSONObject payload = new JSONObject(new String(data, StandardCharsets.UTF_8));
                JSONObject embeddedCaseFile = payload.optJSONObject("caseFile");
                if (embeddedCaseFile != null) {
                    CaseFile caseFile = CaseFile.fromJson(embeddedCaseFile);
                    caseFile.setMergedFolderPath(dir.getAbsolutePath());
                    caseFile.setMergedForensicJsonPath(mergedJson.getAbsolutePath());
                    syncCaseFileArtifacts(caseFile, dir, mergedJson);
                    restoreScanFoldersFromCaseDirectory(caseFile, dir);
                    return caseFile;
                }
            } catch (Exception ignored) {
            }
        }
        CaseFile caseFile = new CaseFile();
        caseFile.setCaseId(HashUtil.truncate(dir.getName(), 24));
        caseFile.setName(dir.getName());
        caseFile.setMergedFolderPath(dir.getAbsolutePath());
        caseFile.setCreationDate(LocalDateTime.now());
        if (mergedJson.exists()) {
            caseFile.setMergedForensicJsonPath(mergedJson.getAbsolutePath());
        }
        syncCaseFileArtifacts(caseFile, dir, mergedJson);
        restoreScanFoldersFromCaseDirectory(caseFile, dir);
        return caseFile;
    }

    private void syncCaseFileArtifacts(CaseFile caseFile, File dir, File mergedJson) {
        if (caseFile == null) {
            return;
        }
        File resolvedDir = dir;
        if (resolvedDir == null && caseFile.getMergedFolderPath() != null && !caseFile.getMergedFolderPath().trim().isEmpty()) {
            resolvedDir = new File(caseFile.getMergedFolderPath());
        }
        if (resolvedDir == null) {
            return;
        }
        caseFile.setMergedFolderPath(resolvedDir.getAbsolutePath());
        if (mergedJson != null && mergedJson.exists()) {
            caseFile.setMergedForensicJsonPath(mergedJson.getAbsolutePath());
        }
        File sealedJson = new File(resolvedDir, "sealed_narrative.json");
        if (sealedJson.exists()) {
            try (FileInputStream fis = new FileInputStream(sealedJson)) {
                JSONObject payload = new JSONObject(new String(fis.readAllBytes(), StandardCharsets.UTF_8));
                String sealedNarrativePath = firstNonBlank(
                        payload.optString("sealedNarrativePath", ""),
                        new File(resolvedDir, "sealed_narrative.pdf").getAbsolutePath()
                );
                if (!sealedNarrativePath.isEmpty()) {
                    caseFile.setSealedNarrativePath(sealedNarrativePath);
                }
                caseFile.setNarrativeHash(firstNonBlank(
                        payload.optString("narrativeHash", ""),
                        caseFile.getNarrativeHash()
                ));
                caseFile.setBlockchainAnchor(firstNonBlank(
                        payload.optString("blockchainAnchor", ""),
                        caseFile.getBlockchainAnchor()
                ));
            } catch (Exception ignored) {
            }
        }
        File sealedBundleJson = new File(resolvedDir, "sealed_case_bundle.json");
        if (sealedBundleJson.exists()) {
            try (FileInputStream fis = new FileInputStream(sealedBundleJson)) {
                JSONObject payload = new JSONObject(new String(fis.readAllBytes(), StandardCharsets.UTF_8));
                String sealedBundlePath = firstNonBlank(
                        payload.optString("sealedBundlePath", ""),
                        new File(resolvedDir, "sealed_case_bundle.pdf").getAbsolutePath()
                );
                if (!sealedBundlePath.isEmpty()) {
                    caseFile.setSealedBundlePath(sealedBundlePath);
                }
            } catch (Exception ignored) {
            }
        }
        File sealedNarrative = new File(resolvedDir, "sealed_narrative.pdf");
        if (sealedNarrative.exists()) {
            caseFile.setSealedNarrativePath(sealedNarrative.getAbsolutePath());
        }
        File sealedBundle = new File(resolvedDir, "sealed_case_bundle.pdf");
        if (sealedBundle.exists()) {
            caseFile.setSealedBundlePath(sealedBundle.getAbsolutePath());
        }
    }

    private void updateMergedPayloadCaseFile(File mergedJson, CaseFile caseFile) {
        if (mergedJson == null || caseFile == null || !mergedJson.exists()) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(mergedJson)) {
            JSONObject payload = new JSONObject(new String(fis.readAllBytes(), StandardCharsets.UTF_8));
            payload.put("caseFile", caseFile.toJson());
            payload.put("sealedBundlePath", caseFile.getSealedBundlePath() == null ? "" : caseFile.getSealedBundlePath());
            payload.put("sealedNarrativePath", caseFile.getSealedNarrativePath() == null ? "" : caseFile.getSealedNarrativePath());
            payload.put("narrativeHash", caseFile.getNarrativeHash() == null ? "" : caseFile.getNarrativeHash());
            payload.put("blockchainAnchor", caseFile.getBlockchainAnchor() == null ? "" : caseFile.getBlockchainAnchor());
            try (FileOutputStream fos = new FileOutputStream(mergedJson)) {
                fos.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private void writeMergedPayload(File mergedJson, JSONObject payload) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(mergedJson)) {
            fos.write(payload.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONObject buildRecoveryPayload(
            CaseFile caseFile,
            List<ScanFolder> folders,
            List<File> evidenceFiles,
            List<File> indexedPdfFiles,
            JSONObject manifestJson,
            Throwable mergeFailure
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("caseId", caseFile.getCaseId());
        payload.put("summary", "Case bundle created in recovery mode after the merged analysis path failed. "
                + "The indexed PDFs were preserved byte-for-byte and should be treated as the primary record.");
        payload.put("mergeRecoveryMode", true);
        payload.put("mergeFailure", mergeFailure == null ? "" : safeFailureMessage(mergeFailure));
        payload.put("caseFile", caseFile.toJson());
        payload.put("sourceScanCount", folders == null ? 0 : folders.size());
        payload.put("sourceEvidenceCount", evidenceFiles == null ? 0 : evidenceFiles.size());
        payload.put("indexedPdfCount", indexedPdfFiles == null ? 0 : indexedPdfFiles.size());
        payload.put("evidenceManifest", manifestJson == null ? new JSONObject() : manifestJson);
        payload.put("certifiedFindings", new JSONArray());
        payload.put("verifiedFindings", new JSONArray());
        payload.put("guardianApprovedFindings", new JSONArray());
        payload.put("topLiabilities", new JSONArray());
        payload.put("legalReferences", new JSONArray());
        return payload;
    }

    private JSONObject writeManifest(
            File manifest,
            List<ScanFolder> folders,
            List<File> evidenceFiles,
            List<File> indexedPdfFiles
    ) throws Exception {
        JSONObject root = new JSONObject();
        root.put("generatedAt", LocalDateTime.now().toString());
        JSONArray scans = new JSONArray();
        for (ScanFolder folder : folders) {
            scans.put(folder != null ? folder.toJson() : new JSONObject());
        }
        JSONArray evidence = new JSONArray();
        for (File file : evidenceFiles) {
            JSONObject item = new JSONObject();
            item.put("name", file.getName());
            item.put("path", file.getAbsolutePath());
            item.put("sizeBytes", file.length());
            try {
                item.put("sha512", HashUtil.sha512File(file));
            } catch (Exception e) {
                item.put("sha512", "HASH_ERROR");
            }
            evidence.put(item);
        }
        root.put("scanFolders", scans);
        root.put("evidenceFiles", evidence);
        JSONArray indexedPdfs = new JSONArray();
        for (File file : indexedPdfFiles) {
            indexedPdfs.put(buildFileManifestItem(file));
        }
        root.put("indexedPdfs", indexedPdfs);
        try (FileOutputStream fos = new FileOutputStream(manifest)) {
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    private void restoreScanFoldersFromCaseDirectory(CaseFile caseFile, File dir) {
        if (caseFile == null || dir == null) {
            return;
        }
        List<ScanFolder> existing = caseFile.getScanFolders();
        if (existing != null && !existing.isEmpty()) {
            return;
        }
        File scansDir = new File(dir, "scans");
        File[] children = scansDir.listFiles(File::isDirectory);
        if (children == null || children.length == 0) {
            return;
        }
        List<ScanFolder> inferred = new ArrayList<>();
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            ScanFolder folder = loadEmbeddedScanFolder(child);
            if (folder == null) {
                folder = new ScanFolder();
                folder.setFolderPath(child.getAbsolutePath());
                folder.setName(child.getName());
                folder.setScanDate(LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(child.lastModified()),
                        java.time.ZoneId.systemDefault()
                ));
                folder.setFilePaths(listContainedFilePaths(child));
            }
            inferred.add(folder);
        }
        caseFile.setScanFolders(inferred);
    }

    private ScanFolder loadEmbeddedScanFolder(File dir) {
        if (dir == null) {
            return null;
        }
        File metadata = new File(dir, "scan_folder.json");
        if (!metadata.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(metadata)) {
            JSONObject object = new JSONObject(new String(fis.readAllBytes(), StandardCharsets.UTF_8));
            ScanFolder folder = ScanFolder.fromJson(object);
            folder.setFolderPath(dir.getAbsolutePath());
            if (folder.getName() == null || folder.getName().trim().isEmpty()) {
                folder.setName(dir.getName());
            }
            return folder;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> listContainedFilePaths(File dir) {
        List<String> paths = new ArrayList<>();
        if (dir == null || !dir.exists()) {
            return paths;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return paths;
        }
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (child.isDirectory()) {
                paths.addAll(listContainedFilePaths(child));
            } else {
                paths.add(child.getAbsolutePath());
            }
        }
        return paths;
    }

    private String safeFailureMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message.trim();
    }

    private void copyFolder(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyFolder(child, new File(target, child.getName()));
            }
            return;
        }
        if (!target.getParentFile().exists()) {
            target.getParentFile().mkdirs();
        }
        ScanFolderManager.copyFile(source, target);
    }

    private List<File> listEvidenceFiles(File root) {
        List<File> strictFiles = new ArrayList<>();
        collectEvidenceFiles(root, strictFiles, false);
        if (!strictFiles.isEmpty()) {
            strictFiles.sort(Comparator.comparing(File::getName));
            return strictFiles;
        }

        List<File> fallbackFiles = new ArrayList<>();
        collectEvidenceFiles(root, fallbackFiles, true);
        List<File> preferredFallback = preferFallbackEvidence(fallbackFiles);
        preferredFallback.sort(Comparator.comparing(File::getName));
        return preferredFallback;
    }

    private void collectEvidenceFiles(File file, List<File> out, boolean allowScanArtifacts) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                collectEvidenceFiles(child, out, allowScanArtifacts);
            }
            return;
        }
        if (isEvidenceFile(file, allowScanArtifacts)) {
            out.add(file);
        }
    }

    private List<File> preferFallbackEvidence(List<File> files) {
        List<File> sealedEvidence = new ArrayList<>();
        List<File> pdfs = new ArrayList<>();
        List<File> remaining = new ArrayList<>();
        for (File file : files) {
            if (file == null) {
                continue;
            }
            String name = file.getName().toLowerCase(Locale.US);
            if (name.contains("sealed-evidence") && name.endsWith(".pdf")) {
                sealedEvidence.add(file);
            } else if (name.endsWith(".pdf")) {
                pdfs.add(file);
            } else {
                remaining.add(file);
            }
        }
        List<File> ordered = new ArrayList<>();
        ordered.addAll(sealedEvidence);
        ordered.addAll(pdfs);
        ordered.addAll(remaining);
        return ordered;
    }

    private void collectIndexedPdfFiles(File file, List<File> out) {
        if (file == null || !file.exists() || out == null) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                collectIndexedPdfFiles(child, out);
            }
            return;
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".pdf") && isManifestSourcePdf(name)) {
            out.add(file);
        }
    }

    private boolean isManifestSourcePdf(String lowerName) {
        if (lowerName == null || !lowerName.endsWith(".pdf")) {
            return false;
        }
        return !lowerName.contains("sealed-evidence")
                && !lowerName.contains("forensic-audit-report")
                && !lowerName.contains("human-forensic-report")
                && !lowerName.contains("readable-forensic-brief")
                && !lowerName.contains("constitutional-vault-report")
                && !lowerName.contains("sealed_narrative")
                && !lowerName.contains("sealed_case_bundle")
                && !lowerName.contains("legal-advisory")
                && !lowerName.contains("visual-findings");
    }

    private JSONObject buildFileManifestItem(File file) throws Exception {
        JSONObject item = new JSONObject();
        item.put("name", file.getName());
        item.put("path", file.getAbsolutePath());
        item.put("sizeBytes", file.length());
        try {
            item.put("sha512", HashUtil.sha512File(file));
        } catch (Exception e) {
            item.put("sha512", "HASH_ERROR");
        }
        return item;
    }

    private String buildEmergencyNarrative(File mergedJson, CaseFile caseFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Case Narrative\n");
        sb.append("This sealed case narrative was generated in recovery mode after the primary case-writer path failed. ");
        sb.append("The indexed PDFs were preserved byte-for-byte inside the case bundle and should be treated as the primary record.\n\n");
        try (FileInputStream fis = new FileInputStream(mergedJson)) {
            JSONObject root = new JSONObject(new String(fis.readAllBytes(), StandardCharsets.UTF_8));
            sb.append("Case ID: ").append(root.optString("caseId", caseFile != null ? caseFile.getCaseId() : "unknown")).append("\n");
            sb.append("Summary: ").append(root.optString("summary", "No merged summary was available.")).append("\n\n");
            JSONObject evidenceManifest = root.optJSONObject("evidenceManifest");
            JSONArray indexedPdfs = evidenceManifest != null ? evidenceManifest.optJSONArray("indexedPdfs") : null;
            sb.append("Indexed PDF Sources\n");
            if (indexedPdfs == null || indexedPdfs.length() == 0) {
                sb.append("- No indexed PDFs were recorded in the manifest.\n");
            } else {
                for (int i = 0; i < indexedPdfs.length() && i < 12; i++) {
                    JSONObject item = indexedPdfs.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    sb.append("- ").append(item.optString("name", "source.pdf"))
                            .append(" | SHA-512 ").append(item.optString("sha512", "HASH_ERROR"))
                            .append("\n");
                }
            }
            sb.append("\nVerified Findings\n");
            JSONArray certifiedFindings = root.optJSONArray("certifiedFindings");
            if (certifiedFindings == null || certifiedFindings.length() == 0) {
                sb.append("- No guardian-approved certified findings were present in the merged record.\n");
            } else {
                for (int i = 0; i < certifiedFindings.length() && i < 8; i++) {
                    JSONObject item = certifiedFindings.optJSONObject(i);
                    JSONObject finding = item != null ? item.optJSONObject("finding") : null;
                    if (finding == null) {
                        continue;
                    }
                    sb.append("- ").append(firstNonBlank(
                            finding.optString("summary", ""),
                            finding.optString("whyItConflicts", ""),
                            finding.optString("excerpt", ""),
                            finding.optString("findingType", "Certified finding")
                    )).append("\n");
                }
            }
            sb.append("\nRecommended Actions\n");
            sb.append("- Use the sealed PDFs and the evidence manifest as the primary court bundle.\n");
            sb.append("- Cross-check any claim against the indexed PDF hashes before disclosure.\n");
        } catch (Exception ignored) {
            sb.append("Recommended Actions\n");
            sb.append("- Review the indexed PDFs in the case bundle directly.\n");
        }
        return sb.toString().trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isEvidenceFile(File file, boolean allowScanArtifacts) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".seal.json")
                || name.endsWith(".legal.json")
                || name.equals(METADATA_FILE)
                || name.equals("scan_folder.json")
                || name.equals("sealed_narrative.json")
                || name.equals("sealed_narrative.txt")
                || name.equals("scan_manifest.json")
                || name.equals("evidence_manifest.json")
                || name.endsWith("-seal-manifest.json")
                || name.equals("human_report_snapshot.txt")
                || name.equals("rnd-mesh.json")) {
            return false;
        }
        if (!allowScanArtifacts && (name.contains("forensic-report")
                || name.contains("forensic-audit-report")
                || name.contains("human-forensic-report")
                || name.contains("readable-forensic-brief")
                || name.contains("constitutional-vault-report")
                || name.contains("sealed-evidence")
                || name.contains("findings-package")
                || name.contains("sealed_narrative")
                || name.contains("visual-findings")
                || name.contains("legal-advisory"))) {
            return false;
        }
        return name.endsWith(".pdf")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".webp")
                || name.endsWith(".mp4")
                || name.endsWith(".mp3")
                || name.endsWith(".wav")
                || name.endsWith(".m4a");
    }
}
