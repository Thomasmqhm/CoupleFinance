package com.couplefinance.data;

import android.os.Handler;
import android.os.Looper;

import com.couplefinance.AuthManager;
import com.couplefinance.UserRepository;
import com.couplefinance.UserSession;
import com.couplefinance.models.UserProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class UserManager {

	private static volatile UserManager instance;

	private final Handler handler = new Handler(Looper.getMainLooper());

	private boolean registeredInFirestore = false;
	private int registerRetryCount = 0;
	private static final int MAX_REGISTER_RETRIES = 10;

	public interface ExpulsionCallback {
		void onExpelled();
	}

	public static UserManager getInstance() {
		if (instance == null) {
			synchronized (UserManager.class) {
				if (instance == null) instance = new UserManager();
			}
		}
		return instance;
	}

	public boolean isRegisteredInFirestore() {
		return registeredInFirestore;
	}

	public void resetRegistrationState() {
		registeredInFirestore = false;
		registerRetryCount = 0;
	}

	public String getCurrentDisplayNameOrFallback() {
		String reliable = getReliableDisplayName();
		return reliable != null ? reliable : "Moi";
	}

	public String getCurrentDisplayName(android.content.Context ctx) {
		// 1) SharedPreferences (le plus fiable — prénom configuré par l'utilisateur)
		try {
			String saved = ctx.getSharedPreferences("couplefinance_profile",
					android.content.Context.MODE_PRIVATE).getString("display_name", "");
			if (saved != null && !saved.trim().isEmpty()
					&& !saved.equalsIgnoreCase("Moi")) return saved.trim();
		} catch (Exception ignored) {}
		// 2) Fallback classique
		String reliable = getReliableDisplayName();
		return reliable != null ? reliable : "";
	}

	public String getReliableDisplayName() {
		// 1) Membre du foyer dont le userId correspond à l'utilisateur connecté
		try {
			String myUid = AuthManager.getInstance().getUserId();
			if (myUid != null && !myUid.isEmpty()) {
				com.couplefinance.ui.settings.SettingsModels.State state =
						com.couplefinance.ui.settings.SettingsCache.get();
				if (state != null && state.members != null) {
					for (com.couplefinance.ui.settings.SettingsModels.Member m : state.members) {
						if (myUid.equals(m.userId) && m.name != null && !m.name.trim().isEmpty()) {
							return m.name.trim();
						}
					}
				}
			}
		} catch (Exception ignored) {}

		// 2) Profil UserSession — seulement si pas un email/identifiant
		UserProfile profile = UserSession.getInstance().getUser();
		if (profile != null
				&& profile.displayName != null
				&& !profile.displayName.trim().isEmpty()
				&& !profile.displayName.equalsIgnoreCase("Moi")
				&& !profile.displayName.contains("@")
				&& !profile.displayName.contains(".")) {
			return profile.displayName.trim();
		}

		// 3) Nom Firebase Auth — seulement si pas un email/identifiant
		String name = AuthManager.getInstance().getDisplayName();
		if (name != null
				&& !name.trim().isEmpty()
				&& !name.equalsIgnoreCase("Moi")
				&& !name.contains("@")
				&& !name.contains(".")) {
			return name.trim();
		}

		return null;
	}

	public void registerCurrentUserAsMember() {
		if (registeredInFirestore)
			return;

		registerRetryCount = 0;
		ensureDisplayNameThenRegister();
	}

	public void registerCurrentUserAsMemberWithName(String name) {
		if (name == null || name.trim().isEmpty() || name.equalsIgnoreCase("Moi"))
			return;
		registerRetryCount = 0;
		registerMemberInFirestore(name.trim());
	}

	private void ensureDisplayNameThenRegister() {
		String name = getReliableDisplayName();

		if (name != null) {
			registerMemberInFirestore(name);
			return;
		}

		String uid = AuthManager.getInstance().getUserId();

		if (uid == null || uid.isEmpty()) {
			if (registerRetryCount++ < MAX_REGISTER_RETRIES) {
				handler.postDelayed(this::ensureDisplayNameThenRegister, 2000);
			}
			return;
		}

		UserRepository.getInstance().loadUser(uid, profile -> {
			String loadedName = getReliableDisplayName();

			if (loadedName != null) {
				registerMemberInFirestore(loadedName);
			} else {
				if (registerRetryCount++ < MAX_REGISTER_RETRIES) {
					handler.postDelayed(this::ensureDisplayNameThenRegister, 3000);
				}
			}
		});
	}

	private void registerMemberInFirestore(String name) {
		if (name == null || name.trim().isEmpty() || name.equalsIgnoreCase("Moi")) {
			return;
		}

		if (!HouseholdManager.getInstance().hasHousehold()) {
			android.util.Log.d("USER_MANAGER", "Pas de foyer, inscription membre ignorée");
			return;
		}

		final String cleanName = name.trim();
		final String currentUserId = AuthManager.getInstance().getUserId();

		HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				String existingDocPath = findExistingMemberDocPath(response, cleanName, currentUserId);

				if (existingDocPath != null) {
					registeredInFirestore = true;
					registerRetryCount = 0;
					android.util.Log.d("USER_MANAGER", "Membre déjà existant : " + cleanName);
					// Patch le document existant pour ajouter le userId s'il manque
					if (currentUserId != null && !currentUserId.isEmpty()) {
						patchMemberUserId(existingDocPath, currentUserId, cleanName);
					}
					return;
				}

				SettingsManager.getInstance().addPerson(cleanName, new FirestoreManager.Callback() {
					public void onSuccess(String r) {
						registeredInFirestore = true;
						registerRetryCount = 0;
						android.util.Log.d("USER_MANAGER", "Membre enregistré : " + cleanName + " (" + r + ")");
					}

					public void onError(String e) {
						android.util.Log.d("USER_MANAGER", "Erreur inscription membre : " + e);

						if (registerRetryCount++ < MAX_REGISTER_RETRIES) {
							handler.postDelayed(() -> registerMemberInFirestore(cleanName), 5000);
						}
					}
				});
			}

			public void onError(String error) {
				android.util.Log.d("USER_MANAGER", "Impossible de vérifier les membres existants : " + error);

				if (registerRetryCount++ < MAX_REGISTER_RETRIES) {
					handler.postDelayed(() -> registerMemberInFirestore(cleanName), 5000);
				}
			}
		});
	}

	private void patchMemberUserId(String docPath, String userId, String name) {
		if (docPath == null || docPath.trim().isEmpty()) return;
		try {
			// docPath = chemin complet Firestore REST : "projects/.../documents/..."
			// FirestoreManager attend le chemin SANS le préfixe "projects/.../documents/"
			String path = docPath;
			if (path.startsWith("projects/")) {
				int idx = path.indexOf("/documents/");
				if (idx >= 0) path = path.substring(idx + "/documents/".length());
			}
			String body = "{\"fields\":{\"userId\":{\"stringValue\":\"" + userId + "\"}}}";
			FirestoreManager.getInstance().patchDocument(path, body, "updateMask.fieldPaths=userId", new FirestoreManager.Callback() {
				public void onSuccess(String r) {
					android.util.Log.d("USER_MANAGER", "userId ajouté au membre : " + name);
				}
				public void onError(String e) {
					android.util.Log.d("USER_MANAGER", "Impossible d'ajouter userId : " + e);
				}
			});
		} catch (Exception e) {
			android.util.Log.d("USER_MANAGER", "patchMemberUserId exception : " + e.getMessage());
		}
	}

	private String findExistingMemberDocPath(String response, String name, String userId) {
		if (response == null || response.trim().isEmpty()) return null;
		try {
			JSONObject json = new JSONObject(response);
			JSONArray docs = json.optJSONArray("documents");
			if (docs == null) return null;
			String targetName = normalize(name);
			String targetUserId = userId == null ? "" : userId.trim();
			for (int i = 0; i < docs.length(); i++) {
				JSONObject doc = docs.optJSONObject(i);
				if (doc == null) continue;
				JSONObject fields = doc.optJSONObject("fields");
				if (fields == null) continue;
				String existingName = firstNonEmpty(str(fields, "name"), str(fields, "displayName"),
						str(fields, "prenom"), str(fields, "firstName"));
				String existingUserId = str(fields, "userId");
				boolean matchById = !targetUserId.isEmpty() && existingUserId != null
						&& existingUserId.equals(targetUserId);
				boolean matchByName = !targetName.isEmpty()
						&& normalize(existingName).equals(targetName);
				if (matchById || matchByName) {
					return doc.optString("name", ""); // "name" = docPath in Firestore REST API
				}
			}
		} catch (Exception ignored) {}
		return null;
	}

	private boolean memberAlreadyExists(String response, String name, String userId) {
		if (response == null || response.trim().isEmpty())
			return false;

		try {
			JSONObject json = new JSONObject(response);
			JSONArray docs = json.optJSONArray("documents");

			if (docs == null)
				return false;

			String targetName = normalize(name);
			String targetUserId = userId == null ? "" : userId.trim();

			for (int i = 0; i < docs.length(); i++) {
				JSONObject doc = docs.optJSONObject(i);
				if (doc == null)
					continue;

				JSONObject fields = doc.optJSONObject("fields");
				if (fields == null)
					continue;

				String existingName = firstNonEmpty(
						str(fields, "name"),
						str(fields, "displayName"),
						str(fields, "prenom"),
						str(fields, "firstName")
				);

				String existingUserId = str(fields, "userId");

				if (!targetUserId.isEmpty()
						&& existingUserId != null
						&& existingUserId.equals(targetUserId)) {
					return true;
				}

				if (!targetName.isEmpty()
						&& normalize(existingName).equals(targetName)) {
					return true;
				}
			}

		} catch (Exception ignored) {
			String lower = response.toLowerCase(Locale.FRANCE);
			String normalizedName = normalize(name);

			return lower.contains(normalizedName)
					|| (userId != null && !userId.isEmpty() && lower.contains(userId.toLowerCase(Locale.FRANCE)));
		}

		return false;
	}

	public void checkIfStillMember(ExpulsionCallback callback) {
		if (!registeredInFirestore)
			return;

		String myUserId = AuthManager.getInstance().getUserId();
		String myName = getReliableDisplayName();

		if (myName == null || myUserId == null)
			return;

		SettingsManager.getInstance().getHouseholdOwner(new FirestoreManager.Callback() {
			public void onSuccess(String ownerUserId) {
				if (ownerUserId != null && ownerUserId.equals(myUserId)) {
					return;
				}

				checkMembersList(myName, callback);
			}

			public void onError(String e) {
			}
		});
	}

	private void checkMembersList(String myName, ExpulsionCallback callback) {
		HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				if (isEmptyResponse(response))
					return;

				String nameLower = normalize(myName);
				String responseLower = normalize(response);

				String myUserId = AuthManager.getInstance().getUserId();

				boolean foundByName = responseLower.contains(nameLower);
				boolean foundByUserId = myUserId != null && responseLower.contains(myUserId.toLowerCase(Locale.FRANCE));

				if (!foundByName && !foundByUserId) {
					handler.postDelayed(() -> checkIfStillMemberFinal(myName, callback), 8000);
				}
			}

			public void onError(String e) {
			}
		});
	}

	private void checkIfStillMemberFinal(String myName, ExpulsionCallback callback) {
		String myUserId = AuthManager.getInstance().getUserId();

		SettingsManager.getInstance().getHouseholdOwner(new FirestoreManager.Callback() {
			public void onSuccess(String ownerUserId) {
				if (ownerUserId != null && ownerUserId.equals(myUserId))
					return;

				HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
					public void onSuccess(String response) {
						if (isEmptyResponse(response))
							return;

						String nameLower = normalize(myName);
						String responseLower = normalize(response);
						String myUserId = AuthManager.getInstance().getUserId();

						boolean found = responseLower.contains(nameLower)
								|| (myUserId != null && responseLower.contains(myUserId.toLowerCase(Locale.FRANCE)));

						if (!found && callback != null) {
							handler.post(callback::onExpelled);
						}
					}

					public void onError(String e) {
					}
				});
			}

			public void onError(String e) {
			}
		});
	}

	private String str(JSONObject fields, String key) {
		try {
			JSONObject f = fields.optJSONObject(key);
			if (f == null)
				return "";

			return f.optString("stringValue", "").trim();

		} catch (Exception e) {
			return "";
		}
	}

	private String firstNonEmpty(String... values) {
		if (values == null)
			return "";

		for (String v : values) {
			if (v != null && !v.trim().isEmpty()) {
				return v.trim();
			}
		}

		return "";
	}

	private String normalize(String value) {
		if (value == null)
			return "";

		return value
				.trim()
				.toLowerCase(Locale.FRANCE)
				.replace("é", "e")
				.replace("è", "e")
				.replace("ê", "e")
				.replace("ë", "e")
				.replace("à", "a")
				.replace("â", "a")
				.replace("ù", "u")
				.replace("û", "u")
				.replace("î", "i")
				.replace("ï", "i")
				.replace("ô", "o")
				.replace("ç", "c")
				.replaceAll("\\s+", " ");
	}

	private boolean isEmptyResponse(String response) {
		return response == null
				|| response.trim().isEmpty()
				|| response.equals("{\"documents\":[]}")
				|| response.contains("\"documents\": []");
	}
}