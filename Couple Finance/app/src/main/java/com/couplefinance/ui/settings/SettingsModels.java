package com.couplefinance.ui.settings;

import java.util.ArrayList;

public class SettingsModels {

    public static class Member {
        public String docPath;
        public String name;
        public String userId;
        public String role;
        public String color;
        public double income;
        public double overdraft;
        public double monthlyStartBalance;
        public boolean notifications;
        public boolean overdraftAlert;
        public boolean admin;

        public Member() {
            this.name = "";
            this.role = "MEMBRE";
            this.color = "#C0614A";
            this.income = 0;
            this.overdraft = 0;
            this.monthlyStartBalance = 0;
            this.notifications = true;
            this.overdraftAlert = false;
            this.admin = false;
        }

        public Member(String name, String role, String color, boolean admin) {
            this.name = name;
            this.role = role;
            this.color = color;
            this.admin = admin;
            this.income = 0;
            this.overdraft = 200;
            this.monthlyStartBalance = 0;
            this.notifications = true;
            this.overdraftAlert = false;
        }

        public String initial() {
            if (name == null || name.trim().isEmpty()) return "?";
            return name.trim().substring(0, 1).toUpperCase();
        }
    }

    public static class Category {
        public String docPath;
        public String name;
        public String type;
        public String emoji;
        public String color;
        public double budget;
        public boolean active;

        public Category() {
            this.name = "";
            this.type = "expense";
            this.emoji = "🏷️";
            this.color = "#C0614A";
            this.budget = 0;
            this.active = true;
        }

        public Category(String name, String type, boolean active) {
            this.name = name;
            this.type = type;
            this.active = active;
            this.emoji = "income".equals(type) ? "↗️" : "🏷️";
            this.color = "#C0614A";
            this.budget = 0;
        }

        public String displayEmoji() {
            return emoji == null || emoji.trim().isEmpty() ? "🏷️" : emoji.trim();
        }
    }

    public static class FixedCharge {
        public String docPath;
        public String name;
        public String category;
        public String icon;
        public double amount;
        public String frequency;
        public int ratioA;
        public int ratioB;
        public int dayOfMonth;
        public String lastAppliedMonth;

        // NOUVEAU : personne qui paie réellement la charge fixe.
        // Utilisé pour générer la transaction auto : "Thomas · EDF"
        public String paidBy;

        public FixedCharge() {
            this.icon = "💳";
            this.name = "";
            this.category = "Général";
            this.amount = 0;
            this.frequency = "Mensuel";
            this.ratioA = 50;
            this.ratioB = 50;
            this.dayOfMonth = 1;
            this.lastAppliedMonth = "";
            this.paidBy = "";
        }

        public FixedCharge(String icon, String name, String category, double amount) {
            this.icon = icon;
            this.name = name;
            this.category = category;
            this.amount = amount;
            this.frequency = "Mensuel";
            this.ratioA = 50;
            this.ratioB = 50;
            this.dayOfMonth = 1;
            this.lastAppliedMonth = "";
            this.paidBy = "";
        }

        public double perPerson() {
            return amount / 2.0;
        }
    }

    public static class State {
        public String householdName = "Foyer";
        public String description = "Finances à deux";
        public String createdAtLabel = "Mars 2024";
        public String accentColor = "#C86B4A";
        public String currency = "Euro (€)";
        public String language = "Français";
        public boolean darkMode = false;
        public boolean compactMode = false;

        public ArrayList<Member> members = new ArrayList<>();
        public ArrayList<Category> categories = new ArrayList<>();
        public ArrayList<FixedCharge> charges = new ArrayList<>();

        public double totalCharges() {
            double total = 0;
            if (charges == null) return 0;

            for (FixedCharge c : charges) {
                if (c != null) total += c.amount;
            }

            return total;
        }

        public int memberCount() {
            return members == null ? 0 : members.size();
        }
    }
}