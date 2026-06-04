package com.couplefinance.ui.transactions;

import android.app.Activity;

import com.couplefinance.AuthManager;
import com.couplefinance.data.CategoryManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.TransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class TransactionsRepository {

	private TransactionsRepository() {
	}

	public interface OnDataLoaded {
		void onLoaded(List<TransactionsModels.Transaction> transactions, List<String> members,
				List<String[]> categories);

		void onError(String message);
	}

	public interface OnWriteComplete {
		void onSuccess();

		void onError(String message);
	}

	public interface OnImportProgress {
		void onProgress(int done, int total);
	}

	public static void loadAll(Activity activity, OnDataLoaded callback) {
		AtomicInteger done = new AtomicInteger(0);

		final List<TransactionsModels.Transaction>[] txList = new List[1];
		final List<String>[] members = new List[1];
		final List<String[]>[] categories = new List[1];

		txList[0] = new ArrayList<>();
		members[0] = new ArrayList<>();
		categories[0] = new ArrayList<>();

		Runnable checkAndDeliver = () -> {
			if (done.incrementAndGet() == 3) {
				String me = AuthManager.getInstance().getDisplayName();

				if (me != null && !me.trim().isEmpty()) {
					boolean found = false;

					for (String m : members[0]) {
						if (m != null && m.equalsIgnoreCase(me)) {
							found = true;
							break;
						}
					}

					if (!found) {
						members[0].add(0, me);
					}
				}

				if (members[0].isEmpty()) {
					members[0].add("Moi");
				}

				final List<TransactionsModels.Transaction> tx = txList[0];
				final List<String> m = members[0];
				final List<String[]> cat = categories[0];

				if (activity != null) {
					activity.runOnUiThread(() -> callback.onLoaded(tx, m, cat));
				} else {
					callback.onLoaded(tx, m, cat);
				}
			}
		};

		TransactionManager.getInstance().getTransactions(new FirestoreManager.Callback() {
			public void onSuccess(String json) {
				txList[0] = TransactionsParser.parseTransactions(json);
				checkAndDeliver.run();
			}

			public void onError(String e) {
				checkAndDeliver.run();
			}
		});

		HouseholdManager.getInstance().getMembers(new FirestoreManager.Callback() {
			public void onSuccess(String json) {
				members[0] = TransactionsParser.parseMembers(json);
				checkAndDeliver.run();
			}

			public void onError(String e) {
				checkAndDeliver.run();
			}
		});

		CategoryManager.getInstance().getCategories(new FirestoreManager.Callback() {
			public void onSuccess(String json) {
				categories[0] = TransactionsParser.parseCategories(json);
				checkAndDeliver.run();
			}

			public void onError(String e) {
				checkAndDeliver.run();
			}
		});
	}

	public static void addTransaction(String label, double amount, String type, String category, long dateMs,
			String person, boolean shared, boolean isShareSplit, Activity activity, OnWriteComplete callback) {
		addTransaction(label, amount, type, category, dateMs, person, shared, isShareSplit, "", activity, callback);
	}

	public static void addTransaction(String label, double amount, String type, String category, long dateMs,
			String person, boolean shared, boolean isShareSplit, String compte, Activity activity,
			OnWriteComplete callback) {

		String cleanType = normalizeTransactionType(type);
		String cleanCategory = normalizeCategory(category);

		TransactionManager.getInstance().addTransactionWithDateAndShared(label, amount, cleanType, cleanCategory,
				dateMs, person, shared, isShareSplit, compte == null ? "" : compte.trim(),
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
				});
	}

	public static void updateTransaction(String docId, String label, double amount, String type, String category,
			long dateMs, String person, boolean shared, Activity activity, OnWriteComplete callback) {
		updateTransaction(docId, label, amount, type, category, dateMs, person, shared, "", activity, callback);
	}

	public static void updateTransaction(String docId, String label, double amount, String type, String category,
			long dateMs, String person, boolean shared, String compte, Activity activity, OnWriteComplete callback) {

		String cleanId = cleanDocId(docId);

		if (cleanId.isEmpty()) {
			if (activity != null) {
				activity.runOnUiThread(() -> callback.onError("Transaction introuvable"));
			} else {
				callback.onError("Transaction introuvable");
			}
			return;
		}

		String cleanType = normalizeTransactionType(type);
		String cleanCategory = normalizeCategory(category);

		TransactionManager.getInstance().updateTransactionWithShared(cleanId, label, amount, cleanType, cleanCategory,
				dateMs, person, shared, compte == null ? "" : compte.trim(), new FirestoreManager.Callback() {
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
				});
	}

	public static void deleteTransaction(String docId, Activity activity, OnWriteComplete callback) {
		String cleanId = cleanDocId(docId);

		if (cleanId.isEmpty()) {
			if (activity != null) {
				activity.runOnUiThread(() -> callback.onError("Transaction introuvable"));
			} else {
				callback.onError("Transaction introuvable");
			}
			return;
		}

		TransactionManager.getInstance().deleteTransaction(cleanId, new FirestoreManager.Callback() {
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
		});
	}

	public static void deleteLinkedShareSplits(String baseLabel, List<TransactionsModels.Transaction> allTx,
			Activity activity, OnWriteComplete callback) {

		List<String> toDelete = new ArrayList<>();

		if (allTx != null) {
			for (TransactionsModels.Transaction tx : allTx) {
				if (tx != null && tx.isShareSplit && tx.label != null && baseLabel != null
						&& tx.label.contains(baseLabel)) {
					String cleanId = cleanDocId(tx.docId);
					if (!cleanId.isEmpty()) {
						toDelete.add(cleanId);
					}
				}
			}
		}

		if (toDelete.isEmpty()) {
			if (activity != null) {
				activity.runOnUiThread(callback::onSuccess);
			} else {
				callback.onSuccess();
			}
			return;
		}

		AtomicInteger done = new AtomicInteger(0);
		AtomicInteger errors = new AtomicInteger(0);

		for (String docId : toDelete) {
			TransactionManager.getInstance().deleteTransaction(docId, new FirestoreManager.Callback() {
				public void onSuccess(String r) {
					if (done.incrementAndGet() + errors.get() == toDelete.size()) {
						if (activity != null) {
							activity.runOnUiThread(callback::onSuccess);
						} else {
							callback.onSuccess();
						}
					}
				}

				public void onError(String e) {
					errors.incrementAndGet();

					if (done.get() + errors.get() == toDelete.size()) {
						if (activity != null) {
							activity.runOnUiThread(callback::onSuccess);
						} else {
							callback.onSuccess();
						}
					}
				}
			});
		}
	}

	/**
	 * Import avec délai de 300ms entre chaque transaction.
	 * Simple, lisible, sans récursion ni saturation du pool de threads.
	 */
	public static void importBatch(List<TransactionsModels.Transaction> transactions, Activity activity,
			OnImportProgress onProgress, OnWriteComplete callback) {

		if (transactions == null || transactions.isEmpty()) {
			if (activity != null) {
				activity.runOnUiThread(callback::onSuccess);
			} else {
				callback.onSuccess();
			}
			return;
		}

		List<TransactionsModels.Transaction> queue =
				new ArrayList<TransactionsModels.Transaction>(transactions);
		int total = queue.size();

		android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

		Runnable[] step = new Runnable[1];
		final int[] idx = {0};

		step[0] = () -> {
			if (idx[0] >= total) {
				callback.onSuccess();
				return;
			}

			TransactionsModels.Transaction tx = queue.get(idx[0]);
			String cleanType = normalizeTransactionType(tx.type);
			String cleanCategory = normalizeCategory(tx.category);
			final int current = idx[0];

			TransactionManager.getInstance().addTransactionWithDateAndShared(
					tx.label, tx.amount, cleanType, cleanCategory,
					tx.dateMs, tx.person, tx.shared, tx.isShareSplit, tx.compte,
					new FirestoreManager.Callback() {
						public void onSuccess(String r) {
							int done = current + 1;
							if (onProgress != null && activity != null) {
								activity.runOnUiThread(() -> onProgress.onProgress(done, total));
							}
							idx[0] = done;
							// 300ms entre chaque transaction pour ne pas saturer Firestore
							handler.postDelayed(step[0], 300);
						}

						public void onError(String e) {
							// On continue malgré l'erreur
							int done = current + 1;
							if (onProgress != null && activity != null) {
								activity.runOnUiThread(() -> onProgress.onProgress(done, total));
							}
							idx[0] = done;
							handler.postDelayed(step[0], 300);
						}
					});
		};

		// Démarrer le premier step
		handler.post(step[0]);
	}

	private static String normalizeTransactionType(String type) {
		if (type == null) {
			return "variable";
		}

		String clean = type.trim();

		if (clean.equalsIgnoreCase("income")) {
			return "income";
		}

		/*
		 * Important :
		 * "Charge fixe" est une notion d'abonnement/récurrence.
		 * La transaction réelle doit rester une dépense classique,
		 * sinon certains filtres/listes peuvent ne plus l'afficher.
		 */
		if (clean.equalsIgnoreCase("fixed") || clean.equalsIgnoreCase("fixed_planned")
				|| clean.equalsIgnoreCase("fixed_done") || clean.equalsIgnoreCase("expense")) {
			return "variable";
		}

		return "variable";
	}

	private static String normalizeCategory(String category) {
		if (category == null || category.trim().isEmpty()) {
			return "Autre";
		}
		return category.trim();
	}

	private static String cleanDocId(String docId) {
		if (docId == null) {
			return "";
		}

		String clean = docId.trim();

		if (clean.isEmpty()) {
			return "";
		}

		if (clean.contains("/")) {
			int lastSlash = clean.lastIndexOf("/");
			if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
				clean = clean.substring(lastSlash + 1);
			}
		}

		if (clean.contains("\"")) {
			clean = clean.replace("\"", "").trim();
		}

		return clean;
	}
}