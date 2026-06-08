package com.couplefinance.ui.settings;

import android.app.Activity;

import com.couplefinance.AuthManager;
import com.couplefinance.data.CategoryManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.FixedChargeManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;

public class SettingsRepository {

	public interface LoadCallback {
		void onLoaded(SettingsModels.State state);

		void onError(String error);
	}

	private final Activity activity;

	public SettingsRepository(Activity activity) {
		this.activity = activity;
	}

	public void load(LoadCallback cb) {
		SettingsModels.State state = new SettingsModels.State();

		loadHousehold(state, () -> loadMembers(state, () -> loadCategories(state, () -> loadCharges(state, () -> {
			SettingsCache.set(state);

			if (cb != null) {
				cb.onLoaded(state);
			}
		}))));
	}

	private void loadHousehold(SettingsModels.State state, Runnable next) {
		try {
			HouseholdManager.getInstance().getHousehold(new FirestoreManager.Callback() {
				public void onSuccess(String response) {
					try {
						JSONObject fields = new JSONObject(response).optJSONObject("fields");

						if (fields != null) {
							String main = str(fields, "mainAccount");

							if (!main.isEmpty()) {
								state.householdName = main;
							}

							String desc = str(fields, "description");

							if (!desc.isEmpty()) {
								state.description = desc;
							}
						}

					} catch (Exception ignored) {
					}

					next.run();
				}

				public void onError(String error) {
					next.run();
				}
			});

		} catch (Exception e) {
			next.run();
		}
	}

