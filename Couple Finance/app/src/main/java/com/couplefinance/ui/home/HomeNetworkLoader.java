package com.couplefinance.ui.home;

import android.app.Activity;
import android.content.Context;

import com.couplefinance.AuthManager;
import com.couplefinance.data.BalanceManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.data.RecurringChargeManager;
import com.couplefinance.utils.NotificationHelper;
import com.couplefinance.data.UserManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HomeNetworkLoader — Toutes les opérations réseau/Firestore de HomeView.
 *
 * Extrait de HomeView.java. Orchestre :
 *  - chargement des membres
 *  - chargement des transactions
 *  - chargement des soldes de début de mois
 *  - chargement du découvert
 *  - notifications partenaire
 *
 * Callbacks vers HomeView via l'interface {@link Listener}.
 */
public final class HomeNetworkLoader {

	// ─────────────────────────────────────────────────────────────
	// Interface callback vers HomeView
	// ─────────────────────────────────────────────────────────────

	public interface Listener {
		boolean isActive();

		Activity getActivity();

		// Données chargées
		void onMembersLoaded(List<String> memberNames, Map<String, String> userIdToName);

		void onTransactionsLoaded(List<String[]> transactions);

		void onFinancialSettingsLoaded(double myBalance, boolean myDefined, long myAnchorDate, double totalBalance,
				long maxAnchorDate, Map<String, Double> memberBalances);

		void onOverdraftLoaded(boolean defined, double limit);

		void onLoadError(String error);
	}

	// ─────────────────────────────────────────────────────────────
	// Chargement principal
	// ─────────────────────────────────────────────────────────────

	private final Listener listener;
	private final HomeData homeData;

	public HomeNetworkLoader(Listener listener, HomeData homeData) {
		this.listener = listener;
		this.homeData = homeData;
	}

	/**
	 * Lance la chaîne de chargement :
	 * membres → transactions → settings financiers → découvert → processData
	 */
	public void load() {
		if (!isNetworkAvailable()) {
			listener.onLoadError("offline");
			return;
		}

		loadMembers();
	}

	// ─────────────────────────────────────────────────────────────
	// Étape 1 : Membres
	// ─────────────────────────────────────────────────────────────

	private void loadMembers() {
		HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				List<HomeParser.MemberEntry> entries = HomeParser.parseMembers(response);
				List<String> names = new ArrayList<>();
				Map<String, String> uidToName = new HashMap<>();

				for (HomeParser.MemberEntry e : entries) {
					boolean exists = false;
					for (String n : names) {
						if (n.equalsIgnoreCase(e.name)) {
							exists = true;
							break;
						}
					}
					if (!exists)
						names.add(e.name);
					if (!e.userId.isEmpty())
						uidToName.put(e.userId, e.name);
				}

				listener.onMembersLoaded(names, uidToName);
				loadTransactions();
			}

