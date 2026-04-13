package com.verum.omnis.forensic;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.verum.omnis.core.MediaForensics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class NativePdfTextExtractor {

    private NativePdfTextExtractor() {}

    public static List<OcrTextBlock> extract(Context context, File file) {
        List<OcrTextBlock> blocks = new ArrayList<>();
        if (context == null || file == null || !MediaForensics.isPdf(file)) {
            return blocks;
        }

        PDDocument document = null;
        try {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            document = PDDocument.load(file);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);
                String text = stripper.getText(document);
                if (text == null) {
                    continue;
                }
                text = text.replace('\u0000', ' ').trim();
                if (text.length() < 24) {
                    continue;
                }
                blocks.add(new OcrTextBlock(pageIndex, text, 0.99f));
            }
        } catch (Throwable ignored) {
            // Fall back to OCR-only processing when embedded PDF text is unavailable.
        } finally {
            try {
                if (document != null) {
                    document.close();
                }
            } catch (Exception ignored) {
            }
        }
        return blocks;
    }
}
