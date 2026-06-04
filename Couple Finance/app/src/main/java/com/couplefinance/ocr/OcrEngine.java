package com.couplefinance.ocr;

import android.app.Activity;
import android.graphics.Bitmap;

/**
 * OcrEngine — abstraction du moteur d'OCR.
 *
 * <p>L'import OCR ne dépend jamais directement de Tesseract : il passe par
 * cette interface. Cela permet de remplacer l'implémentation (Tesseract,
 * ML Kit, OCR distant…) sans toucher au parsing ni à l'interface.</p>
 *
 * <p>Implémentation par défaut : {@link TesseractOcrEngine} (hors-ligne).</p>
 */
public interface OcrEngine {

	/** Résultat asynchrone d'une reconnaissance de texte. */
	interface Callback {
		/** @param rawText texte brut reconnu, lignes séparées par '\n'. */
		void onSuccess(String rawText);

		void onError(String message);
	}

	/**
	 * Reconnaît le texte d'une image.
	 *
	 * <p>L'implémentation effectue le travail lourd hors du thread principal
	 * et invoque {@code callback} sur le thread principal.</p>
	 *
	 * @param activity activité hôte (contexte + accès assets).
	 * @param bitmap   image à analyser (non nulle).
	 * @param callback callback de résultat.
	 */
	void recognize(Activity activity, Bitmap bitmap, Callback callback);

	/** Libère les ressources natives éventuelles. À appeler après usage. */
	void release();
}
