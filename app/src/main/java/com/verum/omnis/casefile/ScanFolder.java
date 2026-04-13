package com.verum.omnis.casefile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ScanFolder {
    private String folderPath;
    private String name;
    private LocalDateTime scanDate;
    private List<String> filePaths = new ArrayList<>();

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getScanDate() {
        return scanDate;
    }

    public void setScanDate(LocalDateTime scanDate) {
        this.scanDate = scanDate;
    }

    public List<String> getFilePaths() {
        return filePaths;
    }

    public void setFilePaths(List<String> filePaths) {
        this.filePaths = filePaths == null ? new ArrayList<>() : new ArrayList<>(filePaths);
    }

    public JSONObject toJson() throws Exception {
        JSONObject out = new JSONObject();
        out.put("folderPath", folderPath == null ? "" : folderPath);
        out.put("name", name == null ? "" : name);
        out.put("scanDate", scanDate == null ? "" : scanDate.toString());
        JSONArray files = new JSONArray();
        for (String path : filePaths) {
            files.put(path);
        }
        out.put("filePaths", files);
        return out;
    }

    public static ScanFolder fromJson(JSONObject object) {
        ScanFolder folder = new ScanFolder();
        if (object == null) {
            return folder;
        }
        folder.setFolderPath(object.optString("folderPath", ""));
        folder.setName(object.optString("name", ""));
        String scanDate = object.optString("scanDate", "").trim();
        if (!scanDate.isEmpty()) {
            try {
                folder.setScanDate(LocalDateTime.parse(scanDate));
            } catch (Exception ignored) {
            }
        }
        JSONArray files = object.optJSONArray("filePaths");
        List<String> filePaths = new ArrayList<>();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                String path = files.optString(i, "").trim();
                if (!path.isEmpty()) {
                    filePaths.add(path);
                }
            }
        }
        folder.setFilePaths(filePaths);
        return folder;
    }
}
