package com.verum.omnis.forensic;

import android.content.Context;

import com.verum.omnis.core.PDFSealer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Recovery Ledger (business-only)
 * - Local JSONL file under app files dir
 * - Each entry sealed with SHA-512 and mirrored as a sealed PDF
 * - Ignores private citizens; logs only if responsible_party looks like a business
 */
public class RecoveryLedger {

    public static class Entry {
        public String caseId;
        public double fraudAmount;       // original amount
        public double fraudAmountUsd;    // normalized to USD
        public String currency;
        public String partyName;
        public String partyJurisdiction;
        public String sourceSha512;
        public String detectedAt; // ISO-8601 UTC
        public String detectedBy; // app version
        public String entrySha512;
        public File sealedPdf; // sealed PDF generated automatically
    }

    private static final String[] BUSINESS_TOKENS = new String[]{
            "ltd","(pty)","pty","llc","inc","corp","gmbh","sarl","bv","plc","limited",
            "proprietary","company","co.","s.a.","ag","oy","ab","kft","s.p.a","srl"
    };

    public static boolean looksLikeBusiness(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String t : BUSINESS_TOKENS) {
            if (n.contains(t)) return true;
        }
        return false;
    }

    public static Entry create(Context ctx,
                               String caseId,
                               double amount,
                               double amountUsd,
                               String currency,
                               String partyName,
                               String partyJurisdiction,
                               String sourceSha512,
                               String appVersion) throws Exception {
        if (!com.verum.omnis.ai.CompanyDetector.looksLikeBusiness(partyName)) return null;

        Entry e = new Entry();
        e.caseId = caseId;
        e.fraudAmount = amount;
        e.fraudAmountUsd = amountUsd;
        e.currency = currency;
        e.partyName = partyName;
        e.partyJurisdiction = partyJurisdiction;
        e.sourceSha512 = sourceSha512;
        e.detectedAt = isoNow();
        e.detectedBy = appVersion;

        JSONObject payload = new JSONObject();
        payload.put("case_id", caseId);
        payload.put("fraud_amount", amount);
        payload.put("fraud_amount_usd", amountUsd);
        payload.put("currency", currency);
        payload.put("party_name", partyName);
        payload.put("party_jurisdiction", partyJurisdiction);
        payload.put("source_sha512", sourceSha512);
        payload.put("detected_at", e.detectedAt);
        payload.put("detected_by", appVersion);

        e.entrySha512 = sha512(payload.toString().getBytes(StandardCharsets.UTF_8));

        File ledger = new File(VaultManager.getVaultDir(ctx), "recovery_ledger.jsonl");
        try (FileOutputStream fos = new FileOutputStream(ledger, true)) {
            JSONObject out = new JSONObject(payload.toString());
            out.put("entry_sha512", e.entrySha512);
            fos.write((out.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        }

        File outFile = VaultManager.createVaultFile(ctx, "recovery-ledger", ".pdf");
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "Verum Omnis Recovery Ledger Seal";
        req.summary = "Business recovery ledger entry sealed from the constitutional recovery ledger.";
        req.includeQr = true;
        req.includeHash = true;
        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
        req.evidenceHash = e.entrySha512;
        req.caseId = caseId;
        req.jurisdiction = partyJurisdiction;
        req.legalSummary = "Business-only recovery ledger entry for " + partyName + " in " + currency + ".";
        req.sourceFileName = ledger.getName();
        PDFSealer.generateSealedPdf(ctx, req, outFile);
        e.sealedPdf = outFile;

        return e;
    }

    private static String isoNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private static String sha512(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(b);
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
