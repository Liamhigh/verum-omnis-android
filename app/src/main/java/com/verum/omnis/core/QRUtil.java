package com.verum.omnis.core;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Utility class for generating QR code bitmaps.
 *
 * <p>This class is intentionally simple and self‑contained.  It wraps the
 * ZXing {@link QRCodeWriter} to produce a {@link Bitmap} from arbitrary
 * text.  If the underlying QR code generator throws, a white square of
 * the requested size is returned instead of propagating the exception.</p>
 */
public final class QRUtil {
    private QRUtil() {}

    /**
     * Generate a placeholder QR code bitmap from the given text.
     *
     * @param text the content to encode in the QR code
     * @param size the width and height in pixels of the resulting square bitmap
     * @return a bitmap containing the QR code, or a blank bitmap on error
     */
    public static Bitmap placeholderQR(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            Bitmap fallback = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            fallback.eraseColor(Color.WHITE);
            return fallback;
        }
    }
}