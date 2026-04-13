package com.verum.omnis.core;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LocalAiModelRegistry {

    public enum Backend {
        MEDIAPIPE_TASK,
        EXTERNAL_GGUF
    }

    public static final String MODEL_GEMMA_3_1B = "gemma-3-1b-it-int4";
    public static final String MODEL_PHI3_MINI = "phi3-mini-4k-instruct";

    public static final class ModelDescriptor {
        public final String id;
        public final String displayName;
        public final String fileName;
        public final String assetPath;
        public final Backend backend;
        public final int promptBudgetChars;
        public final int sessionTopK;
        public final float sessionTemperature;
        public final int inferenceMaxTopK;
        public final boolean bundledByDefault;
        public final String installHint;

        ModelDescriptor(
                String id,
                String displayName,
                String fileName,
                String assetPath,
                Backend backend,
                int promptBudgetChars,
                int sessionTopK,
                float sessionTemperature,
                int inferenceMaxTopK,
                boolean bundledByDefault,
                String installHint
        ) {
            this.id = id;
            this.displayName = displayName;
            this.fileName = fileName;
            this.assetPath = assetPath;
            this.backend = backend;
            this.promptBudgetChars = promptBudgetChars;
            this.sessionTopK = sessionTopK;
            this.sessionTemperature = sessionTemperature;
            this.inferenceMaxTopK = inferenceMaxTopK;
            this.bundledByDefault = bundledByDefault;
            this.installHint = installHint;
        }
    }

    private static final Map<String, ModelDescriptor> DESCRIPTORS = buildDescriptors();

    private LocalAiModelRegistry() {}

    private static Map<String, ModelDescriptor> buildDescriptors() {
        LinkedHashMap<String, ModelDescriptor> map = new LinkedHashMap<>();
        register(map, new ModelDescriptor(
                MODEL_GEMMA_3_1B,
                "Gemma 3 1B Instruct",
                "gemma3-1B-it-int4.task",
                "model/gemma3-1B-it-int4.task",
                Backend.MEDIAPIPE_TASK,
                1200,
                40,
                0.25f,
                64,
                true,
                "Bundled with the app."
        ));
        register(map, new ModelDescriptor(
                MODEL_PHI3_MINI,
                "Phi-3 Mini 4K Instruct",
                "Phi-3-mini-4k-instruct-q4.gguf",
                null,
                Backend.EXTERNAL_GGUF,
                6500,
                40,
                0.20f,
                64,
                false,
                "Place the GGUF file in the app's external models folder. This keeps the 2.2 GB model outside the APK."
        ));
        return Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, ModelDescriptor> map, ModelDescriptor descriptor) {
        map.put(descriptor.id, descriptor);
    }

    public static ModelDescriptor getDefaultModel() {
        return DESCRIPTORS.get(MODEL_GEMMA_3_1B);
    }

    public static ModelDescriptor get(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            return getDefaultModel();
        }
        ModelDescriptor descriptor = DESCRIPTORS.get(modelId.trim().toLowerCase(Locale.US));
        return descriptor != null ? descriptor : getDefaultModel();
    }

    public static List<ModelDescriptor> getSupportedModels() {
        return new ArrayList<>(DESCRIPTORS.values());
    }

    public static File getModelDirectory(Context context) {
        File modelDir = new File(context.getFilesDir(), "models");
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }
        return modelDir;
    }

    public static File getExternalModelDirectory(Context context) {
        File base = context.getExternalFilesDir(null);
        File modelDir = base != null ? new File(base, "models") : new File(context.getFilesDir(), "external-models");
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }
        return modelDir;
    }

    public static File getInstalledModelFile(Context context, String modelId) {
        ModelDescriptor descriptor = get(modelId);
        File internalFile = new File(getModelDirectory(context), descriptor.fileName);
        if (internalFile.exists() && internalFile.length() > 0L) {
            return internalFile;
        }
        File externalFile = new File(getExternalModelDirectory(context), descriptor.fileName);
        return externalFile.exists() && externalFile.length() > 0L ? externalFile : null;
    }

    public static boolean isInstalled(Context context, String modelId) {
        Context appContext = context.getApplicationContext();
        if (getInstalledModelFile(appContext, modelId) != null) {
            return true;
        }
        ModelDescriptor descriptor = get(modelId);
        return assetExists(appContext, descriptor.assetPath);
    }

    public static boolean isRunnableWithCurrentRuntime(String modelId) {
        return get(modelId).backend == Backend.MEDIAPIPE_TASK;
    }

    public static List<ModelDescriptor> getInstalledModels(Context context) {
        Context appContext = context.getApplicationContext();
        List<ModelDescriptor> installed = new ArrayList<>();
        for (ModelDescriptor descriptor : DESCRIPTORS.values()) {
            if (isInstalled(appContext, descriptor.id)) {
                installed.add(descriptor);
            }
        }
        return installed;
    }

    public static String describeInstalledModels(Context context) {
        List<ModelDescriptor> installed = getInstalledModels(context);
        if (installed.isEmpty()) {
            return "No local AI model packs are installed.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < installed.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(installed.get(i).displayName);
        }
        return sb.toString();
    }

    public static String describeAvailability(Context context, String modelId) {
        ModelDescriptor descriptor = get(modelId);
        if (isInstalled(context, modelId)) {
            if (descriptor.backend == Backend.EXTERNAL_GGUF) {
                return descriptor.displayName + " is staged locally as GGUF. A GGUF runtime bridge is still required before it can answer prompts on-device.";
            }
            return descriptor.displayName + " is installed for offline use.";
        }
        return descriptor.displayName + " is supported but not installed yet. " + descriptor.installHint;
    }

    public static File resolveModelFile(Context context, String modelId) throws Exception {
        Context appContext = context.getApplicationContext();
        ModelDescriptor descriptor = get(modelId);
        File installed = getInstalledModelFile(appContext, descriptor.id);
        if (installed != null) {
            return installed;
        }
        if (!assetExists(appContext, descriptor.assetPath)) {
            throw new IllegalStateException(descriptor.displayName + " is not installed locally. " + descriptor.installHint);
        }
        return copyAssetToModelDir(appContext, descriptor);
    }

    private static boolean assetExists(Context context, String assetPath) {
        if (assetPath == null || assetPath.trim().isEmpty()) {
            return false;
        }
        try {
            AssetManager assets = context.getAssets();
            try (InputStream ignored = assets.open(assetPath)) {
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static File copyAssetToModelDir(Context context, ModelDescriptor descriptor) throws Exception {
        File outFile = new File(getModelDirectory(context), descriptor.fileName);
        if (outFile.exists() && outFile.length() > 0L) {
            return outFile;
        }
        AssetManager assets = context.getAssets();
        try (InputStream in = assets.open(descriptor.assetPath);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024 * 1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        return outFile;
    }
}
