package com.verum.omnis.rules;

import android.util.Log;

import com.verum.omnis.BuildConfig;

import org.json.JSONObject;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class VerumMailer {
    private static final String TAG = "VerumMailer";

    // === Twilio SendGrid SMTP values ===
    private static final String SMTP_HOST = "smtp.sendgrid.net";
    private static final String SMTP_PORT = "587";
    // Username is literally the string "apikey" for SendGrid
    private static final String USERNAME = "apikey";
    // Loaded from local/private Gradle properties.
    private static final String PASSWORD = BuildConfig.SENDGRID_API_KEY;

    // Common recipients
    private static final String MAIL_TO_AUDIT   = "audit@verumglobal.foundation";
    private static final String MAIL_TO_OWNER   = "liam@verumglobal.foundation";
    private static final String MAIL_TO_SUPPORT = "support@verumglobal.foundation";

    public static void sendUploadReceived(String caseId, String filename, String size, String sha256) {
        sendUploadReceived(caseId, filename, size, sha256, null);
    }

    public static void sendUploadReceived(String caseId, String filename, String size, String sha256, JSONObject payload) {
        String subject = "VO • Upload received • " + caseId;
        String body = buildEventBody("upload_received", caseId, filename, size, sha256, payload);
        send(MAIL_TO_AUDIT, subject, body, MAIL_TO_OWNER);
    }

    public static void sendContradiction(String caseId, String details, String hash) {
        sendContradiction(caseId, details, hash, null);
    }

    public static void sendContradiction(String caseId, String details, String hash, JSONObject payload) {
        String subject = "VO • CONTRADICTION • " + caseId;
        String body = buildEventBody("contradiction_notice", caseId, details, "", hash, payload);
        send(MAIL_TO_AUDIT, subject, body, MAIL_TO_OWNER);
    }

    public static void sendError(String caseId, String details) {
        String subject = "VO • ERROR • " + caseId;
        String body = "Error: " + details;
        send(MAIL_TO_SUPPORT, subject, body, MAIL_TO_OWNER);
    }

    private static String buildEventBody(
            String eventType,
            String caseId,
            String primaryValue,
            String secondaryValue,
            String hash,
            JSONObject payload
    ) {
        try {
            if (payload != null && looksLikeForensicReportPayload(payload)) {
                JSONObject reportPayload = new JSONObject(payload.toString());
                return buildMailFriendlyReportPayload(reportPayload, caseId).toString(2);
            }
            JSONObject root = new JSONObject();
            root.put("eventType", eventType);
            root.put("caseId", caseId == null ? "N/A" : caseId);
            if (primaryValue != null && !primaryValue.trim().isEmpty()) {
                root.put("primary", primaryValue.trim());
            }
            if (secondaryValue != null && !secondaryValue.trim().isEmpty()) {
                root.put("secondary", secondaryValue.trim());
            }
            if (hash != null && !hash.trim().isEmpty()) {
                root.put("hash", hash.trim());
            }
            if (payload != null) {
                JSONObject report = new JSONObject(payload.toString());
                root.put("forensicReport", report);
                java.util.Iterator<String> keys = report.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key == null || key.trim().isEmpty() || "forensicReport".equals(key)) {
                        continue;
                    }
                    Object value = report.opt(key);
                    if (value != null) {
                        root.put(key, value);
                    }
                }
                if (!root.has("guardianDecision")) {
                    root.put("guardianDecision", new JSONObject());
                }
                if (!root.has("certifiedFindings")) {
                    root.put("certifiedFindings", report.optJSONArray("certifiedFindings") != null ? report.optJSONArray("certifiedFindings") : new org.json.JSONArray());
                }
            }
            return root.toString(2);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append("Event: ").append(eventType).append("\n");
            sb.append("Case ID: ").append(caseId).append("\n");
            if (primaryValue != null && !primaryValue.trim().isEmpty()) {
                sb.append("Primary: ").append(primaryValue).append("\n");
            }
            if (secondaryValue != null && !secondaryValue.trim().isEmpty()) {
                sb.append("Secondary: ").append(secondaryValue).append("\n");
            }
            if (hash != null && !hash.trim().isEmpty()) {
                sb.append("Hash: ").append(hash).append("\n");
            }
            return sb.toString();
        }
    }

    private static boolean looksLikeForensicReportPayload(JSONObject payload) {
        if (payload == null) {
            return false;
        }
        return payload.has("caseId")
                && payload.has("evidenceHash")
                && payload.has("diagnostics");
    }

    private static JSONObject buildMailFriendlyReportPayload(JSONObject reportPayload, String fallbackCaseId) throws Exception {
        JSONObject root = new JSONObject();
        root.put("caseId", firstNonBlank(reportPayload.optString("caseId", null), fallbackCaseId, "N/A"));
        root.put("evidenceHash", reportPayload.optString("evidenceHash", ""));
        root.put("guardianDecision", reportPayload.optJSONObject("guardianDecision") != null
                ? reportPayload.optJSONObject("guardianDecision")
                : new JSONObject());
        root.put("certifiedFindings", reportPayload.optJSONArray("certifiedFindings") != null
                ? reportPayload.optJSONArray("certifiedFindings")
                : new org.json.JSONArray());
        copyIfPresent(root, reportPayload, "jurisdiction");
        copyIfPresent(root, reportPayload, "jurisdictionName");
        copyIfPresent(root, reportPayload, "jurisdictionAnchor");
        copyIfPresent(root, reportPayload, "summary");
        copyIfPresent(root, reportPayload, "blockchainAnchor");
        copyIfPresent(root, reportPayload, "sourceFile");
        copyIfPresent(root, reportPayload, "engineVersion");
        copyIfPresent(root, reportPayload, "rulesVersion");
        copyIfPresent(root, reportPayload, "generatedAt");
        copyIfPresent(root, reportPayload, "evidenceBundleHash");
        copyIfPresent(root, reportPayload, "deterministicRunId");
        copyIfPresent(root, reportPayload, "legalReferences");
        copyIfPresent(root, reportPayload, "topLiabilities");
        copyIfPresent(root, reportPayload, "diagnostics");
        copyIfPresent(root, reportPayload, "behavioralProfile");
        copyIfPresent(root, reportPayload, "nativeEvidence");
        copyIfPresent(root, reportPayload, "constitutionalExtraction");
        copyIfPresent(root, reportPayload, "truthContinuityAnalysis");
        copyIfPresent(root, reportPayload, "patternAnalysis");
        copyIfPresent(root, reportPayload, "vulnerabilityAnalysis");
        copyIfPresent(root, reportPayload, "rndAnalysis");
        copyIfPresent(root, reportPayload, "brainAnalysis");
        copyIfPresent(root, reportPayload, "humanReadableReport");
        copyIfPresent(root, reportPayload, "auditorReport");
        return root;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static void copyIfPresent(JSONObject target, JSONObject source, String key) throws Exception {
        if (target == null || source == null || key == null || key.trim().isEmpty()) {
            return;
        }
        if (source.has(key) && !source.isNull(key)) {
            target.put(key, source.get(key));
        }
    }

    private static void send(String to, String subject, String body, String cc) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(USERNAME, PASSWORD);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("notifications@verumglobal.foundation"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            if (cc != null) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);

            Log.d(TAG, "Email sent to " + to + " (cc: " + cc + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to send email", e);
        }
    }
}
