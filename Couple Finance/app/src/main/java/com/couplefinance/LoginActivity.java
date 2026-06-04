package com.couplefinance;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

import com.couplefinance.core.theme.ThemeColors;
import com.couplefinance.core.theme.ThemeManager;
import com.couplefinance.core.ui.DS;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.PersonManager;
import com.couplefinance.models.UserProfile;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.couplefinance.ui.DashboardActivity;
import com.couplefinance.ui.bank.BankConnectionView;

import java.util.Locale;

/**
 * LoginActivity — Refonte complète premium.
 *
 * 3 étapes :
 *   STEP_LOGIN     → Connexion
 *   STEP_REGISTER  → Création de compte
 *   STEP_HOUSEHOLD → Configuration du foyer (créer ou rejoindre)
 *
 * Design :
 *  • Fond dégradé primary → primaryDark en haut (hero banner)
 *  • Card blanche centrée (max 480dp, responsive téléphone)
 *  • Illustration Canvas dans le hero
 *  • Thème dynamique complet (ThemeColors)
 *  • Transitions fade + slide entre étapes
 *  • Skeleton loading sur le bouton principal
 *  • Anti-double-tap (bouton désactivé pendant la requête)
 */
public class LoginActivity extends Activity {

	// ── États ────────────────────────────────────────────────────
	private static final int STEP_LOGIN = 0;
	private static final int STEP_REGISTER = 1;
	private static final int STEP_HOUSEHOLD = 2;

	private int currentStep = STEP_LOGIN;

	// ── Auth state ────────────────────────────────────────────────
	private String currentToken;
	private String currentUserId;
	private static String SAVED_TOKEN = null;
	private static String SAVED_USER_ID = null;

	// ── UI ────────────────────────────────────────────────────────
	private ScrollView scrollView;
	private LinearLayout rootContainer;
	private LinearLayout heroSection;
	private LinearLayout card;
	private TextView registerButton;

	// Login fields
	private EditText etEmail, etPassword;
	private CheckBox cbRemember;
	private boolean passwordVisible = false;

	// Register fields
	private EditText etFirstName, etEmailReg, etPasswordReg;
	private String selectedColor = "#C86B4A";
	private String selectedAvatar = "fox";
	private UserProfile pendingProfile;
	private boolean passwordVisibleReg = false;

	// Household
	private EditText etCode;

	// ─────────────────────────────────────────────────────────────
	// Lifecycle
	// ─────────────────────────────────────────────────────────────

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		AuthManager.getInstance().init(this);
		HouseholdManager.getInstance().init(this);

		boolean forceHousehold = getIntent().getBooleanExtra("show_household_screen", false);
		SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
		boolean remember = prefs.getBoolean("remember", false);

		if (remember && AuthManager.getInstance().isLoggedIn()) {
			goToDashboard();
			return;
		}

		if (AuthManager.getInstance().isLoggedIn()) {
			String uid = AuthManager.getInstance().getUserId();
			if (uid != null)
				UserRepository.getInstance().loadUser(uid, null);
			routeHouseholdOrRestore(forceHousehold);
			return;
		}

