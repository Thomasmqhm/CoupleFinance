package com.couplefinance.ui.home;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.ui.DS;
import com.couplefinance.core.ui.effects.GradientFactory;
import com.couplefinance.data.JointAccountManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HomeMemberSection {

	private final Activity activity;

	public HomeMemberSection(Activity activity) {
		this.activity = activity;
	}

	public void render(LinearLayout container, List<HomeMemberCard.Data> members,
					   HomeMemberCard.Data joint, boolean showJoint) {
		if (container == null) return;

		container.removeAllViews();

        if ((members == null || members.isEmpty()) && (!showJoint || joint == null))
	return;
	
		container.addView(buildSectionHeader(showJoint));

		for (int i = 0; i < members.size(); i++) {
			View card = HomeMemberCard.build(activity, members.get(i));

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.topMargin = DS.dp(activity, DS.SPACE_12);

			container.addView(card, lp);
			HomeDashboardStyle.fadeIn(card, i * 45L + 60L);
		}

		if (showJoint && joint != null) {
			View jointCard = HomeMemberCard.build(activity, joint);

			LinearLayout.LayoutParams jointLp = new LinearLayout.LayoutParams(-1, -2);
			jointLp.topMargin = DS.dp(activity, DS.SPACE_12);

			container.addView(jointCard, jointLp);
			HomeDashboardStyle.fadeIn(jointCard, 140L);
		}

		if (!showJoint) {
			View cta = buildJointAccountCta();

			LinearLayout.LayoutParams ctaLp = new LinearLayout.LayoutParams(-1, -2);
			ctaLp.topMargin = DS.dp(activity, DS.SPACE_12);

			container.addView(cta, ctaLp);
			HomeDashboardStyle.fadeIn(cta, 120L);
		}
	}

	private View buildSectionHeader(boolean jointEnabled) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

		TextView title = new TextView(activity);
		title.setText("Comptes liés");
		title.setTextColor(ThemeColors.textPrimary());
		title.setTextSize(DS.TEXT_SUBTITLE);
		title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		title.setLetterSpacing(-0.012f);
		title.setIncludeFontPadding(false);

		row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

		if (jointEnabled) {
			TextView badge = new TextView(activity);
			badge.setText("Compte joint");
			badge.setTextColor(ThemeColors.primary());
			badge.setTextSize(11f);
			badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
			badge.setGravity(Gravity.CENTER);
			badge.setIncludeFontPadding(false);

			badge.setPadding(
					DS.dp(activity, 10),
					DS.dp(activity, 6),
					DS.dp(activity, 10),
					DS.dp(activity, 6)
			);

			badge.setBackground(
					GradientFactory.bordered(
							activity,
							ThemeColors.withAlpha(ThemeColors.primary(), 16),
							ThemeColors.withAlpha(ThemeColors.primary(), 46),
							DS.R_PILL
					)
			);

			row.addView(badge);
		}

		return row;
	}

	private View buildJointAccountCta() {
		LinearLayout cta = new LinearLayout(activity);
		cta.setOrientation(LinearLayout.HORIZONTAL);
		cta.setGravity(Gravity.CENTER_VERTICAL);

		cta.setPadding(
				DS.dp(activity, DS.SPACE_18),
				DS.dp(activity, DS.SPACE_16),
				DS.dp(activity, DS.SPACE_18),
				DS.dp(activity, DS.SPACE_16)
		);

		cta.setBackground(
				GradientFactory.bordered(
						activity,
						ThemeColors.surfaceFloating(),
						ThemeColors.borderSoft(),
						DS.RADIUS_XL
				)
		);

		cta.setElevation(DS.dp(activity, 4));
		HomeDashboardStyle.applyPressEffect(cta);

		TextView icon = new TextView(activity);
		icon.setText("🏦");
		icon.setTextSize(20f);
		icon.setGravity(Gravity.CENTER);
		icon.setIncludeFontPadding(false);

		LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
				DS.dp(activity, 44),
				DS.dp(activity, 44)
		);
		iconLp.rightMargin = DS.dp(activity, DS.SPACE_14);

		cta.addView(icon, iconLp);

		LinearLayout textCol = new LinearLayout(activity);
		textCol.setOrientation(LinearLayout.VERTICAL);

		TextView label = new TextView(activity);
		label.setText("Compte joint");
		label.setTextColor(ThemeColors.textPrimary());
		label.setTextSize(15f);
		label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		label.setLetterSpacing(-0.01f);
		label.setIncludeFontPadding(false);

		textCol.addView(label);

		TextView sub = new TextView(activity);
		sub.setText("Activez un compte commun dans Paramètres → Foyer");
		sub.setTextColor(ThemeColors.textMuted());
		sub.setTextSize(12f);
		sub.setLineSpacing(DS.dp(activity, 2), 1f);
		sub.setIncludeFontPadding(false);

		LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
		subLp.topMargin = DS.dp(activity, DS.SPACE_4);

		textCol.addView(sub, subLp);

		cta.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView chevron = new TextView(activity);
		chevron.setText("›");
		chevron.setTextColor(ThemeColors.primary());
		chevron.setTextSize(22f);
		chevron.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
		chevron.setIncludeFontPadding(false);

		cta.addView(chevron);

		return cta;
	}

	public static List<HomeMemberCard.Data> buildMemberDataList(
			Map<String, double[]> personBalances,
			Map<String, Double> memberBalances,
			String myName,
			Map<String, String> memberColors
	) {
		return buildMemberDataList(
				personBalances,
				memberBalances,
				myName,
				memberColors,
				null,
				null,
				null
		);
	}

	public static List<HomeMemberCard.Data> buildMemberDataList(
			Map<String, double[]> personBalances,
			Map<String, Double> memberBalances,
			String myName,
			Map<String, String> memberColors,
			Map<String, Double> upcomingByMember,
			Map<String, Integer> upcomingCountByMember
	) {
		return buildMemberDataList(
				personBalances,
				memberBalances,
				myName,
				memberColors,
				upcomingByMember,
				upcomingCountByMember,
				null
		);
	}

	public static List<HomeMemberCard.Data> buildMemberDataList(
			Map<String, double[]> personBalances,
			Map<String, Double> memberBalances,
			String myName,
			Map<String, String> memberColors,
			Map<String, Double> upcomingByMember,
			Map<String, Integer> upcomingCountByMember,
			List<String[]> subscriptions
	) {
		List<HomeMemberCard.Data> result = new ArrayList<>();

		if (personBalances == null) return result;

		for (Map.Entry<String, double[]> entry : personBalances.entrySet()) {
			String name = entry.getKey();

			if (name == null || name.trim().isEmpty()) continue;

			name = name.trim();

			if (isJointName(name)) continue;

			double[] vals = entry.getValue();

			double income = vals != null && vals.length > 0 ? vals[0] : 0;
			double expenses = vals != null && vals.length > 1 ? vals[1] : 0;
			double impactIncome = vals != null && vals.length > 2 ? vals[2] : 0;
			double impactExpenses = vals != null && vals.length > 3 ? vals[3] : 0;

			String colorHex = findColorIgnoreCase(memberColors, name);
			boolean isCurrentUser = myName != null && myName.trim().equalsIgnoreCase(name);

			HomeMemberCard.Data data = new HomeMemberCard.Data(
					name,
					colorHex,
					false,
					isCurrentUser
			);

			double subscriptionsAmount = computeMemberSubscriptions(name, subscriptions);
			double upcomingAmount = subscriptionsAmount > 0
					? subscriptionsAmount
					: findDoubleIgnoreCase(upcomingByMember, name);

			data.startBalance = findDoubleIgnoreCase(memberBalances, name);
			data.income = income;
			data.expenses = expenses;
			data.upcomingExpenses = upcomingAmount;
			data.upcomingCount = findIntIgnoreCase(upcomingCountByMember, name);
			data.lockCurrentBalanceToStart = false;

			data.compute();

			data.currentBalance =
					data.startBalance
							+ impactIncome
							- impactExpenses;

			data.forecastBalance =
					data.currentBalance
							- Math.max(0, data.upcomingExpenses);

			result.add(data);
		}

		return result;
	}

	public static HomeMemberCard.Data buildJointData(List<String[]> allTransactions) {
		return buildJointData(allTransactions, null);
	}

	public static HomeMemberCard.Data buildJointData(List<String[]> allTransactions, List<String[]> subscriptions) {
		JointAccountManager jm = JointAccountManager.getInstance();

		double jointIncome = 0;
		double jointExpenses = 0;

		double impactIncome = 0;
		double impactExpenses = 0;

		long anchor = getJointAnchorMillisSafe(jm);

		Calendar now = Calendar.getInstance();
		int curMonth = now.get(Calendar.MONTH);
		int curYear = now.get(Calendar.YEAR);

		if (allTransactions != null) {
			for (String[] tx : allTransactions) {
				if (tx == null || tx.length < 10)
					continue;

				if (!"joint".equalsIgnoreCase(tx[9]))
					continue;

				if ("true".equals(tx[5]))
					continue;

				String type = tx[2] == null ? "" : tx[2];
				String category = tx[3] == null ? "" : tx[3];

				// Un virement DONT le compte est "joint" concerne directement
				// le Compte Joint : il doit compter comme un revenu (virement
				// reçu) ou une dépense (virement émis) du compte commun.
				// Seuls les virements génériques NON tagués "joint" sont ignorés
				// (ceux-là transitent entre comptes personnels uniquement, et
				// ne portent pas tx[9] == "joint" — donc déjà exclus plus haut).
				boolean isJointTransfer =
						category.equalsIgnoreCase(JointAccountManager.JOINT_TRANSFER_CATEGORY);

				boolean isLegacyTransferCategory =
						category.equalsIgnoreCase("Virement")
								|| category.equalsIgnoreCase("Virements");

				// On NE saute plus les virements tagués "joint" : ils alimentent
				// bien la carte du compte joint. On ne saute que les éventuelles
				// catégories de virement génériques qui ne seraient pas des
				// mouvements joint identifiés.
				if (isLegacyTransferCategory && !isJointTransfer) {
					// Cas historique : virement générique tagué joint sans la
					// catégorie dédiée — on le compte quand même, car tx[9]
					// vaut "joint", donc il concerne réellement le compte joint.
					isJointTransfer = true;
				}

				long dateMs = 0;

				try {
					dateMs = Long.parseLong(tx[4]);
				} catch (Exception ignored) {
				}

				if (dateMs > 0) {
					Calendar c = Calendar.getInstance();
					c.setTimeInMillis(dateMs);

					if (c.get(Calendar.MONTH) != curMonth || c.get(Calendar.YEAR) != curYear)
						continue;
				}

				double amount = 0;

				try {
					amount = Double.parseDouble(tx[1]);
				} catch (Exception ignored) {
				}

				boolean isIncome = "income".equalsIgnoreCase(type);

				if (isIncome) {
					jointIncome += amount;
				} else {
					jointExpenses += amount;
				}

				boolean impactsBalance = anchor <= 0 || dateMs <= 0 || dateMs >= anchor;

				if (impactsBalance) {
					if (isIncome) {
						impactIncome += amount;
					} else {
						impactExpenses += amount;
					}
				}
			}
		}

		String jointName = jm.getNameLocal();

		HomeMemberCard.Data data = new HomeMemberCard.Data(
				jointName,
				null,
				true,
				false
		);

		data.startBalance = jm.getBalanceLocal();
		data.income = jointIncome;
		data.expenses = jointExpenses;
		data.upcomingExpenses = computeMemberSubscriptions(jointName, subscriptions);
		data.upcomingCount = data.upcomingExpenses > 0 ? 1 : 0;
		data.lockCurrentBalanceToStart = false;

		data.compute();

		data.currentBalance =
				data.startBalance
						+ impactIncome
						- impactExpenses;

		data.forecastBalance =
				data.currentBalance
						- Math.max(0, data.upcomingExpenses);

		return data;
	}

	public static HomeMemberCard.Data buildJointData(double income, double expenses) {
		JointAccountManager jm = JointAccountManager.getInstance();

		HomeMemberCard.Data data = new HomeMemberCard.Data(
				jm.getNameLocal(),
				null,
				true,
				false
		);

		data.startBalance = jm.getBalanceLocal();
		data.income = income;
		data.expenses = expenses;
		data.lockCurrentBalanceToStart = false;
		data.compute();

		return data;
	}
	
	public static HomeMemberCard.Data buildJointData(
		List<String[]> allTransactions,
		Map<String, Double> upcomingByMember,
		Map<String, Integer> upcomingCountByMember
) {
	HomeMemberCard.Data data = buildJointData(allTransactions);

	JointAccountManager jm = JointAccountManager.getInstance();
	String jointName = jm.getNameLocal();

	data.upcomingExpenses = findDoubleIgnoreCase(upcomingByMember, jointName);
	data.upcomingCount = findIntIgnoreCase(upcomingCountByMember, jointName);

	data.forecastBalance =
			data.currentBalance
					- Math.max(0, data.upcomingExpenses);

	return data;
}

	private static double computeMemberSubscriptions(
			String memberName,
			List<String[]> subscriptions
	) {
		if (memberName == null || subscriptions == null)
			return 0;

		double total = 0;

		String cleanMember = normalizeKey(memberName);

		for (String[] sub : subscriptions) {
			if (sub == null || sub.length < 5)
				continue;

			String payer = sub[4] == null ? "" : sub[4].trim();

			if (payer.isEmpty())
				continue;

			String cleanPayer = normalizeKey(payer);

			if (!cleanPayer.equals(cleanMember))
				continue;

			try {
				total += Double.parseDouble(sub[1]);
			} catch (Exception ignored) {
			}
		}

		return total;
	}

	private static long getJointAnchorMillisSafe(JointAccountManager jm) {
		try {
			return jm.getAnchorLocal();
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static boolean isJointName(String name) {
		if (name == null) return false;

		String n = normalizeKey(name);

		return n.equals("compte joint")
				|| n.equals("joint")
				|| n.equals("compte commun");
	}

	private static String findColorIgnoreCase(Map<String, String> map, String key) {
		if (map == null || key == null)
			return "#C86B4A";

		String clean = normalizeKey(key);

		for (Map.Entry<String, String> e : map.entrySet()) {
			if (e.getKey() == null)
				continue;

			if (normalizeKey(e.getKey()).equals(clean)) {
				String value = e.getValue();

				if (value != null && !value.trim().isEmpty())
					return value.trim();
			}
		}

		return "#C86B4A";
	}

	private static double findDoubleIgnoreCase(Map<String, Double> map, String key) {
		if (map == null || key == null)
			return 0;

		String clean = normalizeKey(key);

		Double direct = map.get(key);
		if (direct != null)
			return direct;

		Double lowerFr = map.get(key.toLowerCase(Locale.FRANCE));
		if (lowerFr != null)
			return lowerFr;

		Double lowerRoot = map.get(key.toLowerCase(Locale.ROOT));
		if (lowerRoot != null)
			return lowerRoot;

		for (Map.Entry<String, Double> e : map.entrySet()) {
			if (e.getKey() == null)
				continue;

			if (normalizeKey(e.getKey()).equals(clean)) {
				return e.getValue() == null ? 0 : e.getValue();
			}
		}

		return 0;
	}

	private static int findIntIgnoreCase(Map<String, Integer> map, String key) {
		if (map == null || key == null)
			return 0;

		String clean = normalizeKey(key);

		Integer direct = map.get(key);
		if (direct != null)
			return direct;

		Integer lowerFr = map.get(key.toLowerCase(Locale.FRANCE));
		if (lowerFr != null)
			return lowerFr;

		Integer lowerRoot = map.get(key.toLowerCase(Locale.ROOT));
		if (lowerRoot != null)
			return lowerRoot;

		for (Map.Entry<String, Integer> e : map.entrySet()) {
			if (e.getKey() == null)
				continue;

			if (normalizeKey(e.getKey()).equals(clean)) {
				return e.getValue() == null ? 0 : e.getValue();
			}
		}

		return 0;
	}

	private static String normalizeKey(String value) {
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
				.replace("ä", "a")
				.replace("î", "i")
				.replace("ï", "i")
				.replace("ô", "o")
				.replace("ö", "o")
				.replace("ù", "u")
				.replace("û", "u")
				.replace("ü", "u")
				.replace("ç", "c");
	}
}