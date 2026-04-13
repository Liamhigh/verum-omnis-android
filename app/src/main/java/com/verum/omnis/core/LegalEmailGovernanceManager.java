package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

public final class LegalEmailGovernanceManager {

    public static final class Decision {
        public final boolean allowed;
        public final String recipient;
        public final int draftsLast24h;
        public final int draftsLast30d;
        public final long nextAllowedAtMs;
        public final String reason;

        public Decision(
                boolean allowed,
                String recipient,
                int draftsLast24h,
                int draftsLast30d,
                long nextAllowedAtMs,
                String reason
        ) {
            this.allowed = allowed;
            this.recipient = recipient;
            this.draftsLast24h = draftsLast24h;
            this.draftsLast30d = draftsLast30d;
            this.nextAllowedAtMs = nextAllowedAtMs;
            this.reason = reason;
        }

        public String summary() {
            if (allowed) {
                return "Governance check passed for " + recipient
                        + ". Drafts opened for this recipient: "
                        + draftsLast24h + " in the last 24h, "
                        + draftsLast30d + " in the last 30 days.";
            }
            String waitUntil = nextAllowedAtMs > 0
                    ? new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(new Date(nextAllowedAtMs))
                    : "a later date";
            return "Governance hold: " + reason
                    + " Recipient: " + recipient
                    + ". Next safe draft window: " + waitUntil + ".";
        }
    }

    private static final String FILE_NAME = "legal_email_governance.json";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long THIRTY_DAYS_MS = 30L * DAY_MS;
    private static final int MAX_PER_24H = 1;
    private static final int MAX_PER_30D = 4;

    private LegalEmailGovernanceManager() {}

    public static Decision assess(Context context, String recipient) {
        String normalizedRecipient = normalizeRecipient(recipient);
        if (normalizedRecipient.isEmpty()) {
            return new Decision(false, "", 0, 0, 0L,
                    "No recipient email was detected in the request");
        }

        JSONArray entries = loadEntries(context);
        long now = System.currentTimeMillis();
        int draftsLast24h = 0;
        int draftsLast30d = 0;
        long next24h = 0L;
        long next30d = 0L;

        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (!normalizedRecipient.equals(normalizeRecipient(item.optString("recipient", "")))) {
                continue;
            }
            long createdAt = item.optLong("createdAtUtcMs", 0L);
            if (createdAt <= 0L || createdAt > now) {
                continue;
            }
            long age = now - createdAt;
            if (age <= DAY_MS) {
                draftsLast24h++;
                next24h = Math.max(next24h, createdAt + DAY_MS);
            }
            if (age <= THIRTY_DAYS_MS) {
                draftsLast30d++;
                next30d = Math.max(next30d, createdAt + THIRTY_DAYS_MS);
            }
        }

        if (draftsLast24h >= MAX_PER_24H) {
            return new Decision(
                    false,
                    normalizedRecipient,
                    draftsLast24h,
                    draftsLast30d,
                    next24h,
                    "a legal email draft was already opened for this recipient within the last 24 hours"
            );
        }
        if (draftsLast30d >= MAX_PER_30D) {
            return new Decision(
                    false,
                    normalizedRecipient,
                    draftsLast24h,
                    draftsLast30d,
                    next30d,
                    "the recipient has already received the maximum governed draft volume for the last 30 days"
            );
        }
        return new Decision(true, normalizedRecipient, draftsLast24h, draftsLast30d, 0L, "");
    }

    public static void recordDraft(Context context, String recipient, String caseId, String subject) {
        String normalizedRecipient = normalizeRecipient(recipient);
        if (normalizedRecipient.isEmpty()) {
            return;
        }

        JSONArray entries = loadEntries(context);
        try {
            JSONObject entry = new JSONObject();
            entry.put("recipient", normalizedRecipient);
            entry.put("caseId", caseId == null ? "" : caseId);
            entry.put("subject", subject == null ? "" : subject);
            entry.put("createdAtUtcMs", System.currentTimeMillis());
            entries.put(entry);
        } catch (Exception ignored) {
            return;
        }

        JSONArray trimmed = new JSONArray();
        int start = Math.max(0, entries.length() - 120);
        for (int i = start; i < entries.length(); i++) {
            trimmed.put(entries.opt(i));
        }
        writeEntries(context, trimmed);
    }

    private static JSONArray loadEntries(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new JSONArray();
        }
        try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8.name())) {
            String json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            return new JSONArray(json);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void writeEntries(Context context, JSONArray entries) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(entries.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static String normalizeRecipient(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.US);
    }
}
