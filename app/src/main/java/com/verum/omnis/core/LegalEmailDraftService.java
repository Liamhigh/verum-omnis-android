package com.verum.omnis.core;

import android.content.Context;

import com.verum.omnis.forensic.VaultManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegalEmailDraftService {

    public static final class DraftResult {
        public final boolean readyToCompose;
        public final String userMessage;
        public final String recipientEmail;
        public final String subject;
        public final String mailBody;
        public final File attachmentPdf;
        public final String caseId;

        public DraftResult(
                boolean readyToCompose,
                String userMessage,
                String recipientEmail,
                String subject,
                String mailBody,
                File attachmentPdf,
                String caseId
        ) {
            this.readyToCompose = readyToCompose;
            this.userMessage = userMessage;
            this.recipientEmail = recipientEmail;
            this.subject = subject;
            this.mailBody = mailBody;
            this.attachmentPdf = attachmentPdf;
            this.caseId = caseId;
        }
    }

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE);

    private LegalEmailDraftService() {}

    public static DraftResult prepare(
            Context context,
            GemmaCaseContextStore.Snapshot snapshot,
            String userRequest
    ) {
        if (snapshot == null || !snapshot.available || snapshot.contextText == null || snapshot.contextText.trim().isEmpty()) {
            return new DraftResult(
                    false,
                    "Run the forensic scan first. I only generate governed legal email drafts from a sealed case context.",
                    "",
                    "",
                    "",
                    null,
                    ""
            );
        }

        String recipientEmail = extractRecipientEmail(userRequest);
        LegalEmailGovernanceManager.Decision decision = LegalEmailGovernanceManager.assess(context, recipientEmail);
        if (!decision.allowed) {
            return new DraftResult(
                    false,
                    decision.summary() + "\n\nI did not generate a draft because the contact cadence needs to stay controlled.",
                    recipientEmail,
                    "",
                    "",
                    null,
                    snapshot.caseId
            );
        }

        String purpose = inferPurpose(userRequest);
        String subject = buildSubject(snapshot, purpose);
        String mailBody = buildMailBody(snapshot, purpose);
        String attachmentBody = buildAttachmentBody(snapshot, recipientEmail, subject, purpose, userRequest, decision);

        try {
            File attachment = VaultManager.createVaultFile(
                    context,
                    "sealed-legal-email-draft",
                    ".pdf",
                    snapshot.sourceFile
            );

            PDFSealer.SealRequest req = new PDFSealer.SealRequest();
            req.title = "VERUM OMNIS - SEALED LEGAL EMAIL DRAFT";
            req.summary = "System-generated legal correspondence draft for " + recipientEmail;
            req.caseId = snapshot.caseId;
            req.jurisdiction = "Governed legal correspondence";
            req.legalSummary = "This draft is generated from the sealed case context. The operative communication is the sealed PDF attachment, not free-text edits.";
            req.bodyText = attachmentBody;
            req.intakeMetadata = "Generated locally on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            req.sourceFileName = snapshot.sourceFile;
            req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
            req.evidenceHash = HashUtil.sha512(attachmentBody.getBytes(StandardCharsets.UTF_8));
            PDFSealer.generateSealedPdf(context, req, attachment);

            return new DraftResult(
                    true,
                    "I prepared a governed legal email draft for " + recipientEmail
                            + " and sealed it as a PDF attachment in the vault.\n\n"
                            + decision.summary()
                            + "\n\nThe email body will stay minimal and the legal position will remain inside the sealed PDF attachment.",
                    recipientEmail,
                    subject,
                    mailBody,
                    attachment,
                    snapshot.caseId
            );
        } catch (Exception e) {
            return new DraftResult(
                    false,
                    "I could not prepare the sealed legal email draft: " + e.getMessage(),
                    recipientEmail,
                    subject,
                    "",
                    null,
                    snapshot.caseId
            );
        }
    }

    private static String extractRecipientEmail(String userRequest) {
        if (userRequest == null) {
            return "";
        }
        Matcher matcher = EMAIL_PATTERN.matcher(userRequest);
        return matcher.find() ? matcher.group().trim() : "";
    }

    private static String inferPurpose(String userRequest) {
        String normalized = userRequest == null ? "" : userRequest.toLowerCase(Locale.US);
        if (normalized.contains("compensation") || normalized.contains("demand money")
                || normalized.contains("payment") || normalized.contains("settlement")) {
            return "formal demand for payment or compensation";
        }
        if (normalized.contains("respond") || normalized.contains("reply") || normalized.contains("response")) {
            return "formal legal response";
        }
        if (normalized.contains("cease") || normalized.contains("stop")) {
            return "formal notice to cease the challenged conduct";
        }
        if (normalized.contains("preserve") || normalized.contains("spoliation")) {
            return "preservation and evidence notice";
        }
        return "formal case-linked legal correspondence";
    }

    private static String buildSubject(GemmaCaseContextStore.Snapshot snapshot, String purpose) {
        String caseId = snapshot.caseId == null ? "case" : snapshot.caseId;
        if (purpose.contains("payment") || purpose.contains("compensation")) {
            return "Verum Omnis sealed demand for payment - " + caseId;
        }
        if (purpose.contains("response")) {
            return "Verum Omnis sealed legal response - " + caseId;
        }
        if (purpose.contains("cease")) {
            return "Verum Omnis sealed notice - " + caseId;
        }
        if (purpose.contains("preservation")) {
            return "Verum Omnis sealed evidence notice - " + caseId;
        }
        return "Verum Omnis sealed legal correspondence - " + caseId;
    }

    private static String buildMailBody(GemmaCaseContextStore.Snapshot snapshot, String purpose) {
        return "Please find attached a cryptographically sealed Verum Omnis PDF generated from the local case context for "
                + snapshot.caseId
                + ".\n\n"
                + "Purpose: " + purpose + ".\n"
                + "The operative legal communication is the sealed PDF attachment.\n"
                + "This body is intentionally kept minimal to avoid uncontrolled or inaccurate legal wording.";
    }

    private static String buildAttachmentBody(
            GemmaCaseContextStore.Snapshot snapshot,
            String recipientEmail,
            String subject,
            String purpose,
            String userRequest,
            LegalEmailGovernanceManager.Decision decision
    ) {
        String caseExcerpt = clip(snapshot.contextText, 2600);
        return "Recipient: " + recipientEmail + "\n"
                + "Subject: " + subject + "\n"
                + "Case ID: " + snapshot.caseId + "\n"
                + "Source file: " + snapshot.sourceFile + "\n"
                + "Purpose: " + purpose + "\n"
                + "Governance: " + decision.summary() + "\n"
                + "Instruction: This draft was generated by the system from the sealed case context. Free-form legal wording is intentionally restricted.\n\n"
                + "Draft email text\n"
                + "---------------\n"
                + "Dear Recipient,\n\n"
                + "This communication concerns " + snapshot.caseId + " and the attached sealed Verum Omnis record.\n"
                + "The attached PDF is the governing communication generated from the forensic case context presently stored on-device.\n"
                + "It is issued for the purpose of " + purpose + ".\n\n"
                + "You are required to review the attached record, preserve relevant evidence, and respond in writing within a reasonable period.\n"
                + "If the issue concerns payment, compensation, or cessation of conduct, the attached record should be treated as the structured basis of that demand.\n"
                + "Future communication should remain in writing and should engage the sealed record directly rather than informal narrative.\n\n"
                + "Regards,\n"
                + "Verum Omnis governed correspondence layer\n\n"
                + "User request captured for draft generation\n"
                + "----------------------------------------\n"
                + clip(userRequest, 700)
                + "\n\n"
                + "Compact sealed case context excerpt\n"
                + "---------------------------------\n"
                + caseExcerpt;
    }

    private static String clip(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + " ...[continued in vault context]...";
    }
}
