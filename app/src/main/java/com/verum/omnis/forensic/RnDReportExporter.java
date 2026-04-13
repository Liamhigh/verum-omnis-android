
package com.verum.omnis.forensic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import com.verum.omnis.ai.RnDController;
import com.verum.omnis.core.QRUtil;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Stateless exporter: writes a sealed demo PDF to app cache
 * (Replace PdfSealerV2 with audited PDF/A-3B engine in production)
 */
public class RnDReportExporter {

    public static File export(Context ctx, RnDController.Feedback fb, Bitmap logo) throws Exception {
        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas canvas = page.getCanvas();
        Paint p = new Paint();

        // Header
        p.setTextSize(16f);
        canvas.drawText("Verum Omnis – R&D Feedback Report (Stateless)", 60, 60, p);
        p.setTextSize(12f);
        canvas.drawText("Mode: rules-only", 60, 80, p);

        // Body (truncate to first 700 chars for demo)
        String js = fb.report.toString();
        String body = js.length() > 700 ? js.substring(0, 700) + "..." : js;
        drawMultiline(canvas, body, 60, 110, 12f, 470);

        // Footer block
        String cert = "\u2714 Patent Pending Verum Omnis";
        p.setTextSize(10f);
        float tw = p.measureText(cert);
        canvas.drawText(cert, info.getPageWidth() - tw - 24, info.getPageHeight() - 36, p);

        // QR from the JSON content (deterministic placeholder)
        Bitmap qr = QRUtil.placeholderQR(js, 140);
        canvas.drawBitmap(qr, info.getPageWidth() - 24 - qr.getWidth(), info.getPageHeight() - 36 - qr.getHeight() - 8, p);

        doc.finishPage(page);
        File out = new File(ctx.getCacheDir(), "verum_rnd_feedback_demo.pdf");
        FileOutputStream fos = new FileOutputStream(out);
        doc.writeTo(fos);
        fos.close();
        doc.close();
        return out;
    }

    private static void drawMultiline(Canvas c, String text, float x, float y, float size, float width) {
        Paint p = new Paint();
        p.setTextSize(size);
        float lineHeight = size + 4f;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + 100);
            String line = text.substring(start, end);
            c.drawText(line, x, y, p);
            y += lineHeight;
            start = end;
        }
    }
}
