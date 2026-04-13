package com.verum.omnis.casefile;

import android.content.Context;

import com.verum.omnis.core.PDFSealer;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.verum.omnis.forensic.VaultManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SealingService {
    private final Context context;

    public SealingService(Context context) {
        this.context = context.getApplicationContext();
    }

    public String computeHash(byte[] data) throws Exception {
        return com.verum.omnis.core.HashUtil.sha512(data);
    }

    public String generateBlockchainAnchor(String hash) throws Exception {
        File anchorRoot = context.getExternalFilesDir("cases");
        if (anchorRoot == null) {
            anchorRoot = new File(context.getFilesDir(), "cases");
        }
        if (!anchorRoot.exists()) {
            anchorRoot.mkdirs();
        }
        File anchorFile = new File(anchorRoot, "anchors.txt");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date());
        String anchor = "local-anchor:" + timestamp + ":" + hash.substring(0, Math.min(24, hash.length()));
        try (FileOutputStream fos = new FileOutputStream(anchorFile, true)) {
            fos.write((timestamp + " " + hash + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        }
        return anchor;
    }

    public void sealNarrative(String narrativeText, CaseFile caseFile) throws Exception {
        if (caseFile == null || caseFile.getMergedFolderPath() == null) {
            throw new IllegalArgumentException("Case file and merged folder path are required.");
        }
        File caseDir = new File(caseFile.getMergedFolderPath());
        if (!caseDir.exists()) {
            caseDir.mkdirs();
        }
        byte[] bytes = narrativeText.getBytes(StandardCharsets.UTF_8);
        String hash = computeHash(bytes);
        String anchor = generateBlockchainAnchor(hash);
        String sealedText = narrativeText
                + "\n\n---\nSHA-512 Hash: " + hash
                + "\nBlockchain Anchor: " + anchor;

        File txtFile = new File(caseDir, "sealed_narrative.txt");
        try (FileOutputStream fos = new FileOutputStream(txtFile)) {
            fos.write(sealedText.getBytes(StandardCharsets.UTF_8));
        }

        File pdfFile = new File(caseDir, "sealed_narrative.pdf");
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "Verum Omnis Sealed Case Narrative";
        req.summary = "Merged case narrative generated from the sealed forensic case file.";
        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
        req.caseId = caseFile.getCaseId();
        req.evidenceHash = hash;
        req.sourceFileName = caseFile.getName();
        req.bodyText = sealedText;
        req.legalSummary = "Case narrative sealed after case-file generation.";
        PDFSealer.generateSealedPdf(context, req, pdfFile);
        VaultManager.writeSealManifest(context, pdfFile, "case-narrative", caseFile.getCaseId(), hash);

        File jsonFile = new File(caseDir, "sealed_narrative.json");
        JSONObject narrativeJson = new JSONObject();
        narrativeJson.put("caseId", caseFile.getCaseId());
        narrativeJson.put("sealedNarrativePath", pdfFile.getAbsolutePath());
        narrativeJson.put("narrativeTextPath", txtFile.getAbsolutePath());
        narrativeJson.put("narrativeHash", hash);
        narrativeJson.put("blockchainAnchor", anchor);
        narrativeJson.put("sealedAtUtc", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));
        try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
            fos.write(narrativeJson.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        File bundleFile = new File(caseDir, "sealed_case_bundle.pdf");
        int preservedPdfCount = buildSealedCaseBundle(caseFile, pdfFile, bundleFile);
        String bundleHash = com.verum.omnis.core.HashUtil.sha512File(bundleFile);
        String bundleAnchor = generateBlockchainAnchor(bundleHash);
        VaultManager.writeSealManifest(context, bundleFile, "case-bundle", caseFile.getCaseId(), bundleHash);

        File bundleJsonFile = new File(caseDir, "sealed_case_bundle.json");
        JSONObject bundleJson = new JSONObject();
        bundleJson.put("caseId", caseFile.getCaseId());
        bundleJson.put("sealedBundlePath", bundleFile.getAbsolutePath());
        bundleJson.put("bundleHash", bundleHash);
        bundleJson.put("bundleAnchor", bundleAnchor);
        bundleJson.put("preservedPdfCount", preservedPdfCount);
        bundleJson.put("sealedNarrativePath", pdfFile.getAbsolutePath());
        bundleJson.put("sealedAtUtc", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));
        try (FileOutputStream fos = new FileOutputStream(bundleJsonFile)) {
            fos.write(bundleJson.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        caseFile.setSealedNarrativePath(pdfFile.getAbsolutePath());
        caseFile.setSealedBundlePath(bundleFile.getAbsolutePath());
        caseFile.setNarrativeHash(hash);
        caseFile.setBlockchainAnchor(anchor);
    }

    private int buildSealedCaseBundle(CaseFile caseFile, File narrativePdf, File bundleFile) throws Exception {
        if (caseFile == null || narrativePdf == null || bundleFile == null) {
            throw new IllegalArgumentException("Case file, narrative PDF, and bundle file are required.");
        }
        PDFBoxResourceLoader.init(context.getApplicationContext());
        List<File> preservedPdfs = collectBundleSourcePdfs(caseFile);
        try (PDDocument bundle = new PDDocument()) {
            appendPdf(bundle, narrativePdf);
            for (File preservedPdf : preservedPdfs) {
                appendPdf(bundle, preservedPdf);
            }
            try (FileOutputStream fos = new FileOutputStream(bundleFile)) {
                bundle.save(fos);
            }
        }
        return preservedPdfs.size();
    }

    private List<File> collectBundleSourcePdfs(CaseFile caseFile) {
        List<File> files = new ArrayList<>();
        if (caseFile == null) {
            return files;
        }
        if (caseFile.getMergedForensicJsonPath() != null && !caseFile.getMergedForensicJsonPath().trim().isEmpty()) {
            File mergedJson = new File(caseFile.getMergedForensicJsonPath());
            if (mergedJson.exists()) {
                try (FileInputStream fis = new FileInputStream(mergedJson)) {
                    JSONObject payload = new JSONObject(new String(fis.readAllBytes(), StandardCharsets.UTF_8));
                    JSONObject manifest = payload.optJSONObject("evidenceManifest");
                    JSONArray indexedPdfs = manifest != null ? manifest.optJSONArray("indexedPdfs") : null;
                    if (indexedPdfs != null) {
                        for (int i = 0; i < indexedPdfs.length(); i++) {
                            JSONObject item = indexedPdfs.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }
                            File candidate = new File(item.optString("path", ""));
                            if (isBundleSourcePdf(candidate)) {
                                addUnique(files, candidate);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (files.isEmpty() && caseFile.getMergedFolderPath() != null && !caseFile.getMergedFolderPath().trim().isEmpty()) {
            collectBundleSourcePdfs(new File(caseFile.getMergedFolderPath(), "scans"), files);
        }
        files.sort(Comparator.comparing(file -> file.getAbsolutePath().toLowerCase(Locale.US)));
        return files;
    }

    private void collectBundleSourcePdfs(File root, List<File> files) {
        if (root == null || files == null || !root.exists()) {
            return;
        }
        if (root.isDirectory()) {
            File[] children = root.listFiles();
            if (children == null) {
                return;
            }
            List<File> ordered = new ArrayList<>();
            for (File child : children) {
                ordered.add(child);
            }
            ordered.sort(Comparator.comparing(file -> file.getName().toLowerCase(Locale.US)));
            for (File child : ordered) {
                collectBundleSourcePdfs(child, files);
            }
            return;
        }
        if (isBundleSourcePdf(root)) {
            addUnique(files, root);
        }
    }

    private boolean isBundleSourcePdf(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.US);
        if (!name.endsWith(".pdf")) {
            return false;
        }
        return !name.equals("sealed_narrative.pdf")
                && !name.equals("sealed_case_bundle.pdf");
    }

    private void addUnique(List<File> files, File file) {
        if (files == null || file == null) {
            return;
        }
        for (File existing : files) {
            if (existing.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                return;
            }
        }
        files.add(file);
    }

    private void appendPdf(PDDocument destination, File sourceFile) throws Exception {
        if (destination == null || sourceFile == null || !sourceFile.exists() || sourceFile.isDirectory()) {
            return;
        }
        try (PDDocument source = PDDocument.load(sourceFile)) {
            for (int i = 0; i < source.getNumberOfPages(); i++) {
                destination.importPage(source.getPage(i));
            }
        }
    }
}
