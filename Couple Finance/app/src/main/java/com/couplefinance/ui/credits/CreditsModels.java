package com.couplefinance.ui.credits;

public final class CreditsModels {

	private CreditsModels() {
	}

	public static class Credit {
		public final String docId;
		public final String name;
		public final double totalAmount;
		public final double monthlyPayment;
		public final long startDateMs;
		public final int durationMonths;
		public final String emoji;
		public final String bank;
		public final String type;
		public final double rate;

		public final String paidBy;
		public final String compte;
		public final int paymentDay;

		public Credit(
				String docId,
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
				int paymentDay
		) {
			this.docId = docId;
			this.name = name;
			this.totalAmount = totalAmount;
			this.monthlyPayment = monthlyPayment;
			this.startDateMs = startDateMs;
			this.durationMonths = durationMonths;
			this.emoji = emoji;
			this.bank = bank;
			this.type = type;
			this.rate = rate;
			this.paidBy = paidBy == null ? "" : paidBy.trim();
			this.compte = compte == null ? "" : compte.trim();
			this.paymentDay = paymentDay <= 0 ? 1 : Math.min(paymentDay, 28);
		}

		public Credit(
				String docId,
				String name,
				double totalAmount,
				double monthlyPayment,
				long startDateMs,
				int durationMonths,
				String emoji,
				String bank,
				String type,
				double rate
		) {
			this(
					docId,
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
					1
			);
		}

		public boolean isJoint() {
			return "joint".equalsIgnoreCase(compte)
					|| "Compte joint".equalsIgnoreCase(paidBy)
					|| "Compte commun".equalsIgnoreCase(paidBy);
		}
	}

	public static class CreditsData {
		public final java.util.List<Credit> credits;
		public final double totalRevenue;
		public final double totalFixedCharges;

		public CreditsData(
				java.util.List<Credit> credits,
				double totalRevenue,
				double totalFixedCharges
		) {
			this.credits = credits;
			this.totalRevenue = totalRevenue;
			this.totalFixedCharges = totalFixedCharges;
		}
	}

	public enum CreditType {
		IMMOBILIER("Immobilier", "#3D5A80", "#EEF4FF"),
		CONSOMMATION("Consommation", "#7C3AED", "#EDE9FE"),
		AUTO("Auto", "#0369A1", "#E0F2FE"),
		TRAVAUX("Travaux", "#065F46", "#D1FAE5"),
		PERSONNEL("Personnel", "#92400E", "#FEF3C7"),
		AUTRE("Autre", "#6B7280", "#F3F4F6");

		public final String label;
		public final String textColor;
		public final String bgColor;

		CreditType(String label, String textColor, String bgColor) {
			this.label = label;
			this.textColor = textColor;
			this.bgColor = bgColor;
		}

		public static CreditType fromLabel(String label) {
			if (label == null)
				return AUTRE;

			for (CreditType t : values()) {
				if (t.label.equalsIgnoreCase(label))
					return t;
			}

			return AUTRE;
		}

		public static String[] allLabels() {
			CreditType[] types = values();
			String[] labels = new String[types.length];

			for (int i = 0; i < types.length; i++) {
				labels[i] = types[i].label;
			}

			return labels;
		}
	}

	public static final String[] CREDIT_EMOJIS = {
			"🏠", "🚗", "🎓", "🏗️", "💼", "🏥", "✈️", "🛋️", "⚡", "📱", "🏊", "🔑"
	};

	public static String emojiForName(String name) {
		if (name == null || name.isEmpty())
			return "🏦";

		return CREDIT_EMOJIS[Math.abs(name.hashCode()) % CREDIT_EMOJIS.length];
	}
}