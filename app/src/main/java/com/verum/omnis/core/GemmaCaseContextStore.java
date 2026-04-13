package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class GemmaCaseContextStore {

    public static final class Snapshot {
        public final boolean available;
        public final String caseId;
        public final String sourceFile;
        public final String contextText;

        public Snapshot(boolean available, String caseId, String sourceFile, String contextText) {
            this.available = available;
            this.caseId = caseId;
            this.sourceFile = sourceFile;
            this.contextText = contextText;
        }
    }

    private static final String FILE_NAME = "latest_case_context.json";

    private GemmaCaseContextStore() {}

    public static void save(Context context, String caseId, String sourceFile, String contextText) {
        try {
            JSONObject root = new JSONObject();
            root.put("caseId", caseId);
            root.put("sourceFile", sourceFile);
            root.put("contextText", contextText);
            File file = new File(context.getFilesDir(), FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    public static Snapshot load(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new Snapshot(false, "", "", "");
        }
        try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8.name())) {
            String json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            JSONObject root = new JSONObject(json);
            return new Snapshot(
                    true,
                    root.optString("caseId", ""),
                    root.optString("sourceFile", ""),
                    root.optString("contextText", "")
            );
        } catch (Exception ignored) {
            return new Snapshot(false, "", "", "");
        }
    }
}
