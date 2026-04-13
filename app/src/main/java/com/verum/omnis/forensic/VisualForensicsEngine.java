package com.verum.omnis.forensic;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public final class VisualForensicsEngine {

    private VisualForensicsEngine() {}

    public static List<VisualForgeryFinding> inspect(List<Bitmap> renderedPages) {
        List<VisualForgeryFinding> findings = new ArrayList<>();
        for (int i = 0; i < renderedPages.size(); i++) {
            findings.addAll(inspectPage(renderedPages.get(i), i));
        }
        return findings;
    }

    public static List<VisualForgeryFinding> inspectPage(Bitmap bitmap, int pageIndex) {
        List<VisualForgeryFinding> findings = new ArrayList<>();
        if (bitmap == null) {
            return findings;
        }

        float aspect = bitmap.getWidth() / (float) Math.max(1, bitmap.getHeight());
        if (aspect > 1.15f) {
            findings.add(new VisualForgeryFinding(
                    pageIndex,
                    "PAGE_LAYOUT_ANOMALY",
                    "medium",
                    "Rendered page aspect ratio is unusually wide; review for crop or stitch anomalies.",
                    "page-frame"
            ));
        }

        float edgeDensity = estimateEdgeDensity(bitmap);
        float bottomWhitespace = estimateBottomWhitespace(bitmap);
        float patchVariance = estimatePatchVariance(bitmap);
        SignatureRegion signatureRegion = inspectSignatureRegion(bitmap);

        if (bottomWhitespace > 0.88f) {
            findings.add(new VisualForgeryFinding(
                    pageIndex,
                    "SIGNATURE_REGION_EMPTY",
                    "medium",
                    "Lower page region is predominantly blank; verify whether a required signature block is missing.",
                    "bottom-band"
            ));
        }

        if (edgeDensity < 0.015f) {
            findings.add(new VisualForgeryFinding(
                    pageIndex,
                    "FLATTENED_OR_BLURRED_CONTENT",
                    "low",
                    "Rendered page has unusually low edge density; review for blur, over-compression, or flattened image content.",
                    "full-page"
            ));
        }

        if (patchVariance < 7.5f && (signatureRegion.blockLikeOverlayLikely || edgeDensity < 0.012f)) {
            findings.add(new VisualForgeryFinding(
                    pageIndex,
                    "POSSIBLE_OVERLAY_REGION",
                    "low",
                    "Page contains unusually flat low-variance regions. Review only in conjunction with stronger page-specific integrity anomalies.",
                    "patch-scan"
            ));
        }

        if (signatureRegion.presentLikely) {
            findings.add(new VisualForgeryFinding(
                    pageIndex,
                    "SIGNATURE_MARKS_PRESENT",
                    "info",
                    "Lower-right signature zone contains visible pen-like stroke density.",
                    signatureRegion.regionLabel
            ));
        } else {
            findings.add(new VisualForgeryFinding(
                    pageIndex,
                    "SIGNATURE_MARKS_NOT_FOUND",
                    "medium",
                    "No strong pen-like stroke pattern detected in the expected lower-right signature zone.",
                    signatureRegion.regionLabel
            ));
        }

        if (signatureRegion.blockLikeOverlayLikely) {
            findings.add(new VisualForgeryFinding(
                    pageIndex,
                    "SIGNATURE_ZONE_OVERLAY_SUSPECTED",
                    "high",
                    "Expected signature zone appears unusually flat or uniform, which can be consistent with a pasted panel or whiteout overlay.",
                    signatureRegion.regionLabel
            ));
        }

        findings.add(new VisualForgeryFinding(
                pageIndex,
                "VISUAL_SIGNATURE_REVIEW",
                "info",
                "Automated visual review completed. Signature presence, overlays, and tamper signals should be confirmed against page anchors.",
                signatureRegion.regionLabel
        ));
        return findings;
    }

    private static SignatureRegion inspectSignatureRegion(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int startX = (int) (width * 0.55f);
        int startY = (int) (height * 0.72f);
        int regionWidth = Math.max(1, width - startX - Math.max(8, width / 30));
        int regionHeight = Math.max(1, height - startY - Math.max(8, height / 40));

        float edgeDensity = regionEdgeDensity(bitmap, startX, startY, regionWidth, regionHeight);
        float variance = patchVariance(bitmap, startX, startY, regionWidth, regionHeight);
        boolean presentLikely = edgeDensity > 0.045f && variance > 22f;
        boolean blockLikeOverlayLikely = edgeDensity < 0.01f && variance < 8f;

        return new SignatureRegion(
                presentLikely,
                blockLikeOverlayLikely,
                "x=" + startX + ",y=" + startY + ",w=" + regionWidth + ",h=" + regionHeight
        );
    }

    private static float estimateEdgeDensity(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width < 3 || height < 3) {
            return 0f;
        }

        long edges = 0;
        long samples = 0;
        int stepX = Math.max(1, width / 160);
        int stepY = Math.max(1, height / 220);

        for (int y = stepY; y < height - stepY; y += stepY) {
            for (int x = stepX; x < width - stepX; x += stepX) {
                int c = luminance(bitmap.getPixel(x, y));
                int right = luminance(bitmap.getPixel(x + stepX, y));
                int down = luminance(bitmap.getPixel(x, y + stepY));
                if (Math.abs(c - right) > 18 || Math.abs(c - down) > 18) {
                    edges++;
                }
                samples++;
            }
        }
        return samples == 0 ? 0f : edges / (float) samples;
    }

    private static float estimateBottomWhitespace(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int startY = (int) (height * 0.72f);
        long bright = 0;
        long total = 0;
        int stepX = Math.max(1, width / 180);
        int stepY = Math.max(1, (height - startY) / 80);

        for (int y = startY; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                if (luminance(bitmap.getPixel(x, y)) > 235) {
                    bright++;
                }
                total++;
            }
        }
        return total == 0 ? 0f : bright / (float) total;
    }

    private static float estimatePatchVariance(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int patchSize = Math.max(16, Math.min(width, height) / 12);
        if (patchSize <= 0) {
            return 0f;
        }

        float lowestVariance = Float.MAX_VALUE;
        for (int py = 0; py < height; py += patchSize) {
            for (int px = 0; px < width; px += patchSize) {
                float variance = patchVariance(bitmap, px, py, Math.min(patchSize, width - px), Math.min(patchSize, height - py));
                if (variance < lowestVariance) {
                    lowestVariance = variance;
                }
            }
        }
        return lowestVariance == Float.MAX_VALUE ? 0f : lowestVariance;
    }

    private static float regionEdgeDensity(Bitmap bitmap, int startX, int startY, int regionWidth, int regionHeight) {
        if (regionWidth < 3 || regionHeight < 3) {
            return 0f;
        }
        long edges = 0;
        long samples = 0;
        int stepX = Math.max(1, regionWidth / 32);
        int stepY = Math.max(1, regionHeight / 24);

        for (int y = startY; y < startY + regionHeight - stepY; y += stepY) {
            for (int x = startX; x < startX + regionWidth - stepX; x += stepX) {
                int c = luminance(bitmap.getPixel(x, y));
                int right = luminance(bitmap.getPixel(x + stepX, y));
                int down = luminance(bitmap.getPixel(x, y + stepY));
                if (Math.abs(c - right) > 18 || Math.abs(c - down) > 18) {
                    edges++;
                }
                samples++;
            }
        }
        return samples == 0 ? 0f : edges / (float) samples;
    }

    private static float patchVariance(Bitmap bitmap, int startX, int startY, int patchWidth, int patchHeight) {
        if (patchWidth <= 1 || patchHeight <= 1) {
            return 0f;
        }

        long sum = 0;
        long sumSq = 0;
        long count = 0;
        int stepX = Math.max(1, patchWidth / 10);
        int stepY = Math.max(1, patchHeight / 10);

        for (int y = startY; y < startY + patchHeight; y += stepY) {
            for (int x = startX; x < startX + patchWidth; x += stepX) {
                int lum = luminance(bitmap.getPixel(x, y));
                sum += lum;
                sumSq += (long) lum * lum;
                count++;
            }
        }

        if (count == 0) {
            return 0f;
        }

        float mean = sum / (float) count;
        return (sumSq / (float) count) - (mean * mean);
    }

    private static int luminance(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return (int) (0.299f * r + 0.587f * g + 0.114f * b);
    }

    private static final class SignatureRegion {
        final boolean presentLikely;
        final boolean blockLikeOverlayLikely;
        final String regionLabel;

        SignatureRegion(boolean presentLikely, boolean blockLikeOverlayLikely, String regionLabel) {
            this.presentLikely = presentLikely;
            this.blockLikeOverlayLikely = blockLikeOverlayLikely;
            this.regionLabel = regionLabel;
        }
    }
}
