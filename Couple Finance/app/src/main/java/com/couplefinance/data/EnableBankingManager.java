package com.couplefinance.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * EnableBankingManager — Client REST Enable Banking (Open Banking DSP2).
 *
 * Fonctionnalités :
 *  - Connexion bancaire via OAuth / JWT RS256
 *  - Mémorisation de la banque sélectionnée
 *  - Extraction IBAN + nom de chaque compte depuis la session
 *  - Attribution compte → propriétaire (Thomas / Mélissa / Joint)
 *  - Récupération des transactions (format standard + format CMB/Arkéa)
 *  - Récupération des soldes par compte
 */
public class EnableBankingManager {

    private static final String TAG = "EnableBanking";

    public static final String BASE_URL     = "https://api.enablebanking.com/";
    public static final String REDIRECT_URI = "https://localhost/callback";

    // ── Clés SharedPreferences ────────────────────────────────────
    private static final String PREFS         = "enablebanking_prefs";
    private static final String K_APP_ID      = "eb_application_id";
    private static final String K_PRIV_KEY    = "eb_private_key_pem";
    private static final String K_SESSION     = "eb_session_id";
    private static final String K_UIDS        = "eb_account_uids";
    private static final String K_TX_URLS     = "eb_tx_urls";
    private static final String K_BAL_URLS    = "eb_bal_urls";
    private static final String K_STATE       = "eb_last_state";
    private static final String K_BANK_NAME   = "eb_saved_bank_name";
    private static final String K_BANK_ID     = "eb_saved_bank_id";
    private static final String K_IBANS       = "eb_account_ibans";     // ||| séparateur
    private static final String K_ACC_NAMES   = "eb_account_names";     // ||| séparateur
    private static final String K_OWNERS      = "eb_account_owners";    // ||| séparateur : "thomas","melissa","joint",""
    private static final String SEP           = "|||";

    private static volatile EnableBankingManager instance;
    private final Executor executor = Executors.newFixedThreadPool(2);
    private final Handler  handler  = new Handler(Looper.getMainLooper());
    private Context context;

    // ─────────────────────────────────────────────────────────────
    // Modèles publics
    // ─────────────────────────────────────────────────────────────

    public static class Institution {
        public final String id, name, logo, bic, country;
        public Institution(String id, String name, String logo, String bic, String country) {
            this.id = id; this.name = name;
            this.logo = logo != null ? logo : "";
            this.bic  = bic  != null ? bic  : "";
            this.country = country != null ? country : "";
        }
        @Override public String toString() { return name; }
    }

    public static class BankTransaction {
        public String  id, label, currency, bookingDate, accountId;
        public double  amount;
        public long    dateMs;
        public boolean pending;
        public int     accountIndex;   // 0 = premier compte, 1 = second, etc.
        public String  accountIban;    // IBAN court (ex: "...04036")
        public String  owner;          // "thomas", "melissa", "joint"
        public BankTransaction() {}
    }

    public static class AccountBalance {
        public double amount;
        public String currency, type, indicator, date;
        public int    accountIndex;
        public String balanceUrl;
        public AccountBalance() {}
        public double signedAmount() {
            // Le montant renvoyé par Enable Banking porte déjà son propre signe
            // (ex. "-177.28" pour un découvert). On ne se fie PLUS au champ
            // credit_debit_indicator car certaines banques (CMB) l'envoient de
            // façon incohérente entre deux requêtes (DBIT/CRDT alternés sur le
            // même solde XPCD), ce qui inversait le signe du solde de façon
            // aléatoire d'une synchro à l'autre.
            return amount;
        }
    }

    public interface Callback { void onSuccess(String r); void onError(String e); }
    public interface InstitutionsCallback { void onResult(List<Institution> l); void onError(String e); }
    public interface TransactionsCallback { void onResult(List<BankTransaction> l); void onError(String e); }
    public interface BalancesCallback { void onResult(List<AccountBalance> l, double mainBalance); void onError(String e); }

