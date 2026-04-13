package com.verum.omnis.forensic;

import org.json.JSONException;
import org.json.JSONObject;

public final class EvidenceAnchor {
    public final String evidenceId;
    public final String fileName;
    public final int page;
    public final int lineStart;
    public final int lineEnd;
    public final String messageId;
    public final String timestamp;
    public final String blockId;
    public final long fileOffset;
    public final String exhibitId;
    public final String excerpt;
    public final String type;

    public EvidenceAnchor(String evidenceId, String fileName, int page, String excerpt, String type) {
        this(evidenceId, fileName, page, 0, 0, "", "", "", -1L, "", excerpt, type);
    }

    public EvidenceAnchor(
            String evidenceId,
            String fileName,
            int page,
            int lineStart,
            int lineEnd,
            String messageId,
            String timestamp,
            String blockId,
            long fileOffset,
            String exhibitId,
            String excerpt,
            String type
    ) {
        this.evidenceId = evidenceId;
        this.fileName = fileName;
        this.page = page;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
        this.messageId = messageId;
        this.timestamp = timestamp;
        this.blockId = blockId;
        this.fileOffset = fileOffset;
        this.exhibitId = exhibitId;
        this.excerpt = excerpt;
        this.type = type;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject item = new JSONObject();
        item.put("evidenceId", evidenceId);
        item.put("file", fileName);
        item.put("page", page);
        if (lineStart > 0) {
            item.put("lineStart", lineStart);
        }
        if (lineEnd > 0) {
            item.put("lineEnd", lineEnd);
        }
        if (messageId != null && !messageId.trim().isEmpty()) {
            item.put("messageId", messageId);
        }
        if (timestamp != null && !timestamp.trim().isEmpty()) {
            item.put("timestamp", timestamp);
        }
        if (blockId != null && !blockId.trim().isEmpty()) {
            item.put("blockId", blockId);
        }
        if (fileOffset >= 0L) {
            item.put("fileOffset", fileOffset);
        }
        if (exhibitId != null && !exhibitId.trim().isEmpty()) {
            item.put("exhibitId", exhibitId);
        }
        item.put("excerpt", excerpt);
        item.put("type", type);
        return item;
    }
}
