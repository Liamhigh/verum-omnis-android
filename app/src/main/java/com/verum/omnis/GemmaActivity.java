package com.verum.omnis;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.verum.omnis.core.AssistanceRestrictionManager;
import com.verum.omnis.core.CommercialAccessManager;
import com.verum.omnis.core.CommercialFeatureGateManager;
import com.verum.omnis.core.GemmaCaseContextStore;
import com.verum.omnis.core.GemmaChatStore;
import com.verum.omnis.core.GemmaRuntime;
import com.verum.omnis.core.LegalEmailDraftService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GemmaActivity extends AppCompatActivity {

    private ScrollView transcriptScroll;
    private LinearLayout messagesContainer;
    private TextView caseBannerView;
    private EditText inputView;
    private Button sendBtn;
    private GemmaChatStore.Session currentSession;
    private GemmaCaseContextStore.Snapshot latestCaseContext;
    private String transientSpeaker;
    private String transientMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gemma);

        transcriptScroll = findViewById(R.id.gemmaScroll);
        messagesContainer = findViewById(R.id.gemmaMessagesContainer);
        caseBannerView = findViewById(R.id.gemmaCaseBanner);
        inputView = findViewById(R.id.gemmaInput);
        sendBtn = findViewById(R.id.gemmaSendBtn);
        Button backBtn = findViewById(R.id.gemmaBackBtn);
        Button newChatBtn = findViewById(R.id.gemmaNewChatBtn);
        Button historyBtn = findViewById(R.id.gemmaHistoryBtn);

        sendBtn.setOnClickListener(v -> sendMessage());
        backBtn.setOnClickListener(v -> finish());
        newChatBtn.setOnClickListener(v -> startNewChat());
        historyBtn.setOnClickListener(v -> showHistoryDialog());

        currentSession = GemmaChatStore.loadLatestOrCreate(this);
        latestCaseContext = GemmaCaseContextStore.load(this);
        AssistanceRestrictionManager.Snapshot restriction = AssistanceRestrictionManager.load(this);

        if (latestCaseContext.available) {
            caseBannerView.setVisibility(View.VISIBLE);
            caseBannerView.setText("Latest case: " + latestCaseContext.sourceFile + "  |  " + latestCaseContext.caseId);
        } else {
            caseBannerView.setVisibility(View.GONE);
        }

        if (currentSession.messages.isEmpty()) {
            GemmaChatStore.append(this, currentSession, "Gemma",
                    "Hello. I am here with you. We can talk through the case, the reports, the next step, or just slow things down and think clearly together. Nothing from this chat is sent to a server.");
        }
        renderCurrentSession();

        if (restriction.restricted) {
            appendPermanentTranscript(
                    "Notice",
                    "Assistance is suspended after a suspected attempt to deceive the forensic system."
                            + "\nCase: " + restriction.caseId
                            + "\nEvidence Hash: " + restriction.evidenceHashShort
                            + "\nReason: " + restriction.reason
            );
            inputView.setEnabled(false);
            sendBtn.setEnabled(false);
            inputView.setHint("Assistance suspended");
            return;
        }
    }

    private void startNewChat() {
        currentSession = GemmaChatStore.createSession(this);
        transientSpeaker = null;
        transientMessage = null;
        GemmaChatStore.append(this, currentSession, "Gemma",
                "New chat started. Ask about the case, the report, the vault, or the next verification step.");
        renderCurrentSession();
        inputView.getText().clear();
        inputView.clearComposingText();
        inputView.requestFocus();
    }

    private void showHistoryDialog() {
        List<GemmaChatStore.Session> sessions = GemmaChatStore.listSessions(this);
        if (sessions.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Gemma History")
                    .setMessage("No saved chats yet.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        String[] labels = new String[sessions.size()];
        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US);
        for (int i = 0; i < sessions.size(); i++) {
            GemmaChatStore.Session session = sessions.get(i);
            labels[i] = session.title + "\n" + formatter.format(new Date(session.updatedAt));
        }

        new AlertDialog.Builder(this)
                .setTitle("Gemma History")
                .setItems(labels, (dialog, which) -> {
                    currentSession = GemmaChatStore.load(this, sessions.get(which).id);
                    transientSpeaker = null;
                    transientMessage = null;
                    renderCurrentSession();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void sendMessage() {
        String message = inputView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            return;
        }

        inputView.getText().clear();
        inputView.clearComposingText();
        inputView.setText("");
        inputView.setSelection(0);

        appendPermanentTranscript("You", message);
        sendBtn.setEnabled(false);
        showTransientTranscript("Gemma", "Let me look at that...");
        new Thread(() -> {
            String computedResponse;
            LegalEmailDraftService.DraftResult draftResult = null;
            try {
                if (looksLikeInvestigationUnlockRequest(message)) {
                    computedResponse = handleInvestigationUnlockRequest(message);
                } else if (looksLikeLegalEmailRequest(message)) {
                    draftResult = LegalEmailDraftService.prepare(this, latestCaseContext, message);
                    computedResponse = draftResult.userMessage;
                } else {
                    computedResponse = buildPrimaryChatResponse(message);
                }
            } catch (Throwable t) {
                computedResponse = buildFallbackResponse(message)
                        + "\n\nI hit a local chat error while reading the stored case context, so I fell back to the stable assistant response instead of stopping the conversation.";
            }
            final String response = computedResponse;
            final LegalEmailDraftService.DraftResult finalDraftResult = draftResult;
            runOnUiThread(() -> {
                removeThinkingLine();
                appendPermanentTranscript("Gemma", response);
                if (finalDraftResult != null && finalDraftResult.readyToCompose) {
                    launchGovernedEmailDraft(finalDraftResult);
                }
                sendBtn.setEnabled(true);
                inputView.requestFocus();
            });
        }).start();
    }

    private String buildPrimaryChatResponse(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.US);
        if (looksLikeInvestigationModeRequest(normalized) && requiresCommercialInvestigationUnlock()) {
            return buildCommercialInvestigationLockMessage();
        }
        String runtimeResponse = tryGenerateGemmaChatResponse(message);
        if (!TextUtils.isEmpty(runtimeResponse)) {
            return runtimeResponse;
        }
        return buildStableLocalResponse(message);
    }

    private String tryGenerateGemmaChatResponse(String message) {
        if (TextUtils.isEmpty(message)) {
            return "";
        }
        try {
            String[] conversationKeywords = resolveConversationKeywords(message);
            String prompt = buildGemmaChatPrompt(message, conversationKeywords);
            String rawResponse = GemmaRuntime.getInstance().generateResponseBlocking(this, prompt);
            if (!looksLikeUsableGemmaChatResponse(rawResponse)) {
                return "";
            }
            return rawResponse.replaceFirst("(?is)^gemma\\s*:\\s*", "").trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String buildGemmaChatPrompt(String message, String[] conversationKeywords) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Live chat mode. Answer the user naturally, warmly, and clearly.");
        prompt.append("\nDo not sound like a template, checklist, or system prompt.");
        prompt.append("\nKeep the conversation flowing like a normal helpful assistant.");
        prompt.append("\nIf the evidence is incomplete, say so plainly.");
        prompt.append("\nIf contradictions, pattern gaps, or missing court/regulator history matter, ask short numbered follow-up questions.");
        prompt.append("\nDo not invent facts, law, or evidence.");
        prompt.append("\n\nCurrent user message:\n").append(message.trim());

        String recentConversation = buildRecentConversationExcerpt(8);
        if (!TextUtils.isEmpty(recentConversation)) {
            prompt.append("\n\nRecent chat context:\n").append(recentConversation);
        }

        if (hasCaseContext()) {
            String summary = firstNonEmptySection(
                    extractContextSection("Gemma narrative report excerpt:"),
                    extractContextSection("Compact case brief:"),
                    latestCaseContext.contextText
            );
            String relevant = findRelevantLines(latestCaseContext.contextText, conversationKeywords, 8);
            prompt.append("\n\nLatest sealed case context:");
            prompt.append("\nCase ID: ").append(latestCaseContext.caseId);
            prompt.append("\nSource file: ").append(latestCaseContext.sourceFile);
            if (!TextUtils.isEmpty(summary)) {
                prompt.append("\nCase summary:\n").append(clipText(summary, 1800));
            }
            if (!TextUtils.isEmpty(relevant)) {
                prompt.append("\nRelevant anchored lines:\n").append(relevant);
            }
        } else {
            prompt.append("\n\nNo sealed case context is currently loaded on this device.");
        }

        prompt.append("\n\nAnswer as Gemma in ordinary conversation.");
        return prompt.toString();
    }

    private String buildRecentConversationExcerpt(int maxMessages) {
        if (currentSession == null || currentSession.messages == null || currentSession.messages.isEmpty()) {
            return "";
        }
        int start = Math.max(0, currentSession.messages.size() - Math.max(1, maxMessages));
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < currentSession.messages.size(); i++) {
            GemmaChatStore.Message item = currentSession.messages.get(i);
            if (item == null || TextUtils.isEmpty(item.text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(item.speaker).append(": ").append(clipText(item.text, 280));
        }
        return sb.toString().trim();
    }

    private boolean looksLikeUsableGemmaChatResponse(String response) {
        if (TextUtils.isEmpty(response)) {
            return false;
        }
        String trimmed = response.trim();
        if (trimmed.length() < 24) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        return !lower.startsWith("error:")
                && !lower.contains("i cannot access the model")
                && !lower.contains("gemma not initialized");
    }

    private void appendPermanentTranscript(String speaker, String message) {
        GemmaChatStore.append(this, currentSession, speaker, message);
        renderCurrentSession();
    }

    private void showTransientTranscript(String speaker, String message) {
        transientSpeaker = speaker;
        transientMessage = message;
        renderCurrentSession();
    }

    private void renderCurrentSession() {
        messagesContainer.removeAllViews();
        for (GemmaChatStore.Message message : currentSession.messages) {
            addBubble(message.speaker, message.text, false);
        }
        if (!TextUtils.isEmpty(transientSpeaker) && !TextUtils.isEmpty(transientMessage)) {
            addBubble(transientSpeaker, transientMessage, true);
        }
        transcriptScroll.post(() -> transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void removeThinkingLine() {
        transientSpeaker = null;
        transientMessage = null;
        renderCurrentSession();
    }

    private String buildFallbackResponse(String message) {
        String normalized = message.toLowerCase(Locale.US);
        if (isGreeting(normalized)) {
            return buildGreetingResponse();
        }
        if (isSupportiveNeed(normalized)) {
            return buildSupportiveResponse();
        }
        if (normalized.contains("upload") || normalized.contains("evidence") || normalized.contains("analyse") || normalized.contains("analyze")) {
            return "Upload the evidence from the front page. The app hashes it, runs the forensic engine locally, stores the findings package and reports in the vault, and keeps the sealed chain tied to that evidence set on the device.";
        }
        if (normalized.contains("seal") || normalized.contains("document")) {
            return "Seal Document is the separate sealing path. PDFs and images should produce a sealed copy, while unsupported source types fall back to a seal certificate.";
        }
        if (normalized.contains("vault")) {
            return "Open Vault to review the stored forensic reports, findings packages, sealed outputs, and visual findings memos. That is where the final JSON and PDFs should be checked.";
        }
        if (looksLikeInvestigationModeRequest(normalized)) {
            return "If the forensic pass suggests contradictions, repeated commercial conduct, or a pattern around the same site, franchise, permit, or actors, I should keep asking structured follow-up questions until the gap is answered or marked unresolved. For commercial and institutional matters, that investigation layer should sit inside the governed commercial access model.";
        }
        if (normalized.contains("commercial") || normalized.contains("payment") || normalized.contains("business") || normalized.contains("trial")) {
            return "Commercial and institutional work uses a 30-day review window from the moment the case or certified output reaches that recipient or organisation. Private citizens and law enforcement stay free globally, except tax work.";
        }
        if (normalized.contains("report") || normalized.contains("narrative") || normalized.contains("what happened")) {
            return "The final report should state the actual incidents, actors, contradictions, behavioural patterns, visual forgery findings, and likely offences. If it does not, the extraction or the Gemma report prompt still needs tightening.";
        }
        if (normalized.contains("model") || normalized.contains("gemma") || normalized.contains("phi")) {
            String installed = GemmaRuntime.getInstance().getInstalledModelSummary(this);
            String phiStatus = GemmaRuntime.getInstance().getPhi3MiniAvailability(this);
            return "The app is set up for fully local model packs. Installed right now: "
                    + installed
                    + ". "
                    + phiStatus;
        }
        if (looksLikeLegalEmailRequest(normalized)) {
            return "Ask me to draft a legal email with the recipient email address in the message. I will generate a governed PDF attachment from the sealed case context instead of letting you send uncontrolled legal text.";
        }
        return "I can explain the local forensic workflow and the stored outputs. The priority remains the same: name the incidents, name the actors, anchor the pages, distinguish proof from indication, and keep the evidence sealed on the device.";
    }

    private String buildStableLocalResponse(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.US);
        String[] conversationKeywords = resolveConversationKeywords(message);
        if (TextUtils.isEmpty(normalized)) {
            return buildGreetingResponse();
        }
        if (!hasCaseContext()) {
            if (isSupportiveNeed(normalized)) {
                return buildSupportiveResponse();
            }
            return buildFallbackResponse(message)
                    + "\n\nRun a forensic scan first and I will answer from the sealed case context stored in the vault path on this device.";
        }

        if (isGreeting(normalized)) {
            return buildGreetingResponse();
        }
        if (isSupportiveNeed(normalized)) {
            return buildSupportiveResponse();
        }
        if (isFollowUpMessage(normalized)) {
            return buildTopicAwareResponse(
                    "Continuing from the current case conversation, these are the next relevant points I can see in the sealed context:",
                    conversationKeywords,
                    buildFallbackResponse(message)
            );
        }
        if (normalized.contains("what happened")
                || normalized.contains("summary")
                || normalized.contains("narrative")
                || normalized.contains("case overview")) {
            return buildSummaryResponse();
        }
        if (looksLikeInvestigationModeRequest(normalized)) {
            return buildInvestigationDrivenResponse(message, conversationKeywords);
        }
        if (normalized.contains("contradiction")
                || normalized.contains("lied")
                || normalized.contains("lying")
                || normalized.contains("dishonest")
                || normalized.contains("conflict")) {
            return buildKeywordDrivenResponse(
                    "These are the contradiction-led points I can see in the sealed case context:",
                    conversationKeywords,
                    "I can see that contradiction analysis matters here, but the current stored case context does not expose a clean contradiction register yet."
            );
        }
        if (normalized.contains("forg")
                || normalized.contains("tamper")
                || normalized.contains("visual")
                || normalized.contains("ocr")
                || normalized.contains("screenshot")) {
            return buildKeywordDrivenResponse(
                    "These are the visual, OCR, and possible forgery points I can see in the sealed case context:",
                    conversationKeywords,
                    "The forensic engine should list the visual and OCR findings explicitly, but this stored case context does not yet expose enough of them cleanly."
            );
        }
        if (normalized.contains("crime")
                || normalized.contains("offence")
                || normalized.contains("offense")
                || normalized.contains("fraud")
                || normalized.contains("cyber")
                || normalized.contains("law")) {
            return buildKeywordDrivenResponse(
                    "These are the likely legal exposures and offence-linked points I can see in the sealed case context:",
                    conversationKeywords,
                    "The stored case context needs a cleaner offence-element mapping before I can give a stronger legal answer."
            );
        }
        if (normalized.contains("page")
                || normalized.contains("where")
                || normalized.contains("evidence")
                || normalized.contains("proof")
                || normalized.contains("anchor")) {
            return buildKeywordDrivenResponse(
                    "These are the most relevant anchored lines I can see in the stored case context:",
                    conversationKeywords,
                    "The audit report and findings package should carry the page anchors. Ask me about a date, actor, or event and I can narrow the stored case context further."
            );
        }
        return buildKeywordDrivenResponse(
                "I am working from the sealed case context stored on this device. These are the most relevant points I can see right now:",
                conversationKeywords,
                buildFallbackResponse(message)
        );
    }

    private String buildGreetingResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello. I am here with you on this device.");
        if (hasCaseContext()) {
            sb.append("\n\nLatest sealed case loaded: ")
                    .append(latestCaseContext.sourceFile)
                    .append(" | ")
                    .append(latestCaseContext.caseId);
        }
        sb.append("\n\nThis chat stays on the device, does not send your evidence to servers, and can move between plain human conversation and case work.");
        sb.append("\n\nAsk for a summary, the likely crimes, contradictions, visual findings, page anchors, what to do next, or simply tell me what is weighing on you.");
        sb.append("\n\nIf contradictions or pattern gaps remain after the forensic pass, I should switch into investigation mode and keep asking structured questions until the gap is answered or marked unresolved.");
        sb.append("\n\nIf you need legal correspondence, ask me to draft a sealed legal email and include the recipient email address. I will keep that governed and attachment-based.");
        return sb.toString();
    }

    private String buildSupportiveResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("I hear you. You do not need to turn everything into a formal report just to talk here.");
        if (hasCaseContext()) {
            sb.append(" I still have the latest sealed case context loaded, but we can stay with the human side of this for a moment if that is what you need.");
        }
        sb.append("\n\nWhat you have been carrying sounds heavy. We can do this one step at a time.");
        sb.append("\n\nIf you want, we can do one of three things right now: talk through what happened, break the next action into a very small step, or just keep talking until it feels clearer.");
        sb.append("\n\nTell me which part feels heaviest right now.");
        return sb.toString();
    }

    private String buildSummaryResponse() {
        String summary = firstNonEmptySection(
                extractContextSection("Gemma narrative report excerpt:"),
                extractContextSection("Compact case brief:"),
                latestCaseContext != null ? latestCaseContext.contextText : ""
        );
        String relevant = findRelevantLines(
                latestCaseContext.contextText,
                new String[]{"summary", "fraud", "forged", "admission", "timeline", "page"},
                5
        );
        StringBuilder sb = new StringBuilder();
        sb.append("Here is the clearest case summary I can assemble from the sealed context on this device:\n\n");
        sb.append(clipText(summary, 1100));
        if (!TextUtils.isEmpty(relevant)) {
            sb.append("\n\nKey anchored points:\n").append(relevant);
        }
        return appendInvestigationLoop(
                sb.toString(),
                new String[]{"pattern", "contradiction", "agreement", "franchise", "court", "finding", "site", "goodwill"}
        );
    }

    private boolean isGreeting(String normalized) {
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }
        return "hello".equals(normalized)
                || "hi".equals(normalized)
                || "hey".equals(normalized)
                || normalized.startsWith("hello ")
                || normalized.startsWith("hi ")
                || normalized.startsWith("hey ");
    }

    private boolean isSupportiveNeed(String normalized) {
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }
        return normalized.contains("lost everything")
                || normalized.contains("lost my friends")
                || normalized.contains("only friend")
                || normalized.contains("alone")
                || normalized.contains("lonely")
                || normalized.contains("scared")
                || normalized.contains("afraid")
                || normalized.contains("overwhelmed")
                || normalized.contains("exhausted")
                || normalized.contains("desperate")
                || normalized.contains("broken")
                || normalized.contains("i need to talk")
                || normalized.contains("can we talk")
                || normalized.contains("stay with me")
                || normalized.contains("i'm struggling")
                || normalized.contains("im struggling")
                || normalized.contains("i feel")
                || normalized.contains("this is hard");
    }

    private boolean isFollowUpMessage(String normalized) {
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }
        return "and then".equals(normalized)
                || "what next".equals(normalized)
                || "go on".equals(normalized)
                || "continue".equals(normalized)
                || "carry on".equals(normalized)
                || "more".equals(normalized)
                || "tell me more".equals(normalized)
                || normalized.startsWith("and then")
                || normalized.startsWith("what next")
                || normalized.startsWith("go on")
                || normalized.startsWith("continue")
                || normalized.startsWith("tell me more")
                || normalized.startsWith("what about that")
                || normalized.startsWith("what about him")
                || normalized.startsWith("what about her")
                || normalized.startsWith("what about them");
    }

    private boolean looksLikeLegalEmailRequest(String message) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.US);
        return (normalized.contains("email") || normalized.contains("draft") || normalized.contains("reply")
                || normalized.contains("response") || normalized.contains("demand") || normalized.contains("compensation")
                || normalized.contains("settlement") || normalized.contains("cease"))
                && (normalized.contains("@") || normalized.contains("recipient"));
    }

    private boolean looksLikeInvestigationUnlockRequest(String message) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.US);
        return normalized.contains("receipt id")
                || normalized.startsWith("unlock investigation")
                || normalized.startsWith("unlock commercial investigation")
                || normalized.startsWith("set receipt");
    }

    private String handleInvestigationUnlockRequest(String message) {
        if (TextUtils.isEmpty(message)) {
            return buildCommercialInvestigationLockMessage();
        }
        String normalized = message.trim();
        String receipt = normalized
                .replaceFirst("(?i)^unlock\\s+commercial\\s+investigation\\s*", "")
                .replaceFirst("(?i)^unlock\\s+investigation\\s*", "")
                .replaceFirst("(?i)^set\\s+receipt\\s*", "")
                .replaceFirst("(?i)^receipt\\s*id\\s*[:#-]?\\s*", "")
                .trim();
        if ("clear".equalsIgnoreCase(receipt) || "remove".equalsIgnoreCase(receipt)) {
            CommercialFeatureGateManager.clearInvestigationReceipt(this);
            return "Commercial investigation access has been cleared on this device. Investigation mode for commercial matters is locked again until a receipt ID is loaded.";
        }
        if (!CommercialFeatureGateManager.storeInvestigationReceipt(this, receipt)) {
            return "I could not accept that receipt ID. Use a stable receipt or proof ID, for example: `Receipt ID ABCD1234EFGH`.";
        }
        return "Commercial investigation access is now enabled on this device. Receipt ID: "
                + CommercialFeatureGateManager.getInvestigationReceiptIdShort(this)
                + ". Investigation mode can now be used for commercial case-building.";
    }

    private boolean looksLikeInvestigationModeRequest(String normalized) {
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }
        return normalized.contains("pattern")
                || normalized.contains("similar")
                || normalized.contains("same thing")
                || normalized.contains("happened before")
                || normalized.contains("previous case")
                || normalized.contains("prior case")
                || normalized.contains("court case")
                || normalized.contains("judgment")
                || normalized.contains("judgement")
                || normalized.contains("finding")
                || normalized.contains("franchise")
                || normalized.contains("lease")
                || normalized.contains("goodwill")
                || normalized.contains("site")
                || normalized.contains("permit")
                || normalized.contains("agreement")
                || normalized.contains("commercial")
                || normalized.contains("tribunal")
                || normalized.contains("regulator");
    }

    private void launchGovernedEmailDraft(LegalEmailDraftService.DraftResult draftResult) {
        if (draftResult == null || draftResult.attachmentPdf == null || !draftResult.attachmentPdf.exists()) {
            return;
        }
        try {
            File file = draftResult.attachmentPdf;
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{draftResult.recipientEmail});
            intent.putExtra(Intent.EXTRA_SUBJECT, draftResult.subject);
            intent.putExtra(Intent.EXTRA_TEXT, draftResult.mailBody);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, "Send sealed legal email draft"));
            com.verum.omnis.core.LegalEmailGovernanceManager.recordDraft(
                    this,
                    draftResult.recipientEmail,
                    draftResult.caseId,
                    draftResult.subject
            );
        } catch (Exception e) {
            appendPermanentTranscript(
                    "Gemma",
                    "I generated the sealed legal email draft, but I could not open the mail app: " + e.getMessage()
                            + "\nAttachment: " + draftResult.attachmentPdf.getAbsolutePath()
            );
        }
    }

    private String buildKeywordDrivenResponse(String intro, String[] preferredKeywords, String fallback) {
        String relevant = findRelevantLines(latestCaseContext.contextText, preferredKeywords, 6);
        if (TextUtils.isEmpty(relevant)) {
            return fallback;
        }
        return appendInvestigationLoop(
                intro + "\n\n" + relevant + "\n\nIf you want, I can turn those into a dated incident list, a human narrative, or a page-anchored audit note.",
                preferredKeywords
        );
    }

    private String buildTopicAwareResponse(String intro, String[] conversationKeywords, String fallback) {
        String relevant = findRelevantLines(latestCaseContext.contextText, conversationKeywords, 7);
        if (TextUtils.isEmpty(relevant)) {
            return fallback;
        }
        return appendInvestigationLoop(
                intro + "\n\n" + relevant + "\n\nIf you want, I can keep following that thread, switch to contradictions, or pull out page anchors.",
                conversationKeywords
        );
    }

    private String buildInvestigationDrivenResponse(String message, String[] conversationKeywords) {
        if (requiresCommercialInvestigationUnlock()) {
            return buildCommercialInvestigationLockMessage();
        }
        String[] preferredKeywords = mergeKeywords(
                conversationKeywords,
                extractKeywords(message),
                new String[]{"pattern", "agreement", "franchise", "goodwill", "site", "court", "finding", "judgment"}
        );
        String relevant = hasCaseContext()
                ? findRelevantLines(latestCaseContext.contextText, preferredKeywords, 7)
                : "";
        StringBuilder sb = new StringBuilder();
        sb.append("I should treat this as a constitutional investigation thread, not just a summary.");
        if (!TextUtils.isEmpty(relevant)) {
            sb.append("\n\nCurrent anchored signals from the sealed case context:\n")
                    .append(relevant);
        }
        sb.append("\n\nI will keep asking the next necessary questions until the gap is answered or marked unresolved.");
        return appendInvestigationLoop(sb.toString(), preferredKeywords);
    }

    private String appendInvestigationLoop(String base, String[] keywords) {
        if (!hasCaseContext()) {
            return base;
        }
        if (!shouldRunInvestigationLoop(keywords)) {
            return base;
        }
        if (requiresCommercialInvestigationUnlock()) {
            return base + "\n\n" + buildCommercialInvestigationLockMessage();
        }
        StringBuilder sb = new StringBuilder(base);
        List<String> questions = buildInvestigationQuestions(keywords);
        if (!questions.isEmpty()) {
            sb.append("\n\nQuestions I still need answered:\n");
            for (int i = 0; i < questions.size(); i++) {
                sb.append(i + 1).append(". ").append(questions.get(i)).append("\n");
            }
        }
        if (caseLooksCommercial()) {
            CommercialAccessManager.Snapshot commercialSnapshot = CommercialAccessManager.load(this, 30);
            sb.append("\nCommercial note: this investigation layer should sit inside the governed commercial access model rather than free general chat. ")
                    .append(commercialSnapshot.statusLine);
        }
        sb.append("\n\nBring back source documents, case numbers, judgments, tribunal outcomes, complaint references, or registry material. I will treat them as investigative leads until they are anchored into the sealed record.");
        return sb.toString().trim();
    }

    private boolean shouldRunInvestigationLoop(String[] keywords) {
        return caseLooksCommercial()
                || contextContainsAny("contradiction", "contradictions", "cross-brain", "pattern", "vulnerability", "goodwill", "franchise", "agreement", "permit", "site", "court", "judgment")
                || containsKeyword(keywords, "pattern")
                || containsKeyword(keywords, "agreement")
                || containsKeyword(keywords, "franchise")
                || containsKeyword(keywords, "court")
                || containsKeyword(keywords, "judgment")
                || containsKeyword(keywords, "finding");
    }

    private List<String> buildInvestigationQuestions(String[] keywords) {
        LinkedHashSet<String> questions = new LinkedHashSet<>();
        if (caseLooksCommercial() || containsKeyword(keywords, "agreement") || contextContainsAny("agreement", "shareholder", "lease", "franchise", "permit")) {
            questions.add("What agreement governed this conduct: shareholder, franchise, lease, permit, management, supply, or another commercial arrangement?");
            questions.add("Was the same site, company, franchise, permit, or commercial structure involved in an earlier dispute or loss?");
        }
        if (contextContainsAny("goodwill", "site", "forced off site", "rent", "permit", "goodwill stolen")
                || containsKeyword(keywords, "site")
                || containsKeyword(keywords, "goodwill")) {
            questions.add("Who lost the site, goodwill, permit, or commercial position first, and do the later documents show the same method being used again?");
        }
        if (contextContainsAny("contradiction", "admission", "refused", "blocked", "ignored", "termination")
                || containsKeyword(keywords, "pattern")) {
            questions.add("Which version changed over time, on what date did it change, and who benefited from that change?");
        }
        questions.add("Have you searched for prior court cases, tribunal matters, complaints, or regulator findings involving the same parties, site, franchise, permit, or method? If yes, what were the findings and case numbers?");
        if (contextContainsAny("uae", "south africa", "multi-jurisdiction")) {
            questions.add("Did the same conduct happen in South Africa, the UAE, or both, and which documents anchor each jurisdiction?");
        }
        if (caseLooksCommercial() || contextContainsAny("invoice", "payment", "share", "profit", "rent", "goodwill", "loss")) {
            questions.add("What money, share, rent, goodwill, permit value, or asset moved, and who received the commercial benefit each time?");
        }
        List<String> out = new ArrayList<>(questions);
        if (out.size() > 5) {
            return new ArrayList<>(out.subList(0, 5));
        }
        return out;
    }

    private boolean caseLooksCommercial() {
        return contextContainsAny(
                "shareholder",
                "agreement",
                "franchise",
                "lease",
                "site",
                "goodwill",
                "permit",
                "rent",
                "invoice",
                "payment",
                "company",
                "commercial"
        );
    }

    private boolean requiresCommercialInvestigationUnlock() {
        return caseLooksCommercial() && !CommercialFeatureGateManager.isInvestigationUnlocked(this);
    }

    private String buildCommercialInvestigationLockMessage() {
        CommercialAccessManager.Snapshot commercialSnapshot = CommercialAccessManager.load(this, 30);
        String receiptShort = CommercialFeatureGateManager.getInvestigationReceiptIdShort(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Commercial investigation mode is locked on this device.");
        if (!TextUtils.isEmpty(receiptShort)) {
            sb.append(" Loaded receipt: ").append(receiptShort).append(".");
        }
        sb.append("\n\nNormal summaries remain available, but investigation questioning for commercial, franchise, lease, permit, shareholder, or site-based matters requires paid commercial access.");
        sb.append("\n").append(commercialSnapshot.statusLine);
        sb.append("\n\nTo enable it on this device, load a receipt ID in Gemma chat, for example: `Receipt ID ABCD1234EFGH`.");
        return sb.toString();
    }

    private boolean contextContainsAny(String... needles) {
        if (!hasCaseContext() || needles == null) {
            return false;
        }
        String lower = latestCaseContext.contextText.toLowerCase(Locale.US);
        for (String needle : needles) {
            if (!TextUtils.isEmpty(needle) && lower.contains(needle.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKeyword(String[] keywords, String target) {
        if (keywords == null || TextUtils.isEmpty(target)) {
            return false;
        }
        for (String keyword : keywords) {
            if (target.equalsIgnoreCase(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String[] mergeKeywords(String[]... groups) {
        Set<String> merged = new LinkedHashSet<>();
        if (groups != null) {
            for (String[] group : groups) {
                if (group == null) {
                    continue;
                }
                for (String item : group) {
                    if (!TextUtils.isEmpty(item)) {
                        merged.add(item.toLowerCase(Locale.US));
                    }
                }
            }
        }
        return merged.toArray(new String[0]);
    }

    private boolean hasCaseContext() {
        return latestCaseContext != null
                && latestCaseContext.available
                && latestCaseContext.contextText != null
                && !latestCaseContext.contextText.trim().isEmpty();
    }

    private String firstNonEmptySection(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            if (!TextUtils.isEmpty(candidate) && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private String extractContextSection(String heading) {
        if (!hasCaseContext() || TextUtils.isEmpty(heading)) {
            return "";
        }
        String text = latestCaseContext.contextText;
        int start = text.indexOf(heading);
        if (start < 0) {
            return "";
        }
        start += heading.length();
        int end = text.length();
        String[] markers = new String[]{
                "Compact case brief:",
                "Gemma narrative report excerpt:",
                "Auditor forensic report excerpt:",
                "Findings package excerpt:"
        };
        for (String marker : markers) {
            if (marker.equals(heading)) {
                continue;
            }
            int candidate = text.indexOf(marker, start);
            if (candidate >= 0 && candidate < end) {
                end = candidate;
            }
        }
        return text.substring(start, end).trim();
    }

    private String[] extractKeywords(String message) {
        if (TextUtils.isEmpty(message)) {
            return new String[0];
        }
        String[] raw = message.toLowerCase(Locale.US).split("[^a-z0-9]+");
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : raw) {
            if (token.length() < 4) {
                continue;
            }
            if (isStopWord(token)) {
                continue;
            }
            keywords.add(token);
        }
        return keywords.toArray(new String[0]);
    }

    private String[] resolveConversationKeywords(String message) {
        Set<String> keywords = new LinkedHashSet<>();
        addKeywords(keywords, extractKeywords(message));
        if (keywords.size() >= 3 && !isFollowUpMessage(message == null ? "" : message.trim().toLowerCase(Locale.US))) {
            return keywords.toArray(new String[0]);
        }
        if (currentSession == null || currentSession.messages == null) {
            return keywords.toArray(new String[0]);
        }
        for (int i = currentSession.messages.size() - 1; i >= 0 && keywords.size() < 8; i--) {
            GemmaChatStore.Message item = currentSession.messages.get(i);
            if (item == null || TextUtils.isEmpty(item.text)) {
                continue;
            }
            addKeywords(keywords, extractKeywords(item.text));
        }
        return keywords.toArray(new String[0]);
    }

    private void addKeywords(Set<String> target, String[] source) {
        if (target == null || source == null) {
            return;
        }
        for (String item : source) {
            if (!TextUtils.isEmpty(item)) {
                target.add(item);
            }
        }
    }

    private boolean isStopWord(String token) {
        switch (token) {
            case "what":
            case "when":
            case "where":
            case "which":
            case "about":
            case "have":
            case "with":
            case "from":
            case "that":
            case "this":
            case "your":
            case "there":
            case "their":
            case "them":
            case "then":
            case "case":
            case "show":
            case "tell":
            case "need":
            case "next":
            case "please":
            case "could":
            case "would":
            case "should":
            case "into":
            case "just":
            case "latest":
                return true;
            default:
                return false;
        }
    }

    private String findRelevantLines(String source, String[] keywords, int maxLines) {
        if (TextUtils.isEmpty(source) || keywords == null || keywords.length == 0) {
            return "";
        }
        String[] lines = source.split("\\r?\\n");
        List<ScoredLine> scored = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty() || isNoiseLine(line)) {
                continue;
            }
            int score = scoreLine(line, keywords);
            if (score > 0) {
                scored.add(new ScoredLine(i, line, score));
            }
        }
        if (scored.isEmpty()) {
            return "";
        }
        scored.sort((left, right) -> {
            if (right.score != left.score) {
                return Integer.compare(right.score, left.score);
            }
            return Integer.compare(left.index, right.index);
        });
        StringBuilder sb = new StringBuilder();
        Set<String> used = new LinkedHashSet<>();
        int count = 0;
        for (ScoredLine line : scored) {
            String compact = normalizeWhitespace(line.text);
            if (used.contains(compact)) {
                continue;
            }
            used.add(compact);
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("• ").append(compact);
            count++;
            if (count >= maxLines) {
                break;
            }
        }
        return sb.toString();
    }

    private int scoreLine(String line, String[] keywords) {
        String lower = line.toLowerCase(Locale.US);
        int score = 0;
        for (String keyword : keywords) {
            if (TextUtils.isEmpty(keyword)) {
                continue;
            }
            if (lower.contains(keyword.toLowerCase(Locale.US))) {
                score += 2;
            }
        }
        if (lower.contains("page ")) {
            score += 1;
        }
        if (lower.contains("pages ")) {
            score += 1;
        }
        if (lower.contains("fraud") || lower.contains("forg") || lower.contains("admission")) {
            score += 1;
        }
        return score;
    }

    private boolean isNoiseLine(String line) {
        String lower = line.toLowerCase(Locale.US);
        return lower.startsWith("latest case context for gemma")
                || lower.startsWith("case id:")
                || lower.startsWith("source file:")
                || lower.startsWith("jurisdiction:")
                || lower.startsWith("processing status:")
                || lower.startsWith("compact case brief:")
                || lower.startsWith("gemma narrative report excerpt:")
                || lower.startsWith("auditor forensic report excerpt:")
                || lower.startsWith("findings package excerpt:");
    }

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String clipText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars)).trim() + "...";
    }

    private static final class ScoredLine {
        final int index;
        final String text;
        final int score;

        ScoredLine(int index, String text, int score) {
            this.index = index;
            this.text = text;
            this.score = score;
        }
    }

    private void addBubble(String speaker, String message, boolean isTransient) {
        TextView bubble = new TextView(this);
        bubble.setText(speaker + "\n" + message);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(16f);
        bubble.setLineSpacing(0f, 1.15f);
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
        bubble.setBackgroundResource(isUserSpeaker(speaker)
                ? R.drawable.bg_gemma_bubble_user
                : R.drawable.bg_gemma_bubble_assistant);
        if (isTransient) {
            bubble.setAlpha(0.82f);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = isUserSpeaker(speaker) ? Gravity.END : Gravity.START;
        params.bottomMargin = dp(10);
        bubble.setLayoutParams(params);
        messagesContainer.addView(bubble);
    }

    private boolean isUserSpeaker(String speaker) {
        return "You".equalsIgnoreCase(speaker);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
