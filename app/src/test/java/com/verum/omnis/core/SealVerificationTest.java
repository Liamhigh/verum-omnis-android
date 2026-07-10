package com.verum.omnis.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Validates the offline OpenTimestamps proof reader and seal verifier against
 * the real detached proof bundled with the app.
 */
public class SealVerificationTest {

    private static final String PDF_ASSET =
            "app/src/main/assets/docs/VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320.PDF";
    private static final String OTS_ASSET =
            "app/src/main/assets/docs/VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320.PDF.ots";

    private File resolveAsset(String repoRelativePath) {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path[] candidates = new Path[]{
                cwd.resolve(repoRelativePath),
                cwd.getParent() != null ? cwd.getParent().resolve(repoRelativePath) : null,
                // When the test runs with the module dir as cwd, strip the "app/" prefix.
                repoRelativePath.startsWith("app/") ? cwd.resolve(repoRelativePath.substring(4)) : null
        };
        for (Path candidate : candidates) {
            if (candidate != null && candidate.toFile().exists()) {
                return candidate.toFile();
            }
        }
        throw new IllegalStateException("Asset not found on any candidate path: " + repoRelativePath);
    }

    @Test
    public void parsesRealBitcoinAnchoredProof() {
        OpenTimestampsProof proof = OpenTimestampsProof.parse(resolveAsset(OTS_ASSET));

        assertTrue("proof should be structurally valid", proof.valid);
        assertEquals("OpenTimestamps stamps the SHA-256 of the file", "SHA-256", proof.fileHashAlgorithm);
        assertEquals(64, proof.committedDigestHex.length());
        assertTrue("this bundled proof is Bitcoin-confirmed", proof.hasBitcoinAttestation);
        assertEquals(OpenTimestampsProof.AnchorState.CONFIRMED_BITCOIN, proof.anchorState());
    }

    @Test
    public void committedDigestMatchesFileSha256() throws Exception {
        OpenTimestampsProof proof = OpenTimestampsProof.parse(resolveAsset(OTS_ASSET));
        String actualSha256 = HashUtil.sha256File(resolveAsset(PDF_ASSET));
        assertEquals(
                "the .ots committed digest must equal the SHA-256 of the complete document",
                actualSha256,
                proof.committedDigestHex
        );
    }

    @Test
    public void verifierReadsFullSha512AndBitcoinAnchor() {
        SealVerifier.Report report = SealVerifier.verify(resolveAsset(PDF_ASSET), resolveAsset(OTS_ASSET));

        assertTrue(report.fileReadable);
        assertEquals("SHA-512 covers the complete document", 128, report.sha512.length());
        assertEquals(64, report.sha256.length());
        assertTrue(report.otsPresent);
        assertTrue(report.otsValid);
        assertTrue("the proof anchors this exact document", report.otsDigestMatchesFile);
        assertEquals(OpenTimestampsProof.AnchorState.CONFIRMED_BITCOIN, report.anchorState);
        assertNotNull(report.summary);
        assertTrue(report.summary.contains("Bitcoin"));
    }

    @Test
    public void rejectsNonOtsBytes() {
        OpenTimestampsProof proof = OpenTimestampsProof.parse("this is not an ots proof".getBytes());
        assertFalse(proof.valid);
        assertEquals(OpenTimestampsProof.AnchorState.INVALID, proof.anchorState());
    }

    @Test
    public void reportsMissingProofClearly() {
        SealVerifier.Report report = SealVerifier.verify(resolveAsset(PDF_ASSET), new File("/nonexistent/x.ots"));
        assertTrue(report.fileReadable);
        assertFalse(report.otsPresent);
        assertTrue(report.summary.contains("no OpenTimestamps"));
    }
}
