package com.couplefinance.models;

import java.util.HashMap;
import java.util.Map;

public class UserProfile {

	public String uid;
	public String displayName;
	public String email;
	public String color;
	public String avatar;
	public String householdId;
	public long createdAt;

	public UserProfile() {
	}

	public UserProfile(String uid, String displayName, String email, long createdAt) {
		this.uid = uid;
		this.displayName = displayName;
		this.email = email;
		this.createdAt = createdAt;
	}

	public UserProfile(String uid, String displayName, String email, String color, long createdAt) {
		this.uid = uid;
		this.displayName = displayName;
		this.email = email;
		this.color = color;
		this.createdAt = createdAt;
	}

	public String getUid() {
		return uid;
	}

	public String getDisplayName() {
		return displayName != null && !displayName.isEmpty() ? displayName : "Moi";
	}

	public String getFirstName() {
		return getDisplayName();
	}

	public String getEmail() {
		return email;
	}

	public String getColor() {
		return color != null && !color.isEmpty() ? color : "#C0614A";
	}

	public String getAvatar() {
		return avatar != null && !avatar.isEmpty() ? avatar : "fox";
	}

	public String getHouseholdId() {
		return householdId;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("uid", uid);
		map.put("displayName", getDisplayName());
		map.put("firstName", getDisplayName());
		map.put("email", email);
		map.put("color", getColor());
		map.put("avatar", getAvatar());
		map.put("householdId", householdId);
		map.put("createdAt", createdAt);
		return map;
	}

	public static UserProfile fromMap(Map<String, Object> map) {
		if (map == null) return null;

		UserProfile user = new UserProfile();

		Object uidObj = map.get("uid");
		Object displayObj = map.get("displayName");
		Object firstNameObj = map.get("firstName");
		Object emailObj = map.get("email");
		Object colorObj = map.get("color");
		Object householdObj = map.get("householdId");
		Object createdObj = map.get("createdAt");

		user.uid = uidObj != null ? String.valueOf(uidObj) : null;

		if (displayObj != null) {
			user.displayName = String.valueOf(displayObj);
		} else if (firstNameObj != null) {
			user.displayName = String.valueOf(firstNameObj);
		} else {
			user.displayName = "Moi";
		}

		user.email = emailObj != null ? String.valueOf(emailObj) : null;
		user.color = colorObj != null ? String.valueOf(colorObj) : "#C0614A";
		Object avatarObj = map.get("avatar");
		user.avatar = avatarObj != null ? String.valueOf(avatarObj) : "fox";
		user.householdId = householdObj != null ? String.valueOf(householdObj) : null;

		if (createdObj instanceof Long) {
			user.createdAt = (Long) createdObj;
		} else if (createdObj instanceof Integer) {
			user.createdAt = ((Integer) createdObj).longValue();
		} else if (createdObj instanceof Double) {
			user.createdAt = ((Double) createdObj).longValue();
		} else {
			user.createdAt = System.currentTimeMillis();
		}

		return user;
	}
}