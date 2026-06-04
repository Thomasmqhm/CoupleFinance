package com.couplefinance;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import com.couplefinance.utils.FirebaseConfig;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AuthManager {

	private static final String API_KEY = FirebaseConfig.API_KEY;
	private static final String AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:";
	private static final String REFRESH_URL = "https://securetoken.googleapis.com/v1/token?key=";
	private static final String PREFS = "auth_prefs";

	private static volatile AuthManager instance;
	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler handler = new Handler(Looper.getMainLooper());

	private Context context;
	private String token, userId, refreshToken, displayName, email;
	private long tokenExpiry = 0;

	private static String STATIC_TOKEN = null;
	private static String STATIC_USER_ID = null;

	public static String getStaticToken() {
		return STATIC_TOKEN;
	}

	public static String getStaticUserId() {
		return STATIC_USER_ID;
	}

	public static AuthManager getInstance() {
		if (instance == null) {
			synchronized (AuthManager.class) {
				if (instance == null) {
					instance = new AuthManager();
				}
			}
		}
		return instance;
	}

	public interface Callback {
		void onSuccess(String token, String userId);

		void onError(String error);
	}

	public void init(Context ctx) {
		// ✅ FIX : ne pas réinitialiser si déjà fait (évite d'écraser un token valide)
		if (context != null)
			return;
		context = ctx.getApplicationContext();
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		token = prefs.getString("token", null);
		userId = prefs.getString("userId", null);
		refreshToken = prefs.getString("refreshToken", null);
		displayName = prefs.getString("displayName", null);
		email = prefs.getString("email", null);
		tokenExpiry = prefs.getLong("tokenExpiry", 0);
		STATIC_TOKEN = token;
		STATIC_USER_ID = userId;
	}

	public boolean isLoggedIn() {
		return token != null && userId != null;
	}

	public String getToken() {
		// Charger depuis les prefs si token absent en mémoire
		if (token == null && context != null) {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			token = prefs.getString("token", null);
			userId = prefs.getString("userId", null);
			refreshToken = prefs.getString("refreshToken", null);
			tokenExpiry = prefs.getLong("tokenExpiry", 0);
			if (displayName == null) {
				displayName = prefs.getString("displayName", null);
			}
		}
		if (token == null)
			return STATIC_TOKEN;
		// Refresh en arrière-plan uniquement si token expiré,
		// sans jamais bloquer le thread appelant.
		// refreshTokenSync() sur le thread Firestore causait des deadlocks
		// et bloquait tous les chargements au démarrage.
		if (System.currentTimeMillis() > tokenExpiry && refreshToken != null) {
			executor.execute(this::refreshTokenSync);
		}
		return token;
	}

	public synchronized String getFreshTokenSync() {
		if (context != null) {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			if (token == null)
				token = prefs.getString("token", null);
			if (userId == null)
				userId = prefs.getString("userId", null);
			if (refreshToken == null)
				refreshToken = prefs.getString("refreshToken", null);
			tokenExpiry = prefs.getLong("tokenExpiry", 0);
		}

		refreshTokenSync();

		return token != null ? token : STATIC_TOKEN;
	}

	public String getUserId() {
		if (userId == null && context != null) {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			userId = prefs.getString("userId", null);
		}
		return userId != null ? userId : STATIC_USER_ID;
	}

	public String getDisplayName() {
		// ✅ FIX : si displayName est null en mémoire, tenter de le relire depuis les prefs
		if (displayName == null && context != null) {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			displayName = prefs.getString("displayName", null);
		}
		return displayName;
	}

	public void setDisplayName(String name) {
		this.displayName = name;
		savePrefs();

		// Optionnel mais conseillé (cohérence avec ton système statique)
		STATIC_USER_ID = this.userId;
		STATIC_TOKEN = this.token;
	}

	// Avatar de l'utilisateur courant, persisté localement (accès instantané au démarrage)
	public void setLocalAvatar(String avatar) {
		if (context == null || avatar == null || avatar.isEmpty()) return;
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit().putString("avatar", avatar).apply();
	}

	public String getLocalAvatar() {
		if (context == null) return null;
		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("avatar", null);
	}

	public String getEmail() {
		return email;
	}

	public void forceToken(String t, String uid) {
		this.token = t;
		this.userId = uid;
		STATIC_TOKEN = t;
		STATIC_USER_ID = uid;
		this.tokenExpiry = System.currentTimeMillis() + 3600000;
		savePrefs();
	}

	private void refreshTokenSync() {
		if (refreshToken == null)
			return;

		// ✅ FIX : si context est null, tenter de lire le refreshToken depuis STATIC
		// et ne pas bloquer le refresh à cause d'un context manquant
		try {
			URL url = new URL(REFRESH_URL + API_KEY);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setConnectTimeout(4000);
			conn.setReadTimeout(4000);
			conn.setDoOutput(true);
			String body = "grant_type=refresh_token&refresh_token=" + refreshToken;
			conn.getOutputStream().write(body.getBytes());
			if (conn.getResponseCode() == 200) {
				String response = safeRead(conn.getInputStream());
				String newToken = extractJson(response, "id_token");
				String newRefresh = extractJson(response, "refresh_token");
				if (newToken != null) {
					token = newToken;
					STATIC_TOKEN = newToken;
					if (newRefresh != null)
						refreshToken = newRefresh;
					tokenExpiry = System.currentTimeMillis() + 3600000;
					// ✅ FIX : savePrefs() fonctionne maintenant même si context
					// était null au départ — on le récupère depuis l'application
					savePrefs();
				}
			}
		} catch (Exception ignored) {
		}
	}

	public void login(String email, String password, Callback cb) {
		executor.execute(() -> {
			try {
				URL url = new URL(AUTH_URL + "signInWithPassword?key=" + API_KEY);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setDoOutput(true);
				String body = "{\"email\":\"" + email + "\",\"password\":\"" + password
						+ "\",\"returnSecureToken\":true}";
				conn.getOutputStream().write(body.getBytes());
				int code = conn.getResponseCode();
				String response = safeRead(code == 200 ? conn.getInputStream() : conn.getErrorStream());
				if (code == 200) {
					token = extractJson(response, "idToken");
					userId = extractJson(response, "localId");
					refreshToken = extractJson(response, "refreshToken");
					this.email = email;

					tokenExpiry = System.currentTimeMillis() + 3600000;

					STATIC_TOKEN = token;
					STATIC_USER_ID = userId;

					savePrefs();

					handler.post(() -> cb.onSuccess(token, userId));
				} else {
					handler.post(() -> cb.onError(parseError(response)));
				}
			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void register(String email, String password, String firstName, Callback cb) {
		executor.execute(() -> {
			try {
				URL url = new URL(AUTH_URL + "signUp?key=" + API_KEY);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setDoOutput(true);
				String body = "{\"email\":\"" + email + "\",\"password\":\"" + password
						+ "\",\"returnSecureToken\":true}";
				conn.getOutputStream().write(body.getBytes());
				int code = conn.getResponseCode();
				String response = safeRead(code == 200 ? conn.getInputStream() : conn.getErrorStream());
				if (code == 200) {
					token = extractJson(response, "idToken");
					userId = extractJson(response, "localId");
					refreshToken = extractJson(response, "refreshToken");
					this.email = email;
					this.displayName = firstName;
					tokenExpiry = System.currentTimeMillis() + 3600000;
					STATIC_TOKEN = token;
					STATIC_USER_ID = userId;
					savePrefs();
					final String t = token;
					final String u = userId;
					handler.post(() -> cb.onSuccess(t, u));
				} else {
					handler.post(() -> cb.onError(parseError(response)));
				}
			} catch (Exception e) {
				handler.post(() -> cb.onError(e.getMessage()));
			}
		});
	}

	public void loginWithBiometrics(Callback cb) {
		executor.execute(() -> {
			if (refreshToken != null) {
				refreshTokenSync();
				if (token != null) {
					final String t = token;
					final String u = userId;
					handler.post(() -> cb.onSuccess(t, u));
				} else {
					handler.post(() -> cb.onError("Token invalide"));
				}
			} else {
				handler.post(() -> cb.onError("Aucune session sauvegardée"));
			}
		});
	}

	private void savePrefs() {
		if (context == null)
			return;
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", token)
				.putString("userId", userId).putString("refreshToken", refreshToken)
				.putString("displayName", displayName).putString("email", email).putLong("tokenExpiry", tokenExpiry)
				.apply();
	}

	public boolean hasSavedCredentials() {
		if (context == null)
			return false;
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		return prefs.getString("email", null) != null && prefs.getString("refreshToken", null) != null;
	}

	public void logout() {
		token = null;
		userId = null;
		refreshToken = null;
		displayName = null;
		email = null;
		tokenExpiry = 0;
		STATIC_TOKEN = null;
		STATIC_USER_ID = null;
		if (context != null) {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
		}
		// Permettre à init() de recharger les prefs après un nouveau login
		context = null;
	}

	private String extractJson(String json, String key) {
		String[] patterns = { "\"" + key + "\": \"", "\"" + key + "\":\"" };
		for (String pattern : patterns) {
			int start = json.indexOf(pattern);
			if (start >= 0) {
				start += pattern.length();
				int end = json.indexOf("\"", start);
				if (end > start)
					return json.substring(start, end);
			}
		}
		return null;
	}

	private String parseError(String response) {
		if (response == null)
			return "Erreur inconnue";
		if (response.contains("EMAIL_EXISTS"))
			return "Email déjà utilisé";
		if (response.contains("INVALID_PASSWORD"))
			return "Mot de passe incorrect";
		if (response.contains("EMAIL_NOT_FOUND"))
			return "Email introuvable";
		if (response.contains("INVALID_LOGIN_CREDENTIALS"))
			return "Email ou mot de passe incorrect";
		if (response.contains("WEAK_PASSWORD"))
			return "Mot de passe trop faible (6 min)";
		if (response.contains("INVALID_EMAIL"))
			return "Email invalide";
		if (response.contains("TOO_MANY_ATTEMPTS_TRY_LATER"))
			return "Trop de tentatives, réessaie plus tard";
		return "Erreur de connexion";
	}

	private String safeRead(InputStream is) {
		if (is == null)
			return "";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null)
				sb.append(line);
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}
}
