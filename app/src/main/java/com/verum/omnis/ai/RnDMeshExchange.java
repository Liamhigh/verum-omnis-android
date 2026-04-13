package com.verum.omnis.ai;

import android.content.Context;
import android.os.Build;

import com.verum.omnis.core.ReportMailer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Stateless Mesh Exchange
 * - Exports/Imports **training directives** only (no case data)
 * - Supports both file-based export and email-based export
 * - Includes integrity fields (sha512 over payload)
 */
public class RnDMeshExchange {

    public static class MeshPacket {
        public String schema = "verum.mesh.v1";
        public String templateVersion = "5.1.1";
        public String appVersion = "5.2.6";
        public String timestampUtc;   // ISO8601
        public JSONObject directives; // from RnDController.Feedback.report.directive
        public JSONObject stats;      // counts & risk
        public String sha512;         // over {schema,templateVersion,appVersion,timestampUtc,directives,stats}
    }

    /**
     * Export packet to a local file (external-files if available).
     */
    public static File exportPacketToFile(Context ctx, RnDController.Feedback fb) throws Exception {
        MeshPacket pkt = buildPacket(fb);

        JSONObject payload = new JSONObject();
        payload.put("schema", pkt.schema);
        payload.put("templateVersion", pkt.templateVersion);
        payload.put("appVersion", pkt.appVersion);
        payload.put("timestampUtc", pkt.timestampUtc);
        payload.put("directives", pkt.directives);
        payload.put("stats", pkt.stats);

        String body = payload.toString();
        pkt.sha512 = sha512(body.getBytes(StandardCharsets.UTF_8));

        JSONObject out = new JSONObject(body);
        out.put("sha512", pkt.sha512);

        // write to app external files if available, else cache
        File dir;
        if (Build.VERSION.SDK_INT >= 19 && ctx.getExternalFilesDir(null) != null) {
            dir = ctx.getExternalFilesDir(null);
        } else {
            dir = ctx.getCacheDir();
        }
        File f = new File(dir, "verum_mesh_" + pkt.timestampUtc.replaceAll("[:\\-T]", "") + ".json");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(out.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    /**
     * Export packet by email (signed/timestamped JSON attachment).
     */
    /**
     * Export packet by email (signed/timestamped JSON attachment).
     */
    public static void exportPacketByEmail(Context ctx,
                                           RnDController.Feedback fb,
                                           com.verum.omnis.core.AnalysisEngine.ForensicReport report,
                                           String smtpHost, int smtpPort,
                                           String username, String password,
                                           String recipient) throws Exception {
        MeshPacket pkt = buildPacket(fb);

        // Base payload
        JSONObject payload = new JSONObject();
        payload.put("schema", pkt.schema);
        payload.put("templateVersion", pkt.templateVersion);
        payload.put("appVersion", pkt.appVersion);
        payload.put("timestampUtc", pkt.timestampUtc);
        payload.put("directives", pkt.directives);
        payload.put("stats", pkt.stats);

        // Add forensic summary
        if (report != null) {
            JSONObject summary = new JSONObject();
            summary.put("evidence_hash", report.evidenceHash);
            summary.put("risk_score", report.riskScore);
            summary.put("jurisdiction", report.jurisdiction);
            summary.put("blockchain_anchor", report.blockchainAnchor);
            if (report.topLiabilities != null) {
                summary.put("top_liabilities", report.topLiabilities);
            }
            payload.put("forensic_report", summary);
        }

        // Add ledger entry if present
        if (report != null && report.ledgerEntry != null) {
            JSONObject entry = new JSONObject();
            entry.put("case_id", report.ledgerEntry.caseId);
            entry.put("fraud_amount", report.ledgerEntry.fraudAmount);
            entry.put("fraud_amount_usd", report.ledgerEntry.fraudAmountUsd);
            entry.put("currency", report.ledgerEntry.currency);
            entry.put("party_name", report.ledgerEntry.partyName);
            entry.put("party_jurisdiction", report.ledgerEntry.partyJurisdiction);
            entry.put("source_sha512", report.ledgerEntry.sourceSha512);
            entry.put("detected_at", report.ledgerEntry.detectedAt);
            entry.put("detected_by", report.ledgerEntry.detectedBy);
            entry.put("entry_sha512", report.ledgerEntry.entrySha512);
            payload.put("ledger_entry", entry);
        }

        // Sign payload
        String body = payload.toString();
        pkt.sha512 = sha512(body.getBytes(StandardCharsets.UTF_8));
        payload.put("sha512", pkt.sha512);

        // Send via mailer
        ReportMailer.sendReport(ctx, smtpHost, smtpPort, username, password, recipient, payload);
    }

    /**
     * Import and apply a mesh packet from JSON string.
     */
    public static boolean importAndApply(Context ctx, String jsonString, RnDController.Feedback sink) {
        try {
            JSONObject obj = new JSONObject(jsonString);
            String sha = obj.optString("sha512", "");
            JSONObject copy = new JSONObject(obj.toString());
            copy.remove("sha512");
            String recomputed = sha512(copy.toString().getBytes(StandardCharsets.UTF_8));
            if (!sha.equalsIgnoreCase(recomputed)) return false; // integrity fail

            // merge directives: logical OR with existing directive if present
            JSONObject dir = obj.optJSONObject("directives");
            if (dir != null && sink != null && sink.report != null) {
                JSONObject existing = sink.report.optJSONObject("directive");
                if (existing == null) existing = new JSONObject();
                JSONObject merged = new JSONObject();
                // keys we know
                String[] keys = new String[]{
                        "prioritize_contradictions","prioritize_concealment",
                        "tighten_evasion_threshold","reinforce_financial_flags",
                        "min_keywords_entities"
                };
                for (String k : keys) {
                    boolean a = existing.optBoolean(k, false);
                    boolean b = dir.optBoolean(k, false);
                    merged.put(k, a || b);
                }
                sink.report.put("directive", merged);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String readFile(File f) throws Exception {
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            bytes = bos.toByteArray();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // === Helpers ===

    private static MeshPacket buildPacket(RnDController.Feedback fb) throws Exception {
        MeshPacket pkt = new MeshPacket();
        pkt.timestampUtc = isoNow();
        pkt.directives = fb.report.optJSONObject("directive");
        JSONObject stats = new JSONObject();
        stats.put("risk_score", fb.report.optDouble("risk_score", 0.0));
        stats.put("contradictions", fb.report.optInt("contradictions_hits", 0));
        stats.put("concealment", fb.report.optInt("concealment_hits", 0));
        stats.put("evasion", fb.report.optInt("evasion_hits", 0));
        stats.put("financial", fb.report.optInt("financial_hits", 0));
        stats.put("keywords", fb.report.optInt("keywords_hits", 0));
        stats.put("entities", fb.report.optInt("entities_hits", 0));
        pkt.stats = stats;
        return pkt;
    }

    private static String sha512(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(b);
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String isoNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
