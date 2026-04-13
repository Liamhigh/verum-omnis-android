package com.verum.omnis.forensic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.Log;

import com.verum.omnis.core.HashUtil;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Alternative implementation of PdfSealer.
 * Produces a PDF with hash text and optional logo.
 */
public class PdfSealerV2 implements PdfSealer {

    @Override
    public Result seal(Context ctx, File inputFile, Bitmap logo) throws Exception {
        Result result = new Result();

        // Compute file hash
        String hashHex = HashUtil.sha512File(inputFile);

        // Create PDF document
        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(14f);

        // Title
        canvas.drawText("Verum Omnis – Forensic Report (V2)", 72, 72, paint);

        // Hash
        paint.setTextSize(12f);
        canvas.drawText("SHA-512: " + hashHex, 72, 100, paint);

        // Logo if provided
        if (logo != null) {
            canvas.drawBitmap(logo, 72, 140, null);
        }

        doc.finishPage(page);

        // Save output
        File outFile = new File(ctx.getCacheDir(),
                "sealed_" + System.currentTimeMillis() + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            doc.writeTo(fos);
        } catch (Exception e) {
            Log.e("PdfSealerV2", "Failed to write PDF: " + e.getMessage());
            throw e;
        } finally {
            doc.close();
        }

        // Fill result object
        result.pdfFile = outFile;
        result.sha512Hex = hashHex;

        return result;
    }
}