    // ─────────────────────────────────────────────────────────────
    // Singleton
    // ─────────────────────────────────────────────────────────────

    private EnableBankingManager() {}
    public static EnableBankingManager getInstance() {
        if (instance == null) {
            synchronized (EnableBankingManager.class) {
                if (instance == null) instance = new EnableBankingManager();
            }
        }
        return instance;
    }
    public void init(Context ctx) {
        if (ctx != null) context = ctx.getApplicationContext();
    }

    // ─────────────────────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────────────────────

    public void configure(String appId, String pemKey) {
        prefs().edit().putString(K_APP_ID, appId).putString(K_PRIV_KEY, pemKey).apply();
    }
    public boolean isConfigured() {
        return !p(K_APP_ID).isEmpty() && !p(K_PRIV_KEY).isEmpty();
    }
    public boolean isConnected() {
        return isConfigured() && !p(K_UIDS).isEmpty();
    }

    // ── Banque mémorisée ─────────────────────────────────────────
    public void saveBankSelection(String bankName, String bankId) {
        prefs().edit().putString(K_BANK_NAME, bankName).putString(K_BANK_ID, bankId).apply();
    }
    public String getSavedBankName() { return p(K_BANK_NAME); }
    public String getSavedBankId()   { return p(K_BANK_ID);   }

    // ── Comptes ──────────────────────────────────────────────────
    public List<String> getSavedAccountIds()  { return split(p(K_UIDS),     ","); }
    public List<String> getSavedTxUrls()      { return split(p(K_TX_URLS),  SEP); }
    public List<String> getSavedBalUrls()     { return split(p(K_BAL_URLS), SEP); }
    public List<String> getAccountIbans()     { return split(p(K_IBANS),    SEP); }
    public List<String> getAccountNames()     { return split(p(K_ACC_NAMES),SEP); }
    public List<String> getAccountOwners()    { return split(p(K_OWNERS),   SEP); }
    public String getSavedRequisitionId()     { return p(K_SESSION); }

    /** Retourne le propriétaire d'un compte par son index. */
    public String getOwnerForIndex(int idx) {
        List<String> owners = getAccountOwners();
        if (idx >= 0 && idx < owners.size()) return owners.get(idx);
        return "";
    }

    /** Retourne l'IBAN court (4 derniers chiffres) pour un index de compte. */
    public String getShortIban(int idx) {
        List<String> ibans = getAccountIbans();
        if (idx >= 0 && idx < ibans.size()) {
            String iban = ibans.get(idx);
            return iban.length() > 4 ? "..." + iban.substring(iban.length() - 5) : iban;
        }
        return "Compte " + (idx + 1);
    }

    /** Sauvegarde l'attribution propriétaire pour un compte. */
    public void saveAccountOwner(int idx, String owner) {
        List<String> owners = new ArrayList<>(getAccountOwners());
        while (owners.size() <= idx) owners.add("");
        owners.set(idx, owner != null ? owner : "");
        prefs().edit().putString(K_OWNERS, join(owners, SEP)).apply();
    }

    public void clearConnection() {
        prefs().edit()
                .remove(K_SESSION).remove(K_UIDS)
                .remove(K_TX_URLS).remove(K_BAL_URLS)
                .remove(K_STATE).remove(K_IBANS)
                .remove(K_ACC_NAMES).remove(K_OWNERS)
                .apply();
    }
    public void clearAll() { prefs().edit().clear().apply(); }

    // ─────────────────────────────────────────────────────────────
    // Institutions
    // ─────────────────────────────────────────────────────────────

