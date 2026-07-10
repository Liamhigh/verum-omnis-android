package com.verum.omnis.core;

import java.io.File;

/**
 * Verifies a sealed artifact by reading the SHA-512 of the complete document
 * "with everything in it" (the hash is computed last, over the final file that
 * contains all seal annotations, QR, watermark and certification), and, when a
 * detached OpenTimestamps ({@code .ots}) sidecar is present, reports whether the
 * complete document is anchored to the Bitcoin blockchain.
 *
 * <p>This class is deterministic and fully offline. Live Bitcoin anchoring
 * (submitting a fresh digest to an OpenTimestamps calendar) is a separate,
 * network-dependent step and is intentionally not performed here.</p>
 */
public final class SealVerifier {

    private SealVerifier() {
    }

    public static final class Report {
        public boolean fileReadable;
        /** SHA-512 of the complete sealed document (everything in it). */
        public String sha512 = "";
        /** SHA-256 of the complete sealed document (the digest OpenTimestamps anchors). */
        public String sha256 = "";
        public boolean otsPresent;
        public boolean otsValid;
        public boolean otsDigestMatchesFile;
        public OpenTimestampsProof.AnchorState anchorState = OpenTimestampsProof.AnchorState.NONE;
        public boolean manifestPresent;
        public boolean manifestMatches;
        public String summary = "";
    }

    /** Returns the conventional detached OpenTimestamps sidecar for a sealed file ({@code <name>.ots}). */
    public static File defaultOtsSidecar(File sealedFile) {
        if (sealedFile == null) {
            return null;
        }
        return new File(sealedFile.getParentFile(), sealedFile.getName() + ".ots");
    }

    public static Report verify(File sealedFile) {
        return verify(sealedFile, defaultOtsSidecar(sealedFile), SealManifest.sidecarFor(sealedFile));
    }

    public static Report verify(File sealedFile, File otsFile) {
        return verify(sealedFile, otsFile, SealManifest.sidecarFor(sealedFile));
    }

    public static Report verify(File sealedFile, File otsFile, File manifestFile) {
        Report report = new Report();
        if (sealedFile == null || !sealedFile.exists() || !sealedFile.isFile()) {
            report.fileReadable = false;
            report.summary = "No readable document was available to verify.";
            return report;
        }

        try {
            report.sha512 = HashUtil.sha512File(sealedFile);
            report.sha256 = HashUtil.sha256File(sealedFile);
            report.fileReadable = true;
        } catch (Exception e) {
            report.fileReadable = false;
            report.summary = "Could not read the document to compute its SHA-512: " + e.getMessage();
            return report;
        }

        SealManifest manifest = SealManifest.parse(manifestFile);
        if (manifest != null) {
            report.manifestPresent = true;
            report.manifestMatches =
                    report.sha512.equalsIgnoreCase(manifest.sha512)
                            && report.sha256.equalsIgnoreCase(manifest.sha256);
        }

        if (otsFile != null && otsFile.exists() && otsFile.isFile()) {
            report.otsPresent = true;
            OpenTimestampsProof proof = OpenTimestampsProof.parse(otsFile);
            report.otsValid = proof.valid;
            report.anchorState = proof.anchorState();
            if (proof.valid) {
                String fileDigest = "SHA-256".equals(proof.fileHashAlgorithm) ? report.sha256 : "";
                report.otsDigestMatchesFile =
                        !fileDigest.isEmpty() && fileDigest.equalsIgnoreCase(proof.committedDigestHex);
            }
        }

        report.summary = buildSummary(report);
        return report;
    }

    private static String buildSummary(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Full document SHA-512 (everything in it):\n").append(report.sha512).append("\n\n");
        sb.append("Document SHA-256 (Bitcoin-anchor digest):\n").append(report.sha256).append("\n\n");

        if (report.manifestPresent) {
            sb.append(report.manifestMatches
                    ? "Seal manifest: found and matches this document.\n\n"
                    : "Seal manifest: WARNING - found but its recorded hashes do NOT match this document.\n\n");
        }

        if (!report.otsPresent) {
            sb.append("Blockchain anchor: no OpenTimestamps (.ots) proof was found next to this document.\n");
            sb.append("To anchor it, submit the SHA-256 above to an OpenTimestamps calendar and keep the returned .ots proof.");
            return sb.toString();
        }

        if (!report.otsValid) {
            sb.append("Blockchain anchor: an .ots file was found but could not be parsed as a valid OpenTimestamps proof.");
            return sb.toString();
        }

        sb.append("Blockchain anchor: OpenTimestamps proof found. ");
        sb.append(report.otsDigestMatchesFile
                ? "Its committed digest matches this document.\n"
                : "WARNING: its committed digest does NOT match this document.\n");

        switch (report.anchorState) {
            case CONFIRMED_BITCOIN:
                sb.append("Status: anchored to the Bitcoin blockchain (Bitcoin attestation present).");
                break;
            case PENDING:
                sb.append("Status: pending Bitcoin confirmation (calendar attestation present).");
                break;
            case OTHER_CHAIN:
                sb.append("Status: anchored to a non-Bitcoin chain attestation.");
                break;
            default:
                sb.append("Status: no blockchain attestation is present in the proof yet.");
                break;
        }
        return sb.toString();
    }
}
