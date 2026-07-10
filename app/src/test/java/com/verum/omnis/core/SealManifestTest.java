package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class SealManifestTest {

    @Test
    public void buildIsDeterministicAndParsesBack() {
        String a = SealManifest.build("report.pdf", "aa", "bb");
        String b = SealManifest.build("report.pdf", "aa", "bb");
        assertEquals("manifest output must be byte-identical for identical inputs", a, b);

        SealManifest parsed = SealManifest.parse(a);
        assertNotNull(parsed);
        assertEquals("report.pdf", parsed.artifact);
        assertEquals("aa", parsed.sha512);
        assertEquals("bb", parsed.sha256);
    }

    @Test
    public void parseRejectsForeignJson() {
        assertNull(SealManifest.parse("{\"scheme\":\"something-else\"}"));
        assertNull(SealManifest.parse("not json"));
        assertNull(SealManifest.parse(""));
    }

    @Test
    public void verifierCrossChecksManifestAgainstFile() throws Exception {
        File dir = File.createTempFile("verum-seal", "-dir").getParentFile();
        File sealed = new File(dir, "seal-" + System.nanoTime() + ".bin");
        byte[] payload = "the complete sealed document, everything in it".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(sealed)) {
            fos.write(payload);
        }

        String sha512 = HashUtil.sha512File(sealed);
        String sha256 = HashUtil.sha256File(sealed);

        File manifest = SealManifest.sidecarFor(sealed);
        try (FileOutputStream fos = new FileOutputStream(manifest)) {
            fos.write(SealManifest.build(sealed.getName(), sha512, sha256).getBytes(StandardCharsets.UTF_8));
        }

        SealVerifier.Report ok = SealVerifier.verify(sealed);
        assertTrue(ok.fileReadable);
        assertEquals(sha512, ok.sha512);
        assertTrue("manifest present", ok.manifestPresent);
        assertTrue("manifest hashes match the file", ok.manifestMatches);
        assertTrue(ok.summary.contains("matches this document"));

        // Tamper: change the file so the manifest no longer matches.
        try (FileOutputStream fos = new FileOutputStream(sealed)) {
            fos.write("tampered".getBytes(StandardCharsets.UTF_8));
        }
        SealVerifier.Report mismatch = SealVerifier.verify(sealed);
        assertTrue(mismatch.manifestPresent);
        assertFalse("manifest must not match a tampered file", mismatch.manifestMatches);

        sealed.delete();
        manifest.delete();
    }
}
