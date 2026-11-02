package app.familytv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Household settings from res/raw/family.json, copied from config/family.json at build time. */
final class FamilyConfig {
    private static JSONObject root;
    private static final String[] DEFAULT_TOP = {
        "com.google.android.youtube.tv",
        "com.google.android.youtube.tvunplugged",
        "com.netflix.ninja",
        "com.disney.disneyplus",
        "com.plexapp.android"
    };
    private static final String[] DEFAULT_HIDDEN = {
        "com.tcl.tv", "com.tcl.suspension", "com.tcl.tv.plus",
        "com.tcl.t_solo", "com.tcl.ui_mediaCenter", "com.tcl.dashboard"
    };

    static void init(Context context) {
        if (root != null) return;
        int id = context.getResources().getIdentifier("family", "raw", context.getPackageName());
        if (id == 0) { root = new JSONObject(); return; }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(id), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            root = new JSONObject(sb.toString());
        } catch (Exception ignored) {
            root = new JSONObject();
        }
    }

    private static JSONObject section(String name) {
        if (root == null) return new JSONObject();
        JSONObject o = root.optJSONObject(name);
        return o == null ? new JSONObject() : o;
    }

    static String parentName() { String v = section("parent").optString("name", "Alex"); return v.isEmpty() ? "Alex" : v; }
    static String familyName() { String v = section("family").optString("name", "Example family"); return v.isEmpty() ? "Example family" : v; }
    static String homeName() { String v = section("home").optString("name", "Example City"); return v.isEmpty() ? "Example City" : v; }
    static double[] homePlace() {
        JSONObject home = section("home");
        return new double[] { home.optDouble("latitude", 39.9526), home.optDouble("longitude", -75.1652) };
    }
    static String factPack() { String v = section("launcher").optString("factPack", "general"); return v.isEmpty() ? "general" : v; }
    static boolean seasonalDefault() { return section("launcher").optBoolean("seasonal", true); }
    static String notesPackage() { return "app.familytv.notes"; }
    static String weatherPackage() { return "app.familytv.weather"; }

    static boolean fahrenheit(SharedPreferences prefs) {
        String unit = prefs.getString("temp_unit", "auto");
        if ("f".equals(unit)) return true;
        if ("c".equals(unit)) return false;
        return !section("home").optString("units", "fahrenheit").startsWith("c");
    }

    static boolean clock12(SharedPreferences prefs) {
        String clock = prefs.getString("clock_fmt", "auto");
        if ("12".equals(clock)) return true;
        if ("24".equals(clock)) return false;
        return !"24".equals(section("home").optString("clock", "12"));
    }

    static String timePattern(SharedPreferences prefs) { return clock12(prefs) ? "h:mm a" : "HH:mm"; }

    static String server() {
        String v = section("ntfy").optString("server", "https://ntfy.sh");
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v.isEmpty() ? "https://ntfy.sh" : v;
    }

    static String topic(SharedPreferences prefs) {
        String saved = prefs.getString("dad_topic", "").trim();
        if (!saved.isEmpty()) return saved;
        String bundled = section("ntfy").optString("topic", "").trim();
        if (!bundled.isEmpty()) return bundled;
        String generated = prefs.getString("generated_topic", "").trim();
        if (generated.isEmpty()) {
            generated = "family-" + UUID.randomUUID().toString().replace("-", "");
            prefs.edit().putString("generated_topic", generated).apply();
        }
        return generated;
    }

    static String[] topRow() {
        List<String> list = strings(section("launcher").optJSONArray("topRowApps"));
        return list.isEmpty() ? DEFAULT_TOP : list.toArray(new String[0]);
    }

    static Set<String> hidden() {
        List<String> list = strings(section("launcher").optJSONArray("hiddenApps"));
        if (list.isEmpty()) list = Arrays.asList(DEFAULT_HIDDEN);
        return new HashSet<>(list);
    }

    private static List<String> strings(JSONArray array) {
        if (array == null) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String v = array.optString(i, "").trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }
}