    public void getInstitutions(String country, InstitutionsCallback cb) {
        if (!isConfigured()) { handler.post(() -> cb.onError("Non configuré.")); return; }
        executor.execute(() -> {
            try {
                JSONObject res = getJson("aspsps?country=" + country, buildJwt());
                JSONArray  arr = res.optJSONArray("aspsps");
                List<Institution> result = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String name = o.optString("name", "");
                        if (!name.isEmpty())
                            result.add(new Institution(name, name,
                                    o.optString("logo",""), o.optString("bic",""),
                                    o.optString("country", country)));
                    }
                }
                result.sort((a,b) -> a.name.compareToIgnoreCase(b.name));
                handler.post(() -> cb.onResult(result));
            } catch (Exception e) {
                Log.e(TAG, "getInstitutions", e);
                handler.post(() -> cb.onError("Banques : " + e.getMessage()));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Autorisation OAuth
    // ─────────────────────────────────────────────────────────────

    public void createRequisition(String institutionId, Callback cb) {
        if (!isConfigured()) { handler.post(() -> cb.onError("Non configuré.")); return; }
        executor.execute(() -> {
            try {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, 90);
                String validUntil = String.format(java.util.Locale.US,
                        "%04d-%02d-%02dT00:00:00.000Z",
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1,
                        cal.get(Calendar.DAY_OF_MONTH));

                String state = "cf_" + System.currentTimeMillis();
                JSONObject access = new JSONObject(); access.put("valid_until", validUntil);
                JSONObject aspsp  = new JSONObject(); aspsp.put("name", institutionId); aspsp.put("country","FR");
                JSONObject body   = new JSONObject();
                body.put("access", access); body.put("aspsp", aspsp);
                body.put("state", state);
                body.put("redirect_url", REDIRECT_URI);
                body.put("psu_type", "personal");

                JSONObject res = postJson("auth", buildJwt(), body.toString());
                String url = res.optString("url", "");
                if (url.isEmpty()) {
                    handler.post(() -> cb.onError("URL OAuth absente: " + res.toString()));
                    return;
                }
                prefs().edit().putString(K_STATE, state).apply();
                handler.post(() -> cb.onSuccess(url));
            } catch (Exception e) {
                Log.e(TAG, "createRequisition", e);
                handler.post(() -> cb.onError("OAuth : " + e.getMessage()));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Récupération des comptes — extrait IBANs, noms, URLs
    // ─────────────────────────────────────────────────────────────

    public void fetchAccounts(String callbackUrl, Callback cb) {
        executor.execute(() -> {
            try {
                String code  = extractParam(callbackUrl, "code");
                String state = extractParam(callbackUrl, "state");
                String error = extractParam(callbackUrl, "error");
                if (!error.isEmpty()) {
                    String desc = extractParam(callbackUrl, "error_description");
                    handler.post(() -> cb.onError("Refusé : " + (desc.isEmpty() ? error : desc)));
                    return;
                }
                if (code.isEmpty()) {
                    handler.post(() -> cb.onError("Code OAuth absent : " + callbackUrl));
                    return;
                }
                JSONObject sb = new JSONObject();
                sb.put("code", code); sb.put("state", state);
                JSONObject res = postJson("sessions", buildJwt(), sb.toString());

                String    session  = res.optString("session_id", "");
                JSONArray accounts = res.optJSONArray("accounts");
                if (session.isEmpty()) {
                    handler.post(() -> cb.onError("session_id absent: " + res.toString()));
                    return;
                }
                if (accounts == null || accounts.length() == 0) {
                    handler.post(() -> cb.onError("Aucun compte. Liez vos comptes sur enablebanking.com"));
                    return;
                }

                List<String> uids    = new ArrayList<>();
                List<String> txUrls  = new ArrayList<>();
                List<String> balUrls = new ArrayList<>();
                List<String> ibans   = new ArrayList<>();
                List<String> names   = new ArrayList<>();
                List<String> owners  = new ArrayList<>();

                for (int i = 0; i < accounts.length(); i++) {
                    JSONObject acc = accounts.optJSONObject(i);
                    if (acc == null) continue;

                    String uid    = acc.optString("uid", "");
                    String txUrl  = acc.optString("transactions_url", "");
                    String balUrl = acc.optString("balances_url",     "");

                    if (txUrl.isEmpty()  && !uid.isEmpty())
                        txUrl  = BASE_URL + "accounts/" + urlEncode(uid) + "/transactions";
                    if (balUrl.isEmpty() && !uid.isEmpty())
                        balUrl = BASE_URL + "accounts/" + urlEncode(uid) + "/balances";

                    // ── Extraire IBAN et nom du compte ────────────────
                    String iban = "";
                    JSONObject accountId = acc.optJSONObject("account_id");
                    if (accountId != null) {
                        iban = accountId.optString("iban", "");
                        if (iban.isEmpty()) iban = accountId.optString("other", "");
                    }
                    String accName = acc.optString("name",
                            acc.optString("product",
                            acc.optString("cash_account_type",
                            "Compte " + (i + 1))));

                    if (!uid.isEmpty()) {
                        uids.add(uid);
                        txUrls.add(txUrl);
                        balUrls.add(balUrl);
                        ibans.add(iban);
                        names.add(accName);
                        owners.add(""); // attribution par défaut vide
                    }
                }

                if (uids.isEmpty()) {
                    handler.post(() -> cb.onError("Aucun compte valide."));
                    return;
                }

                prefs().edit()
                        .putString(K_SESSION,   session)
                        .putString(K_UIDS,      join(uids,    ","))
                        .putString(K_TX_URLS,   join(txUrls,  SEP))
                        .putString(K_BAL_URLS,  join(balUrls, SEP))
                        .putString(K_IBANS,     join(ibans,   SEP))
                        .putString(K_ACC_NAMES, join(names,   SEP))
                        .putString(K_OWNERS,    join(owners,  SEP))
                        .apply();

                handler.post(() -> cb.onSuccess(join(uids, ",")));
            } catch (Exception e) {
                Log.e(TAG, "fetchAccounts", e);
                handler.post(() -> cb.onError("Comptes : " + e.getMessage()));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Transactions
    // ─────────────────────────────────────────────────────────────

    public void syncAllAccounts(String dateFrom, String dateTo, TransactionsCallback cb) {
        List<String> txUrls = getSavedTxUrls();
        List<String> uids   = getSavedAccountIds();

        if (txUrls.isEmpty() && !uids.isEmpty()) {
            List<String> fb = new ArrayList<>();
            for (String uid : uids)
                fb.add(BASE_URL + "accounts/" + urlEncode(uid) + "/transactions");
            syncFromUrlList(fb, dateFrom, dateTo, cb);
            return;
        }
        if (txUrls.isEmpty()) {
            handler.post(() -> cb.onError("Aucun compte connecté."));
            return;
        }
        syncFromUrlList(txUrls, dateFrom, dateTo, cb);
    }

    private void syncFromUrlList(List<String> txUrls, String dateFrom, String dateTo,
                                  TransactionsCallback cb) {
        executor.execute(() -> {
            List<BankTransaction> all = new ArrayList<>();
            String lastError = null;
            try {
                String jwt = buildJwt();
                for (int urlIdx = 0; urlIdx < txUrls.size(); urlIdx++) {
                    String txUrl = txUrls.get(urlIdx);
                    if (txUrl.isEmpty()) continue;
                    final int idx = urlIdx;
                    // Compte exclu pendant l'attribution : on ne le synchronise pas
                    if ("__skip__".equals(getOwnerForIndex(idx))) continue;
                    try {
                        JSONObject res = tryFetchTx(txUrl, dateFrom, dateTo, jwt);
                        parseTxResponse(res, txUrl, idx, all);
                    } catch (Exception e) {
                        Log.e(TAG, "Erreur compte " + idx, e);
                        lastError = e.getMessage();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "syncFromUrlList", e);
                lastError = e.getMessage();
            }
            final List<BankTransaction> fa = all;
            final String fe = lastError;
            handler.post(() -> {
                if (fa.isEmpty() && fe != null) cb.onError(fe);
                else cb.onResult(fa);
            });
        });
    }

    /**
     * Tente 3 stratégies de dates pour maximiser la compatibilité CMB :
     * 1. Dates demandées → 2. 7 derniers jours → 3. Sans dates
     */
    private JSONObject tryFetchTx(String txUrl, String dateFrom, String dateTo,
                                   String jwt) throws Exception {
        // Essai 1 : dates demandées
        String sep = txUrl.contains("?") ? "&" : "?";
        try {
            return getJsonFullUrl(txUrl + sep + "date_from=" + dateFrom + "&date_to=" + dateTo, jwt);
        } catch (Exception e1) {
            if (e1.getMessage() == null || !e1.getMessage().contains("ASPSP_ERROR")) throw e1;
        }
        // Essai 2 : 7 derniers jours
        Calendar cal = Calendar.getInstance();
        String to7   = millisToDateStr(cal.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -7);
        String from7 = millisToDateStr(cal.getTimeInMillis());
        try {
            return getJsonFullUrl(txUrl + sep + "date_from=" + from7 + "&date_to=" + to7, jwt);
        } catch (Exception e2) {
            if (e2.getMessage() == null || !e2.getMessage().contains("ASPSP_ERROR")) throw e2;
        }
        // Essai 3 : sans dates
        return getJsonFullUrl(txUrl, jwt);
    }

    /** Parse la réponse transactions (format standard OU format CMB flat). */
    private void parseTxResponse(JSONObject res, String txUrl, int accountIdx,
                                  List<BankTransaction> out) {
        String accountIban  = getShortIban(accountIdx);
        String owner        = getOwnerForIndex(accountIdx);

        // Format standard : {"transactions": {"booked": [...], "pending": [...]}}
        JSONObject txObj = res.optJSONObject("transactions");
        if (txObj != null) {
            // DEBUG : dump de la première transaction pour identifier les champs
            JSONArray bk = txObj.optJSONArray("booked");
            if (bk != null && bk.length() > 0)
                Log.d(TAG, "RAW tx[0] (standard) : " + bk.optJSONObject(0));
            out.addAll(parseTxArray(bk, accountIdx, accountIban, owner, false));
            out.addAll(parseTxArray(txObj.optJSONArray("pending"), accountIdx, accountIban, owner, true));
            return;
        }
        // Format CMB/Arkéa : {"transactions": [...], "continuation_key": ...}
        JSONArray flat = res.optJSONArray("transactions");
        if (flat != null) {
            // DEBUG : dump de la première transaction CMB
            if (flat.length() > 0)
                Log.d(TAG, "RAW tx[0] (CMB flat) : " + flat.optJSONObject(0));
            out.addAll(parseTxArray(flat, accountIdx, accountIban, owner, false));
            String contKey = res.optString("continuation_key", "");
            if (!contKey.isEmpty() && !"null".equals(contKey)) {
                try {
                    String sep  = txUrl.contains("?") ? "&" : "?";
                    JSONObject  next = getJsonFullUrl(txUrl + sep + "continuation_key=" + urlEncode(contKey), buildJwt());
                    JSONArray   nextArr = next.optJSONArray("transactions");
                    if (nextArr != null)
                        out.addAll(parseTxArray(nextArr, accountIdx, accountIban, owner, false));
                } catch (Exception ignored) {}
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Parsing transaction — gère les deux formats
    // ─────────────────────────────────────────────────────────────

    private List<BankTransaction> parseTxArray(JSONArray arr, int accountIdx,
                                                String accountIban, String owner,
                                                boolean pending) {
        List<BankTransaction> list = new ArrayList<>();
        if (arr == null) return list;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;

            BankTransaction bt = new BankTransaction();
            bt.accountId    = String.valueOf(accountIdx);
            bt.accountIndex = accountIdx;
            bt.accountIban  = accountIban;
            bt.owner        = owner;
            bt.pending      = pending;
            bt.id           = obj.optString("transaction_id",
                    obj.optString("entry_reference", ""));

            // ── Montant : format objet OU direct ─────────────────────
            JSONObject ao = obj.optJSONObject("transaction_amount");
            if (ao != null) {
                bt.currency = ao.optString("currency", "EUR");
                try { bt.amount = parseAmt(ao.optString("amount", "0")); }
                catch (Exception ignored) {}
            } else {
                // Format flat CMB : champ direct
                bt.currency = obj.optString("currency", "EUR");
                try { bt.amount = parseAmt(obj.optString("amount", "0")); }
                catch (Exception ignored) {}
            }
            // Appliquer le credit_debit_indicator si montant non signé
            if (bt.amount >= 0) {
                String cdi = obj.optString("credit_debit_indicator", "");
                if ("DBIT".equals(cdi)) bt.amount = -Math.abs(bt.amount);
            }

            // ── Date ─────────────────────────────────────────────────
            String ds = obj.optString("booking_date", obj.optString("value_date", ""));
            bt.bookingDate = ds;
            bt.dateMs      = parseDateToMillis(ds);

            // ── Libellé — ordre de priorité ───────────────────────────
            bt.label = extractLabel(obj, ds);

            if (Math.abs(bt.amount) > 0.001) list.add(bt);
        }
        return list;
    }

    /**
     * Extrait le libellé d'une transaction en essayant tous les champs connus.
     * CMB/Arkéa utilisent des noms de champs non standard.
     */
    private String extractLabel(JSONObject obj, String fallbackDate) {
        // 1. remittance_information en TABLEAU (format Berlin Group / Arkéa)
        JSONArray remArr = obj.optJSONArray("remittance_information");
        if (remArr != null && remArr.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < remArr.length(); i++) {
                String part = remArr.optString(i, "").trim();
                if (!part.isEmpty()) { if (sb.length()>0) sb.append(" "); sb.append(part); }
            }
            if (sb.length() > 0) return cleanLabel(sb.toString());
        }

        // 2. remittance_information en OBJET {unstructured:[...], structured:...}
        JSONObject remObj = obj.optJSONObject("remittance_information");
        if (remObj != null) {
            JSONArray uns = remObj.optJSONArray("unstructured");
            if (uns != null && uns.length() > 0) {
                String v = uns.optString(0, "").trim();
                if (!v.isEmpty()) return cleanLabel(v);
            }
            String s = remObj.optString("unstructured", "").trim();
            if (!s.isEmpty()) return cleanLabel(s);
        }

        // 3. Champs string directs
        String[] stringFields = {
            "remittance_information_unstructured",
            "remittance_information_structured",
            "additional_information",
            "transaction_information",
            "creditor_name",
            "debtor_name",
            "label", "description", "narrative", "reference",
            "transaction_reference", "end_to_end_id"
        };
        for (String field : stringFields) {
            String v = obj.optString(field, "").trim();
            if (!v.isEmpty() && !v.equals("null")) return cleanLabel(v);
        }

        // 4. Objets creditor / debtor
        JSONObject creditor = obj.optJSONObject("creditor");
        if (creditor != null) {
            String v = creditor.optString("name", "").trim();
            if (!v.isEmpty()) return cleanLabel(v);
        }
        JSONObject debtor = obj.optJSONObject("debtor");
        if (debtor != null) {
            String v = debtor.optString("name", "").trim();
            if (!v.isEmpty()) return cleanLabel(v);
        }

        // 5. bank_transaction_code (dernier recours avant fallback date)
        JSONObject btc = obj.optJSONObject("bank_transaction_code");
        if (btc != null) {
            String desc = btc.optString("description", "").trim();
            if (!desc.isEmpty()) return cleanLabel(desc);
        }

        return "Transaction " + fallbackDate;
    }

    private double parseAmt(String s) {
        if (s == null || s.isEmpty()) return 0;
        return Double.parseDouble(s.replace(",", ".").trim());
    }

    // ─────────────────────────────────────────────────────────────
    // Balances
    // ─────────────────────────────────────────────────────────────

    public void getAllBalances(BalancesCallback cb) {
        List<String> balUrls = getSavedBalUrls();
        List<String> uids    = getSavedAccountIds();
        List<String> urls = new ArrayList<>();
        if (!balUrls.isEmpty()) urls.addAll(balUrls);
        else for (String uid : uids) urls.add(BASE_URL + "accounts/" + urlEncode(uid) + "/balances");
        if (urls.isEmpty()) { handler.post(() -> cb.onError("Aucun compte.")); return; }

        executor.execute(() -> {
            List<AccountBalance> all = new ArrayList<>();
            double main = 0; String lastError = null;
            try {
                String jwt = buildJwt();
                int urlIdx = 0;
                for (String balUrl : urls) {
                    if (balUrl.isEmpty()) { urlIdx++; continue; }
                    final int idx = urlIdx++;
                    try {
                        JSONObject res = getJsonFullUrl(balUrl, jwt);
                        JSONArray  arr = res.optJSONArray("balances");
                        if (arr == null) continue;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject b = arr.optJSONObject(i);
                            if (b == null) continue;
                            AccountBalance ab = new AccountBalance();
                            ab.type         = b.optString("balance_type",           "");
                            ab.indicator    = b.optString("credit_debit_indicator", "CRDT");
                            ab.date         = b.optString("reference_date",         "");
                            ab.accountIndex = idx;
                            ab.balanceUrl   = balUrl;
                            JSONObject ao = b.optJSONObject("balance_amount");
                            if (ao != null) {
                                ab.currency = ao.optString("currency", "EUR");
                                try { ab.amount = parseAmt(ao.optString("amount","0")); } catch (Exception ignored) {}
                            }
                            all.add(ab);
                            if (idx == 0 && ("ITBD".equals(ab.type) || "CLBD".equals(ab.type))) {
                                main = ab.signedAmount();
                                if ("ITBD".equals(ab.type)) break;
                            }
                        }
                    } catch (Exception e) { Log.e(TAG, "Balance", e); lastError = e.getMessage(); }
                }
            } catch (Exception e) { Log.e(TAG, "getAllBalances", e); lastError = e.getMessage(); }
            final List<AccountBalance> fa = all;
            final double fm = main; final String fe = lastError;
            handler.post(() -> { if (fa.isEmpty() && fe != null) cb.onError(fe); else cb.onResult(fa, fm); });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // JWT RS256
    // ─────────────────────────────────────────────────────────────

    private String buildJwt() throws Exception {
        String appId = p(K_APP_ID); String pem = p(K_PRIV_KEY);
        if (appId.isEmpty() || pem.isEmpty()) throw new Exception("Non configuré.");
        JSONObject header = new JSONObject(); header.put("alg","RS256"); header.put("kid",appId);
        JSONObject payload = new JSONObject();
        payload.put("iss","enablebanking.com"); payload.put("aud","api.enablebanking.com");
        long iat = System.currentTimeMillis()/1000L;
        payload.put("iat",iat); payload.put("exp",iat+3600L);
        String h64 = base64url(header.toString().getBytes("UTF-8"));
        String p64 = base64url(payload.toString().getBytes("UTF-8"));
        String signing = h64+"."+p64;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(loadPrivateKey(pem));
        sig.update(signing.getBytes("UTF-8"));
        return signing+"."+base64url(sig.sign());
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String b64 = pem
                .replace("-----BEGIN PRIVATE KEY-----","")
                .replace("-----END PRIVATE KEY-----","")
                .replace("-----BEGIN RSA PRIVATE KEY-----","")
                .replace("-----END RSA PRIVATE KEY-----","")
                .replaceAll("[\\r\\n\\s]","");
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(b64, Base64.DEFAULT)));
    }
    private static String base64url(byte[] d) {
        return Base64.encodeToString(d, Base64.NO_PADDING|Base64.NO_WRAP|Base64.URL_SAFE);
    }

    // ─────────────────────────────────────────────────────────────
    // HTTP
    // ─────────────────────────────────────────────────────────────

    private JSONObject getJson(String endpoint, String jwt) throws Exception {
        return getJsonFullUrl(BASE_URL + endpoint, jwt);
    }
    private JSONObject getJsonFullUrl(String url, String jwt) throws Exception {
        HttpURLConnection c = openConn(url, "GET", jwt, false);
        try {
            int code = c.getResponseCode();
            String body = safeRead(code < 400 ? c.getInputStream() : c.getErrorStream());
            if (code == 401) throw new Exception("JWT invalide (401).");
            if (code == 404) throw new Exception("404 — URL incorrecte. Reconnectez la banque.");
            if (code == 400) {
                if (body.contains("ASPSP_ERROR"))
                    throw new Exception("ASPSP_ERROR — Banque a refusé (auth expirée ou dates trop larges).");
                throw new Exception("HTTP 400 : " + body);
            }
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " : " + body);
            return new JSONObject(body);
        } finally {
            c.disconnect();
        }
    }
    private JSONObject postJson(String endpoint, String jwt, String bodyStr) throws Exception {
        HttpURLConnection c = openConn(BASE_URL + endpoint, "POST", jwt, true);
        try {
            try (DataOutputStream dos = new DataOutputStream(c.getOutputStream())) {
                dos.write(bodyStr.getBytes("UTF-8"));
            }
            int code = c.getResponseCode();
            String body = safeRead(code < 400 ? c.getInputStream() : c.getErrorStream());
            if (code == 401) throw new Exception("JWT invalide (401).");
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " : " + body);
            return new JSONObject(body);
        } finally {
            c.disconnect();
        }
    }
    private HttpURLConnection openConn(String url, String method, String jwt, boolean out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Accept",       "application/json");
        c.setRequestProperty("Content-Type", "application/json");
        if (jwt != null && !jwt.isEmpty()) c.setRequestProperty("Authorization","Bearer "+jwt);
        c.setConnectTimeout(15000); c.setReadTimeout(15000); c.setDoOutput(out);
        return c;
    }

    // ─────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────

    public static long parseDateToMillis(String s) {
        if (s == null || s.length() < 10) return System.currentTimeMillis();
        try {
            int y = Integer.parseInt(s.substring(0,4));
            int m = Integer.parseInt(s.substring(5,7))-1;
            int d = Integer.parseInt(s.substring(8,10));
            Calendar c = Calendar.getInstance(); c.set(y,m,d,12,0,0);
            c.set(Calendar.MILLISECOND,0); return c.getTimeInMillis();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }
    public static String millisToDateStr(long millis) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(millis);
        return String.format(java.util.Locale.US,"%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH));
    }

    private String cleanLabel(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Transaction";
        String s = raw.trim();
        s = s.replaceAll("(?i)SEPA\\s+\\w+\\s+TRANSFER\\s*","");
        s = s.replaceAll("(?i)VIREMENT\\s+SEPA\\s*","");
        s = s.replaceAll("\\s+"," ").trim();
        return s.isEmpty() ? raw.trim() : s;
    }
    private String extractParam(String url, String param) {
        try {
            int idx = url.indexOf(param+"="); if(idx<0) return "";
            int start = idx+param.length()+1;
            int end   = url.indexOf("&",start);
            return java.net.URLDecoder.decode(end<0?url.substring(start):url.substring(start,end),"UTF-8");
        } catch (Exception e) { return ""; }
    }
    private String urlEncode(String uid) {
        try { return java.net.URLEncoder.encode(uid,"UTF-8").replace("+","%20"); }
        catch (Exception e) { return uid; }
    }
    private String safeRead(InputStream is) {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
    private List<String> split(String raw, String sep) {
        List<String> l = new ArrayList<>();
        if (raw==null||raw.isEmpty()) return l;
        String escaped = sep.equals(SEP) ? "\\|\\|\\|" : sep;
        for (String s : raw.split(escaped)) { String c=s.trim(); if(!c.isEmpty()) l.add(c); }
        return l;
    }
    private String join(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) { if(sb.length()>0) sb.append(sep); sb.append(s); }
        return sb.toString();
    }
    private String p(String key) { return prefs().getString(key,""); }
    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
