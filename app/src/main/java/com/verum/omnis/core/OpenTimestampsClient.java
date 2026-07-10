package com.verum.omnis.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Minimal OpenTimestamps stamping client. Submits the SHA-256 of a complete
 * sealed document to an OpenTimestamps calendar server and assembles a detached
 * {@code .ots} proof anchoring that document to the Bitcoin blockchain.
 *
 * <p>Flow (matches the standard OpenTimestamps model):</p>
 * <ol>
 *   <li>Compute SHA-256 of the finished document (done last, over everything).</li>
 *   <li>POST that 32-byte digest to a calendar's {@code /digest} endpoint; the
 *       calendar returns a serialized timestamp within seconds.</li>
 *   <li>Assemble the detached proof: header magic + version + SHA-256 file op +
 *       the file digest + the calendar timestamp.</li>
 * </ol>
 *
 * <p>The returned proof carries a <em>pending</em> calendar attestation
 * immediately; the Bitcoin block attestation appears after the calendar's next
 * Bitcoin transaction confirms (upgrade later). This is network-dependent and
 * must be invoked off the UI thread as an explicit, opt-in action — the offline
 * sealing pipeline never depends on it.</p>
 */
public final class OpenTimestampsClient {

    /** Public OpenTimestamps Bitcoin calendars, tried in order. */
    public static final String[] DEFAULT_CALENDARS = {
            "https://alice.btc.calendar.opentimestamps.org",
            "https://bob.btc.calendar.opentimestamps.org",
            "https://finney.calendar.eternitywall.com"
    };

    private static final byte[] HEADER_MAGIC = new byte[]{
            (byte) 0x00, 'O', 'p', 'e', 'n', 'T', 'i', 'm', 'e', 's', 't', 'a', 'm', 'p', 's', (byte) 0x00,
            (byte) 0x00, 'P', 'r', 'o', 'o', 'f', (byte) 0x00,
            (byte) 0xbf, (byte) 0x89, (byte) 0xe2, (byte) 0xe8, (byte) 0x84, (byte) 0xe8, (byte) 0x92, (byte) 0x94
    };
    private static final byte OP_SHA256 = (byte) 0x08;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    public static final class Result {
        public final boolean success;
        public final File otsFile;
        public final String sha256;
        public final String calendarUsed;
        public final String message;

        Result(boolean success, File otsFile, String sha256, String calendarUsed, String message) {
            this.success = success;
            this.otsFile = otsFile;
            this.sha256 = sha256;
            this.calendarUsed = calendarUsed;
            this.message = message;
        }
    }

    private OpenTimestampsClient() {
    }

    /**
     * Stamps a file: computes its SHA-256, requests a timestamp from a calendar,
     * and writes {@code <targetDir>/<file name>.ots}.
     */
    public static Result stampFile(File file, File otsOutputDir) {
        if (file == null || !file.exists() || !file.isFile()) {
            return new Result(false, null, "", "", "No readable document was available to anchor.");
        }
        byte[] digest;
        String sha256Hex;
        try {
            digest = sha256(file);
            sha256Hex = toHex(digest);
        } catch (Exception e) {
            return new Result(false, null, "", "", "Could not hash the document: " + e.getMessage());
        }

        String lastError = "No calendar could be reached.";
        for (String calendar : DEFAULT_CALENDARS) {
            try {
                byte[] calendarTimestamp = submitDigest(calendar, digest);
                byte[] ots = buildDetachedOts(digest, calendarTimestamp);
                File dir = otsOutputDir != null ? otsOutputDir : file.getParentFile();
                File otsFile = new File(dir, file.getName() + ".ots");
                try (FileOutputStream fos = new FileOutputStream(otsFile)) {
                    fos.write(ots);
                }
                return new Result(true, otsFile, sha256Hex, calendar,
                        "Anchored. Pending Bitcoin confirmation via " + calendar + ".");
            } catch (Exception e) {
                lastError = "Calendar " + calendar + " failed: " + e.getMessage();
            }
        }
        return new Result(false, null, sha256Hex, "", lastError);
    }

    /** POSTs a 32-byte digest to {@code <calendar>/digest} and returns the serialized timestamp. */
    public static byte[] submitDigest(String calendarBaseUrl, byte[] digest) throws Exception {
        URL url = new URL(calendarBaseUrl.replaceAll("/+$", "") + "/digest");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.opentimestamps.v1");
            conn.setRequestProperty("User-Agent", "verum-omnis");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setFixedLengthStreamingMode(digest.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(digest);
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                byte[] body = out.toByteArray();
                if (body.length == 0) {
                    throw new Exception("empty calendar response");
                }
                return body;
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Assembles a detached OpenTimestamps proof for a file whose SHA-256 digest
     * was submitted directly to the calendar.
     */
    public static byte[] buildDetachedOts(byte[] fileSha256, byte[] calendarTimestamp) {
        if (fileSha256 == null || fileSha256.length != 32) {
            throw new IllegalArgumentException("fileSha256 must be 32 bytes");
        }
        if (calendarTimestamp == null || calendarTimestamp.length == 0) {
            throw new IllegalArgumentException("calendarTimestamp must be non-empty");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER_MAGIC, 0, HEADER_MAGIC.length);
        out.write(0x01); // major version
        out.write(OP_SHA256);
        out.write(fileSha256, 0, fileSha256.length);
        out.write(calendarTimestamp, 0, calendarTimestamp.length);
        return out.toByteArray();
    }

    private static byte[] sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        return md.digest();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}
