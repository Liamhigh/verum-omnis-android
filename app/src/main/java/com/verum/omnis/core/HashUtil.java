package com.verum.omnis.core;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

public class HashUtil {

    /** Compute SHA-512 hash of a file */
    public static String sha512File(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = fis.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        return toHex(md.digest());
    }

    /** Compute SHA-512 hash of a byte[] */
    public static String sha512(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(data);
        return toHex(md.digest());
    }

    /** Compute SHA-256 hash of a String */
    public static String sha256(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(text.getBytes("UTF-8"));
        return toHex(md.digest());
    }

    /** Truncate a hash for display (e.g. show first 8 chars) */
    public static String truncate(String fullHash, int chars) {
        if (fullHash == null) return "";
        return fullHash.length() <= chars ? fullHash : fullHash.substring(0, chars);
    }

    /** Internal: convert hash bytes to hex string */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }
}
