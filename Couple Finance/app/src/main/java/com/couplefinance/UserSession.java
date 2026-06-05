package com.couplefinance;

import com.couplefinance.models.UserProfile;

public class UserSession {

    private static UserSession instance;
    private UserProfile currentUser;

    public static UserSession getInstance() {
        if (instance == null) instance = new UserSession();
        return instance;
    }

    public void setUser(UserProfile user) {
        this.currentUser = user;
    }

    public UserProfile getUser() {
        return currentUser;
    }

    public String getName() {
        return currentUser != null && currentUser.displayName != null
                ? currentUser.displayName : "";
    }

    public void clear() {
        this.currentUser = null;
    }

    public String getNameOrFallback() {
        // Ne jamais retourner un nom qui ressemble à une adresse mail ou un identifiant
        String name = getName();
        if (!name.isEmpty()) return name;

        String dn = AuthManager.getInstance().getDisplayName();
        if (dn != null && !dn.isEmpty()) return dn;

        return "Moi";
    }
}