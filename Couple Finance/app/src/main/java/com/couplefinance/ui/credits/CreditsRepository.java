package com.couplefinance.ui.credits;

import android.app.Activity;

import com.couplefinance.data.CreditManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class CreditsRepository {

	private CreditsRepository() {
	}

	public interface OnDataLoaded {
		void onLoaded(CreditsModels.CreditsData data);

		void onError(String message);
	}

	public interface OnWriteComplete {
		void onSuccess();

		void onError(String message);
	}

	public static void loadAll(Activity activity, OnDataLoaded callback) {
		AtomicInteger loaded = new AtomicInteger(0);

		final List<CreditsModels.Credit>[] credits = new List[1];
		final double[] revenue = { 0 };
		final double[] fixedCharges = { 0 };

		Runnable checkAndDeliver = () -> {
			if (loaded.incrementAndGet() == 3) {
				CreditsModels.CreditsData data = new CreditsModels.CreditsData(
						credits[0] != null ? credits[0] : new java.util.ArrayList<>(),
						revenue[0],
						fixedCharges[0]
				);

				if (activity != null) {
					activity.runOnUiThread(() -> callback.onLoaded(data));
				} else {
					callback.onLoaded(data);
				}
			}
		};

		CreditManager.getInstance().getCredits(new FirestoreManager.Callback() {
			public void onSuccess(String json) {
				credits[0] = CreditsParser.parseCredits(json);
				checkAndDeliver.run();
			}

			public void onError(String e) {
				credits[0] = new java.util.ArrayList<>();
				checkAndDeliver.run();
			}
		});

		String householdId = HouseholdManager.getInstance().getHouseholdId();

		if (householdId == null || householdId.trim().isEmpty()) {
			loaded.incrementAndGet();
			loaded.incrementAndGet();

			if (loaded.get() >= 3) {
				CreditsModels.CreditsData data = new CreditsModels.CreditsData(
						credits[0] != null ? credits[0] : new java.util.ArrayList<>(),
						0,
						0
				);

				if (activity != null) {
					activity.runOnUiThread(() -> callback.onLoaded(data));
				} else {
					callback.onLoaded(data);
				}
			}

			return;
		}

		FirestoreManager.getInstance().getCollection(
				"households/" + householdId + "/persons",
				new FirestoreManager.Callback() {
					public void onSuccess(String json) {
						revenue[0] = CreditsParser.parsePersonsRevenue(json);
						checkAndDeliver.run();
					}

					public void onError(String e) {
						checkAndDeliver.run();
					}
				}
		);

		FirestoreManager.getInstance().getCollection(
				"households/" + householdId + "/fixedcharges",
				new FirestoreManager.Callback() {
					public void onSuccess(String json) {
						fixedCharges[0] = CreditsParser.parseFixedChargesTotal(json);
						checkAndDeliver.run();
					}

					public void onError(String e) {
						checkAndDeliver.run();
					}
				}
		);
	}

	public static void addCredit(
			String name,
			double totalAmount,
			double monthlyPayment,
			long startDateMs,
			int durationMonths,
			String emoji,
			String bank,
			String type,
			double rate,
			String paidBy,
			String compte,
			int paymentDay,
			Activity activity,
			OnWriteComplete callback
	) {
		CreditManager.getInstance().addCredit(
				name,
				totalAmount,
				monthlyPayment,
				startDateMs,
				durationMonths,
				emoji,
				bank,
				type,
				rate,
				paidBy,
				compte,
				paymentDay,
				new FirestoreManager.Callback() {
					public void onSuccess(String r) {
						// Déclencher immédiatement la génération de la transaction du mois courant
						if (activity != null) CreditManager.getInstance().init(activity);
						CreditManager.getInstance().checkAndApplyCredits(null);
						if (activity != null) {
							activity.runOnUiThread(callback::onSuccess);
						} else {
							callback.onSuccess();
						}
					}

					public void onError(String e) {
						if (activity != null) {
							activity.runOnUiThread(() -> callback.onError(e));
						} else {
							callback.onError(e);
						}
					}
				}
		);
	}

	public static void addCredit(
			String name,
			double totalAmount,
			double monthlyPayment,
			long startDateMs,
			int durationMonths,
			String emoji,
			String bank,
			String type,
			double rate,
			Activity activity,
			OnWriteComplete callback
	) {
		addCredit(
				name,
				totalAmount,
				monthlyPayment,
				startDateMs,
				durationMonths,
				emoji,
				bank,
				type,
				rate,
				"",
				"",
				1,
				activity,
				callback
		);
	}

	public static void deleteCredit(
			String docId,
			Activity activity,
			OnWriteComplete callback
	) {
		CreditManager.getInstance().deleteCredit(
				docId,
				new FirestoreManager.Callback() {
					public void onSuccess(String r) {
						if (activity != null) {
							activity.runOnUiThread(callback::onSuccess);
						} else {
							callback.onSuccess();
						}
					}

					public void onError(String e) {
						if (activity != null) {
							activity.runOnUiThread(() -> callback.onError(e));
						} else {
							callback.onError(e);
						}
					}
				}
		);
	}
}