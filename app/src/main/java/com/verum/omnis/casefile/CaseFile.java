package com.verum.omnis.casefile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CaseFile {
    private String caseId;
    private String name;
    private List<ScanFolder> scanFolders = new ArrayList<>();
    private String mergedFolderPath;
    private LocalDateTime creationDate;
    private String sealedNarrativePath;
    private String sealedBundlePath;
    private String narrativeHash;
    private String blockchainAnchor;
    private String mergedForensicJsonPath;

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ScanFolder> getScanFolders() {
        return scanFolders;
    }

    public void setScanFolders(List<ScanFolder> scanFolders) {
        this.scanFolders = scanFolders == null ? new ArrayList<>() : new ArrayList<>(scanFolders);
    }

    public String getMergedFolderPath() {
        return mergedFolderPath;
    }

    public void setMergedFolderPath(String mergedFolderPath) {
        this.mergedFolderPath = mergedFolderPath;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getSealedNarrativePath() {
        return sealedNarrativePath;
    }

    public void setSealedNarrativePath(String sealedNarrativePath) {
        this.sealedNarrativePath = sealedNarrativePath;
    }

    public String getSealedBundlePath() {
        return sealedBundlePath;
    }

    public void setSealedBundlePath(String sealedBundlePath) {
        this.sealedBundlePath = sealedBundlePath;
    }

    public String getNarrativeHash() {
        return narrativeHash;
    }

    public void setNarrativeHash(String narrativeHash) {
        this.narrativeHash = narrativeHash;
    }

    public String getBlockchainAnchor() {
        return blockchainAnchor;
    }

    public void setBlockchainAnchor(String blockchainAnchor) {
        this.blockchainAnchor = blockchainAnchor;
    }

    public String getMergedForensicJsonPath() {
        return mergedForensicJsonPath;
    }

    public void setMergedForensicJsonPath(String mergedForensicJsonPath) {
        this.mergedForensicJsonPath = mergedForensicJsonPath;
    }

    public JSONObject toJson() throws Exception {
        JSONObject out = new JSONObject();
        out.put("caseId", caseId == null ? "" : caseId);
        out.put("name", name == null ? "" : name);
        out.put("mergedFolderPath", mergedFolderPath == null ? "" : mergedFolderPath);
        out.put("creationDate", creationDate == null ? "" : creationDate.toString());
        out.put("sealedNarrativePath", sealedNarrativePath == null ? "" : sealedNarrativePath);
        out.put("sealedBundlePath", sealedBundlePath == null ? "" : sealedBundlePath);
        out.put("narrativeHash", narrativeHash == null ? "" : narrativeHash);
        out.put("blockchainAnchor", blockchainAnchor == null ? "" : blockchainAnchor);
        out.put("mergedForensicJsonPath", mergedForensicJsonPath == null ? "" : mergedForensicJsonPath);
        JSONArray scans = new JSONArray();
        for (ScanFolder folder : scanFolders) {
            scans.put(folder != null ? folder.toJson() : new JSONObject());
        }
        out.put("scanFolders", scans);
        return out;
    }

    public static CaseFile fromJson(JSONObject object) {
        CaseFile caseFile = new CaseFile();
        if (object == null) {
            return caseFile;
        }
        caseFile.setCaseId(object.optString("caseId", ""));
        caseFile.setName(object.optString("name", ""));
        caseFile.setMergedFolderPath(object.optString("mergedFolderPath", ""));
        caseFile.setSealedNarrativePath(object.optString("sealedNarrativePath", ""));
        caseFile.setSealedBundlePath(object.optString("sealedBundlePath", ""));
        caseFile.setNarrativeHash(object.optString("narrativeHash", ""));
        caseFile.setBlockchainAnchor(object.optString("blockchainAnchor", ""));
        caseFile.setMergedForensicJsonPath(object.optString("mergedForensicJsonPath", ""));
        String creationDate = object.optString("creationDate", "").trim();
        if (!creationDate.isEmpty()) {
            try {
                caseFile.setCreationDate(LocalDateTime.parse(creationDate));
            } catch (Exception ignored) {
            }
        }
        JSONArray scans = object.optJSONArray("scanFolders");
        List<ScanFolder> folders = new ArrayList<>();
        if (scans != null) {
            for (int i = 0; i < scans.length(); i++) {
                folders.add(ScanFolder.fromJson(scans.optJSONObject(i)));
            }
        }
        caseFile.setScanFolders(folders);
        return caseFile;
    }
}
