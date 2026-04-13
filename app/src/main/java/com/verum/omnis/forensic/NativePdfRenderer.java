package com.verum.omnis.forensic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.verum.omnis.core.MediaForensics;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NativePdfRenderer {

    public interface PageConsumer {
        void accept(int pageIndex, int totalPages, Bitmap bitmap);
    }

    private NativePdfRenderer() {}

    public static int countPages(File file) {
        if (!MediaForensics.isPdf(file)) {
            return 0;
        }

        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            return renderer.getPageCount();
        } catch (Throwable ignored) {
            return 0;
        } finally {
            try {
                if (renderer != null) renderer.close();
            } catch (Exception ignored) {}
            try {
                if (descriptor != null) descriptor.close();
            } catch (Exception ignored) {}
        }
    }

    public static List<Bitmap> render(Context context, File file, int maxPages) {
        List<Bitmap> pages = new ArrayList<>();
        if (!MediaForensics.isPdf(file)) {
            return pages;
        }

        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            int count = Math.min(renderer.getPageCount(), maxPages);
            for (int i = 0; i < count; i++) {
                PdfRenderer.Page page = null;
                try {
                    page = renderer.openPage(i);
                    float scale = Math.min(1.35f, 1600f / Math.max(1f, Math.max(page.getWidth(), page.getHeight())));
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pages.add(bitmap);
                } catch (Throwable ignored) {
                } finally {
                    try {
                        if (page != null) page.close();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (renderer != null) renderer.close();
            } catch (Exception ignored) {}
            try {
                if (descriptor != null) descriptor.close();
            } catch (Exception ignored) {}
        }
        return pages;
    }

    public static boolean renderEachPage(File file, int maxPages, PageConsumer consumer) {
        if (!MediaForensics.isPdf(file) || consumer == null) {
            return false;
        }

        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        boolean renderedAny = false;
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            int totalPages = renderer.getPageCount();
            int count = maxPages > 0 ? Math.min(totalPages, maxPages) : totalPages;
            for (int i = 0; i < count; i++) {
                PdfRenderer.Page page = null;
                Bitmap bitmap = null;
                try {
                    page = renderer.openPage(i);
                    float scale = Math.min(1.35f, 1600f / Math.max(1f, Math.max(page.getWidth(), page.getHeight())));
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    consumer.accept(i, count, bitmap);
                    renderedAny = true;
                } catch (Throwable ignored) {
                } finally {
                    try {
                        if (page != null) page.close();
                    } catch (Exception ignored) {}
                    try {
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (renderer != null) renderer.close();
            } catch (Exception ignored) {}
            try {
                if (descriptor != null) descriptor.close();
            } catch (Exception ignored) {}
        }
        return renderedAny;
    }

    public static boolean renderSelectedPages(File file, List<Integer> pageIndexes, PageConsumer consumer) {
        if (!MediaForensics.isPdf(file) || consumer == null || pageIndexes == null || pageIndexes.isEmpty()) {
            return false;
        }

        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        boolean renderedAny = false;
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            int sourceTotalPages = renderer.getPageCount();
            Set<Integer> uniquePages = new LinkedHashSet<>();
            for (Integer pageIndex : pageIndexes) {
                if (pageIndex == null) {
                    continue;
                }
                if (pageIndex < 0 || pageIndex >= sourceTotalPages) {
                    continue;
                }
                uniquePages.add(pageIndex);
            }

            int selectedCount = uniquePages.size();
            for (Integer pageIndex : uniquePages) {
                PdfRenderer.Page page = null;
                Bitmap bitmap = null;
                try {
                    page = renderer.openPage(pageIndex);
                    float scale = Math.min(1.35f, 1600f / Math.max(1f, Math.max(page.getWidth(), page.getHeight())));
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    consumer.accept(pageIndex, selectedCount, bitmap);
                    renderedAny = true;
                } catch (Throwable ignored) {
                } finally {
                    try {
                        if (page != null) page.close();
                    } catch (Exception ignored) {}
                    try {
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (renderer != null) renderer.close();
            } catch (Exception ignored) {}
            try {
                if (descriptor != null) descriptor.close();
            } catch (Exception ignored) {}
        }
        return renderedAny;
    }
}
