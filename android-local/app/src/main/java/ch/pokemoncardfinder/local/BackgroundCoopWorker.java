package ch.pokemoncardfinder.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Background Coop stock refresh using CoopActivity's existing read-only JSON service. */
public final class BackgroundCoopWorker extends Worker {
    private static final String API = "https://www.coop.ch/rest/v2/coopathome";
    private final Context app;

    public BackgroundCoopWorker(Context context, WorkerParameters params) {
        super(context, params);
        app = context.getApplicationContext();
    }

    @Override public Result doWork() {
        SharedPreferences prefs = app.getSharedPreferences("coop_finder", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto", false)) return Result.success();
        if (!BackgroundRefreshScheduler.isAllowedRefreshTime()) {
            prefs.edit().putString("background_refresh_state", "Pausiert: automatische Abfragen laufen nur Mo–Sa zwischen 08:00 und 21:00 Uhr.").commit();
            return Result.success();
        }
        try {
            double lat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToRawLongBits(46.948)));
            double lon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToRawLongBits(7.447)));
            int radius = Math.max(2, Math.min(100, prefs.getInt("radius", 15)));
            LinkedHashMap<String, JSONObject> stores = fetchStores(null, lat, lon, radius);
            if (stores.isEmpty()) throw new IllegalStateException("Coop hat keine Filialen im Suchradius geliefert.");

            JSONArray storeList = new JSONArray();
            for (JSONObject store : stores.values()) storeList.put(store);
            prefs.edit().putString("stores", storeList.toString()).commit();

            JSONArray products = new JSONArray(prefs.getString("products", "[]"));
            if (products.length() == 0) throw new IllegalStateException("Coop-Produktliste ist leer.");
            String selected = prefs.getString("product_selection", "");
            Set<String> selectedIds = new LinkedHashSet<>();
            if (!selected.isEmpty()) selectedIds.addAll(Arrays.asList(selected.split(",")));
            if (selectedIds.isEmpty() && products.optJSONObject(0) != null) selectedIds.add(products.optJSONObject(0).optString("id"));

            LinkedHashSet<String> allowedStores = new LinkedHashSet<>();
            boolean restricted = prefs.getBoolean("manual_selection", false);
            JSONArray savedStores = new JSONArray(prefs.getString("selected_stores", "[]"));
            for (int i = 0; i < savedStores.length(); i++) allowedStores.add(savedStores.optString(i));

            JSONObject history = new JSONObject(prefs.getString("stock", "{}"));
            int checkedProducts = 0;
            for (String productId : selectedIds) {
                JSONObject product = findProduct(products, productId);
                if (product == null) continue;
                LinkedHashMap<String, JSONObject> available = fetchStores(productId, lat, lon, radius);
                JSONObject oldRows = history.optJSONObject(productId);
                if (oldRows == null) oldRows = new JSONObject();
                JSONObject rows = new JSONObject(oldRows.toString());
                for (Map.Entry<String, JSONObject> entry : stores.entrySet()) {
                    String storeId = entry.getKey();
                    if (restricted && !allowedStores.contains(storeId)) continue;
                    boolean isAvailable = available.containsKey(storeId);
                    JSONObject old = oldRows.optJSONObject(storeId);
                    JSONObject row = new JSONObject();
                    row.put("status", isAvailable ? "available" : "not_reported");
                    row.put("checked_at", System.currentTimeMillis());
                    rows.put(storeId, row);
                    if (isAvailable && old != null && "not_reported".equals(old.optString("status"))) {
                        notifyAvailable(prefs, productId, product.optString("name", productId), entry.getValue().optString("name"));
                    }
                }
                history.put(productId, rows);
                prefs.edit().putString("stock", history.toString())
                    .putLong("last_checked", System.currentTimeMillis()).commit();
                checkedProducts++;
            }
            if (checkedProducts == 0) throw new IllegalStateException("Kein ausgewähltes Coop-Produkt konnte geprüft werden.");
            prefs.edit().putString("background_refresh_state", "Coop-Hintergrundprüfung: " + checkedProducts + " Produkte.").commit();
            return Result.success();
        } catch (Exception error) {
            prefs.edit().putString("background_refresh_state", "Coop-Hintergrundprüfung fehlgeschlagen; alte Bestände bleiben erhalten.").commit();
            return Result.retry();
        }
    }

    private JSONObject findProduct(JSONArray products, String id) {
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product != null && id.equals(product.optString("id"))) return product;
        }
        return null;
    }

    private LinkedHashMap<String, JSONObject> fetchStores(String productId, double lat, double lon, int radiusKm) throws Exception {
        LinkedHashMap<String, JSONObject> found = new LinkedHashMap<>();
        for (int page = 0; page < 6; page++) {
            String path = "/locations/searchAroundCoordinates?latitude=" + lat + "&longitude=" + lon
                    + "&currentPage=" + page + "&language=de";
            if (productId != null) path += "&availabilityProductId=" + URLEncoder.encode(productId, "UTF-8")
                    + "&onlyWithAvailableProductId=true";
            JSONObject response = request(path);
            JSONArray locations = response.optJSONArray("locations");
            if (locations == null) locations = response.optJSONArray("stores");
            if (locations == null) throw new IllegalStateException("Coop hat keine Filialliste geliefert.");
            for (int i = 0; i < locations.length(); i++) {
                JSONObject raw = locations.optJSONObject(i);
                if (raw == null) continue;
                String id = raw.optString("vstId");
                JSONObject geo = raw.optJSONObject("geoPoint");
                if (id.isEmpty() || geo == null) continue;
                double storeLat = geo.optDouble("latitude", Double.NaN);
                double storeLon = geo.optDouble("longitude", Double.NaN);
                if (Double.isNaN(storeLat) || Double.isNaN(storeLon) || distance(lat, lon, storeLat, storeLon) > radiusKm) continue;
                JSONObject address = raw.optJSONObject("address");
                JSONObject store = new JSONObject();
                store.put("id", id);
                store.put("name", raw.optString("displayName", raw.optString("name", "Coop " + id)));
                store.put("address", address == null ? "" : (address.optString("line1") + " " + address.optString("postalCode") + " " + address.optString("town")).trim());
                store.put("lat", storeLat);
                store.put("lon", storeLon);
                found.put(id, store);
            }
            JSONObject pagination = response.optJSONObject("pagination");
            if (pagination == null || page + 1 >= pagination.optInt("totalPages", page + 1)) break;
            Thread.sleep(250);
        }
        return found;
    }

    private JSONObject request(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API + path).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(16000);
        conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Language", "de-CH,de;q=0.9");
        try {
            int status = conn.getResponseCode();
            if (status == 403 || status == 429 || status == 503) throw new java.io.IOException("Coop-Schnittstelle blockiert (HTTP " + status + ")");
            if (status < 200 || status >= 300) throw new java.io.IOException("Coop HTTP " + status);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 2_000_000) throw new java.io.IOException("Coop-Antwort zu gross.");
                }
            }
            if (body.length() == 0 || body.charAt(0) == '<') throw new java.io.IOException("Kein lesbares Coop-JSON.");
            return new JSONObject(body.toString());
        } finally {
            conn.disconnect();
        }
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.sin(Math.toRadians(lat2-lat1)/2); a *= a;
        double b = Math.sin(Math.toRadians(lon2-lon1)/2); b *= b;
        return 12742 * Math.asin(Math.min(1, Math.sqrt(a + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*b)));
    }

    private void notifyAvailable(SharedPreferences prefs, String productId, String product, String store) {
        if (!prefs.getBoolean("alert", true)) return;
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channel = "coop_pokemon_availability";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "Coop Pokémon-Verfügbarkeit", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(app, channel) : new Notification.Builder(app);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Coop: neu als verfügbar gemeldet")
                .setContentText(product + " · " + store).setAutoCancel(true);
        manager.notify(("coop:" + productId + ":" + store).hashCode() & 0x7fffffff, builder.build());
    }
}
