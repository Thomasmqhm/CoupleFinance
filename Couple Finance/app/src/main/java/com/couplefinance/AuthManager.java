package com.couplefinance;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.couplefinance.utils.FirebaseConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AuthManager {

	private static final String API_KEY   = FirebaseConfig.API_KEY;
	private static final String AUTH_URL  = FirebaseConfig.AUTH_URL;
	private static final String PREFS     = "auth_prefs";

	private static volatile AuthManager instance;
	private final Executor executor = Executors.newSingleThreadExecutor();
	private final Handler  handler  = new Handler(Looper.getMainLooper());

	private Context context;
	private String token, userId, refreshToken, displayName, email;
	private long tokenExpiry = 0;

	private static volatile String STATIC_TOKEN   = null;
	private static volatile String STATIC_USER_ID = null;

	public static String getStaticToken()  { return STATIC_TOKEN;   }
	public static String getStaticUserId() { return STATIC_USER_ID; }

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
		if (context != null) return;
		context = ctx.getApplicationContext();
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		token        = prefs.getString("token", null);
		userId       = prefs.getString("userId", null);
		refreshToken = prefs.getString("refreshToken", null);
		displayName  = prefs.getString("displayName", null);
		email        = prefs.getString("email", null);
		tokenExpiry  = prefs.getLong("tokenExpiry", 0);
		STATIC_TOKEN   = token;
		STATIC_USER_ID = userId;
	}

	public boolean isLoggedIn() {
		return token != null && userId != null;
	}

	public String getToken() {
		if (token == null && context != null) {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			token        = prefs.getString("token", null);
			userId       = prefs.getString("userId", null);
			refreshToken = prefs.getString("refreshToken", null);
			tokenExpiry  = prefs.getLong("tokenExpiry", 0);
			if (displayName == null) displayName = prefs.getString("displayName", null);
		}
		if (token == null) return STATIC_TOKEN;
		if (System.currentTimeMillis() > tokenExpiry && refreshToken != null) {
			executor.execute(this::refreshTokenSync);
		}
		return token;
	}

	public synchronized String getFreshTokenSync() {
		if (context != null) {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			if (token == null)        token        = prefs.getString("token", null);
			if (userId == null)       userId       = prefs.getString("userId", null);
			if (refreshToken == null) refreshToken = prefs.getString("refreshToken", null);
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
		if (displayName == null && context != null) {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			displayName = prefs.getString("displayName", null);
		}
		return displayName;
	}

	public void setDisplayName(String name) {
		this.displayName = name;
		savePrefs();
		STATIC_USER_ID = this.userId;
		STATIC_TOKEN   = this.token;
	}

	public void setLocalAvatar(String avatar) {
		if (context == null || avatar == null || avatar.isEmpty()) return;
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit().putString("avatar", avatar).apply();
	}

	public String getLocalAvatar() {
		if (context == null) return null;
		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("avatar", null);
	}

	public String getEmail() { return email; }

	public void forceToken(String t, String uid) {
		this.token  = t;
		this.userId = uid;
		STATIC_TOKEN   = t;
		STATIC_USER_ID = uid;
		this.tokenExpiry = System.currentTimeMillis() + 3600000L;
		savePrefs();
	}

	private synchronized void refreshTokenSync() {
		if (refreshToken == null) return;
		HttpURLConnection conn = null;
		try {
			URL url = new URL(FirebaseConfig.REFRESH_URL);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setConnectTimeout(4000);
			conn.setReadTimeout(4000);
			conn.setDoOutput(true);

			// URL-encode le refreshToken pour éviter tout problème de caractères spéciaux
			String encodedToken = URLEncoder.encode(refreshToken, "UTF-8");
			String body = "grant_type=refresh_token&refresh_token=" + encodedToken;
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body.getBytes("UTF-8"));
			}

			int code = conn.getResponseCode();
			if (code == 200) {
				String response = safeRead(conn.getInputStream());
				String newToken   = extractJson(response, "id_token");
				String newRefresh = extractJson(response, "refresh_token");
				if (newToken != null) {
					token        = newToken;
					STATIC_TOKEN = newToken;
					if (newRefresh != null) refreshToken = newRefresh;
					tokenExpiry = System.currentTimeMillis() + 3600000L;
					savePrefs();
				}
			}
		} catch (Exception ignored) {
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	public void login(String emailInput, String passwordInput, Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				// Utiliser JSONObject pour éviter l'injection JSON
				JSONObject body = new JSONObject();
				body.put("email", emailInput);
				body.put("password", passwordInput);
				body.put("returnSecureToken", true);

				URL url = new URL(AUTH_URL + "signInWithPassword?key=" + API_KEY);
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);
				conn.setDoOutput(true);

				try (OutputStream os = conn.getOutputStream()) {
					os.write(body.toString().getBytes("UTF-8"));
				}

				int code = conn.getResponseCode();
				String response = safeRead(code == 200 ? conn.getInputStream() : conn.getErrorStream());

				if (code == 200) {
					token        = extractJson(response, "idToken");
					userId       = extractJson(response, "localId");
					refreshToken = extractJson(response, "refreshToken");
					this.email   = emailInput;
					tokenExpiry  = System.currentTimeMillis() + 3600000L;
					STATIC_TOKEN   = token;
					STATIC_USER_ID = userId;
					savePrefs();
					handler.post(() -> cb.onSuccess(token, userId));
				} else {
					final String err = parseError(response);
					handler.post(() -> cb.onError(err));
				}
			} catch (Exception e) {
				final String msg = e.getMessage();
				handler.post(() -> cb.onError(msg));
			} finally {
				if (conn != null) conn.disconnect();
			}
		});
	}

	public void register(String emailInput, String passwordInput, String firstName, Callback cb) {
		executor.execute(() -> {
			HttpURLConnection conn = null;
			try {
				// Utiliser JSONObject pour éviter l'injection JSON
				JSONObject body = new JSONObject();
				body.put("email", emailInput);
				body.put("password", passwordInput);
				body.put("returnSecureToken", true);

				URL url = new URL(AUTH_URL + "signUp?key=" + API_KEY);
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);
				conn.setDoOutput(true);

				try (OutputStream os = conn.getOutputStream()) {
					os.write(body.toString().getBytes("UTF-8"));
				}

				int code = conn.getResponseCode();
				String response = safeRead(code == 200 ? conn.getInputStream() : conn.getErrorStream());

				if (code == 200) {
					token        = extractJson(response, "idToken");
					userId       = extractJson(response, "localId");
					refreshToken = extractJson(response, "refreshToken");
					this.email   = emailInput;
					this.displayName = firstName;
					tokenExpiry  = System.currentTimeMillis() + 3600000L;
					STATIC_TOKEN   = token;
					STATIC_USER_ID = userId;
					savePrefs();
					final String t = token;
					final String u = userId;
					handler.post(() -> cb.onSuccess(t, u));
				} else {
					final String err = parseError(response);
					handler.post(() -> cb.onError(err));
				}
			} catch (Exception e) {
				final String msg = e.getMessage();
				handler.post(() -> cb.onError(msg));
			} finally {
				if (conn != null) conn.disconnect();
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
		if (context == null) return;
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
				.putString("token", token)
				.putString("userId", userId)
				.putString("refreshToken", refreshToken)
				.putString("displayName", displayName)
				.putString("email", email)
				.putLong("tokenExpiry", tokenExpiry)
				.apply();
	}

	public boolean hasSavedCredentials() {
		if (context == null) return false;
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		return prefs.getString("email", null) != null && prefs.getString("refreshToken", null) != null;
	}

	public void logout() {
		token        = null;
		userId       = null;
		refreshToken = null;
		displayName  = null;
		email        = null;
		tokenExpiry  = 0;
		STATIC_TOKEN   = null;
		STATIC_USER_ID = null;
		if (context != null) {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
		}
		context = null;
	}

	private String extractJson(String json, String key) {
		if (json == null) return null;
		String[] patterns = { "\"" + key + "\": \"", "\"" + key + "\":\"" };
		for (String pattern : patterns) {
			int start = json.indexOf(pattern);
			if (start >= 0) {
				start += pattern.length();
				int end = json.indexOf("\"", start);
				if (end > start) return json.substring(start, end);
			}
		}
		return null;
	}

	private String parseError(String response) {
		if (response == null) return "Erreur inconnue";
		if (response.contains("EMAIL_EXISTS"))                return "Email déjà utilisé";
		if (response.contains("INVALID_PASSWORD"))            return "Mot de passe incorrect";
		if (response.contains("EMAIL_NOT_FOUND"))             return "Email introuvable";
		if (response.contains("INVALID_LOGIN_CREDENTIALS"))   return "Email ou mot de passe incorrect";
		if (response.contains("WEAK_PASSWORD"))               return "Mot de passe trop faible (6 min)";
		if (response.contains("INVALID_EMAIL"))               return "Email invalide";
		if (response.contains("TOO_MANY_ATTEMPTS_TRY_LATER")) return "Trop de tentatives, réessaie plus tard";
		if (response.contains("USER_DISABLED"))               return "Compte désactivé";
		return "Erreur de connexion";
	}

	private String safeRead(InputStream is) {
		if (is == null) return "";
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) sb.append(line);
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}
}
