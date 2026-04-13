package com.verum.omnis.forensic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class NativeOcrEngine {

    private NativeOcrEngine() {}

    static TextRecognizer createRecognizer() {
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public static List<OcrTextBlock> extract(Context context, File file, List<Bitmap> renderedPages) {
        List<OcrTextBlock> blocks = new ArrayList<>();
        TextRecognizer recognizer = createRecognizer();

        try {
            if (!renderedPages.isEmpty()) {
                for (int i = 0; i < renderedPages.size(); i++) {
                    try {
                        blocks.add(extractPage(recognizer, renderedPages.get(i), i));
                    } catch (Exception e) {
                        blocks.add(new OcrTextBlock(
                                i,
                                "OCR_FAILED page=" + (i + 1) + " error=" + e.getClass().getSimpleName(),
                                0.0f
                        ));
                    }
                }
                return blocks;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                try {
                    blocks.add(extractPage(recognizer, bitmap, 0));
                } catch (Exception e) {
                    blocks.add(new OcrTextBlock(0, "OCR_FAILED page=1 error=" + e.getClass().getSimpleName(), 0.0f));
                }
                return blocks;
            }
        } catch (Exception ignored) {
            // fall through to the failure record below
        } finally {
            recognizer.close();
        }

        blocks.add(new OcrTextBlock(0, "OCR_FAILED file=" + file.getName(), 0.0f));
        return blocks;
    }

    static OcrTextBlock extractPage(TextRecognizer recognizer, Bitmap bitmap, int pageIndex)
            throws Exception {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Text result = Tasks.await(recognizer.process(image));
        String text = result.getText();
        if (text == null || text.trim().isEmpty()) {
            return new OcrTextBlock(pageIndex, "OCR_NO_TEXT page=" + (pageIndex + 1), 0.0f);
        }
        return new OcrTextBlock(pageIndex, text, estimateConfidence(result));
    }

    private static float estimateConfidence(Text result) {
        int blockCount = result.getTextBlocks().size();
        if (blockCount == 0) {
            return 0.0f;
        }

        int lineCount = 0;
        for (Text.TextBlock block : result.getTextBlocks()) {
            lineCount += block.getLines().size();
        }

        float densityScore = Math.min(1.0f, lineCount / 24.0f);
        return Math.max(0.35f, densityScore);
    }
}
