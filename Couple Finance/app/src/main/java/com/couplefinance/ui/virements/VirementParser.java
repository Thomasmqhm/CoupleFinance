package com.couplefinance.ui.virements;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class VirementParser {

    private VirementParser() {
    }

    public static List<VirementModels.Beneficiary> parseBeneficiaries(String json) {
        List<VirementModels.Beneficiary> result = new ArrayList<>();

        try {
            JSONArray docs = documents(json);

            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.optJSONObject(i);
                if (doc == null) {
                    continue;
                }

                JSONObject fields = doc.optJSONObject("fields");
                if (fields == null) {
                    continue;
                }

                result.add(new VirementModels.Beneficiary(
                        extractStr(fields, "name"),
                        extractStr(fields, "iban"),
                        doc.optString("name", "")
                ));
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    public static List<VirementModels.Transfer> parseTransfers(String json) {
        List<VirementModels.Transfer> result = new ArrayList<>();

        try {
            JSONArray docs = documents(json);

            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.optJSONObject(i);
                if (doc == null) {
                    continue;
                }

                JSONObject fields = doc.optJSONObject("fields");
                if (fields == null) {
                    continue;
                }

                String txId = extractStr(fields, "txId");
                if (txId.isEmpty()) {
                    txId = extractStr(fields, "transactionId");
                }

                result.add(new VirementModels.Transfer(
                        extractStr(fields, "from"),
                        extractStr(fields, "to"),
                        extractStr(fields, "motif"),
                        txId,
                        doc.optString("name", ""),
                        extractDouble(fields, "amount"),
                        extractLong(fields, "date")
                ));
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    public static List<String> parseMembersResponse(String json) {
        List<String> result = new ArrayList<>();

        try {
            JSONArray docs = documents(json);

            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.optJSONObject(i);
                if (doc == null) {
                    continue;
                }

                JSONObject fields = doc.optJSONObject("fields");
                if (fields == null) {
                    continue;
                }

                String name = extractStr(fields, "displayName");
                if (name.isEmpty()) {
                    name = extractStr(fields, "name");
                }
                if (name.isEmpty()) {
                    name = extractStr(fields, "fullName");
                }

                if (!name.trim().isEmpty() && !result.contains(name.trim())) {
                    result.add(name.trim());
                }
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    static String extractDocId(String docPathOrResponse) {
        if (docPathOrResponse == null) {
            return "";
        }

        String value = docPathOrResponse.trim();
        if (value.isEmpty()) {
            return "";
        }

        if (value.contains("\"name\"")) {
            try {
                JSONObject obj = new JSONObject(value);
                value = obj.optString("name", value);
            } catch (Exception ignored) {
            }
        }

        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) {
            return value.substring(slash + 1);
        }

        return value;
    }

    private static JSONArray documents(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return new JSONArray();
        }

        JSONObject root = new JSONObject(json);
        JSONArray docs = root.optJSONArray("documents");
        return docs != null ? docs : new JSONArray();
    }

    private static String extractStr(JSONObject fields, String key) {
        try {
            JSONObject obj = fields.optJSONObject(key);
            if (obj == null) {
                return "";
            }
            return obj.optString("stringValue", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static double extractDouble(JSONObject fields, String key) {
        try {
            JSONObject obj = fields.optJSONObject(key);
            if (obj == null) {
                return 0;
            }
            if (obj.has("doubleValue")) {
                return obj.optDouble("doubleValue", 0);
            }
            if (obj.has("integerValue")) {
                return Double.parseDouble(obj.optString("integerValue", "0"));
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long extractLong(JSONObject fields, String key) {
        try {
            JSONObject obj = fields.optJSONObject(key);
            if (obj == null) {
                return 0;
            }
            if (obj.has("integerValue")) {
                return Long.parseLong(obj.optString("integerValue", "0"));
            }
            if (obj.has("doubleValue")) {
                return (long) obj.optDouble("doubleValue", 0);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
