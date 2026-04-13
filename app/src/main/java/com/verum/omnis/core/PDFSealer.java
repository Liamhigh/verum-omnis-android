package com.verum.omnis.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.util.TypedValue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.verum.omnis.R;
import com.verum.omnis.forensic.NativePdfRenderer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PDFSealer {

    private static final float PAGE_LEFT = 40f;
    private static final float PAGE_RIGHT = 555f;
    private static final float QR_PANEL_TOP = 46f;
    private static final float QR_CODE_SIZE = 64f;
    private static final float QR_PANEL_PADDING = 10f;
    private static final float QR_PANEL_SIZE = QR_CODE_SIZE + (QR_PANEL_PADDING * 2f);
    private static final float QR_TEXT_GUTTER = 18f;
    private static final float WATERMARK_LEFT = 54f;
    private static final float WATERMARK_TOP = 172f;
    private static final float WATERMARK_RIGHT_MARGIN = 54f;
    private static final float WATERMARK_BOTTOM_MARGIN = 128f;
    private static final int CANVAS_WATERMARK_ALPHA = 42;
    private static final float PDF_WATERMARK_ALPHA = 0.11f;
    private static final Pattern HASH_LINE_PATTERN = Pattern.compile("Evidence SHA-512:\\s*([0-9a-fA-F]{64,128})");
    private static final Pattern CASE_LINE_PATTERN = Pattern.compile("Case ID:\\s*([^\\r\\n]+)");
    private static final Pattern SOURCE_LINE_PATTERN = Pattern.compile("Source File:\\s*([^\\r\\n]+)");

    public enum DocumentMode {
        SEAL_ONLY,
        FORENSIC_REPORT
    }

    public static class SealInspection {
        public final boolean alreadySealed;
        public final String priorEvidenceHash;
        public final String priorCaseId;
        public final String priorSourceFileName;
        public final String currentArtifactHash;
        public final String validationNote;

        public SealInspection(
                boolean alreadySealed,
                String priorEvidenceHash,
                String priorCaseId,
                String priorSourceFileName,
                String currentArtifactHash,
                String validationNote
        ) {
            this.alreadySealed = alreadySealed;
            this.priorEvidenceHash = priorEvidenceHash;
            this.priorCaseId = priorCaseId;
            this.priorSourceFileName = priorSourceFileName;
            this.currentArtifactHash = currentArtifactHash;
            this.validationNote = validationNote;
        }
    }

    public static class SealRequest {
        /**
         * Title displayed at the top of the generated report.  If null or empty,
         * "Forensic Report" will be used.
         */
        public String title;
        /**
         * Summary description shown beneath the title.  If null or empty, a
         * default message will be printed.
         */
        public String summary;
        /**
         * Whether to include a QR code in the bottom right corner.  Defaults
         * to {@code true} when unset.
         */
        public boolean includeQr = true;
        /**
         * Whether to include the certification block with tick and hash in the
         * bottom right.  Defaults to {@code true} when unset.
         */
        public boolean includeHash = true;
        public String evidenceHash;
        public String caseId;
        public String jurisdiction;
        public String legalSummary;
        public String bodyText;
        public String intakeMetadata;
        public String sourceFileName;
        public DocumentMode mode = DocumentMode.SEAL_ONLY;
    }

    public static SealInspection inspectExistingSeal(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.getName().toLowerCase(Locale.US).endsWith(".pdf")) {
            return new SealInspection(false, "", "", "", "", "No readable PDF artifact was available for prior-seal inspection.");
        }
        try {
            String currentArtifactHash = HashUtil.sha512File(pdfFile);
            try (PDDocument document = PDDocument.load(pdfFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(2, Math.max(1, document.getNumberOfPages())));
                String text = stripper.getText(document);
                String lower = text == null ? "" : text.toLowerCase(Locale.US);
                boolean hasVerumMarker = lower.contains("verum omnis")
                        && (lower.contains("evidence sha-512")
                        || lower.contains("sealed evidence")
                        || lower.contains("seal certificate"));
                if (!hasVerumMarker) {
                    return new SealInspection(
                            false,
                            "",
                            "",
                            "",
                            currentArtifactHash,
                            "No prior Verum seal markers were detected in the uploaded PDF."
                    );
                }
                return new SealInspection(
                        true,
                        firstMatch(text, HASH_LINE_PATTERN),
                        firstMatch(text, CASE_LINE_PATTERN),
                        firstMatch(text, SOURCE_LINE_PATTERN),
                        currentArtifactHash,
                        "Prior Verum seal markers were detected and the wrapped PDF opened successfully. The current artifact hash was recorded without resealing the source pages."
                );
            }
        } catch (Exception e) {
            return new SealInspection(false, "", "", "", "", "Prior-seal inspection failed: " + e.getMessage());
        }
    }

    private static String firstMatch(String text, Pattern pattern) {
        if (text == null || pattern == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        return value == null ? "" : value.trim();
    }

    public static void generateSealedPdf(Context ctx, SealRequest req, File outFile) throws IOException {
        String fullHash = (req != null && req.evidenceHash != null && !req.evidenceHash.trim().isEmpty())
                ? req.evidenceHash.trim()
                : sha512(("VerumOmnisStaticSeal").getBytes());
        String shortHash = truncate(fullHash, 8);

        Bitmap logo = loadReportBannerBitmap(ctx);
        Bitmap watermark = loadWatermarkBitmap(ctx);
        String title = (req != null && req.title != null && !req.title.trim().isEmpty())
                ? req.title : "Forensic Report";
        String summary = (req != null && req.summary != null && !req.summary.trim().isEmpty())
                ? req.summary : "No summary provided.";
        String bodyText = req != null ? req.bodyText : null;

        PdfDocument doc = new PdfDocument();
        String[] bodyLines = (bodyText == null || bodyText.trim().isEmpty())
                ? new String[0] : bodyText.split("\\r?\\n");
        int totalPages = Math.max(1, estimatePageCount(bodyLines));
        int lineCursor = 0;

        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create();
            PdfDocument.Page page = doc.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.drawColor(0xFFFFFFFF);

            drawFullPageWatermark(pageInfo, canvas, watermark);
            drawHeader(pageInfo, canvas, logo, req);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xFF000000);
            textPaint.setTextSize(20f);
            if (pageNumber == 1) {
                float titleY = drawWrappedParagraph(
                        canvas,
                        title,
                        PAGE_LEFT,
                        188f,
                        PAGE_RIGHT - PAGE_LEFT,
                        26f,
                        0xFF0C254D,
                        20f,
                        getQrExclusionRect(req, pageInfo),
                        pageInfo.getPageWidth()
                );
                drawWrappedParagraph(
                        canvas,
                        summary,
                        PAGE_LEFT,
                        titleY + 4f,
                        PAGE_RIGHT - PAGE_LEFT,
                        15f,
                        0xFF1E1E1E,
                        11f,
                        getQrExclusionRect(req, pageInfo),
                        pageInfo.getPageWidth()
                );
            }

            float y = pageNumber == 1 ? 270f : 188f;
            if (pageNumber == 1) {
                if (req != null && req.mode != null) {
                    y = drawWrappedLine(canvas, "Mode: "
                                    + (req.mode == DocumentMode.SEAL_ONLY ? "Document Seal Certificate" : "Constitutional Forensic Report"),
                            PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 0xFF1E1E1E, 10f);
                }
                if (req != null && req.sourceFileName != null && !req.sourceFileName.trim().isEmpty()) {
                    y = drawWrappedLine(canvas, "Source File: " + req.sourceFileName, PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 0xFF1E1E1E, 10f);
                }
                if (req != null && req.caseId != null) {
                    y = drawWrappedLine(canvas, "Case ID: " + req.caseId, PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 0xFF1E1E1E, 10f);
                }
                if (req != null && req.jurisdiction != null) {
                    y = drawWrappedLine(canvas, "Jurisdiction: " + req.jurisdiction, PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 0xFF1E1E1E, 10f);
                }
                y = drawWrappedLine(canvas, "Evidence SHA-512: " + fullHash, PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 0xFF1E1E1E, 10f);
                if (req != null && req.intakeMetadata != null && !req.intakeMetadata.trim().isEmpty()) {
                    y = drawWrappedLine(canvas, "Intake: " + req.intakeMetadata, PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 0xFF1E1E1E, 10f);
                }
                if (req != null && req.legalSummary != null && !req.legalSummary.trim().isEmpty()) {
                    y = drawWrappedLine(canvas, "Constitutional note: " + req.legalSummary, PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 0xFF1E1E1E, 10f);
                }
                y += 10f;
            }

            lineCursor = drawBodyText(canvas, bodyLines, lineCursor, PAGE_LEFT, y, PAGE_RIGHT - PAGE_LEFT, 16f, 11f, pageInfo.getPageHeight() - 140f);
            drawCertificationBlock(
                    canvas,
                    pageInfo,
                    fullHash,
                    shortHash,
                    buildSealPayload(req, fullHash),
                    req == null || req.includeHash,
                    req == null || req.includeQr
            );
            doc.finishPage(page);
        }

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            doc.writeTo(fos);
        }
        doc.close();
    }

    private static Bitmap makeQr(String text) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 256, 256);
        int w = bitMatrix.getWidth();
        int h = bitMatrix.getHeight();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                bmp.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bmp;
    }

    private static Bitmap loadWatermarkBitmap(Context ctx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_verum_watermark_reference, bounds);

        int sampleSize = 1;
        int longestSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (longestSide / sampleSize > 1200) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = Math.max(1, sampleSize);

        Bitmap decoded = BitmapFactory.decodeResource(
                ctx.getResources(),
                R.drawable.ic_verum_watermark_reference,
                options
        );
        return scaleBitmapDown(decoded, 1200);
    }

    private static Bitmap loadReportBannerBitmap(Context ctx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_verum_report_banner, bounds);

        int sampleSize = 1;
        int longestSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (longestSide / sampleSize > 1600) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = Math.max(1, sampleSize);
        Bitmap decoded = BitmapFactory.decodeResource(
                ctx.getResources(),
                R.drawable.ic_verum_report_banner,
                options
        );
        return scaleBitmapDown(decoded, 1600);
    }

    private static Bitmap scaleBitmapDown(Bitmap bitmap, int maxSide) {
        if (bitmap == null) {
            return null;
        }

        int width = Math.max(1, bitmap.getWidth());
        int height = Math.max(1, bitmap.getHeight());
        int longestSide = Math.max(width, height);
        if (longestSide <= maxSide) {
            return bitmap;
        }

        float scale = maxSide / (float) longestSide;
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private static float sp(Context ctx, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.getResources().getDisplayMetrics());
    }

    private static String sha512(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(data);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    public static boolean canRenderSourceDocument(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return false;
        }
        if (MediaForensics.isPdf(sourceFile)) {
            return true;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), bounds);
        return bounds.outWidth > 0 && bounds.outHeight > 0;
    }

    public static void generateSealedSourceDocument(Context ctx, SealRequest req, File sourceFile, File outFile) throws IOException {
        String fullHash = (req != null && req.evidenceHash != null && !req.evidenceHash.trim().isEmpty())
                ? req.evidenceHash.trim()
                : sha512(("VerumOmnisStaticSeal").getBytes());
        String shortHash = truncate(fullHash, 8);
        Bitmap watermark = loadWatermarkBitmap(ctx);

        if (MediaForensics.isPdf(sourceFile)) {
            if (stampPdfDocument(ctx, req, sourceFile, outFile, fullHash, shortHash, watermark)) {
                return;
            }
        }

        PdfDocument doc = new PdfDocument();
        boolean rendered = false;
        List<Bitmap> sourcePages = loadSourcePages(ctx, sourceFile);
        if (!sourcePages.isEmpty()) {
            rendered = true;
            for (int i = 0; i < sourcePages.size(); i++) {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, i + 1).create();
                PdfDocument.Page page = doc.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(0xFFFFFFFF);

                Bitmap sourcePage = sourcePages.get(i);
                drawSourcePage(canvas, pageInfo, sourcePage);
                drawOverlayWatermark(pageInfo, canvas, watermark);
                drawHeader(pageInfo, canvas, null, req);
                drawSealAnnotations(ctx, canvas, pageInfo, sourceFile, req, fullHash, i + 1, sourcePages.size());
                drawCertificationBlock(
                        canvas,
                        pageInfo,
                        fullHash,
                        shortHash,
                        buildSealPayload(req, fullHash),
                        req == null || req.includeHash,
                        req == null || req.includeQr
                );
                doc.finishPage(page);
            }
        }

        if (!rendered) {
            doc.close();
            generateSealedPdf(ctx, req, outFile);
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            doc.writeTo(fos);
        }
        doc.close();
    }

    private static boolean stampPdfDocument(
            Context ctx,
            SealRequest req,
            File sourceFile,
            File outFile,
            String fullHash,
            String shortHash,
            Bitmap watermark
    ) {
        PDDocument document = null;
        Bitmap qrBitmap = null;
        try {
            PDFBoxResourceLoader.init(ctx.getApplicationContext());
            document = PDDocument.load(sourceFile);
            int totalPages = document.getNumberOfPages();
            PDImageXObject watermarkImage = watermark != null
                    ? LosslessFactory.createFromImage(document, watermark)
                    : null;
            PDImageXObject qrImage = null;
            if (req == null || req.includeQr) {
                qrBitmap = makeQr(buildSealPayload(req, fullHash));
                qrImage = LosslessFactory.createFromImage(document, qrBitmap);
            }

            for (int i = 0; i < totalPages; i++) {
                PDPage page = document.getPage(i);
                PDRectangle box = page.getMediaBox();
                if (box == null) {
                    box = PDRectangle.LETTER;
                }

                try (PDPageContentStream stream = new PDPageContentStream(
                        document,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                )) {
                    drawPdfHeaderBand(stream, box, req, sourceFile, i + 1, totalPages);
                    drawPdfFooterBand(stream, box, req, fullHash, shortHash, i + 1, totalPages);
                    drawPdfWatermark(stream, box, watermarkImage);
                    if (qrImage != null) {
                        drawPdfQrPanel(stream, box, qrImage);
                    }
                }
            }

            document.save(outFile);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            try {
                if (document != null) {
                    document.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (qrBitmap != null && !qrBitmap.isRecycled()) {
                    qrBitmap.recycle();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void drawPdfHeaderBand(
            PDPageContentStream stream,
            PDRectangle box,
            SealRequest req,
            File sourceFile,
            int pageNumber,
            int totalPages
    ) throws IOException {
        float pageHeight = box.getHeight();
        float pageWidth = box.getWidth();
        float bandHeight = 54f;
        fillPdfRect(stream, 26f, pageHeight - 74f, pageWidth - 52f, bandHeight, 255, 255, 255, 0.28f);
        strokePdfRect(stream, 26f, pageHeight - 74f, pageWidth - 52f, bandHeight, 126, 160, 198);

        drawPdfText(stream, PDType1Font.HELVETICA_BOLD, 13f, 40f, pageHeight - 42f,
                req != null && req.title != null && !req.title.trim().isEmpty()
                        ? req.title
                        : "Verum Omnis Sealed Evidence",
                12, 37, 77);

        String sourceLabel = "Source: " + ((req != null && req.sourceFileName != null && !req.sourceFileName.trim().isEmpty())
                ? req.sourceFileName
                : (sourceFile != null ? sourceFile.getName() : "unknown"));
        drawPdfText(stream, PDType1Font.HELVETICA, 9f, 40f, pageHeight - 57f, sourceLabel, 30, 30, 30);

        String pageLabel = "Page " + pageNumber + " of " + totalPages;
        float labelWidth = textWidth(PDType1Font.HELVETICA_BOLD, 9f, pageLabel);
        drawPdfText(stream, PDType1Font.HELVETICA_BOLD, 9f, Math.max(40f, pageWidth - 42f - labelWidth),
                pageHeight - 57f, pageLabel, 12, 37, 77);
    }

    private static void drawPdfFooterBand(
            PDPageContentStream stream,
            PDRectangle box,
            SealRequest req,
            String fullHash,
            String shortHash,
            int pageNumber,
            int totalPages
    ) throws IOException {
        float pageWidth = box.getWidth();
        fillPdfRect(stream, 26f, 18f, pageWidth - 52f, 28f, 255, 255, 255, 0.24f);
        strokePdfRect(stream, 26f, 18f, pageWidth - 52f, 28f, 126, 160, 198);

        String caseLabel = req != null && req.caseId != null && !req.caseId.trim().isEmpty()
                ? req.caseId.trim()
                : "no-case-id";
        String footer = "VERUM OMNIS SEAL | " + caseLabel + " | " + formatHashSnippet(fullHash) + " | " + shortHash;
        drawPdfText(stream, PDType1Font.HELVETICA_BOLD, 8.5f, 36f, 28f, footer, 21, 58, 104);

        String pageLabel = pageNumber + "/" + totalPages;
        float labelWidth = textWidth(PDType1Font.HELVETICA_BOLD, 8.5f, pageLabel);
        drawPdfText(stream, PDType1Font.HELVETICA_BOLD, 8.5f, Math.max(36f, pageWidth - 36f - labelWidth),
                28f, pageLabel, 21, 58, 104);
    }

    private static void drawPdfWatermark(
            PDPageContentStream stream,
            PDRectangle box,
            PDImageXObject watermarkImage
    ) throws IOException {
        if (watermarkImage == null) {
            return;
        }
        RectF fitted = getUnifiedWatermarkBounds(
                watermarkImage.getWidth(),
                watermarkImage.getHeight(),
                box.getWidth(),
                box.getHeight()
        );
        stream.saveGraphicsState();
        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(PDF_WATERMARK_ALPHA);
        state.setAlphaSourceFlag(true);
        stream.setGraphicsStateParameters(state);
        stream.drawImage(
                watermarkImage,
                fitted.left,
                box.getHeight() - fitted.bottom,
                fitted.width(),
                fitted.height()
        );
        stream.restoreGraphicsState();
    }

    private static void drawPdfQrPanel(
            PDPageContentStream stream,
            PDRectangle box,
            PDImageXObject qrImage
    ) throws IOException {
        float panelSize = 94f;
        float panelLeft = box.getWidth() - panelSize - 30f;
        float panelBottom = box.getHeight() - panelSize - 112f;
        fillPdfRect(stream, panelLeft, panelBottom, panelSize, panelSize, 255, 255, 255, 0.92f);
        strokePdfRect(stream, panelLeft, panelBottom, panelSize, panelSize, 52, 98, 152);
        stream.drawImage(qrImage, panelLeft + 11f, panelBottom + 18f, 72f, 72f);
        drawPdfText(stream, PDType1Font.HELVETICA_BOLD, 7.5f, panelLeft + 15f, panelBottom + 8f,
                "VERIFY SEAL", 21, 58, 104);
    }

    private static void fillPdfRect(
            PDPageContentStream stream,
            float x,
            float y,
            float width,
            float height,
            int r,
            int g,
            int b,
            float alpha
    ) throws IOException {
        stream.saveGraphicsState();
        PDExtendedGraphicsState state = new PDExtendedGraphicsState();
        state.setNonStrokingAlphaConstant(alpha);
        state.setAlphaSourceFlag(true);
        stream.setGraphicsStateParameters(state);
        stream.setNonStrokingColor(r, g, b);
        stream.addRect(x, y, width, height);
        stream.fill();
        stream.restoreGraphicsState();
    }

    private static void strokePdfRect(
            PDPageContentStream stream,
            float x,
            float y,
            float width,
            float height,
            int r,
            int g,
            int b
    ) throws IOException {
        stream.saveGraphicsState();
        stream.setStrokingColor(r, g, b);
        stream.setLineWidth(0.8f);
        stream.addRect(x, y, width, height);
        stream.stroke();
        stream.restoreGraphicsState();
    }

    private static void drawPdfText(
            PDPageContentStream stream,
            PDType1Font font,
            float size,
            float x,
            float y,
            String text,
            int r,
            int g,
            int b
    ) throws IOException {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        stream.beginText();
        stream.setNonStrokingColor(r, g, b);
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitizePdfText(text));
        stream.endText();
    }

    private static float textWidth(PDType1Font font, float size, String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return (font.getStringWidth(sanitizePdfText(text)) / 1000f) * size;
    }

    private static String sanitizePdfText(String text) {
        return text == null ? "" : text.replace("\n", " ").replace("\r", " ");
    }

    private static List<Bitmap> loadSourcePages(Context ctx, File sourceFile) {
        List<Bitmap> pages = new ArrayList<>();
        if (sourceFile == null || !sourceFile.exists()) {
            return pages;
        }

        if (MediaForensics.isPdf(sourceFile)) {
            pages.addAll(NativePdfRenderer.render(ctx, sourceFile, 250));
            return pages;
        }

        Bitmap image = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        if (image != null) {
            pages.add(image);
        }
        return pages;
    }

    private static void drawSourcePage(Canvas canvas, PdfDocument.PageInfo pageInfo, Bitmap sourcePage) {
        if (sourcePage == null) {
            return;
        }
        float left = 36f;
        float top = 164f;
        float right = pageInfo.getPageWidth() - 36f;
        float bottom = pageInfo.getPageHeight() - 132f;
        RectF target = fitRect(
                sourcePage.getWidth(),
                sourcePage.getHeight(),
                left,
                top,
                right,
                bottom
        );
        canvas.drawBitmap(sourcePage, null, target, null);
    }

    private static RectF fitRect(
            float sourceWidth,
            float sourceHeight,
            float left,
            float top,
            float right,
            float bottom
    ) {
        float availableWidth = right - left;
        float availableHeight = bottom - top;
        float scale = Math.min(availableWidth / Math.max(1f, sourceWidth), availableHeight / Math.max(1f, sourceHeight));
        float drawWidth = sourceWidth * scale;
        float drawHeight = sourceHeight * scale;
        float drawLeft = left + ((availableWidth - drawWidth) / 2f);
        float drawTop = top + ((availableHeight - drawHeight) / 2f);
        return new RectF(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight);
    }

    private static RectF getUnifiedWatermarkBounds(
            float sourceWidth,
            float sourceHeight,
            float pageWidth,
            float pageHeight
    ) {
        return fitRect(
                sourceWidth,
                sourceHeight,
                WATERMARK_LEFT,
                WATERMARK_TOP,
                pageWidth - WATERMARK_RIGHT_MARGIN,
                pageHeight - WATERMARK_BOTTOM_MARGIN
        );
    }

    private static void drawOverlayWatermark(PdfDocument.PageInfo pageInfo, Canvas canvas, Bitmap logo) {
        if (logo == null) {
            return;
        }
        Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        watermarkPaint.setAlpha(CANVAS_WATERMARK_ALPHA);
        RectF bounds = getUnifiedWatermarkBounds(
                logo.getWidth(),
                logo.getHeight(),
                pageInfo.getPageWidth(),
                pageInfo.getPageHeight()
        );
        int width = Math.max(1, Math.round(bounds.width()));
        int height = Math.max(1, Math.round(bounds.height()));
        Bitmap watermark = Bitmap.createScaledBitmap(logo, width, height, true);
        canvas.drawBitmap(watermark, bounds.left, bounds.top, watermarkPaint);
    }

    private static void drawSealAnnotations(
            Context ctx,
            Canvas canvas,
            PdfDocument.PageInfo pageInfo,
            File sourceFile,
            SealRequest req,
            String fullHash,
            int pageNumber,
            int totalPages
    ) {
        Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        metaPaint.setColor(0xFF0C254D);
        metaPaint.setTextSize(sp(ctx, 8));

        float y = pageInfo.getPageHeight() - 94f;
        canvas.drawText("SEALED DOCUMENT COPY", 40f, y, metaPaint);
        y += 12f;

        String sourceLabel = "Source: " + (req != null && req.sourceFileName != null && !req.sourceFileName.trim().isEmpty()
                ? req.sourceFileName
                : sourceFile.getName());
        y = drawWrappedLine(canvas, sourceLabel, 40f, y, 360f, 12f, 0xFF0C254D, 8f);
        y = drawWrappedLine(canvas, "Page " + pageNumber + " of " + totalPages, 40f, y, 360f, 12f, 0xFF0C254D, 8f);
        if (req != null && req.caseId != null && !req.caseId.trim().isEmpty()) {
            y = drawWrappedLine(canvas, "Seal ID: " + req.caseId, 40f, y, 360f, 12f, 0xFF0C254D, 8f);
        }
        drawWrappedLine(canvas, "SHA-512: " + formatHashSnippet(fullHash), 40f, y, 360f, 12f, 0xFF0C254D, 8f);
    }

    private static String truncate(String fullHash, int chars) {
        if (fullHash == null) return "";
        return fullHash.length() <= chars ? fullHash : fullHash.substring(0, chars);
    }

    private static void drawFullPageWatermark(PdfDocument.PageInfo pageInfo, Canvas canvas, Bitmap logo) {
        if (logo == null) {
            return;
        }
        Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        watermarkPaint.setAlpha(CANVAS_WATERMARK_ALPHA);
        RectF bounds = getUnifiedWatermarkBounds(
                logo.getWidth(),
                logo.getHeight(),
                pageInfo.getPageWidth(),
                pageInfo.getPageHeight()
        );
        int width = Math.max(1, Math.round(bounds.width()));
        int height = Math.max(1, Math.round(bounds.height()));
        Bitmap watermark = Bitmap.createScaledBitmap(logo, width, height, true);
        canvas.drawBitmap(watermark, bounds.left, bounds.top, watermarkPaint);
    }

    private static void drawHeader(PdfDocument.PageInfo pageInfo, Canvas canvas, Bitmap logo, SealRequest req) {
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0x55348BFF);
        linePaint.setStrokeWidth(1.6f);
        canvas.drawLine(36f, 148f, pageInfo.getPageWidth() - 36f, 148f, linePaint);

        if (logo != null) {
            RectF logoBounds = fitRect(
                    logo.getWidth(),
                    logo.getHeight(),
                    36f,
                    34f,
                    250f,
                    108f
            );
            Bitmap headerLogo = Bitmap.createScaledBitmap(
                    logo,
                    Math.max(1, Math.round(logoBounds.width())),
                    Math.max(1, Math.round(logoBounds.height())),
                    true
            );
            canvas.drawBitmap(headerLogo, logoBounds.left, logoBounds.top, null);

            Paint modePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            modePaint.setColor(0xFF153A68);
            modePaint.setTextSize(11f);
            String modeLabel = (req != null && req.mode == DocumentMode.FORENSIC_REPORT)
                    ? "SEALED CONSTITUTIONAL REPORT"
                    : "SEALED DOCUMENT";
            float infoX = logoBounds.right + 24f;
            float infoWidth = getQrPanelLeft(pageInfo.getPageWidth()) - infoX - 18f;
            if (infoWidth < 120f) {
                infoX = 36f;
                infoWidth = pageInfo.getPageWidth() - 72f;
            }
            canvas.drawText(modeLabel, infoX, 82f, modePaint);

            Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            detailPaint.setColor(0xFF3B5F8E);
            detailPaint.setTextSize(10f);
            String detailLabel = (req != null && req.mode == DocumentMode.FORENSIC_REPORT)
                    ? "Human and audit outputs generated from sealed findings only."
                    : "Original artifact wrapped with QR, hash, and seal annotations.";
            drawWrappedLine(canvas, detailLabel, infoX, 104f, infoWidth, 13f, 0xFF3B5F8E, 10f);
            return;
        }

        Paint brandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(0xFF0C254D);
        brandPaint.setTextSize(28f);
        canvas.drawText("VERUM OMNIS", 156f, 94f, brandPaint);

        Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subPaint.setColor(0xFF3B5F8E);
        subPaint.setTextSize(12f);
        String subtitle = (req != null && req.mode == DocumentMode.FORENSIC_REPORT)
                ? "CONSTITUTIONAL FORENSIC REPORT"
                : "SEALED DOCUMENT";
        canvas.drawText(subtitle, 158f, 122f, subPaint);
    }

    private static void drawWrappedText(
            Canvas canvas,
            String text,
            float x,
            float y,
            float textSize,
            float maxWidth,
            float lineHeight,
            int color
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(textSize);

        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth) {
                canvas.drawText(line.toString(), x, y, paint);
                y += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y, paint);
        }
    }

    private static float drawWrappedParagraph(
            Canvas canvas,
            String text,
            float x,
            float y,
            float maxWidth,
            float lineHeight,
            int color,
            float textSize,
            RectF exclusionRect,
            float pageWidth
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(textSize);
        if (text == null || text.trim().isEmpty()) {
            return y;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (intersectsExclusion(y, exclusionRect)) {
                float shiftedX = Math.max(x, exclusionRect.right + QR_TEXT_GUTTER);
                float shiftedWidth = pageWidth - shiftedX - PAGE_LEFT;
                if (shiftedWidth < 140f) {
                    y = exclusionRect.bottom + 14f;
                }
            }

            float lineX = getLineX(x, y, exclusionRect);
            float lineWidth = getLineWidth(maxWidth, lineX, pageWidth);
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > lineWidth) {
                canvas.drawText(line.toString(), lineX, y, paint);
                y += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }

        if (line.length() > 0) {
            float lineX = getLineX(x, y, exclusionRect);
            canvas.drawText(line.toString(), lineX, y, paint);
            y += lineHeight;
        }
        return y;
    }

    private static float drawWrappedLine(
            Canvas canvas,
            String text,
            float x,
            float y,
            float maxWidth,
            float lineHeight,
            int color,
            float textSize
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(textSize);

        List<String> words = tokenizeForWrap(paint, text, maxWidth);
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth) {
                if (line.length() > 0) {
                    canvas.drawText(line.toString(), x, y, paint);
                    y += lineHeight;
                    line = new StringBuilder(word);
                } else {
                    canvas.drawText(word, x, y, paint);
                    y += lineHeight;
                }
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), x, y, paint);
            y += lineHeight;
        }
        return y;
    }

    private static boolean intersectsExclusion(float y, RectF exclusionRect) {
        return exclusionRect != null && y >= exclusionRect.top && y <= exclusionRect.bottom;
    }

    private static float getLineX(float defaultX, float y, RectF exclusionRect) {
        if (!intersectsExclusion(y, exclusionRect)) {
            return defaultX;
        }
        return Math.max(defaultX, exclusionRect.right + QR_TEXT_GUTTER);
    }

    private static float getLineWidth(float defaultWidth, float lineX, float pageWidth) {
        return Math.min(defaultWidth, pageWidth - lineX - PAGE_LEFT);
    }

    private static int drawBodyText(
            Canvas canvas,
            String[] lines,
            int startIndex,
            float x,
            float y,
            float maxWidth,
            float lineHeight,
            float textSize,
            float maxY
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF1E1E1E);
        paint.setTextSize(textSize);

        int index = startIndex;
        while (index < lines.length && y <= maxY) {
            String rawLine = lines[index] == null ? "" : lines[index].trim();
            if (rawLine.isEmpty()) {
                y += lineHeight;
                index++;
                continue;
            }

            List<String> words = tokenizeForWrap(paint, rawLine, maxWidth);
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (paint.measureText(candidate) > maxWidth) {
                    if (y > maxY) {
                        return index;
                    }
                    if (line.length() > 0) {
                        canvas.drawText(line.toString(), x, y, paint);
                        y += lineHeight;
                        line = new StringBuilder(word);
                    } else {
                        canvas.drawText(word, x, y, paint);
                        y += lineHeight;
                    }
                } else {
                    line = new StringBuilder(candidate);
                }
            }

            if (line.length() > 0) {
                if (y > maxY) {
                    return index;
                }
                canvas.drawText(line.toString(), x, y, paint);
                y += lineHeight;
            }
            index++;
        }
        return index;
    }

    private static List<String> tokenizeForWrap(Paint paint, String text, float maxWidth) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return tokens;
        }
        for (String token : text.trim().split("\\s+")) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            if (paint.measureText(token) <= maxWidth) {
                tokens.add(token);
                continue;
            }
            tokens.addAll(splitLongToken(paint, token, maxWidth));
        }
        return tokens;
    }

    private static List<String> splitLongToken(Paint paint, String token, float maxWidth) {
        List<String> segments = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            return segments;
        }
        int start = 0;
        while (start < token.length()) {
            int end = token.length();
            while (end > start + 1 && paint.measureText(token.substring(start, end)) > maxWidth) {
                end--;
            }
            if (end <= start) {
                end = Math.min(token.length(), start + 1);
            }
            segments.add(token.substring(start, end));
            start = end;
        }
        return segments;
    }

    private static int estimatePageCount(String[] bodyLines) {
        if (bodyLines.length == 0) {
            return 1;
        }
        int approxLines = 0;
        for (String line : bodyLines) {
            int len = line == null ? 0 : line.length();
            approxLines += Math.max(1, (len / 78) + 1);
        }
        return Math.max(1, (int) Math.ceil(approxLines / 34.0));
    }

    private static void drawCertificationBlock(
            Canvas canvas,
            PdfDocument.PageInfo pageInfo,
            String fullHash,
            String shortHash,
            String qrPayload,
            boolean includeHash,
            boolean includeQr
    ) {
        if (includeHash) {
            Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            footerPaint.setColor(0xFF153A68);
            footerPaint.setTextSize(8f);
            String certText = "VERUM OMNIS SEAL  |  SHA-512 " + formatHashSnippet(fullHash) + "  |  " + shortHash;
            canvas.drawText(certText, 40f, pageInfo.getPageHeight() - 34f, footerPaint);
        }

        if (includeQr) {
            try {
                Bitmap qr = makeQr(qrPayload);
                int q = (int) QR_CODE_SIZE;
                float panelLeft = getQrPanelLeft(pageInfo.getPageWidth());
                float panelTop = QR_PANEL_TOP;
                float panelSize = QR_PANEL_SIZE;

                Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                panelPaint.setColor(0xF7FFFFFF);
                canvas.drawRoundRect(panelLeft, panelTop, panelLeft + panelSize, panelTop + panelSize, 10f, 10f, panelPaint);

                Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                borderPaint.setColor(0x33153A68);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(1.2f);
                canvas.drawRoundRect(panelLeft, panelTop, panelLeft + panelSize, panelTop + panelSize, 10f, 10f, borderPaint);

                canvas.drawBitmap(qr, null,
                        new RectF(panelLeft + QR_PANEL_PADDING, panelTop + QR_PANEL_PADDING,
                                panelLeft + QR_PANEL_PADDING + QR_CODE_SIZE, panelTop + QR_PANEL_PADDING + QR_CODE_SIZE),
                        null);

                Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                labelPaint.setColor(0xFF153A68);
                labelPaint.setTextSize(7f);
                canvas.drawText("VERIFY SEAL", panelLeft + 9f, panelTop + panelSize - 8f, labelPaint);
            } catch (WriterException ignored) {
            }
        }
    }

    private static String formatHashSnippet(String fullHash) {
        if (fullHash == null || fullHash.isEmpty()) {
            return "UNAVAILABLE";
        }
        if (fullHash.length() <= 24) {
            return fullHash;
        }
        return fullHash.substring(0, 16) + "..." + fullHash.substring(fullHash.length() - 8);
    }

    private static RectF getQrExclusionRect(SealRequest req, PdfDocument.PageInfo pageInfo) {
        if (req != null && !req.includeQr) {
            return null;
        }
        return new RectF(
                getQrPanelLeft(pageInfo.getPageWidth()),
                QR_PANEL_TOP,
                getQrPanelLeft(pageInfo.getPageWidth()) + QR_PANEL_SIZE,
                QR_PANEL_TOP + QR_PANEL_SIZE
        );
    }

    private static float getQrPanelLeft(float pageWidth) {
        return pageWidth - QR_PANEL_SIZE - 36f;
    }

    private static String buildSealPayload(SealRequest req, String fullHash) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("scheme", "verum-omnis-seal");
            payload.put("version", "1.0");
            payload.put("sha512", fullHash);
            payload.put("sealId", req != null && req.caseId != null ? req.caseId : JSONObject.NULL);
            payload.put("mode", req != null && req.mode != null ? req.mode.name() : DocumentMode.SEAL_ONLY.name());
            payload.put("title", req != null && req.title != null ? req.title : JSONObject.NULL);
            payload.put("sourceFileName", req != null && req.sourceFileName != null ? req.sourceFileName : JSONObject.NULL);
            payload.put("jurisdiction", req != null && req.jurisdiction != null ? req.jurisdiction : JSONObject.NULL);
            payload.put("intake", req != null && req.intakeMetadata != null ? req.intakeMetadata : JSONObject.NULL);
            payload.put("issuer", "VERUM OMNIS");
            payload.put("verificationHint", "Validate full SHA-512 against the sealed artifact and confirm no tamper findings.");
            return payload.toString();
        } catch (Exception e) {
            return "verum://seal?sha512=" + fullHash
                    + "&sealId=" + safeQrValue(req != null ? req.caseId : null)
                    + "&mode=" + safeQrValue(req != null && req.mode != null ? req.mode.name() : null);
        }
    }

    private static String safeQrValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(" ", "%20");
    }
}
