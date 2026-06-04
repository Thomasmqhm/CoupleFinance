package com.couplefinance.ui.virements;

import android.app.Activity;

import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.JointAccountManager;
import com.couplefinance.data.TransactionManager;
import com.couplefinance.data.TransferManager;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class VirementLogic {

	public interface Callback {
		void onComplete(boolean success, String message);
	}

	private final VirementRepository repository;

	public VirementLogic(VirementRepository repository) {
		this.repository = repository;
	}

	public void doTransfer(String from, String to, double amount, String motif, long date, boolean fromIsHousehold,
			boolean toIsHousehold, Activity activity, Callback callback) {

		if (amount <= 0) {
			post(activity, callback, false, "Montant invalide");
			return;
		}

		if (from == null || from.trim().isEmpty() || to == null || to.trim().isEmpty()) {
			post(activity, callback, false, "Compte source ou destination manquant");
			return;
		}

		if (from.trim().equalsIgnoreCase(to.trim())) {
			post(activity, callback, false, "Choisissez deux comptes différents");
			return;
		}

		try {
			JointAccountManager.getInstance().init(activity);
		} catch (Exception ignored) {
		}

		repository.addTransfer(from, to, amount, motif, date, activity, (success, message) -> {
			if (!success) {
				callback.onComplete(false, message);
				return;
			}

			String transferDocId = VirementParser.extractDocId(message);
			createLinkedTransactions(from, to, amount, motif, date, fromIsHousehold, toIsHousehold, transferDocId,
					activity, callback);
		});
	}

	private void createLinkedTransactions(String from, String to, double amount, String motif, long date,
			boolean fromIsHousehold, boolean toIsHousehold, String transferDocId, Activity activity,
			Callback callback) {

		boolean fromJoint = isJointName(from);
		boolean toJoint = isJointName(to);

		if (fromIsHousehold && toIsHousehold) {
			createInternalTransferTransactions(from, to, amount, motif, date, fromJoint, toJoint, transferDocId,
					activity, callback);
			return;
		}

		if (fromIsHousehold) {
			String label = from + " · Virement → " + to + optionalMotif(motif);
			String compte = fromJoint ? "joint" : "";
			// Si la source est le compte joint, on tague la transaction avec la
			// catégorie dédiée afin que le Dashboard la comptabilise comme une
			// SORTIE du compte joint (et non comme un virement générique ignoré).
			String category = fromJoint
					? JointAccountManager.JOINT_TRANSFER_CATEGORY
					: "Virements";

			TransactionManager.getInstance().addTransactionWithTransferId(
					label,
					amount,
					"variable",
					category,
					date,
					from,
					false,
					false,
					compte,
					transferDocId,
					new FirestoreManager.Callback() {
						public void onSuccess(String response) {
							linkTransfer(transferDocId, VirementParser.extractDocId(response));
							post(activity, callback, true, response);
						}

						public void onError(String error) {
							post(activity, callback, false, error);
						}
					});
			return;
		}

		if (toIsHousehold) {
			String label = to + " · Virement reçu de " + from + optionalMotif(motif);
			String compte = toJoint ? "joint" : "";
			// Si la destination est le compte joint, le virement reçu doit
			// augmenter les revenus/entrées du compte joint : catégorie dédiée.
			String category = toJoint
					? JointAccountManager.JOINT_TRANSFER_CATEGORY
					: "Virements";

			TransactionManager.getInstance().addTransactionWithTransferId(
					label,
					amount,
					"income",
					category,
					date,
					to,
					false,
					false,
					compte,
					transferDocId,
					new FirestoreManager.Callback() {
						public void onSuccess(String response) {
							linkTransfer(transferDocId, VirementParser.extractDocId(response));
							post(activity, callback, true, response);
						}

						public void onError(String error) {
							post(activity, callback, false, error);
						}
					});
			return;
		}

		post(activity, callback, true, "ok");
	}

	private void createInternalTransferTransactions(String from, String to, double amount, String motif, long date,
			boolean fromJoint, boolean toJoint, String transferDocId, Activity activity, Callback callback) {

		String labelExpense = from + " · Virement → " + to + optionalMotif(motif);
		String labelIncome = to + " · Virement reçu de " + from + optionalMotif(motif);

		String expenseCompte = fromJoint ? "joint" : "";
		String incomeCompte = toJoint ? "joint" : "";

		// La transaction côté compte joint utilise la catégorie dédiée afin
		// d'être comptabilisée sur la carte du compte joint. La transaction
		// côté compte personnel reste un "Virements" classique pour ne pas
		// fausser le solde commun global.
		String expenseCategory = fromJoint
				? JointAccountManager.JOINT_TRANSFER_CATEGORY
				: "Virements";
		String incomeCategory = toJoint
				? JointAccountManager.JOINT_TRANSFER_CATEGORY
				: "Virements";

		String expensePerson = from;
		String incomePerson = to;

		AtomicInteger done = new AtomicInteger(0);
		AtomicInteger errors = new AtomicInteger(0);
		String[] firstError = {""};

		FirestoreManager.Callback commonCallback = new FirestoreManager.Callback() {
			public void onSuccess(String response) {
				linkTransfer(transferDocId, VirementParser.extractDocId(response));

				if (done.incrementAndGet() + errors.get() >= 2) {
					if (errors.get() > 0) {
						post(activity, callback, false, firstError[0]);
					} else {
						post(activity, callback, true, response);
					}
				}
			}

			public void onError(String error) {
				firstError[0] = error;
				errors.incrementAndGet();

				if (done.get() + errors.get() >= 2) {
					post(activity, callback, false, firstError[0]);
				}
			}
		};

		TransactionManager.getInstance().addTransactionWithTransferId(
				labelExpense,
				amount,
				"variable",
				expenseCategory,
				date,
				expensePerson,
				false,
				false,
				expenseCompte,
				transferDocId,
				commonCallback
		);

		TransactionManager.getInstance().addTransactionWithTransferId(
				labelIncome,
				amount,
				"income",
				incomeCategory,
				date,
				incomePerson,
				false,
				false,
				incomeCompte,
				transferDocId,
				commonCallback
		);
	}

	private void linkTransfer(String transferDocId, String txDocId) {
		if (transferDocId == null || transferDocId.trim().isEmpty())
			return;

		if (txDocId == null || txDocId.trim().isEmpty())
			return;

		TransferManager.getInstance().linkTransferToTransaction(transferDocId, txDocId,
				new FirestoreManager.Callback() {
					public void onSuccess(String response) {
					}

					public void onError(String error) {
					}
				});
	}

	private boolean isJointName(String value) {
		if (value == null)
			return false;

		String clean = value.trim().toLowerCase(Locale.FRANCE);

		String localName = "";
		try {
			localName = JointAccountManager.getInstance().getNameLocal();
		} catch (Exception ignored) {
		}

		return clean.equals("compte joint")
				|| clean.equals("joint")
				|| clean.equals("compte commun")
				|| clean.equals(JointAccountManager.DEFAULT_NAME.toLowerCase(Locale.FRANCE))
				|| (!localName.isEmpty() && clean.equals(localName.toLowerCase(Locale.FRANCE)));
	}

	private String optionalMotif(String motif) {
		if (motif == null || motif.trim().isEmpty()) {
			return "";
		}
		return " · " + motif.trim();
	}

	private void post(Activity activity, Callback callback, boolean success, String message) {
		if (activity == null) {
			callback.onComplete(success, message);
			return;
		}

		activity.runOnUiThread(() -> callback.onComplete(success, message));
	}
}