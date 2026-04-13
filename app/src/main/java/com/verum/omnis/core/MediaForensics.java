package com.verum.omnis.core;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

/**
 * MediaForensics – Lightweight EXIF / metadata inspector.
 * Local-only, stateless. Designed for rules-only mode.
 */
public class MediaForensics {

    private static final String TAG = "MediaForensics";

    /**
     * Extract metadata from an image file (JPEG/PNG/WebP).
     */
    public static HashMap<String, String> inspectImage(File file) {
        HashMap<String, String> map = new HashMap<>();
        try {
            ExifInterface exif = new ExifInterface(file);
            String date = exif.getAttribute(ExifInterface.TAG_DATETIME);
            String make = exif.getAttribute(ExifInterface.TAG_MAKE);
            String model = exif.getAttribute(ExifInterface.TAG_MODEL);

            if (date != null) map.put("DateTime", date);
            if (make != null) map.put("CameraMake", make);
            if (model != null) map.put("CameraModel", model);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read EXIF: " + e.getMessage());
        }
        return map;
    }

    /**
     * Extract metadata from an audio/video file (duration, mime).
     */
    public static HashMap<String, String> inspectMedia(File file) {
        HashMap<String, String> map = new HashMap<>();
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);

            if (duration != null) map.put("DurationMs", duration);
            if (mime != null) map.put("MimeType", mime);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read media metadata: " + e.getMessage());
        } finally {
            try {
                mmr.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return map;
    }

    /**
     * Quick header read for PDF (detect "%PDF-" signature).
     */
    public static boolean isPdf(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[5];
            if (fis.read(buf) == 5) {
                String sig = new String(buf);
                return sig.startsWith("%PDF-");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to check PDF header: " + e.getMessage());
        }
        return false;
    }

    /**
     * Convenience wrapper: auto-detect by extension.
     */
    public static HashMap<String, String> inspectFile(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp")) {
            return inspectImage(file);
        } else if (name.endsWith(".mp3") || name.endsWith(".mp4") ||
                name.endsWith(".wav") || name.endsWith(".m4a")) {
            return inspectMedia(file);
        } else if (name.endsWith(".pdf")) {
            HashMap<String, String> pdfInfo = new HashMap<>();
            pdfInfo.put("IsPdf", String.valueOf(isPdf(file)));
            return pdfInfo;
        }
        return new HashMap<>();
    }
}
