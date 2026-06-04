package com.couplefinance.ui.virements;

import java.util.ArrayList;
import java.util.List;

public final class VirementModels {

    private VirementModels() {
    }

    public static class Beneficiary {
        public String name;
        public String iban;
        public String docPath;

        public Beneficiary() {
            this("", "", "");
        }

        public Beneficiary(String name, String iban, String docPath) {
            this.name = safe(name);
            this.iban = safe(iban);
            this.docPath = safe(docPath);
        }
    }

    public static class Transfer {
        public String from;
        public String to;
        public String motif;
        public String txId;
        public String docPath;
        public double amount;
        public long dateMs;

        public Transfer() {
            this("", "", "", "", "", 0, 0);
        }

        public Transfer(String from, String to, String motif, String txId, String docPath, double amount, long dateMs) {
            this.from = safe(from);
            this.to = safe(to);
            this.motif = safe(motif);
            this.txId = safe(txId);
            this.docPath = safe(docPath);
            this.amount = amount;
            this.dateMs = dateMs;
        }
    }

    public static class VirementData {
        public List<Beneficiary> beneficiaries;
        public List<Transfer> transfers;
        public List<String> members;

        public VirementData() {
            beneficiaries = new ArrayList<>();
            transfers = new ArrayList<>();
            members = new ArrayList<>();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
