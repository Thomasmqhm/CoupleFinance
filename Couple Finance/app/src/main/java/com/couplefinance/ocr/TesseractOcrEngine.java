package com.couplefinance.ocr;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TesseractOcrEngine implements OcrEngine {

    private static final String TAG = "OCR_RAW";

    private static final Object LOCK = new Object();
    private static final ArrayList<OcrLine> LAST_LINES = new ArrayList<>();
    private static int lastImageWidth = 0;
    private static int lastImageHeight = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextRecognizer recognizer;

    public TesseractOcrEngine() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    @Override
    public void recognize(Activity activity, Bitmap bitmap, Callback callback) {
        if (activity == null) {
            fail(callback, "Contexte indisponible");
            return;
        }

        if (bitmap == null) {
            fail(callback, "Image illisible");
            return;
        }

        if (recognizer == null) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }

        try {
            Bitmap safeBitmap = ensureArgb(bitmap);
            InputImage image = InputImage.fromBitmap(safeBitmap, 0);

            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String result = extractTextWithVisualOrder(text, safeBitmap);

                        Log.d(TAG, "========== OCR VISUAL RAW ==========");
                        Log.d(TAG, result);
                        Log.d(TAG, "====================================");

                        handler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(result);
                            }
                        });
                    })
                    .addOnFailureListener(e -> fail(callback, "Erreur OCR : " + safeMessage(e)));

        } catch (Throwable t) {
            fail(callback, "Erreur OCR : " + safeMessage(t));
        }
    }

    @Override
    public void release() {
        try {
            if (recognizer != null) {
                recognizer.close();
                recognizer = null;
            }
        } catch (Exception ignored) {
        }
    }

    public static List<OcrLine> getLastLinesSnapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(LAST_LINES);
        }
    }

    public static int getLastImageWidth() {
        synchronized (LOCK) {
            return lastImageWidth;
        }
    }

    public static int getLastImageHeight() {
        synchronized (LOCK) {
            return lastImageHeight;
        }
    }

    private String extractTextWithVisualOrder(Text text, Bitmap bitmap) {
        ArrayList<OcrLine> items = new ArrayList<>();

        if (text != null) {
            for (Text.TextBlock block : text.getTextBlocks()) {
                if (block == null) continue;

                for (Text.Line line : block.getLines()) {
                    if (line == null) continue;

                    String value = line.getText();
                    Rect box = line.getBoundingBox();

                    if (value == null || value.trim().isEmpty() || box == null) {
                        continue;
                    }

                    items.add(new OcrLine(value.trim(), box.left, box.top, box.right, box.bottom));
                }
            }
        }

        Collections.sort(items, new Comparator<OcrLine>() {
            @Override
            public int compare(OcrLine a, OcrLine b) {
                int tolerance = Math.max(12, Math.min(a.height(), b.height()));

                if (Math.abs(a.top - b.top) <= tolerance) {
                    return Integer.compare(a.left, b.left);
                }

                return Integer.compare(a.top, b.top);
            }
        });

        synchronized (LOCK) {
            LAST_LINES.clear();
            LAST_LINES.addAll(items);
            lastImageWidth = bitmap != null ? bitmap.getWidth() : 0;
            lastImageHeight = bitmap != null ? bitmap.getHeight() : 0;
        }

        StringBuilder sb = new StringBuilder();

        for (OcrLine item : items) {
            sb.append(item.text).append("\n");
        }

        return sb.toString().trim();
    }

    public static final class OcrLine {
        public final String text;
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        public OcrLine(String text, int left, int top, int right, int bottom) {
            this.text = text == null ? "" : text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }

        public int height() {
            return Math.max(1, bottom - top);
        }

        public int width() {
            return Math.max(1, right - left);
        }
    }

    private Bitmap ensureArgb(Bitmap src) {
        try {
            if (src.getConfig() == Bitmap.Config.ARGB_8888) {
                return src;
            }

            return src.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable ignored) {
            return src;
        }
    }

    private void fail(Callback callback, String message) {
        handler.post(() -> {
            if (callback != null) {
                callback.onError(message);
            }
        });
    }

    private String safeMessage(Throwable t) {
        if (t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()) {
            return "inconnue";
        }

        return t.getMessage();
    }
}