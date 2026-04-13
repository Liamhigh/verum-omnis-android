package com.verum.omnis.core;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class Phi3ModelPackManager {

    private Phi3ModelPackManager() {}

    public static File getExpectedFile(Context context) {
        LocalAiModelRegistry.ModelDescriptor descriptor =
                LocalAiModelRegistry.get(LocalAiModelRegistry.MODEL_PHI3_MINI);
        return new File(LocalAiModelRegistry.getExternalModelDirectory(context.getApplicationContext()), descriptor.fileName);
    }

    public static boolean isStaged(Context context) {
        File file = getExpectedFile(context);
        return file.exists() && file.length() > 0L;
    }

    public static File stageFromFile(Context context, File sourceFile) throws Exception {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() <= 0L) {
            throw new IllegalArgumentException("Phi-3 source file was missing or empty.");
        }
        String lowerName = sourceFile.getName().toLowerCase();
        if (!lowerName.endsWith(".gguf")) {
            throw new IllegalArgumentException("Phi-3 source file must be a GGUF model.");
        }
        File target = getExpectedFile(context);
        copy(sourceFile, target);
        return target;
    }

    public static String describeStaging(Context context) {
        File target = getExpectedFile(context);
        if (target.exists() && target.length() > 0L) {
            return "Phi-3 Mini GGUF is staged at " + target.getAbsolutePath();
        }
        return "Phi-3 Mini GGUF is not staged yet. Expected path: " + target.getAbsolutePath();
    }

    private static void copy(File sourceFile, File targetFile) throws Exception {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(sourceFile);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }
}
