package com.couplefinance;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.couplefinance.core.theme.ThemeManager;
import com.couplefinance.ui.settings.SettingsStyles;

import com.couplefinance.ui.DashboardActivity;
import com.couplefinance.AuthManager;
import com.couplefinance.data.HouseholdManager;
import com.couplefinance.data.FirestoreManager;
import com.couplefinance.data.TransactionManager;

public class MainActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ThemeManager.getInstance().initialize(this);
		SettingsStyles.syncWithGlobalTheme();
		AuthManager.getInstance().init(this);
		HouseholdManager.getInstance().init(this);

		if (AuthManager.getInstance().isLoggedIn() && HouseholdManager.getInstance().hasHousehold()) {
			startActivity(new Intent(this, DashboardActivity.class));
		} else {
			startActivity(new Intent(this, LoginActivity.class));
		}
		finish();
	}
}