	private void loadMembers(SettingsModels.State state, Runnable next) {
		try {
			HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
				public void onSuccess(String response) {
					try {
						state.members.clear();

						HashSet<String> seen = new HashSet<>();

						String me = currentUserName();

						JSONArray docs = new JSONObject(response).optJSONArray("documents");

						if (docs != null) {
							for (int i = 0; i < docs.length(); i++) {
								JSONObject doc = docs.optJSONObject(i);
								if (doc == null)
									continue;

								JSONObject fields = doc.optJSONObject("fields");
								if (fields == null)
									continue;

								String name = firstNonEmpty(str(fields, "name"), str(fields, "displayName"),
										str(fields, "prenom"), str(fields, "firstName"));

								if (name.isEmpty())
									continue;
								if (name.contains("@"))
									continue;
								if ("Moi".equalsIgnoreCase(name))
									continue;

								String key = normalize(name);

								if (seen.contains(key))
									continue;
								seen.add(key);

								boolean isMe = normalize(name).equals(normalize(me));

								SettingsModels.Member m = new SettingsModels.Member(name,
										isMe ? "ADMINISTRATEUR" : "MEMBRE", defaultColor(state.members.size()), isMe);

								m.docPath = doc.optString("name", "");
								m.userId = str(fields, "userId");
								m.role = firstNonEmpty(str(fields, "role"), m.role);
								m.color = firstNonEmpty(str(fields, "color"), m.color);

							    m.income = firstPositive(
                                    number(fields, "revenue"),
                                    number(fields, "income"),
                                    number(fields, "monthlyIncome")
                                );

                                m.monthlyStartBalance = firstPositive(
                                    number(fields, "monthlyStartBalance"),
                                    number(fields, "startBalance"),
                                    number(fields, "monthStartBalance"),
                                    0
                                );

                                m.overdraft = firstPositive(
                                    number(fields, "overdraft"),
                                    number(fields, "decouvert"),
                                    200
                                );

								m.notifications = bool(fields, "notifications", true);
								m.overdraftAlert = bool(fields, "overdraftAlert", false);

								state.members.add(m);
							}
						}

						boolean meExists = false;

						for (SettingsModels.Member m : state.members) {
							if (normalize(m.name).equals(normalize(me))) {
								meExists = true;
								m.admin = true;
								m.role = "ADMINISTRATEUR";
								break;
							}
						}

					} catch (Exception ignored) {
					}

					next.run();
				}

				public void onError(String error) {
					next.run();
				}
			});

		} catch (Exception e) {
			next.run();
		}
	}

	private void loadCategories(SettingsModels.State state, Runnable next) {
		try {
			CategoryManager.getInstance().getCategories(new FirestoreManager.Callback() {
				public void onSuccess(String response) {
					try {
						state.categories.clear();

						HashSet<String> seen = new HashSet<>();
						JSONArray docs = new JSONObject(response).optJSONArray("documents");

						if (docs != null) {
							for (int i = 0; i < docs.length(); i++) {
								JSONObject doc = docs.optJSONObject(i);
								if (doc == null)
									continue;

								JSONObject fields = doc.optJSONObject("fields");
								if (fields == null)
									continue;

								String rawName = str(fields, "name");

								if (rawName.isEmpty())
									continue;

								String type = firstNonEmpty(str(fields, "type"), "expense");
								String cleanName = rawName;

								if (rawName.endsWith("|income")) {
									type = "income";
									cleanName = rawName.replace("|income", "");
								} else if (rawName.endsWith("|expense")) {
									type = "expense";
									cleanName = rawName.replace("|expense", "");
								}

								String key = normalize(type + "_" + cleanName);

								if (seen.contains(key))
									continue;
								seen.add(key);

								SettingsModels.Category c = new SettingsModels.Category(cleanName, type,
										bool(fields, "active", true));

								c.docPath = doc.optString("name", "");
								c.emoji = firstNonEmpty(str(fields, "emoji"), guessCategoryEmoji(cleanName, type));
								c.color = firstNonEmpty(str(fields, "color"), defaultCategoryColor(type));
								c.budget = firstPositive(number(fields, "budget"), 0);

								state.categories.add(c);
							}
						}

					} catch (Exception ignored) {
					}

					next.run();
				}

				public void onError(String error) {
					next.run();
				}
			});

		} catch (Exception e) {
			next.run();
		}
	}

	private void loadCharges(SettingsModels.State state, Runnable next) {
		try {
			FixedChargeManager.getInstance().getFixedCharges(new FirestoreManager.Callback() {
				public void onSuccess(String response) {
					try {
						state.charges.clear();

						JSONArray docs = new JSONObject(response).optJSONArray("documents");

						if (docs != null) {
							for (int i = 0; i < docs.length(); i++) {
								JSONObject doc = docs.optJSONObject(i);
								if (doc == null)
									continue;

								JSONObject fields = doc.optJSONObject("fields");
								if (fields == null)
									continue;

								String name = firstNonEmpty(str(fields, "name"), str(fields, "title"),
										str(fields, "label"));

								double amount = firstPositive(number(fields, "amount"), number(fields, "montant"), 0);

								if (name.isEmpty())
									continue;

								SettingsModels.FixedCharge c = new SettingsModels.FixedCharge(guessIcon(name), name,
										guessCategory(name), amount);

								c.docPath = doc.optString("name", "");
								c.dayOfMonth = intVal(fields, "dayOfMonth", 1);
								c.category = firstNonEmpty(str(fields, "category"), c.category);
								c.icon = firstNonEmpty(str(fields, "icon"), c.icon);
								c.frequency = firstNonEmpty(str(fields, "frequency"), c.frequency);
								c.lastAppliedMonth = str(fields, "lastAppliedMonth");
								c.amountMin = number(fields, "amountMin");
								c.amountMax = number(fields, "amountMax");
								c.paidBy = str(fields, "paidBy");
								c.ratioA = intVal(fields, "ratioA", 50);
								c.ratioB = intVal(fields, "ratioB", 50);

								state.charges.add(c);
							}
						}

					} catch (Exception ignored) {
					}

					next.run();
				}

				public void onError(String error) {
					next.run();
				}
			});

		} catch (Exception e) {
			next.run();
		}
	}

	private String currentUserName() {
		String me = AuthManager.getInstance().getDisplayName();

		if (me == null || me.trim().isEmpty()) {
			me = AuthManager.getInstance().getEmail();
		}

		if (me == null || me.trim().isEmpty()) {
			me = "Membre";
		}

		return me.trim();
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

	private double number(JSONObject fields, String key) {
		try {
			JSONObject f = fields.optJSONObject(key);

			if (f == null)
				return 0;

			if (f.has("doubleValue")) {
				return f.optDouble("doubleValue", 0);
			}

			if (f.has("integerValue")) {
				return Double.parseDouble(f.optString("integerValue", "0"));
			}

			return 0;

		} catch (Exception e) {
			return 0;
		}
	}

	private int intVal(JSONObject fields, String key, int fallback) {
		try {
			JSONObject f = fields.optJSONObject(key);

			if (f == null)
				return fallback;

			if (f.has("integerValue")) {
				return Integer.parseInt(f.optString("integerValue", String.valueOf(fallback)));
			}

			if (f.has("doubleValue")) {
				return (int) f.optDouble("doubleValue", fallback);
			}

			return fallback;

		} catch (Exception e) {
			return fallback;
		}
	}

	private boolean bool(JSONObject fields, String key, boolean fallback) {
		try {
			JSONObject f = fields.optJSONObject(key);

			if (f == null)
				return fallback;

			if (f.has("booleanValue")) {
				return f.optBoolean("booleanValue", fallback);
			}

			return fallback;

		} catch (Exception e) {
			return fallback;
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

	private double firstPositive(double... values) {
		if (values == null)
			return 0;

		for (double v : values) {
			if (v > 0)
				return v;
		}

		return 0;
	}

	private String normalize(String value) {
		if (value == null)
			return "";

		return value.trim().toLowerCase(Locale.FRANCE).replace("é", "e").replace("è", "e").replace("ê", "e")
				.replace("ë", "e").replace("à", "a").replace("â", "a").replace("ù", "u").replace("û", "u")
				.replace("î", "i").replace("ï", "i").replace("ô", "o").replace("ç", "c").replaceAll("\\s+", " ");
	}

	private String defaultColor(int index) {
		String[] colors = { "#D8A48F", "#A8C8B8", "#DCCB8F", "#9FB7D9", "#B8A5D6", "#C7B8A3", "#E2B8C6", "#AFCFC4" };

		return colors[Math.abs(index) % colors.length];
	}

	private String guessCategoryEmoji(String name, String type) {
		String n = name == null ? "" : name.toLowerCase(Locale.FRANCE);

		if ("income".equals(type))
			return "↗️";
		if (n.contains("course") || n.contains("carrefour") || n.contains("lidl") || n.contains("leclerc"))
			return "🛒";
		if (n.contains("loyer") || n.contains("logement"))
			return "🏠";
		if (n.contains("edf") || n.contains("électricité") || n.contains("energie") || n.contains("énergie"))
			return "⚡";
		if (n.contains("transport") || n.contains("essence") || n.contains("carburant"))
			return "⛽";
		if (n.contains("restaurant") || n.contains("restauration") || n.contains("fast"))
			return "🍽️";
		if (n.contains("santé") || n.contains("mutuelle") || n.contains("pharmacie"))
			return "🩺";
		if (n.contains("abonnement") || n.contains("netflix") || n.contains("spotify"))
			return "🎬";

		return "🏷️";
	}

	private String defaultCategoryColor(String type) {
		return "income".equals(type) ? "#2D7D55" : "#C0614A";
	}

	private String guessCategory(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.FRANCE);

		if (n.contains("loyer") || n.contains("edf") || n.contains("électricité") || n.contains("gaz")
				|| n.contains("internet") || n.contains("assurance")) {
			return "Logement";
		}

		if (n.contains("netflix") || n.contains("spotify") || n.contains("disney") || n.contains("amazon")
				|| n.contains("canal")) {
			return "Loisirs";
		}

		return "Général";
	}

	private String guessIcon(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.FRANCE);

		if (n.contains("loyer"))
			return "🏠";
		if (n.contains("edf") || n.contains("électricité") || n.contains("gaz"))
			return "⚡";
		if (n.contains("internet") || n.contains("box"))
			return "📶";
		if (n.contains("assurance"))
			return "🔒";
		if (n.contains("netflix") || n.contains("disney") || n.contains("canal"))
			return "🎬";
		if (n.contains("spotify") || n.contains("deezer"))
			return "🎵";

		return "💳";
	}
}