package com.couplefinance.utils;

public final class FirebaseConfig {

	private FirebaseConfig() {}

	public static final String PROJECT_ID = "couple-bacc7";
	public static final String API_KEY = "AIzaSyDZPN2dchmQpk_jk_fOBAf3RdPzjgDURiU";

	public static final String FIRESTORE_BASE_URL =
			"https://firestore.googleapis.com/v1/projects/"
					+ PROJECT_ID
					+ "/databases/(default)/documents/";

	public static final String BASE_URL = FIRESTORE_BASE_URL;

	public static final String AUTH_URL =
			"https://identitytoolkit.googleapis.com/v1/accounts:";

	public static final String REFRESH_URL =
			"https://securetoken.googleapis.com/v1/token?key=" + API_KEY;

	public static String documentUrl(String path) {
		return FIRESTORE_BASE_URL + cleanPath(path) + "?key=" + API_KEY;
	}

	public static String collectionUrl(String path) {
		return documentUrl(path);
	}

	public static String apiRootUrl(String fullPath) {
		return "https://firestore.googleapis.com/v1/" + cleanPath(fullPath) + "?key=" + API_KEY;
	}

	public static String documentDeleteUrl(String basePath, String collection, String docId) {
		return documentUrl(cleanPath(basePath) + "/" + cleanPath(collection) + "/" + cleanPath(docId));
	}

	public static String documentUpdateUrl(String path, String... fields) {
		StringBuilder sb = new StringBuilder(documentUrl(path));

		if (fields != null) {
			for (String field : fields) {
				if (field != null && !field.trim().isEmpty()) {
					sb.append("&updateMask.fieldPaths=").append(field.trim());
				}
			}
		}

		return sb.toString();
	}

	private static String cleanPath(String path) {
		if (path == null) {
			return "";
		}

		String clean = path.trim();
		while (clean.startsWith("/")) {
			clean = clean.substring(1);
		}
		return clean;
	}
}