		buildRootLayout();
		showLoginStep();
	}

	/**
	 * Route après connexion :
	 *  - foyer présent localement -> Dashboard
	 *  - sinon, tente de RESTAURER le foyer depuis le serveur (users/{uid}.householdId)
	 *    avant de proposer d'en créer/rejoindre un. Évite la perte de foyer à la reconnexion.
	 */
	private void routeHouseholdOrRestore(boolean forceHousehold) {
		if (forceHousehold) { showHouseholdStep(); return; }
		if (HouseholdManager.getInstance().hasHousehold()) { goToDashboard(); return; }

		HouseholdManager.getInstance().restoreHousehold(new HouseholdManager.Callback() {
			@Override public void onSuccess(String response) {
				runOnUiThread(() -> goToDashboard());
			}
			@Override public void onError(String error) {
				runOnUiThread(() -> showHouseholdStep());
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Layout racine — construit une seule fois
	// ─────────────────────────────────────────────────────────────

	private void buildRootLayout() {
		// ScrollView racine
		scrollView = new ScrollView(this);
		scrollView.setFillViewport(true);
		scrollView.setBackgroundColor(ThemeColors.background());
		scrollView.setVerticalScrollBarEnabled(false);

		// Container principal vertical
		rootContainer = new LinearLayout(this);
		rootContainer.setOrientation(LinearLayout.VERTICAL);
		rootContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

		// ── Hero banner (dégradé + illustration) ─────────────────
		heroSection = buildHero();
		rootContainer.addView(heroSection);

		// ── Card blanche ──────────────────────────────────────────
		card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);

		// Card responsive : max 480dp sur tablette, pleine largeur sur téléphone
		int screenDp = (int) (getResources().getDisplayMetrics().widthPixels
				/ getResources().getDisplayMetrics().density);
		int cardWidth = screenDp >= 600 ? DS.dp(this, 480) : -1;

		LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardWidth, -2);
		cardLp.gravity = Gravity.CENTER_HORIZONTAL;
		cardLp.setMargins(DS.dp(this, screenDp >= 600 ? 0 : 0), 0, 0, DS.dp(this, 40));

		GradientDrawable cardBg = new GradientDrawable();
		cardBg.setColor(ThemeColors.card());
		cardBg.setCornerRadii(new float[] { 0, 0, 0, 0, // coins haut (pas arrondis)
				DS.dp(this, DS.R_XL), DS.dp(this, DS.R_XL), // bas droite
				DS.dp(this, DS.R_XL), DS.dp(this, DS.R_XL) // bas gauche
		});
		cardBg.setStroke(DS.dp(this, 1), ThemeColors.border());
		card.setBackground(cardBg);
		card.setPadding(DS.dp(this, 28), DS.dp(this, 32), DS.dp(this, 28), DS.dp(this, 32));
		card.setElevation(DS.dp(this, 8));
		card.setLayoutParams(cardLp);

		rootContainer.addView(card);
		scrollView.addView(rootContainer);
		setContentView(scrollView);
	}

	// ─────────────────────────────────────────────────────────────
	// Hero banner
	// ─────────────────────────────────────────────────────────────

	private void refreshRegisterForTheme() {
		String fn = etFirstName != null ? etFirstName.getText().toString() : null;
		String em = etEmailReg != null ? etEmailReg.getText().toString() : null;
		String pw = etPasswordReg != null ? etPasswordReg.getText().toString() : null;
		showRegisterStep();
		if (fn != null && etFirstName != null) etFirstName.setText(fn);
		if (em != null && etEmailReg != null) etEmailReg.setText(em);
		if (pw != null && etPasswordReg != null) etPasswordReg.setText(pw);
		applyLivePreview();
	}

	private void applyLivePreview() {
		if (heroSection != null) {
			GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
					new int[] { ThemeColors.primary(), ThemeColors.primaryDark() });
			heroSection.setBackground(g);
		}
		if (registerButton != null && registerButton.getBackground() instanceof GradientDrawable) {
			((GradientDrawable) registerButton.getBackground()).setColor(ThemeColors.primary());
		}
	}

	private LinearLayout buildHero() {
		LinearLayout hero = new LinearLayout(this);
		hero.setOrientation(LinearLayout.VERTICAL);
		hero.setGravity(Gravity.CENTER_HORIZONTAL);
		hero.setPadding(DS.dp(this, 24), DS.dp(this, 56), DS.dp(this, 24), DS.dp(this, 32));

		// Fond dégradé
		GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
				new int[] { ThemeColors.primary(), ThemeColors.primaryDark() });
		hero.setBackground(heroBg);

		LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, DS.dp(this, 220));
		hero.setLayoutParams(heroLp);

		// Logo + nom
		LinearLayout logoRow = new LinearLayout(this);
		logoRow.setOrientation(LinearLayout.HORIZONTAL);
		logoRow.setGravity(Gravity.CENTER_VERTICAL);

		// Logo maison-cœur (silhouette blanche) sur le fond terracotta
		ImageView logo = new ImageView(this);
		int logoRes = getResources().getIdentifier("ic_stat_sync", "drawable", getPackageName());
		if (logoRes != 0) logo.setImageResource(logoRes);
		logo.setColorFilter(Color.WHITE);
		LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(DS.dp(this, 46), DS.dp(this, 46));
		logoLp.rightMargin = DS.dp(this, 12);
		logo.setLayoutParams(logoLp);
		logoRow.addView(logo);

		LinearLayout brandCol = new LinearLayout(this);
		brandCol.setOrientation(LinearLayout.VERTICAL);

		TextView tvBrand = new TextView(this);
		tvBrand.setText("CoupleFinance");
		tvBrand.setTextSize(18f);
		tvBrand.setTextColor(Color.WHITE);
		tvBrand.setTypeface(null, Typeface.BOLD);
		brandCol.addView(tvBrand);

		TextView tvBrandSub = new TextView(this);
		tvBrandSub.setText("FINANCES À DEUX");
		tvBrandSub.setTextSize(8f);
		tvBrandSub.setTextColor(ThemeColors.withAlpha(Color.WHITE, 180));
		tvBrandSub.setLetterSpacing(0.12f);
		brandCol.addView(tvBrandSub);

		logoRow.addView(brandCol);

		LinearLayout.LayoutParams logoRowLp = new LinearLayout.LayoutParams(-2, -2);
		logoRowLp.bottomMargin = DS.dp(this, 24);
		hero.addView(logoRow, logoRowLp);

		// Illustration Canvas — deux cercles reliés (symbolise le couple)
		CoupleIllustrationView illustration = new CoupleIllustrationView(this);
		hero.addView(illustration, new LinearLayout.LayoutParams(DS.dp(this, 120), DS.dp(this, 80)));

		return hero;
	}

	// ─────────────────────────────────────────────────────────────
	// STEP LOGIN
	// ─────────────────────────────────────────────────────────────

	private void showLoginStep() {
		currentStep = STEP_LOGIN;
		card.removeAllViews();

		// Badge étape
		card.addView(stepBadge("CONNEXION", "1/3"));

		// Titre
		card.addView(sectionTitle("Content de vous revoir", true));
		card.addView(sectionSubtitle("Gérez votre budget à deux, ensemble."));

		addDivider(card);

		// Email
		card.addView(fieldLabel("Adresse e-mail"));
		etEmail = premiumInput("marie@exemple.fr", false);
		card.addView(etEmail);
		addFieldSpacer(card);

		// Mot de passe + toggle
		LinearLayout pwRow = new LinearLayout(this);
		pwRow.setOrientation(LinearLayout.HORIZONTAL);
		pwRow.setGravity(Gravity.CENTER_VERTICAL);
		card.addView(pwRow);

		TextView tvPwLabel = fieldLabel("Mot de passe");
		pwRow.addView(tvPwLabel, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView tvForgot = new TextView(this);
		tvForgot.setText("Mot de passe oublié ?");
		tvForgot.setTextSize(DS.TEXT_XS);
		tvForgot.setTextColor(ThemeColors.primary());
		tvForgot.setTypeface(null, Typeface.BOLD);
		pwRow.addView(tvForgot);

		etPassword = premiumInput("••••••••", true);
		card.addView(etPassword);

		// Voir/Masquer
		card.addView(buildPasswordToggle(false));
		addFieldSpacer(card);

		// Se souvenir
		LinearLayout rememberRow = new LinearLayout(this);
		rememberRow.setOrientation(LinearLayout.HORIZONTAL);
		rememberRow.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams rrLp = new LinearLayout.LayoutParams(-1, -2);
		rrLp.bottomMargin = DS.dp(this, 20);
		rememberRow.setLayoutParams(rrLp);

		cbRemember = new CheckBox(this);
		cbRemember.setTextColor(ThemeColors.subtext());
		cbRemember.setText("Se souvenir de moi");
		cbRemember.setTextSize(DS.TEXT_SM);
		try {
			cbRemember.setButtonTintList(android.content.res.ColorStateList.valueOf(ThemeColors.primary()));
		} catch (Exception ignored) {
		}
		rememberRow.addView(cbRemember);
		card.addView(rememberRow);

		// Bouton principal
		TextView btnLogin = primaryButton("SE CONNECTER →");
		btnLogin.setOnClickListener(v -> handleLogin(btnLogin));
		card.addView(btnLogin);

		// Lien inscription
		card.addView(buildToggleLink("Pas encore de compte ? ", "Créer un foyer",
				() -> animateToStep(() -> showRegisterStep())));

		animateCardIn();
	}

	// ─────────────────────────────────────────────────────────────
	// STEP REGISTER
	// ─────────────────────────────────────────────────────────────

	private void showRegisterStep() {
		currentStep = STEP_REGISTER;
		card.removeAllViews();

		card.addView(stepBadge("NOUVEAU COMPTE", "1/4"));
		card.addView(sectionTitle("Rejoignez votre foyer", false));
		card.addView(sectionSubtitle("Créez votre compte pour commencer."));

		addDivider(card);

		// Prénom
		card.addView(fieldLabel("Votre prénom"));
		etFirstName = premiumInput("Thomas", false);
		card.addView(etFirstName);
		addFieldSpacer(card);

		// Email
		card.addView(fieldLabel("Adresse e-mail"));
		etEmailReg = premiumInput("thomas@exemple.fr", false);
		card.addView(etEmailReg);
		addFieldSpacer(card);

		// Mot de passe
		card.addView(fieldLabel("Mot de passe (8 caractères min.)"));
		etPasswordReg = premiumInput("••••••••", true);
		card.addView(etPasswordReg);
		card.addView(buildPasswordToggle(true));

		// ── Indicateur de force (couleurs du thème, temps réel) ──
		LinearLayout strengthRow = new LinearLayout(this);
		strengthRow.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(-1, DS.dp(this, 6));
		srLp.topMargin = DS.dp(this, 12);
		strengthRow.setLayoutParams(srLp);
		final int segTrack = ThemeColors.withAlpha(ThemeColors.subtext(), 45);
		final View[] segs = new View[4];
		for (int i = 0; i < 4; i++) {
			View seg = new View(this);
			GradientDrawable g = new GradientDrawable();
			g.setColor(segTrack);
			g.setCornerRadius(DS.dp(this, 3));
			seg.setBackground(g);
			LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, -1, 1f);
			if (i > 0) sLp.leftMargin = DS.dp(this, 6);
			strengthRow.addView(seg, sLp);
			segs[i] = seg;
		}
		card.addView(strengthRow);

		final TextView strengthLabel = new TextView(this);
		strengthLabel.setText("Sécurité du mot de passe");
		strengthLabel.setTextColor(ThemeColors.subtext());
		strengthLabel.setTextSize(DS.TEXT_XS);
		LinearLayout.LayoutParams slLp = new LinearLayout.LayoutParams(-1, -2);
		slLp.topMargin = DS.dp(this, 6);
		strengthLabel.setLayoutParams(slLp);
		card.addView(strengthLabel);

		etPasswordReg.addTextChangedListener(new android.text.TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(android.text.Editable e) {
				String pw = e.toString();
				int score = passwordScore(pw);
				int color; String label;
				if (score <= 1)      { color = ThemeColors.danger();  label = "Faible"; }
				else if (score == 2) { color = ThemeColors.warning(); label = "Moyen"; }
				else if (score == 3) { color = ThemeColors.primary(); label = "Bon"; }
				else                 { color = ThemeColors.success(); label = "Fort"; }
				for (int i = 0; i < 4; i++) {
					GradientDrawable g = new GradientDrawable();
					g.setCornerRadius(DS.dp(LoginActivity.this, 3));
					g.setColor(i < score ? color : segTrack);
					segs[i].setBackground(g);
				}
				if (pw.isEmpty()) {
					strengthLabel.setText("Sécurité du mot de passe");
					strengthLabel.setTextColor(ThemeColors.subtext());
				} else {
					strengthLabel.setText("Sécurité : " + label);
					strengthLabel.setTextColor(color);
				}
			}
		});

		addFieldSpacer(card);

		// Couleur avatar
		card.addView(fieldLabel("Votre couleur dans le foyer"));
		card.addView(buildColorPicker());
		addFieldSpacer(card);

		// Bouton principal
		TextView btnRegister = primaryButton("CRÉER MON COMPTE →");
		registerButton = btnRegister;
		btnRegister.setOnClickListener(v -> handleRegister(btnRegister));
		card.addView(btnRegister);

		// Réassurance (touche pro)
		TextView reassure = new TextView(this);
		reassure.setText("Vos données restent privées et chiffrées.");
		reassure.setTextColor(ThemeColors.subtext());
		reassure.setTextSize(DS.TEXT_XS);
		reassure.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams reassureLp = new LinearLayout.LayoutParams(-1, -2);
		reassureLp.topMargin = DS.dp(this, 12);
		reassureLp.bottomMargin = DS.dp(this, 4);
		card.addView(reassure, reassureLp);

		// Lien connexion
		card.addView(buildToggleLink("Déjà un compte ? ", "Se connecter", () -> animateToStep(() -> showLoginStep())));

		animateCardIn();
	}

	// ─────────────────────────────────────────────────────────────
	// STEP PERSONNALISATION (choix de l'avatar animal)
	// ─────────────────────────────────────────────────────────────

	private static final String[] AVATARS = {
			"fox", "cat", "panda", "bear", "bunny", "koala", "dog", "penguin"
	};

	private void showPersonalizationStep() {
		currentStep = STEP_REGISTER;
		if (card == null) buildRootLayout();
		card.removeAllViews();

		card.addView(stepBadge("PERSONNALISATION", "2/4"));
		card.addView(sectionTitle("Choisissez votre animal", false));
		card.addView(sectionSubtitle("Il vous représentera dans le foyer."));
		addDivider(card);

		final View[] tiles = new View[AVATARS.length];

		// Grille 4 colonnes
		LinearLayout grid = new LinearLayout(this);
		grid.setOrientation(LinearLayout.VERTICAL);
		LinearLayout rowL = null;
		for (int i = 0; i < AVATARS.length; i++) {
			if (i % 4 == 0) {
				rowL = new LinearLayout(this);
				rowL.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
				rLp.bottomMargin = DS.dp(this, 10);
				grid.addView(rowL, rLp);
			}
			final int idx = i;
			final String animal = AVATARS[i];

			LinearLayout cell = new LinearLayout(this);
			cell.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, -2, 1f);
			cell.setLayoutParams(cellLp);

			FrameLayout tile = new FrameLayout(this);
			GradientDrawable tileBg = new GradientDrawable();
			tileBg.setShape(GradientDrawable.OVAL);
			tileBg.setColor(ThemeColors.surface());
			tileBg.setStroke(DS.dp(this, 3),
					animal.equals(selectedAvatar) ? Color.parseColor(selectedColor) : ThemeColors.border());
			tile.setBackground(tileBg);

			ImageView img = new ImageView(this);
			int res = getResources().getIdentifier("avatar_" + animal, "drawable", getPackageName());
			if (res != 0) img.setImageResource(res);
			int pad = DS.dp(this, 4);
			img.setPadding(pad, pad, pad, pad);
			tile.addView(img, new FrameLayout.LayoutParams(-1, -1));

			int sz = DS.dp(this, 70);
			LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(sz, sz);
			tile.setLayoutParams(tLp);
			tiles[idx] = tile;

			tile.setOnClickListener(v -> {
				selectedAvatar = animal;
				for (int k = 0; k < tiles.length; k++) {
					GradientDrawable g = new GradientDrawable();
					g.setShape(GradientDrawable.OVAL);
					g.setColor(ThemeColors.surface());
					g.setStroke(DS.dp(this, 3),
							k == idx ? Color.parseColor(selectedColor) : ThemeColors.border());
					tiles[k].setBackground(g);
				}
			});
			cell.addView(tile);
			rowL.addView(cell);
		}
		// Compléter la dernière rangée si incomplète (alignement)
		if (AVATARS.length % 4 != 0 && rowL != null) {
			for (int k = AVATARS.length % 4; k < 4; k++) {
				View spacer = new View(this);
				LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
				int m = DS.dp(this, 5); spLp.leftMargin = m; spLp.rightMargin = m;
				rowL.addView(spacer, spLp);
			}
		}
		card.addView(grid);
		addFieldSpacer(card);

		TextView btnNext = primaryButton("CONTINUER →");
		btnNext.setOnClickListener(v -> {
			if (pendingProfile != null) {
				pendingProfile.avatar = selectedAvatar;
				pendingProfile.color = selectedColor;
				UserSession.getInstance().setUser(pendingProfile);
				UserRepository.getInstance().saveUser(pendingProfile);
			}
			AuthManager.getInstance().setLocalAvatar(selectedAvatar);
			animateToStep(() -> showHouseholdStep());
		});
		card.addView(btnNext);

		animateCardIn();
	}

	// ─────────────────────────────────────────────────────────────
	// STEP HOUSEHOLD
	// ─────────────────────────────────────────────────────────────

	private void showHouseholdStep() {
		currentStep = STEP_HOUSEHOLD;

		if (card == null) {
			buildRootLayout();
		}

		card.removeAllViews();
		card.setVisibility(View.VISIBLE);

		card.addView(stepBadge("CONFIGURATION FOYER", "3/4"));
		card.addView(sectionTitle("Votre foyer commun", false));
		card.addView(sectionSubtitle("Créez ou rejoignez le foyer de votre partenaire."));

		addDivider(card);

		// ── Card Créer ────────────────────────────────────────────
		LinearLayout createCard = buildOptionCard("🏠", "Nouveau foyer",
				"Vous inviterez votre partenaire avec un code unique.");
		card.addView(createCard);

		TextView btnCreate = primaryButton("Créer un nouveau foyer →");
		LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(-1, DS.dp(this, DS.BTN_HEIGHT));
		bcLp.topMargin = DS.dp(this, 14);
		btnCreate.setLayoutParams(bcLp);
		btnCreate.setOnClickListener(v -> doCreateHousehold(btnCreate));
		card.addView(btnCreate);

		// ── Séparateur OU ─────────────────────────────────────────
		card.addView(buildOrSeparator());

		// ── Card Rejoindre ────────────────────────────────────────
		LinearLayout joinCard = buildOptionCard("🔗", "Rejoindre un foyer",
				"Entrez le code partagé par votre partenaire.");
		card.addView(joinCard);

		etCode = premiumInput("XXXXXX", false);
		etCode.setInputType(
				android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
		etCode.setGravity(Gravity.CENTER);
		etCode.setTypeface(null, Typeface.BOLD);
		etCode.setTextSize(22f);
		etCode.setLetterSpacing(0.18f);
		LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(-1, DS.dp(this, 64));
		codeLp.topMargin = DS.dp(this, 14);
		etCode.setLayoutParams(codeLp);
		card.addView(etCode);

		TextView btnJoin = secondaryButton("Rejoindre un foyer existant");
		LinearLayout.LayoutParams bjLp = new LinearLayout.LayoutParams(-1, DS.dp(this, DS.BTN_HEIGHT));
		bjLp.topMargin = DS.dp(this, 12);
		btnJoin.setLayoutParams(bjLp);
		btnJoin.setOnClickListener(v -> doJoinHousehold(btnJoin));
		card.addView(btnJoin);

		animateCardIn();
	}

	// ─────────────────────────────────────────────────────────────
	// Actions Auth
	// ─────────────────────────────────────────────────────────────
	private void handleLogin(TextView btn) {
		String email = etEmail != null ? etEmail.getText().toString().trim() : "";
		String password = etPassword != null ? etPassword.getText().toString().trim() : "";

		if (email.isEmpty() || password.isEmpty()) {
			AppToast.error(this, "Remplis tous les champs");
			return;
		}

		setButtonLoading(btn, "Connexion...");

		AuthManager.getInstance().login(email, password, new AuthManager.Callback() {
			public void onSuccess(String token, String userId) {
				currentToken = token;
				currentUserId = userId;
				SAVED_TOKEN = token;
				SAVED_USER_ID = userId;

				if (cbRemember != null && cbRemember.isChecked()) {
					getSharedPreferences("auth_prefs", MODE_PRIVATE).edit().putBoolean("remember", true).apply();
				}

				final boolean[] continued = { false };

				Runnable safeContinue = () -> {
					if (continued[0])
						return;
					continued[0] = true;
					runOnUiThread(() -> continueAfterLogin(btn));
				};

				try {
					UserRepository.getInstance().loadUser(userId, new UserRepository.OnUserLoaded() {
						public void onLoaded(UserProfile profile) {
							safeContinue.run();
						}
					});

					new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(safeContinue, 1500);

				} catch (Exception e) {
					safeContinue.run();
				}
			}

			public void onError(String error) {
				runOnUiThread(() -> {
					setButtonNormal(btn, "SE CONNECTER →");
					AppToast.error(LoginActivity.this, error);
					shakeCard();
				});
			}
		});
	}

	private void continueAfterLogin(TextView btn) {
		setButtonNormal(btn, "SE CONNECTER →");

		String displayName = UserSession.getInstance().getNameOrFallback();
		if (displayName == null || displayName.trim().isEmpty() || displayName.contains("@")
				|| displayName.contains(".")) {
			displayName = AuthManager.getInstance().getDisplayName();
		}

		if (displayName != null && !displayName.trim().isEmpty()) {
			AuthManager.getInstance().setDisplayName(displayName.trim());
		}

		HouseholdManager.getInstance().init(LoginActivity.this);

		if (HouseholdManager.getInstance().hasHousehold()) {
			goToDashboard();
		} else {
			// Pas de foyer en local : tenter de le restaurer depuis le serveur
			// (users/{uid}.householdId) avant de proposer d'en créer un.
			HouseholdManager.getInstance().restoreHousehold(new HouseholdManager.Callback() {
				@Override public void onSuccess(String response) {
					runOnUiThread(() -> goToDashboard());
				}
				@Override public void onError(String error) {
					runOnUiThread(() -> animateToStep(() -> showHouseholdStep()));
				}
			});
		}
	}

	private void handleRegister(TextView btn) {
		String firstName = etFirstName != null ? etFirstName.getText().toString().trim() : "";
		String email = etEmailReg != null ? etEmailReg.getText().toString().trim() : "";
		String password = etPasswordReg != null ? etPasswordReg.getText().toString().trim() : "";

		if (firstName.isEmpty() || email.isEmpty() || password.isEmpty()) {
			AppToast.error(this, "Remplis tous les champs");
			return;
		}
		if (password.length() < 8) {
			AppToast.error(this, "Mot de passe : 8 caractères minimum");
			return;
		}

		setButtonLoading(btn, "Création...");

		AuthManager.getInstance().register(email, password, firstName, new AuthManager.Callback() {
			public void onSuccess(String token, String userId) {
				currentToken = token;
				currentUserId = userId;
				SAVED_TOKEN = token;
				SAVED_USER_ID = userId;

				AuthManager.getInstance().setDisplayName(firstName);

				UserProfile profile = new UserProfile(userId, firstName, email, selectedColor,
						System.currentTimeMillis());
				profile.avatar = selectedAvatar;
				pendingProfile = profile;
				UserSession.getInstance().setUser(profile);
				UserRepository.getInstance().saveUser(profile);

				runOnUiThread(() -> {
					setButtonNormal(btn, "CRÉER MON COMPTE →");
					AppToast.success(LoginActivity.this, "✓ Compte créé !");
					animateToStep(() -> showPersonalizationStep());
				});
			}

			public void onError(String error) {
				runOnUiThread(() -> {
					setButtonNormal(btn, "CRÉER MON COMPTE →");
					AppToast.error(LoginActivity.this, error);
					shakeCard();
				});
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// Actions Foyer
	// ─────────────────────────────────────────────────────────────

	private void doCreateHousehold(TextView btn) {
		if (currentToken == null)
			currentToken = SAVED_TOKEN;
		if (currentUserId == null)
			currentUserId = SAVED_USER_ID;

		if (currentToken == null || currentToken.isEmpty()) {
			AppToast.error(this, "Erreur de session");
			return;
		}

		setButtonLoading(btn, "Création...");

		HouseholdManager.getInstance().createHouseholdWithToken(currentToken, currentUserId,
				new HouseholdManager.Callback() {
					public void onSuccess(String code) {
						runOnUiThread(() -> {
							registerSelfAsMember();
							AppToast.success(LoginActivity.this, "✓ Foyer créé ! Code : " + code);
							animateToStep(() -> showBankStep());
						});
					}

					public void onError(String error) {
						runOnUiThread(() -> {
							setButtonNormal(btn, "Créer un nouveau foyer →");
							AppToast.error(LoginActivity.this, error);
						});
					}
				});
	}

	private void doJoinHousehold(TextView btn) {
		if (currentToken == null)
			currentToken = SAVED_TOKEN;
		if (currentUserId == null)
			currentUserId = SAVED_USER_ID;
		if (etCode == null)
			return;

		String code = etCode.getText().toString().trim().toUpperCase(Locale.ROOT);
		if (code.length() != 6) {
			AppToast.error(this, "Le code doit faire 6 caractères");
			return;
		}
		if (currentToken == null || currentToken.isEmpty()) {
			AppToast.error(this, "Erreur de session");
			return;
		}

		setButtonLoading(btn, "Connexion...");

		HouseholdManager.getInstance().joinHouseholdWithToken(currentToken, code, new HouseholdManager.Callback() {
			public void onSuccess(String id) {
				runOnUiThread(() -> {
					registerSelfAsMember();
					AppToast.success(LoginActivity.this, "✓ Foyer rejoint !");
					animateToStep(() -> showBankStep());
				});
			}

			public void onError(String error) {
				runOnUiThread(() -> {
					setButtonNormal(btn, "Rejoindre un foyer existant");
					AppToast.error(LoginActivity.this, error);
				});
			}
		});
	}

	private void registerSelfAsMember() {
		String name = AuthManager.getInstance().getDisplayName();
		if (name == null || name.isEmpty() || name.equals("Moi"))
			return;
		PersonManager.getInstance().addPerson(name, new FirestoreManager.Callback() {
			public void onSuccess(String r) {
			}

			public void onError(String e) {
			}
		});
	}

	// ─────────────────────────────────────────────────────────────
	// STEP CONNEXION BANCAIRE (optionnel) — 4/4
	// ─────────────────────────────────────────────────────────────

	private void showBankStep() {
		currentStep = STEP_HOUSEHOLD;
		if (card == null) buildRootLayout();
		card.removeAllViews();

		card.addView(stepBadge("CONNEXION BANCAIRE", "4/4"));
		card.addView(sectionTitle("Connectez votre banque", false));
		card.addView(sectionSubtitle(
				"Synchronisez automatiquement vos soldes et opérations. C'est optionnel — vous pourrez le faire plus tard depuis les Paramètres."));
		addDivider(card);
		addFieldSpacer(card);

		TextView btnConnect = primaryButton("CONNECTER MA BANQUE");
		btnConnect.setOnClickListener(v -> {
			try {
				BankConnectionView.show(LoginActivity.this, new java.util.ArrayList<>(),
						() -> goToDashboard());
			} catch (Exception e) {
				AppToast.error(LoginActivity.this, "Connexion bancaire indisponible pour le moment");
			}
		});
		card.addView(btnConnect);

		card.addView(buildToggleLink("Vous préférez attendre ? ", "Plus tard", () -> goToDashboard()));

		animateCardIn();
	}

	private void goToDashboard() {
		Intent intent = new Intent(this, DashboardActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		finish();
	}

	// ─────────────────────────────────────────────────────────────
	// Builders UI
	// ─────────────────────────────────────────────────────────────

	private View stepBadge(String label, String step) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = DS.dp(this, 16);
		row.setLayoutParams(lp);

		TextView tvLabel = new TextView(this);
		tvLabel.setText(label);
		tvLabel.setTextColor(ThemeColors.primary());
		tvLabel.setTextSize(DS.TEXT_XS);
		tvLabel.setTypeface(null, Typeface.BOLD);
		tvLabel.setLetterSpacing(0.10f);
		row.addView(tvLabel, new LinearLayout.LayoutParams(0, -2, 1f));

		TextView tvStep = new TextView(this);
		tvStep.setText(step);
		tvStep.setTextColor(ThemeColors.subtext());
		tvStep.setTextSize(DS.TEXT_XS);
		row.addView(tvStep);

		// Barre de progression des étapes
		LinearLayout progressBar = new LinearLayout(this);
		progressBar.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(-1, DS.dp(this, 3));
		pbLp.topMargin = DS.dp(this, 8);

		int[] colors = { ThemeColors.primary(), ThemeColors.withAlpha(ThemeColors.primary(), 60),
				ThemeColors.withAlpha(ThemeColors.primary(), 30) };

		LinearLayout wrapper = new LinearLayout(this);
		wrapper.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(-1, -2);
		wLp.bottomMargin = DS.dp(this, 20);
		wrapper.setLayoutParams(wLp);
		wrapper.addView(row);

		LinearLayout pb = new LinearLayout(this);
		pb.setOrientation(LinearLayout.HORIZONTAL);
		int steps = 3;
		for (int i = 0; i < steps; i++) {
			View seg = new View(this);
			int activeStep = step.startsWith("1") ? 0 : step.startsWith("2") ? 1 : 2;
			GradientDrawable segBg = new GradientDrawable();
			segBg.setColor(i <= activeStep ? ThemeColors.primary() : ThemeColors.withAlpha(ThemeColors.primary(), 30));
			segBg.setCornerRadius(DS.dp(this, 2));
			seg.setBackground(segBg);
			LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(0, DS.dp(this, 3), 1f);
			if (i > 0)
				segLp.leftMargin = DS.dp(this, 4);
			pb.addView(seg, segLp);
		}
		wrapper.addView(pb);

		return wrapper;
	}

	private TextView sectionTitle(String text, boolean isMain) {
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setTextColor(ThemeColors.text());
		tv.setTextSize(isMain ? DS.TEXT_TITLE : DS.TEXT_SECTION);
		tv.setTypeface(Typeface.create(isMain ? Typeface.SERIF : Typeface.DEFAULT, Typeface.BOLD));
		tv.setIncludeFontPadding(false);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = DS.dp(this, 6);
		tv.setLayoutParams(lp);
		return tv;
	}

	private TextView sectionSubtitle(String text) {
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setTextColor(ThemeColors.subtext());
		tv.setTextSize(DS.TEXT_SM);
		tv.setLineSpacing(3f, 1f);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.bottomMargin = DS.dp(this, 4);
		tv.setLayoutParams(lp);
		return tv;
	}

	/** Score de force du mot de passe : 0 (vide) à 4 (fort). */
	private int passwordScore(String pw) {
		if (pw == null || pw.isEmpty()) return 0;
		int score = 0;
		if (pw.length() >= 8)  score++;
		if (pw.length() >= 12) score++;
		boolean lower = false, upper = false, digit = false, special = false;
		for (int i = 0; i < pw.length(); i++) {
			char ch = pw.charAt(i);
			if (Character.isLowerCase(ch))      lower = true;
			else if (Character.isUpperCase(ch)) upper = true;
			else if (Character.isDigit(ch))     digit = true;
			else                                special = true;
		}
		int variety = (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
		if (variety >= 2) score++;
		if (variety >= 4) score++;
		return Math.min(score, 4);
	}

	private TextView fieldLabel(String text) {
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setTextColor(ThemeColors.subtext());
		tv.setTextSize(DS.TEXT_XS);
		tv.setTypeface(null, Typeface.BOLD);
		tv.setLetterSpacing(0.06f);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(this, 4);
		lp.bottomMargin = DS.dp(this, 6);
		tv.setLayoutParams(lp);
		return tv;
	}

	private EditText premiumInput(String hint, boolean isPassword) {
		EditText et = new EditText(this);
		et.setHint(hint);
		et.setHintTextColor(ThemeColors.muted());
		et.setTextColor(ThemeColors.text());
		et.setTextSize(DS.TEXT_BODY);
		et.setSingleLine(true);
		et.setPadding(DS.dp(this, DS.PAD_INPUT), 0, DS.dp(this, DS.PAD_INPUT), 0);
		et.setBackground(buildInputBg());
		et.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(this, DS.INPUT_HEIGHT)));
		if (isPassword)
			et.setTransformationMethod(PasswordTransformationMethod.getInstance());
		return et;
	}

	private GradientDrawable buildInputBg() {
		GradientDrawable d = new GradientDrawable();
		d.setColor(ThemeColors.backgroundSecondary());
		d.setCornerRadius(DS.dp(this, DS.R_MD));
		d.setStroke(DS.dp(this, 1), ThemeColors.border());
		return d;
	}

	private TextView buildPasswordToggle(boolean isReg) {
		TextView tv = new TextView(this);
		tv.setText("👁  Voir le mot de passe");
		tv.setTextColor(ThemeColors.primary());
		tv.setTextSize(DS.TEXT_XS);
		tv.setTypeface(null, Typeface.BOLD);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
		lp.topMargin = DS.dp(this, 6);
		tv.setLayoutParams(lp);

		tv.setOnClickListener(v -> {
			if (isReg) {
				passwordVisibleReg = !passwordVisibleReg;
				if (etPasswordReg == null)
					return;
				if (passwordVisibleReg) {
					etPasswordReg.setTransformationMethod(null);
					tv.setText("🙈  Masquer");
				} else {
					etPasswordReg.setTransformationMethod(PasswordTransformationMethod.getInstance());
					tv.setText("👁  Voir le mot de passe");
				}
				etPasswordReg.setSelection(etPasswordReg.getText().length());
			} else {
				passwordVisible = !passwordVisible;
				if (etPassword == null)
					return;
				if (passwordVisible) {
					etPassword.setTransformationMethod(null);
					tv.setText("🙈  Masquer");
				} else {
					etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
					tv.setText("👁  Voir le mot de passe");
				}
				etPassword.setSelection(etPassword.getText().length());
			}
		});

		return tv;
	}

	private LinearLayout buildColorPicker() {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);

		String[] colors = { "#C86B4A", "#6FA17D", "#5D8FA3", "#8065B3", "#B96B8C", "#4C9A8A", "#B9834F" };
		String[] names = { "Terracotta", "Sauge", "Bleu", "Lavande", "Rose", "Menthe", "Sable" };

		for (int i = 0; i < colors.length; i++) {
			final String color = colors[i];
			final int idx = i;

			LinearLayout item = new LinearLayout(this);
			item.setOrientation(LinearLayout.VERTICAL);
			item.setGravity(Gravity.CENTER);

			View circle = new View(this);
			GradientDrawable circleBg = new GradientDrawable();
			circleBg.setShape(GradientDrawable.OVAL);
			try {
				circleBg.setColor(Color.parseColor(color));
			} catch (Exception e) {
				circleBg.setColor(ThemeColors.primary());
			}

			// Surligne la pastille correspondant à la couleur choisie
			if (color.equalsIgnoreCase(selectedColor)) {
				circleBg.setStroke(DS.dp(this, 3), ThemeColors.text());
			}
			circle.setBackground(circleBg);

			LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(DS.dp(this, 36), DS.dp(this, 36));
			circle.setLayoutParams(circleLp);
			item.addView(circle);

			LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, -2, 1f);
			item.setLayoutParams(itemLp);

			final GradientDrawable finalBg = circleBg;
			item.setOnClickListener(v -> {
				selectedColor = color;
				try {
					ThemeManager.getInstance().applyThemeByColor(LoginActivity.this, Color.parseColor(color));
				} catch (Exception ignored) {
				}
				refreshRegisterForTheme();
			});

			row.addView(item);
		}

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(this, 4);
		lp.bottomMargin = DS.dp(this, 4);
		row.setLayoutParams(lp);
		return row;
	}

	private LinearLayout buildOptionCard(String emoji, String title, String subtitle) {
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER_VERTICAL);
		card.setPadding(DS.dp(this, 16), DS.dp(this, 14), DS.dp(this, 16), DS.dp(this, 14));

		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.backgroundSecondary());
		bg.setCornerRadius(DS.dp(this, DS.R_MD));
		bg.setStroke(DS.dp(this, 1), ThemeColors.border());
		card.setBackground(bg);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(this, 8);
		card.setLayoutParams(lp);

		TextView tvEmoji = new TextView(this);
		tvEmoji.setText(emoji);
		tvEmoji.setTextSize(24f);
		tvEmoji.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(DS.dp(this, 44), DS.dp(this, 44));
		eLp.rightMargin = DS.dp(this, 14);
		card.addView(tvEmoji, eLp);

		LinearLayout textCol = new LinearLayout(this);
		textCol.setOrientation(LinearLayout.VERTICAL);

		TextView tvTitle = new TextView(this);
		tvTitle.setText(title);
		tvTitle.setTextColor(ThemeColors.text());
		tvTitle.setTextSize(DS.TEXT_BODY);
		tvTitle.setTypeface(null, Typeface.BOLD);
		textCol.addView(tvTitle);

		TextView tvSub = new TextView(this);
		tvSub.setText(subtitle);
		tvSub.setTextColor(ThemeColors.subtext());
		tvSub.setTextSize(DS.TEXT_XS);
		tvSub.setLineSpacing(2f, 1f);
		textCol.addView(tvSub);

		card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));
		return card;
	}

	private View buildOrSeparator() {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(this, 20);
		lp.bottomMargin = DS.dp(this, 20);
		row.setLayoutParams(lp);

		View line1 = new View(this);
		line1.setBackgroundColor(ThemeColors.divider());
		row.addView(line1, new LinearLayout.LayoutParams(0, DS.dp(this, 1), 1f));

		TextView tvOr = new TextView(this);
		tvOr.setText("  OU  ");
		tvOr.setTextSize(DS.TEXT_XS);
		tvOr.setTextColor(ThemeColors.subtext());
		tvOr.setTypeface(null, Typeface.BOLD);
		tvOr.setLetterSpacing(0.08f);
		row.addView(tvOr);

		View line2 = new View(this);
		line2.setBackgroundColor(ThemeColors.divider());
		row.addView(line2, new LinearLayout.LayoutParams(0, DS.dp(this, 1), 1f));

		return row;
	}

	private View buildToggleLink(String prefix, String link, Runnable action) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
		lp.topMargin = DS.dp(this, 20);
		row.setLayoutParams(lp);

		TextView tvPrefix = new TextView(this);
		tvPrefix.setText(prefix);
		tvPrefix.setTextSize(DS.TEXT_SM);
		tvPrefix.setTextColor(ThemeColors.subtext());
		row.addView(tvPrefix);

		TextView tvLink = new TextView(this);
		tvLink.setText(link);
		tvLink.setTextSize(DS.TEXT_SM);
		tvLink.setTextColor(ThemeColors.primary());
		tvLink.setTypeface(null, Typeface.BOLD);
		tvLink.setOnClickListener(v -> {
			if (action != null)
				action.run();
		});
		row.addView(tvLink);

		return row;
	}

	private TextView primaryButton(String text) {
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setTextColor(Color.WHITE);
		tv.setTextSize(DS.TEXT_SM);
		tv.setTypeface(null, Typeface.BOLD);
		tv.setGravity(Gravity.CENTER);
		tv.setLetterSpacing(0.06f);

		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.primary());
		bg.setCornerRadius(DS.dp(this, DS.R_LG));
		tv.setBackground(bg);

		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, DS.dp(this, DS.BTN_HEIGHT));
		lp.topMargin = DS.dp(this, 8);
		tv.setLayoutParams(lp);
		tv.setElevation(DS.dp(this, 3));
		return tv;
	}

	private TextView secondaryButton(String text) {
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setTextColor(ThemeColors.primary());
		tv.setTextSize(DS.TEXT_SM);
		tv.setTypeface(null, Typeface.BOLD);
		tv.setGravity(Gravity.CENTER);

		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ThemeColors.backgroundSecondary());
		bg.setCornerRadius(DS.dp(this, DS.R_LG));
		bg.setStroke(DS.dp(this, 1), ThemeColors.primary());
		tv.setBackground(bg);

		tv.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(this, DS.BTN_HEIGHT)));
		return tv;
	}

	private void addDivider(LinearLayout parent) {
		View div = new View(this);
		div.setBackgroundColor(ThemeColors.divider());
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, DS.dp(this, 1));
		lp.topMargin = DS.dp(this, 16);
		lp.bottomMargin = DS.dp(this, 20);
		parent.addView(div, lp);
	}

	private void addFieldSpacer(LinearLayout parent) {
		View v = new View(this);
		v.setLayoutParams(new LinearLayout.LayoutParams(-1, DS.dp(this, 12)));
		parent.addView(v);
	}

	// ─────────────────────────────────────────────────────────────
	// Animations
	// ─────────────────────────────────────────────────────────────

	private void animateCardIn() {
		card.setAlpha(0f);
		card.setTranslationY(DS.dp(this, 30));
		card.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(new DecelerateInterpolator(1.5f))
				.start();
	}

	private void animateToStep(Runnable buildStep) {
		card.animate().alpha(0f).translationX(-DS.dp(this, 40)).setDuration(200)
				.setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
					card.setTranslationX(DS.dp(this, 40));
					buildStep.run();
					card.animate().alpha(1f).translationX(0f).setDuration(300)
							.setInterpolator(new DecelerateInterpolator()).start();
				}).start();
	}

	private void shakeCard() {
		ObjectAnimator shake = ObjectAnimator.ofFloat(card, "translationX", 0f, -20f, 20f, -14f, 14f, -8f, 0f);
		shake.setDuration(400);
		shake.setInterpolator(new DecelerateInterpolator());
		shake.start();
	}

	private void setButtonLoading(TextView btn, String text) {
		if (btn == null)
			return;
		btn.setEnabled(false);
		btn.setAlpha(0.65f);
		btn.setText(text);
	}

	private void setButtonNormal(TextView btn, String text) {
		if (btn == null)
			return;
		btn.setEnabled(true);
		btn.setAlpha(1f);
		btn.setText(text);
	}

	// ─────────────────────────────────────────────────────────────
	// Illustration Canvas — deux cercles + cœur
	// ─────────────────────────────────────────────────────────────

	private static class CoupleIllustrationView extends View {

		private final Paint paintA, paintB, paintHeart, paintLine;

		CoupleIllustrationView(android.content.Context ctx) {
			super(ctx);
			paintA = new Paint(Paint.ANTI_ALIAS_FLAG);
			paintA.setStyle(Paint.Style.FILL);
			paintA.setColor(Color.WHITE);

			paintB = new Paint(Paint.ANTI_ALIAS_FLAG);
			paintB.setStyle(Paint.Style.FILL);
			paintB.setColor(Color.argb(160, 255, 255, 255));

			paintHeart = new Paint(Paint.ANTI_ALIAS_FLAG);
			paintHeart.setStyle(Paint.Style.FILL);
			paintHeart.setColor(Color.argb(220, 255, 180, 160));

			paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
			paintLine.setStyle(Paint.Style.STROKE);
			paintLine.setColor(Color.argb(100, 255, 255, 255));
			paintLine.setStrokeWidth(4f);
			paintLine.setStrokeCap(Paint.Cap.ROUND);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			float w = getWidth(), h = getHeight();
			float cy = h / 2f;
			float r = Math.min(w, h) * 0.22f;
			float gap = w * 0.25f;

			// Cercle gauche
			canvas.drawCircle(w / 2f - gap, cy, r, paintA);
			// Cercle droit
			canvas.drawCircle(w / 2f + gap, cy, r * 0.85f, paintB);

			// Ligne de connexion
			canvas.drawLine(w / 2f - gap + r, cy, w / 2f + gap - r * 0.85f, cy, paintLine);

			// Cœur au centre
			drawHeart(canvas, w / 2f, cy, r * 0.45f, paintHeart);

			// Initiales
			Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
			textP.setColor(Color.parseColor("#C0614A"));
			textP.setTextSize(r * 0.7f);
			textP.setTextAlign(Paint.Align.CENTER);
			textP.setTypeface(Typeface.DEFAULT_BOLD);
			canvas.drawText("T", w / 2f - gap, cy + r * 0.25f, textP);
			textP.setColor(Color.parseColor("#2D5A4E"));
			canvas.drawText("M", w / 2f + gap, cy + r * 0.22f, textP);
		}

		private void drawHeart(Canvas c, float cx, float cy, float size, Paint p) {
			android.graphics.Path path = new android.graphics.Path();
			path.moveTo(cx, cy + size * 0.7f);
			path.cubicTo(cx - size * 1.4f, cy + size * 0.1f, cx - size * 1.4f, cy - size * 0.8f, cx, cy - size * 0.1f);
			path.cubicTo(cx + size * 1.4f, cy - size * 0.8f, cx + size * 1.4f, cy + size * 0.1f, cx, cy + size * 0.7f);
			c.drawPath(path, p);
		}
	}
}
