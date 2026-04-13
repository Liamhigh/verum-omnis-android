package com.verum.omnis.core;

import android.content.Context;
import com.verum.legal.LegalGrounding;

public final class GemmaRuntime {

    private static final String MODEL_ID = LocalAiModelRegistry.MODEL_GEMMA_3_1B;
    private static final String PHI3_MODEL_ID = LocalAiModelRegistry.MODEL_PHI3_MINI;

    public interface Callback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    private static GemmaRuntime instance;

    private String status = "Gemma not initialized.";

    private GemmaRuntime() {}

    public static synchronized GemmaRuntime getInstance() {
        if (instance == null) {
            instance = new GemmaRuntime();
        }
        return instance;
    }

    public synchronized String getStatus() {
        return status;
    }

    public String getInstalledModelSummary(Context context) {
        return LocalAiModelRegistry.describeInstalledModels(context.getApplicationContext());
    }

    public boolean isPhi3MiniAvailable(Context context) {
        return LocalAiModelRegistry.isInstalled(context.getApplicationContext(), PHI3_MODEL_ID);
    }

    public boolean isPhi3MiniRunnable(Context context) {
        return Phi3RuntimeBridge.isReady(context.getApplicationContext());
    }

    public String getPhi3MiniAvailability(Context context) {
        if (Phi3RuntimeBridge.isReady(context.getApplicationContext())) {
            return "Phi-3 Mini is staged and the llama.cpp bridge is ready on this device.";
        }
        return LocalAiModelRegistry.describeAvailability(context.getApplicationContext(), PHI3_MODEL_ID)
                + " "
                + Phi3RuntimeBridge.describeRuntime(context.getApplicationContext());
    }

    public void warmUp(Context context, Callback callback) {
        LocalAiRuntime.getInstance().warmUp(
                context.getApplicationContext(),
                MODEL_ID,
                buildSystemPrompt(),
                new LocalAiRuntime.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        synchronized (GemmaRuntime.this) {
                            status = response;
                        }
                        callback.onSuccess("Gemma ready on-device.");
                    }

                    @Override
                    public void onError(String errorMessage) {
                        synchronized (GemmaRuntime.this) {
                            status = errorMessage;
                        }
                        callback.onError(errorMessage);
                    }
                }
        );
    }

    public void generateResponse(Context context, String userPrompt, Callback callback) {
        LocalAiRuntime.getInstance().generateResponse(
                context.getApplicationContext(),
                MODEL_ID,
                buildSystemPrompt(),
                userPrompt,
                new LocalAiRuntime.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        synchronized (GemmaRuntime.this) {
                            status = LocalAiRuntime.getInstance().getStatus();
                        }
                        callback.onSuccess(response);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        synchronized (GemmaRuntime.this) {
                            status = errorMessage;
                        }
                        callback.onError(errorMessage);
                    }
                }
        );
    }

    public void generateGroundedLegalResponse(
            Context context,
            AnalysisEngine.ForensicReport report,
            Callback callback
    ) {
        LocalAiRuntime.getInstance().generateResponse(
                context.getApplicationContext(),
                MODEL_ID,
                buildSystemPrompt(),
                buildGroundedLegalPrompt(context, report),
                new LocalAiRuntime.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        synchronized (GemmaRuntime.this) {
                            status = LocalAiRuntime.getInstance().getStatus();
                        }
                        callback.onSuccess(response);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        synchronized (GemmaRuntime.this) {
                            status = errorMessage;
                        }
                        callback.onError("Gemma grounded legal response failed: " + errorMessage);
                    }
                }
        );
    }

    public String generateResponseBlocking(Context context, String userPrompt) throws Exception {
        String response = LocalAiRuntime.getInstance().generateResponseBlocking(
                context.getApplicationContext(),
                MODEL_ID,
                buildSystemPrompt(),
                userPrompt
        );
        synchronized (this) {
            status = LocalAiRuntime.getInstance().getStatus();
        }
        return response;
    }

    public String generateResponseBlockingWithPhi3Mini(Context context, String userPrompt) throws Exception {
        String response = Phi3RuntimeBridge.generateResponseBlocking(
                context.getApplicationContext(),
                buildSystemPrompt() + "\n\n" + (userPrompt == null ? "" : userPrompt)
        );
        synchronized (this) {
            status = "Phi-3 Mini GGUF ready through llama.cpp bridge.";
        }
        return response;
    }

    private String buildSystemPrompt() {
        return "You are Gemma for Verum Omnis. "
                + "You are offline, constitution-bound, and downstream from the forensic engine. "
                + "Use sealed findings and vault context only. "
                + "Do not invent facts or law. "
                + "Communicate naturally and clearly. "
                + "If the forensic pass suggests contradictions, pattern drift, commercial irregularity, franchise abuse, or unresolved evidential gaps, ask short numbered follow-up questions. "
                + "Continue asking until the gap is answered, the user has no more source material, or the gap is explicitly marked unresolved. "
                + "Ask for prior court cases, regulator findings, complaint files, judgments, or tribunal decisions involving the same parties, site, franchise, permit, or method where that would help test pattern repetition. "
                + "Treat user answers as investigative leads, not certified fact, until they are anchored in evidence. "
                + "For forensic reports, write under the Verum Omnis constitution with contradiction-led reasoning, "
                + "triple verification, anchored incidents, jurisdiction awareness, and a court-readable narrative.";
    }

    private String buildGroundedLegalPrompt(Context context, AnalysisEngine.ForensicReport report) {
        try {
            LegalGrounding grounding = new LegalGrounding(context.getApplicationContext());
            LegalGrounding.PromptPackage promptPackage = grounding.buildPromptPackage(report);
            return promptPackage.prompt;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build grounded legal prompt: " + e.getMessage(), e);
        }
    }
}
