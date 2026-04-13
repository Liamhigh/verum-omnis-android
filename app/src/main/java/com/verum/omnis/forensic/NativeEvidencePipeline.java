package com.verum.omnis.forensic;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.mlkit.vision.text.TextRecognizer;
import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.HashUtil;
import com.verum.omnis.core.MediaForensics;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NativeEvidencePipeline {
    private static final int MAX_RENDERED_PDF_REVIEW_PAGES = 24;

    private NativeEvidencePipeline() {}

    public static NativeEvidenceResult process(Context context, File file) {
        return process(context, file, null);
    }

    public static NativeEvidenceResult process(
            Context context,
            File file,
            AnalysisEngine.ProgressListener progressListener
    ) {
        NativeEvidenceResult result = new NativeEvidenceResult();
        result.fileName = file.getName();
        result.pipelineStatus = "starting";
        try {
            result.evidenceHash = HashUtil.sha512File(file);
        } catch (Exception e) {
            result.evidenceHash = "HASH_ERROR";
        }

        result.pdf = MediaForensics.isPdf(file);
        result.renderLimit = result.pdf ? Math.max(1, NativePdfRenderer.countPages(file)) : 1;
        if (result.pdf) {
            result.sourcePageCount = result.renderLimit;
            notifyProgress(progressListener, "Shredder engine: extracting embedded PDF text across "
                    + Math.max(1, result.sourcePageCount) + " page(s)...");
            List<OcrTextBlock> documentTextBlocks = NativePdfTextExtractor.extract(context, file);
            result.documentTextBlocks.addAll(documentTextBlocks);
            result.documentTextPageCount = documentTextBlocks.size();
            detectSecondaryNarrativeSource(result, result.documentTextBlocks);
            for (OcrTextBlock block : documentTextBlocks) {
                result.anchors.add(new EvidenceAnchor(
                        "PT-" + HashUtil.truncate(result.evidenceHash, 8) + "-" + String.format("%03d", block.pageIndex + 1),
                        result.fileName,
                        block.pageIndex + 1,
                        1,
                        1,
                        "",
                        "",
                        "pdf-text-" + (block.pageIndex + 1),
                        block.pageIndex * 1_000_000L,
                        "EX-" + String.format("%03d", block.pageIndex + 1),
                        truncate(block.text, 220),
                        "PDF_TEXT"
                ));
            }
        } else {
            result.sourcePageCount = 1;
            notifyProgress(progressListener, "Shredder engine: preparing visual source...");
        }

        if (result.pdf) {
            Map<Integer, OcrTextBlock> textByPage = indexBlocksByPage(result.documentTextBlocks);
            List<Integer> renderTargets = selectPdfPagesForRenderedReview(result.sourcePageCount, textByPage);
            result.renderLimit = renderTargets.size();
            result.fastPathTextPageCount = Math.max(0, result.sourcePageCount - renderTargets.size());
            notifyProgress(progressListener, "Shredder engine: targeted OCR and visual review across "
                    + Math.max(0, renderTargets.size()) + " of "
                    + Math.max(1, result.sourcePageCount) + " page(s)...");

            if (renderTargets.isEmpty()) {
                result.pageCount = result.sourcePageCount;
            } else {
            TextRecognizer recognizer = NativeOcrEngine.createRecognizer();
            try {
                boolean renderedAny = NativePdfRenderer.renderSelectedPages(file, renderTargets, (pageIndex, totalPages, bitmap) -> {
                    result.renderedPageCount++;
                    if (result.renderedPageCount == 1
                            || (result.renderedPageCount % 5 == 0)
                            || result.renderedPageCount == totalPages) {
                        notifyProgress(
                                progressListener,
                                "Shredder engine: analysed targeted page " + (pageIndex + 1)
                                        + " (" + result.renderedPageCount + " of " + totalPages + ")..."
                        );
                    }

                    OcrTextBlock embeddedText = textByPage.get(pageIndex);
                    if (shouldRunOcr(embeddedText)) {
                        OcrTextBlock block;
                        try {
                            block = NativeOcrEngine.extractPage(recognizer, bitmap, pageIndex);
                        } catch (Exception e) {
                            block = new OcrTextBlock(
                                    pageIndex,
                                    "OCR_FAILED page=" + (pageIndex + 1) + " error=" + e.getClass().getSimpleName(),
                                    0.0f
                            );
                        }
                        recordOcrBlock(result, block);
                    } else {
                        result.visualOnlyPageCount++;
                    }

                    List<VisualForgeryFinding> findings = VisualForensicsEngine.inspectPage(bitmap, pageIndex);
                    recordVisualFindings(result, findings);
                });
                result.pageCount = result.sourcePageCount > 0 ? result.sourcePageCount : result.renderedPageCount;
                if (!renderedAny) {
                    result.renderError = "PDF renderer returned no pages on-device.";
                }
            } finally {
                recognizer.close();
            }
            }
        } else {
            List<Bitmap> renderedPages = NativePdfRenderer.render(context, file, result.renderLimit);
            result.renderedPageCount = renderedPages.size();
            result.pageCount = result.sourcePageCount > 0 ? result.sourcePageCount : (renderedPages.isEmpty() ? 1 : renderedPages.size());

            notifyProgress(progressListener, "Shredder engine: OCR across " + Math.max(1, result.renderedPageCount) + " rendered page(s)...");
            List<OcrTextBlock> blocks = NativeOcrEngine.extract(context, file, renderedPages);
            for (OcrTextBlock block : blocks) {
                recordOcrBlock(result, block);
            }
            detectSecondaryNarrativeSource(result, result.ocrBlocks);

            notifyProgress(progressListener, "Shredder engine: visual tamper review...");
            recordVisualFindings(result, VisualForensicsEngine.inspect(renderedPages));
        }

        result.propositions.addAll(PropositionExtractor.extract(context, result));
        result.pipelineStatus = "completed";
        notifyProgress(progressListener, "Shredder engine: processed " + result.ocrSuccessCount + " OCR block(s) across "
                + Math.max(1, result.pageCount) + " page(s).");
        return result;
    }

    private static void recordOcrBlock(NativeEvidenceResult result, OcrTextBlock block) {
        result.ocrBlocks.add(block);
        if (block.confidence > 0f) {
            result.ocrSuccessCount++;
        } else {
            result.ocrFailedCount++;
        }
        result.anchors.add(new EvidenceAnchor(
                "EV-" + HashUtil.truncate(result.evidenceHash, 8) + "-" + String.format("%03d", block.pageIndex + 1),
                result.fileName,
                block.pageIndex + 1,
                1,
                1,
                "",
                "",
                "ocr-" + (block.pageIndex + 1),
                block.pageIndex * 1_000_000L,
                "EX-" + String.format("%03d", block.pageIndex + 1),
                truncate(block.text, 220),
                "OCR_BLOCK"
        ));
    }

    private static void recordVisualFindings(NativeEvidenceResult result, List<VisualForgeryFinding> findings) {
        result.visualFindings.addAll(findings);
        for (VisualForgeryFinding finding : findings) {
            result.anchors.add(new EvidenceAnchor(
                    "VF-" + HashUtil.truncate(result.evidenceHash, 8) + "-" + String.format("%03d", finding.pageIndex + 1),
                    result.fileName,
                    finding.pageIndex + 1,
                    0,
                    0,
                    "",
                    "",
                    "visual-" + (finding.pageIndex + 1),
                    finding.pageIndex * 1_000_000L,
                    "EX-" + String.format("%03d", finding.pageIndex + 1),
                    finding.description,
                    finding.type
            ));
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static void notifyProgress(AnalysisEngine.ProgressListener progressListener, String message) {
        if (progressListener != null && message != null && !message.trim().isEmpty()) {
            progressListener.onProgress(message);
        }
    }

    private static Map<Integer, OcrTextBlock> indexBlocksByPage(List<OcrTextBlock> blocks) {
        Map<Integer, OcrTextBlock> indexed = new LinkedHashMap<>();
        if (blocks == null) {
            return indexed;
        }
        for (OcrTextBlock block : blocks) {
            if (block == null || block.pageIndex < 0) {
                continue;
            }
            OcrTextBlock current = indexed.get(block.pageIndex);
            if (current == null || block.text.length() > current.text.length()) {
                indexed.put(block.pageIndex, block);
            }
        }
        return indexed;
    }

    private static List<Integer> selectPdfPagesForRenderedReview(int sourcePageCount, Map<Integer, OcrTextBlock> textByPage) {
        List<Integer> pages = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < sourcePageCount; pageIndex++) {
            OcrTextBlock embeddedText = textByPage.get(pageIndex);
            if (shouldRenderPage(pageIndex, sourcePageCount, embeddedText)) {
                pages.add(pageIndex);
            }
        }
        if (pages.size() <= MAX_RENDERED_PDF_REVIEW_PAGES) {
            return pages;
        }
        return samplePdfReviewPages(pages);
    }

    private static List<Integer> samplePdfReviewPages(List<Integer> candidates) {
        if (candidates == null || candidates.size() <= MAX_RENDERED_PDF_REVIEW_PAGES) {
            return candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
        }
        Set<Integer> selected = new LinkedHashSet<>();
        int total = candidates.size();

        for (int i = 0; i < Math.min(3, total); i++) {
            selected.add(candidates.get(i));
        }
        for (int i = Math.max(3, total - 3); i < total; i++) {
            selected.add(candidates.get(i));
        }

        int remainingBudget = Math.max(0, MAX_RENDERED_PDF_REVIEW_PAGES - selected.size());
        if (remainingBudget <= 0) {
            return new ArrayList<>(selected);
        }

        double stride = (double) total / (double) remainingBudget;
        for (int slot = 0; slot < remainingBudget; slot++) {
            int candidateIndex = (int) Math.floor(slot * stride);
            if (candidateIndex >= 0 && candidateIndex < total) {
                selected.add(candidates.get(candidateIndex));
            }
        }
        return new ArrayList<>(selected);
    }

    private static boolean shouldRenderPage(int pageIndex, int sourcePageCount, OcrTextBlock embeddedText) {
        if (!hasStrongEmbeddedText(embeddedText)) {
            return true;
        }
        if (pageIndex < 2 || pageIndex >= Math.max(0, sourcePageCount - 2)) {
            return true;
        }
        return isVisualPriorityText(embeddedText.text);
    }

    private static boolean shouldRunOcr(OcrTextBlock embeddedText) {
        return !hasStrongEmbeddedText(embeddedText);
    }

    private static boolean hasStrongEmbeddedText(OcrTextBlock embeddedText) {
        return embeddedText != null
                && embeddedText.confidence >= 0.95f
                && embeddedText.text != null
                && embeddedText.text.trim().length() >= 180;
    }

    private static boolean isVisualPriorityText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("signature")
                || lower.contains("signed")
                || lower.contains("countersign")
                || lower.contains("invoice")
                || lower.contains("agreement")
                || lower.contains("contract")
                || lower.contains("affidavit")
                || lower.contains("declaration")
                || lower.contains("annexure")
                || lower.contains("appendix")
                || lower.contains("seal")
                || lower.contains("stamp");
    }

    private static void detectSecondaryNarrativeSource(NativeEvidenceResult result, List<OcrTextBlock> blocks) {
        if (result == null || result.secondaryNarrativeSource || blocks == null || blocks.isEmpty()) {
            return;
        }
        StringBuilder corpus = new StringBuilder();
        int sampled = 0;
        for (OcrTextBlock block : blocks) {
            if (block == null || block.text == null || block.text.trim().isEmpty()) {
                continue;
            }
            corpus.append(block.text).append('\n');
            sampled++;
            if (sampled >= 12 || corpus.length() >= 12000) {
                break;
            }
        }
        if (corpus.length() == 0) {
            return;
        }
        String lower = corpus.toString().toLowerCase(Locale.ROOT);
        int matches = 0;
        String[] markers = new String[]{
                "executive forensic conclusion",
                "triple verification result",
                "findings by",
                "contradiction engine",
                "nine-brain style analysis",
                "financial reconciliation",
                "fraud confirmed",
                "shareholder oppression",
                "very_high",
                "very high",
                "confidence:",
                "this is not a judicial ruling",
                "structured evidentiary assessment"
        };
        for (String marker : markers) {
            if (lower.contains(marker)) {
                matches++;
            }
        }
        if (matches >= 3) {
            result.secondaryNarrativeSource = true;
            result.syntheticForensicSummary = true;
        }
    }
}
