package com.verum.omnis.forensic;

public final class OcrTextBlock {
    public final int pageIndex;
    public final String text;
    public final float confidence;

    public OcrTextBlock(int pageIndex, String text, float confidence) {
        this.pageIndex = pageIndex;
        this.text = text;
        this.confidence = confidence;
    }
}
