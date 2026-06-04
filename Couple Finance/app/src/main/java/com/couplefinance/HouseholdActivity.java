package com.couplefinance;

import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.PersonManager;
import com.couplefinance.ui.DashboardActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class HouseholdActivity extends Activity {

	private EditText etCode;
	private String savedToken;
	private String savedUserId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AuthManager.getInstance().init(this);
		HouseholdManager.getInstance().init(this);

		// Essai 1 : Intent
		savedToken = getIntent().getStringExtra("TOKEN");
		savedUserId = getIntent().getStringExtra("USER_ID");

		// Essai 2 : SharedPreferences
		if (savedToken == null) {
			SharedPreferences prefs = getApplicationContext().getSharedPreferences("auth_prefs", MODE_PRIVATE);
			savedToken = prefs.getString("token", null);
			savedUserId = prefs.getString("userId", null);
		}

		// Essai 3 : AuthManager direct
		if (savedToken == null) {
			savedToken = AuthManager.getInstance().getToken();
			savedUserId = AuthManager.getInstance().getUserId();
		}

		Toast.makeText(this, "Token: " + (savedToken != null ? "✓ " + savedToken.length() + "c" : "NULL"),
				Toast.LENGTH_LONG).show();

		buildUI();
	}

	private void buildUI() {
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setGravity(Gravity.CENTER);
		root.setBackgroundColor(Color.parseColor("#F8FAFC"));
		root.setPadding(48, 48, 48, 48);
		setContentView(root);

		LinearLayout logoIcon = new LinearLayout(this);
		logoIcon.setBackgroundResource(R.drawable.card_green);
		logoIcon.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams lip = new LinearLayout.LayoutParams(72, 72);
		lip.gravity = Gravity.CENTER_HORIZONTAL;
		lip.bottomMargin = 16;
		logoIcon.setLayoutParams(lip);
		TextView tvCF = new TextView(this);
		tvCF.setText("CF");
		tvCF.setTextSize(22f);
		tvCF.setTextColor(Color.WHITE);
		tvCF.setTypeface(null, Typeface.BOLD);
		logoIcon.addView(tvCF);
		root.addView(logoIcon);

		TextView tvTitle = new TextView(this);
		tvTitle.setText("CoupleFinance");
		tvTitle.setTextSize(22f);
		tvTitle.setTextColor(Color.parseColor("#0F172A"));
		tvTitle.setTypeface(null, Typeface.BOLD);
		tvTitle.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		ttp.bottomMargin = 6;
		tvTitle.setLayoutParams(ttp);
		root.addView(tvTitle);

		TextView tvSub = new TextView(this);
		tvSub.setText("Configurez votre foyer");
		tvSub.setTextSize(13f);
		tvSub.setTextColor(Color.parseColor("#64748B"));
		tvSub.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		spp.bottomMargin = 40;
		tvSub.setLayoutParams(spp);
		root.addView(tvSub);

		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackgroundResource(R.drawable.card_shadow);
		card.setPadding(32, 32, 32, 32);
		card.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
		root.addView(card);

		TextView lblCreate = new TextView(this);
		lblCreate.setText("Nouveau foyer");
		lblCreate.setTextSize(15f);
		lblCreate.setTextColor(Color.parseColor("#0F172A"));
		lblCreate.setTypeface(null, Typeface.BOLD);
		LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		lcp.bottomMargin = 6;
		lblCreate.setLayoutParams(lcp);
		card.addView(lblCreate);

		TextView descCreate = new TextView(this);
		descCreate.setText("Créez un foyer et invitez votre partenaire.");
		descCreate.setTextSize(12f);
		descCreate.setTextColor(Color.parseColor("#64748B"));
		LinearLayout.LayoutParams dcp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		dcp.bottomMargin = 16;
		descCreate.setLayoutParams(dcp);
		card.addView(descCreate);

		Button btnCreate = new Button(this);
		btnCreate.setText("CRÉER UN NOUVEAU FOYER");
		btnCreate.setTextColor(Color.WHITE);
		btnCreate.setTextSize(13f);
		btnCreate.setTypeface(null, Typeface.BOLD);
		btnCreate.setBackgroundResource(R.drawable.btn_rounded);
		LinearLayout.LayoutParams bcp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52);
		bcp.bottomMargin = 24;
		btnCreate.setLayoutParams(bcp);
		btnCreate.setOnClickListener(v -> createHousehold(btnCreate));
		card.addView(btnCreate);

		LinearLayout sepRow = new LinearLayout(this);
		sepRow.setOrientation(LinearLayout.HORIZONTAL);
		sepRow.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		srp.bottomMargin = 24;
		sepRow.setLayoutParams(srp);

		View sep1 = new View(this);
		sep1.setBackgroundColor(Color.parseColor("#E2E8F0"));
		sep1.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
		sepRow.addView(sep1);

		TextView tvOr = new TextView(this);
		tvOr.setText("  ou  ");
		tvOr.setTextSize(12f);
		tvOr.setTextColor(Color.parseColor("#94A3B8"));
		sepRow.addView(tvOr);

		View sep2 = new View(this);
		sep2.setBackgroundColor(Color.parseColor("#E2E8F0"));
		sep2.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
		sepRow.addView(sep2);
		card.addView(sepRow);

		TextView lblJoin = new TextView(this);
		lblJoin.setText("Rejoindre un foyer existant");
		lblJoin.setTextSize(15f);
		lblJoin.setTextColor(Color.parseColor("#0F172A"));
		lblJoin.setTypeface(null, Typeface.BOLD);
		LinearLayout.LayoutParams ljp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		ljp.bottomMargin = 6;
		lblJoin.setLayoutParams(ljp);
		card.addView(lblJoin);

		TextView descJoin = new TextView(this);
		descJoin.setText("Entrez le code d'invitation de votre partenaire.");
		descJoin.setTextSize(12f);
		descJoin.setTextColor(Color.parseColor("#64748B"));
		LinearLayout.LayoutParams djp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		djp.bottomMargin = 12;
		descJoin.setLayoutParams(djp);
		card.addView(descJoin);

		etCode = new EditText(this);
		etCode.setHint("Code d'invitation (6 caractères)");
		etCode.setHintTextColor(Color.parseColor("#CBD5E1"));
		etCode.setTextColor(Color.parseColor("#0F172A"));
		etCode.setTextSize(16f);
		etCode.setTypeface(null, Typeface.BOLD);
		etCode.setGravity(Gravity.CENTER);
		etCode.setBackgroundResource(R.drawable.input_rounded);
		etCode.setPadding(24, 16, 24, 16);
		etCode.setInputType(
				android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
		LinearLayout.LayoutParams ecp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 56);
		ecp.bottomMargin = 12;
		etCode.setLayoutParams(ecp);
		card.addView(etCode);

		Button btnJoin = new Button(this);
		btnJoin.setText("REJOINDRE UN FOYER");
		btnJoin.setTextColor(Color.parseColor("#0F172A"));
		btnJoin.setTextSize(13f);
		btnJoin.setTypeface(null, Typeface.BOLD);
		btnJoin.setBackgroundResource(R.drawable.btn_rounded_light);
		btnJoin.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52));
		btnJoin.setOnClickListener(v -> joinHousehold(btnJoin));
		card.addView(btnJoin);
	}

	private void registerSelfAsMember() {
		String myName = AuthManager.getInstance().getDisplayName();

		if (myName == null || myName.isEmpty() || myName.equals("Moi"))
			return;

		PersonManager.getInstance().addPerson(myName, new FirestoreManager.Callback() {
			public void onSuccess(String r) {
			}

			public void onError(String e) {
			}
		});
	}

	private void createHousehold(Button btn) {
		if (savedToken == null || savedToken.isEmpty()) {
			AppToast.error(this, "Session expirée — reconnecte-toi");
			startActivity(new Intent(this, LoginActivity.class));
			return;
		}
		btn.setEnabled(false);
		btn.setText("Création...");
		HouseholdManager.getInstance().createHouseholdWithToken(savedToken, savedUserId,
				new HouseholdManager.Callback() {
					public void onSuccess(String code) {
						runOnUiThread(() -> {
							AppToast.success(HouseholdActivity.this, "✓ Foyer créé ! Code : " + code);
							registerSelfAsMember();
							goToDashboard();
						});
					}

					public void onError(String error) {
						runOnUiThread(() -> {
							btn.setEnabled(true);
							btn.setText("CRÉER UN NOUVEAU FOYER");
							AppToast.error(HouseholdActivity.this, error);
						});
					}
				});
	}

	private void joinHousehold(Button btn) {
		String code = etCode.getText().toString().trim().toUpperCase();
		if (code.length() != 6) {
			AppToast.error(this, "Le code doit faire 6 caractères");
			return;
		}
		if (savedToken == null || savedToken.isEmpty()) {
			AppToast.error(this, "Session expirée — reconnecte-toi");
			startActivity(new Intent(this, LoginActivity.class));
			return;
		}
		btn.setEnabled(false);
		btn.setText("Connexion...");
		HouseholdManager.getInstance().joinHouseholdWithToken(savedToken, code, new HouseholdManager.Callback() {
			public void onSuccess(String id) {
				runOnUiThread(() -> {
					AppToast.success(HouseholdActivity.this, "✓ Foyer rejoint !");
					registerSelfAsMember();
					goToDashboard();
				});
			}

			public void onError(String error) {
				runOnUiThread(() -> {
					btn.setEnabled(true);
					btn.setText("REJOINDRE UN FOYER");
					AppToast.error(HouseholdActivity.this, error);
				});
			}
		});
	}

	private void goToDashboard() {
		Intent intent = new Intent(this, DashboardActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		finish();
	}
}