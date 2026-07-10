package com.verum.omnis.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reader for detached OpenTimestamps proof files (the {@code .ots} sidecar that
 * anchors a document digest to the Bitcoin blockchain).
 *
 * <p>This reader performs deterministic, offline structural verification only:
 * it validates the proof header, extracts the file-hash operation and the
 * committed digest, and reports which blockchain attestations are present
 * (Bitcoin confirmed vs. pending calendar). Full trustless confirmation that a
 * Bitcoin attestation matches a real block header requires a Bitcoin
 * header source and is intentionally out of scope for this offline reader.</p>
 *
 * <p>Standard OpenTimestamps stamps the <em>SHA-256</em> of the complete file,
 * so {@link #committedDigestHex} is the SHA-256 of the sealed artifact "with
 * everything in it". Callers verify a proof belongs to a file by recomputing
 * that digest and comparing.</p>
 */
public final class OpenTimestampsProof {

    private static final byte[] HEADER_MAGIC = new byte[]{
            (byte) 0x00, 'O', 'p', 'e', 'n', 'T', 'i', 'm', 'e', 's', 't', 'a', 'm', 'p', 's', (byte) 0x00,
            (byte) 0x00, 'P', 'r', 'o', 'o', 'f', (byte) 0x00,
            (byte) 0xbf, (byte) 0x89, (byte) 0xe2, (byte) 0xe8, (byte) 0x84, (byte) 0xe8, (byte) 0x92, (byte) 0x94
    };

    private static final byte[] BITCOIN_TAG = {
            (byte) 0x05, (byte) 0x88, (byte) 0x96, (byte) 0x0d, (byte) 0x73, (byte) 0xd7, (byte) 0x19, (byte) 0x01
    };
    private static final byte[] PENDING_TAG = {
            (byte) 0x83, (byte) 0xdf, (byte) 0xe3, (byte) 0x0d, (byte) 0x2e, (byte) 0xf9, (byte) 0x0c, (byte) 0x8e
    };
    private static final byte[] LITECOIN_TAG = {
            (byte) 0x06, (byte) 0x86, (byte) 0x9a, (byte) 0x0d, (byte) 0x73, (byte) 0xd7, (byte) 0x1b, (byte) 0x45
    };

    public enum AnchorState {
        CONFIRMED_BITCOIN,
        PENDING,
        OTHER_CHAIN,
        NONE,
        INVALID
    }

    public final boolean valid;
    public final String fileHashAlgorithm;
    public final String committedDigestHex;
    public final boolean hasBitcoinAttestation;
    public final boolean hasPendingAttestation;
    public final boolean hasLitecoinAttestation;
    public final List<String> calendarUrls;
    public final String parseNote;

    private OpenTimestampsProof(
            boolean valid,
            String fileHashAlgorithm,
            String committedDigestHex,
            boolean hasBitcoinAttestation,
            boolean hasPendingAttestation,
            boolean hasLitecoinAttestation,
            List<String> calendarUrls,
            String parseNote
    ) {
        this.valid = valid;
        this.fileHashAlgorithm = fileHashAlgorithm;
        this.committedDigestHex = committedDigestHex;
        this.hasBitcoinAttestation = hasBitcoinAttestation;
        this.hasPendingAttestation = hasPendingAttestation;
        this.hasLitecoinAttestation = hasLitecoinAttestation;
        this.calendarUrls = Collections.unmodifiableList(calendarUrls);
        this.parseNote = parseNote;
    }

    private static OpenTimestampsProof invalid(String note) {
        return new OpenTimestampsProof(false, "", "", false, false, false, new ArrayList<>(), note);
    }

    public AnchorState anchorState() {
        if (!valid) {
            return AnchorState.INVALID;
        }
        if (hasBitcoinAttestation) {
            return AnchorState.CONFIRMED_BITCOIN;
        }
        if (hasPendingAttestation) {
            return AnchorState.PENDING;
        }
        if (hasLitecoinAttestation) {
            return AnchorState.OTHER_CHAIN;
        }
        return AnchorState.NONE;
    }

    public static OpenTimestampsProof parse(File otsFile) {
        if (otsFile == null || !otsFile.exists() || !otsFile.isFile()) {
            return invalid("No OpenTimestamps proof file was available.");
        }
        try (InputStream in = new FileInputStream(otsFile);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return parse(out.toByteArray());
        } catch (Exception e) {
            return invalid("Failed to read OpenTimestamps proof: " + e.getMessage());
        }
    }

    public static OpenTimestampsProof parse(byte[] data) {
        if (data == null || data.length < HEADER_MAGIC.length + 2) {
            return invalid("Proof is too short to be a valid OpenTimestamps file.");
        }
        for (int i = 0; i < HEADER_MAGIC.length; i++) {
            if (data[i] != HEADER_MAGIC[i]) {
                return invalid("Missing OpenTimestamps header magic.");
            }
        }

        int pos = HEADER_MAGIC.length;

        // Major version varint (value not needed beyond advancing the cursor).
        int[] versionCursor = readVarint(data, pos);
        if (versionCursor == null) {
            return invalid("Truncated version field.");
        }
        pos = versionCursor[1];

        if (pos >= data.length) {
            return invalid("Missing file-hash operation.");
        }

        int op = data[pos++] & 0xff;
        String algorithm;
        int digestLength;
        switch (op) {
            case 0x08:
                algorithm = "SHA-256";
                digestLength = 32;
                break;
            case 0x02:
                algorithm = "SHA-1";
                digestLength = 20;
                break;
            case 0x03:
                algorithm = "RIPEMD-160";
                digestLength = 20;
                break;
            default:
                return invalid(String.format(Locale.US, "Unsupported file-hash op 0x%02x.", op));
        }

        if (pos + digestLength > data.length) {
            return invalid("Truncated committed digest.");
        }
        String committedDigestHex = toHex(data, pos, digestLength);

        boolean bitcoin = indexOf(data, BITCOIN_TAG) >= 0;
        boolean pending = indexOf(data, PENDING_TAG) >= 0;
        boolean litecoin = indexOf(data, LITECOIN_TAG) >= 0;
        List<String> calendars = extractCalendarUrls(data);

        return new OpenTimestampsProof(
                true,
                algorithm,
                committedDigestHex,
                bitcoin,
                pending,
                litecoin,
                calendars,
                "Structural OpenTimestamps proof parsed. Bitcoin block-header confirmation is not performed offline."
        );
    }

    /** OpenTimestamps uses little-endian base-128 varints. Returns {value, nextPos} or null on truncation. */
    private static int[] readVarint(byte[] data, int start) {
        long value = 0;
        int shift = 0;
        int pos = start;
        while (pos < data.length) {
            int b = data[pos++] & 0xff;
            value |= ((long) (b & 0x7f)) << shift;
            if ((b & 0x80) == 0) {
                return new int[]{(int) value, pos};
            }
            shift += 7;
            if (shift > 63) {
                return null;
            }
        }
        return null;
    }

    private static List<String> extractCalendarUrls(byte[] data) {
        List<String> urls = new ArrayList<>();
        int from = 0;
        while (true) {
            int tagAt = indexOf(data, PENDING_TAG, from);
            if (tagAt < 0) {
                break;
            }
            from = tagAt + PENDING_TAG.length;
            // A pending attestation payload is a varint-length-prefixed blob that
            // itself holds a varint-length-prefixed UTF-8 calendar URL.
            int[] payloadLen = readVarint(data, from);
            if (payloadLen == null) {
                continue;
            }
            int[] urlLen = readVarint(data, payloadLen[1]);
            if (urlLen == null) {
                continue;
            }
            int urlStart = urlLen[1];
            int length = urlLen[0];
            if (length <= 0 || urlStart + length > data.length) {
                continue;
            }
            String url = new String(data, urlStart, length, java.nio.charset.StandardCharsets.UTF_8);
            if (url.startsWith("http")) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        return indexOf(haystack, needle, 0);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return -1;
        }
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String toHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            sb.append(String.format(Locale.US, "%02x", bytes[i]));
        }
        return sb.toString();
    }
}