			public void onError(String e) {
				listener.onMembersLoaded(new ArrayList<>(), new HashMap<>());
				loadTransactions();
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Étape 2 : Transactions
	// ─────────────────────────────────────────────────────────────

	private void loadTransactions() {
		TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				List<String[]> transactions = HomeParser.parseTransactions(response);
				listener.onTransactionsLoaded(transactions);
				checkPartnerNotifications(transactions);
				loadFinancialSettings();
			}

			public void onError(String error) {
				listener.onLoadError(error);
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Étape 3 : Soldes membres
	// ─────────────────────────────────────────────────────────────

	private void loadFinancialSettings() {
		BalanceManager.getInstance().getAllMembersStartBalance(new FirestoreManager.Callback() {
			public void onSuccess(String r) {
				String myUserId = AuthManager.getInstance().getUserId();
				String currentMonth = RecurringChargeManager.getCurrentMonth();

				double myBalance = 0;
				double totalBalance = 0;
				boolean myDefined = false;
				long myAnchorDate = 0;
				long maxAnchorDate = 0;

				Map<String, Double> memberBals = new HashMap<>();

				List<HomeParser.BalanceEntry> entries = HomeParser.parseBalances(r);

				for (HomeParser.BalanceEntry entry : entries) {
					if (!currentMonth.equals(entry.month))
						continue;

					totalBalance += entry.amount;
					maxAnchorDate = Math.max(maxAnchorDate, entry.anchorDate);

					// Associer le nom du membre via UserManager si possible
					// (le mapping uid→name est maintenu dans HomeView.userIdToName)
					memberBals.put(entry.userId, entry.amount);

					if (myUserId != null && myUserId.equals(entry.userId)) {
						myBalance = entry.amount;
						myAnchorDate = entry.anchorDate > 0 ? entry.anchorDate
								: BalanceManager.getInstance().getMonthStartMillis();
						myDefined = true;
					}
				}

				if (!myDefined) {
					Double cached = BalanceManager.getInstance().getMonthlyStartBalanceLocal();
					if (cached != null) {
						myBalance = cached;
						myAnchorDate = BalanceManager.getInstance().getMonthlyStartBalanceDateLocal();
						myDefined = true;
					} else {
						myAnchorDate = BalanceManager.getInstance().getMonthStartMillis();
					}
					if (totalBalance == 0)
						totalBalance = myBalance;
				}

				listener.onFinancialSettingsLoaded(myBalance, myDefined, myAnchorDate, totalBalance, maxAnchorDate,
						memberBals);

				loadOverdraft();
			}

			public void onError(String e) {
				Double cached = BalanceManager.getInstance().getMonthlyStartBalanceLocal();
				double myBal = cached != null ? cached : 0;
				long anchor = BalanceManager.getInstance().getMonthlyStartBalanceDateLocal();
				boolean def = cached != null;

				listener.onFinancialSettingsLoaded(myBal, def, anchor, myBal, anchor, new HashMap<>());
				loadOverdraft();
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Étape 4 : Découvert autorisé
	// ─────────────────────────────────────────────────────────────

	private void loadOverdraft() {
		BalanceManager.getInstance().getOverdraftLimit(new FirestoreManager.Callback() {
			public void onSuccess(String r) {
				boolean defined = false;
				double limit = 0;

				try {
					double value = Double.parseDouble(r);
					if (value != 0) {
						defined = true;
						limit = Math.abs(value);
						HomeData.saveOverdraft(listener.getActivity(), value);
					}
				} catch (Exception ignored) {
				}

				if (!defined) {
					Double cached = HomeData.getOverdraft(listener.getActivity());
					if (cached != null && cached != 0) {
						defined = true;
						limit = Math.abs(cached);
					}
				}

				listener.onOverdraftLoaded(defined, limit);
			}

			public void onError(String e) {
				Double cached = HomeData.getOverdraft(listener.getActivity());
				boolean def = cached != null && cached != 0;
				double lim = def ? Math.abs(cached) : 0;
				listener.onOverdraftLoaded(def, lim);
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Notifications partenaire
	// ─────────────────────────────────────────────────────────────

	private void checkPartnerNotifications(List<String[]> transactions) {
		String myUserId = AuthManager.getInstance().getUserId();
		if (myUserId == null || myUserId.isEmpty())
			return;

		NotificationHelper nh = NotificationHelper.getInstance(listener.getActivity());
		long lastSeen = nh.getLastSeenTimestamp();
		long maxPartnerTs = 0;
		String partnerLabel = "";
		double partnerAmount = 0;

		for (String[] tx : transactions) {
			if (tx.length < 8)
				continue;
			String txUserId = tx[7];
			if (txUserId.isEmpty() || txUserId.equals(myUserId))
				continue;

			long ts = 0;
			try {
				ts = Long.parseLong(tx[4]);
			} catch (Exception ignored) {
			}

			if (ts > lastSeen && ts > maxPartnerTs) {
				maxPartnerTs = ts;
				partnerLabel = tx[0];
				try {
					partnerAmount = Double.parseDouble(tx[1]);
				} catch (Exception ignored) {
				}
			}
		}

		if (maxPartnerTs > 0) {
			String name = UserManager.getInstance().getCurrentDisplayNameOrFallback();
			nh.notifyNewPartnerTransaction(name, partnerLabel, partnerAmount, maxPartnerTs);
			nh.markTransactionsAsSeen(maxPartnerTs);
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Utils
	// ─────────────────────────────────────────────────────────────

	private boolean isNetworkAvailable() {
		try {
			android.net.ConnectivityManager cm = (android.net.ConnectivityManager) listener.getActivity()
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			android.net.NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
			return info != null && info.isConnected();
		} catch (Exception e) {
			return false;
		}
	}
}
