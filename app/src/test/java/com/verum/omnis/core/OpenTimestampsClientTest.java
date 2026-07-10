package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public class OpenTimestampsClientTest {

    private static final byte[] PENDING_TAG = {
            (byte) 0x83, (byte) 0xdf, (byte) 0xe3, (byte) 0x0d, (byte) 0x2e, (byte) 0xf9, (byte) 0x0c, (byte) 0x8e
    };

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format(Locale.US, "%02x", x));
        }
        return sb.toString();
    }

    /** Minimal pending calendar timestamp: pending tag + varint(payload) + varint(urlLen) + url. */
    private static byte[] syntheticPendingTimestamp(String url) {
        byte[] urlBytes = url.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + urlBytes.length];
        payload[0] = (byte) urlBytes.length;
        System.arraycopy(urlBytes, 0, payload, 1, urlBytes.length);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PENDING_TAG, 0, PENDING_TAG.length);
        out.write(payload.length);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    @Test
    public void buildsDetachedOtsThatRoundTripsThroughReader() {
        byte[] fileSha256 = new byte[32];
        for (int i = 0; i < 32; i++) {
            fileSha256[i] = (byte) (i + 1);
        }
        String url = "https://alice.btc.calendar.opentimestamps.org";
        byte[] calendar = syntheticPendingTimestamp(url);

        byte[] ots = OpenTimestampsClient.buildDetachedOts(fileSha256, calendar);
        OpenTimestampsProof proof = OpenTimestampsProof.parse(ots);

        assertTrue("assembled proof must be valid", proof.valid);
        assertEquals("SHA-256", proof.fileHashAlgorithm);
        assertEquals(hex(fileSha256), proof.committedDigestHex);
        assertTrue("pending attestation must be detected", proof.hasPendingAttestation);
        assertEquals(OpenTimestampsProof.AnchorState.PENDING, proof.anchorState());
        assertTrue(proof.calendarUrls.contains(url));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongDigestLength() {
        OpenTimestampsClient.buildDetachedOts(new byte[16], new byte[]{1, 2, 3});
    }
}
