package ch.pokemoncardfinder.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Refreshes already-mapped Migros product/store pairs without opening an Activity.
 * Unknown or incomplete API responses are never written as stock results.
 */
public final class BackgroundMigrosWorker extends Worker {
    private static final String PREFS = "MainActivity";
    private static final String BASE = "https://www.migros.ch";
    private static final String CHANNEL = "pokemon_inventory_alerts";
    private static final int BATCH_SIZE = 25;
    private final Context app;

    public BackgroundMigrosWorker(Context context, WorkerParameters params) {
        super(context, params);
        app = context.getApplicationContext();
    }

    @Override public Result doWork() {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_refresh_enabled", true)) {
            setState(prefs, "Automatische Aktualisierung ausgeschaltet.", false);
            return Result.success();
        }
        if (!BackgroundRefreshScheduler.isAllowedRefreshTime()) {
            setState(prefs, "Pausiert: automatische Abfragen laufen nur Mo–Sa zwischen 08:00 und 21:00 Uhr.", false);
            return Result.success();
        }
        try {
            JSONArray allProducts = readProducts(prefs);
            JSONArray allBranches = new JSONObject(readAsset("branches.json")).optJSONArray("branches");
            JSONObject branchCantons = new JSONObject(readAsset("branch-cantons.json")).optJSONObject("branches");
            if (allBranches == null || branchCantons == null || allProducts.length() == 0) {
                setState(prefs, "App-Katalog konnte nicht geladen werden.", false);
                return Result.retry();
            }

            JSONObject productIds = new JSONObject(prefs.getString("migros_api_product_uids", "{}"));
            JSONObject branchIds = new JSONObject(prefs.getString("migros_api_cost_centers", "{}"));
            JSONArray selectedBranches = selectedBranches(prefs, allBranches, branchCantons);
            JSONArray selectedProducts = selectedProducts(prefs, allProducts);
            if (selectedBranches.length() == 0 || selectedProducts.length() == 0) {
                setState(prefs, "Keine ausgewählten Produkte oder Filialen.", false);
                return Result.success();
            }

            ArrayList<String> waitingProducts = new ArrayList<>();
            JSONArray mappedProducts = new JSONArray();
            for (int i = 0; i < selectedProducts.length(); i++) {
                JSONObject p = selectedProducts.optJSONObject(i);
                if (p == null || p.optBoolean("online_only", false)) continue;
                String productId = p.optString("id");
                if (!productIds.optString(productId, "").isEmpty()) mappedProducts.put(p);
                else waitingProducts.add(p.optString("name", productId));
            }
            ArrayList<String> missingBranches = new ArrayList<>();
            for (int i = 0; i < selectedBranches.length(); i++) {
                JSONObject b = selectedBranches.optJSONObject(i);
                if (b == null || !branchIds.has(b.optString("id"))) missingBranches.add(b == null ? "Filiale" : b.optString("name", b.optString("id")));
            }
            if (!missingBranches.isEmpty()) {
                setState(prefs, "Noch nicht alle ausgewählten Migros-Filialkennungen erfasst. Öffne die App und führe eine normale Filialabfrage aus.", false);
                return Result.success();
            }
            if (mappedProducts.length() == 0) {
                String detail = waitingProducts.isEmpty() ? "" : " Artikel warten auf eine erste normale App-Abfrage.";
                setState(prefs, "Noch keine geeigneten Produktkennungen für die Hintergrundabfrage vorhanden." + detail, false);
                return Result.success();
            }
            selectedProducts = mappedProducts;

            String token = fetchGuestToken();
            if (token == null || token.isEmpty()) {
                setState(prefs, "Migros hat kein Hintergrund-Zugriffstoken geliefert.", false);
                return Result.retry();
            }

            JSONObject allStock = new JSONObject(prefs.getString("all_stock_json", "{}"));
            JSONObject checkedTimes = new JSONObject(prefs.getString("all_checked_json", "{}"));
            int saved = 0;
            for (int pi = 0; pi < selectedProducts.length(); pi++) {
                JSONObject product = selectedProducts.getJSONObject(pi);
                if (product.optBoolean("online_only", false)) continue;
                String appProductId = product.getString("id");
                String migrosUid = productIds.getString(appProductId);
                JSONObject productRows = allStock.optJSONObject(appProductId);
                if (productRows == null) productRows = new JSONObject();

                for (int start = 0; start < selectedBranches.length(); start += BATCH_SIZE) {
                    JSONArray ids = new JSONArray();
                    LinkedHashMap<String, JSONObject> chunkBranches = new LinkedHashMap<>();
                    for (int j = start; j < Math.min(start + BATCH_SIZE, selectedBranches.length()); j++) {
                        JSONObject branch = selectedBranches.getJSONObject(j);
                        String branchId = branch.getString("id");
                        ids.put(branchIds.getString(branchId));
                        chunkBranches.put(branchIds.getString(branchId), branch);
                    }
                    JSONObject response = fetchAvailability(migrosUid, ids, token);
                    JSONArray availabilities = response.optJSONArray("availabilities");
                    if (availabilities == null) throw new IllegalStateException("Migros-Antwort ohne Filialbestände");
                    long now = System.currentTimeMillis();
                    for (int ai = 0; ai < availabilities.length(); ai++) {
                        JSONObject availability = availabilities.optJSONObject(ai);
                        if (availability == null) continue;
                        String costCenter = availability.optString("id");
                        JSONObject branch = chunkBranches.get(costCenter);
                        if (branch == null || !availability.has("stock")) continue;
                        Integer quantity = numericStock(availability.opt("stock"));
                        if (quantity == null) continue;
                        JSONObject row = new JSONObject();
                        String state = quantity > 0 ? "estimated_in_stock" : "out_of_stock";
                        row.put("product_id", appProductId);
                        row.put("branch_id", branch.optString("id"));
                        row.put("name", branch.optString("name"));
                        row.put("address", branch.optString("address"));
                        row.put("status", state);
                        if (quantity > 0) row.put("quantity", quantity);
                        row.put("source", "Migros-Verfügbarkeitsdienst · Hintergrundprüfung");
                        row.put("checked_at", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(now)));
                        JSONObject old = productRows.optJSONObject(branch.optString("id"));
                        addSignalAndNotify(prefs, appProductId, product, branch, old, row, quantity, now);
                        productRows.put(branch.optString("id"), row);
                        saved++;
                    }
                }
                if (saved > 0) {
                    allStock.put(appProductId, productRows);
                    checkedTimes.put(appProductId, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()));
                    prefs.edit().putString("all_stock_json", allStock.toString())
                        .putString("all_checked_json", checkedTimes.toString()).commit();
                }
            }
            if (saved == 0) {
                setState(prefs, "Migros-Antwort enthielt keine lesbaren Filialbestände; alte Daten bleiben erhalten.", false);
                return Result.retry();
            }
            prefs.edit().putLong("auto_refresh_anchor_ms", System.currentTimeMillis()).commit();
            String state = "Hintergrundprüfung gespeichert: " + saved + " Filialbestände.";
            if (!waitingProducts.isEmpty()) state += " Artikel warten auf ihre erste normale App-Abfrage (" + waitingProducts.size() + ").";
            setState(prefs, state, true);
            return Result.success();
        } catch (Exception ex) {
            setState(prefs, "Hintergrundprüfung fehlgeschlagen; gespeicherte Bestände blieben erhalten.", false);
            return Result.retry();
        }
    }

    private JSONArray readProducts(SharedPreferences prefs) throws Exception {
        JSONObject catalog = new JSONObject(readAsset("catalog.json"));
        JSONArray products = catalog.optJSONArray("products");
        JSONArray result = new JSONArray();
        if (products != null) for (int i = 0; i < products.length(); i++) result.put(products.optJSONObject(i));
        JSONArray discovered = new JSONArray(prefs.getString("discovered_products_json", "[]"));
        for (int i = 0; i < discovered.length(); i++) {
            JSONObject candidate = discovered.optJSONObject(i);
            if (candidate == null) continue;
            boolean duplicate = false;
            for (int j = 0; j < result.length(); j++) if (candidate.optString("id").equals(result.optJSONObject(j).optString("id"))) duplicate = true;
            if (!duplicate) result.put(candidate);
        }
        return result;
    }

    private JSONArray selectedProducts(SharedPreferences prefs, JSONArray products) {
        String selected = prefs.getString("checked_product_ids", "");
        Set<String> ids = new HashSet<>();
        if (!selected.isEmpty()) for (String id : selected.split(",")) if (!id.trim().isEmpty()) ids.add(id.trim());
        JSONArray result = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            boolean selectedByUser = ids.isEmpty() ? i == 0 : ids.contains(p.optString("id"));
            boolean recentlyNew = !p.optBoolean("online_only", false)
                    && p.optLong("new_until_ms", 0) > now;
            if (selectedByUser || recentlyNew) result.put(p);
        }
        return result;
    }

    private JSONArray selectedBranches(SharedPreferences prefs, JSONArray branches, JSONObject cantons) throws Exception {
        String stored = prefs.getString("query_branch_ids", "");
        Set<String> explicit = new HashSet<>();
        if (!stored.isEmpty()) for (String id : stored.split(",")) explicit.add(id.trim());
        String savedRegions = prefs.getString("selected_regions", null);
        Set<String> regions = new HashSet<>();
        if (savedRegions == null || savedRegions.isEmpty()) {
            JSONArray all = new JSONArray();
            for (int i = 0; i < branches.length(); i++) {
                JSONObject branch = branches.optJSONObject(i);
                if (branch != null && (explicit.isEmpty() || explicit.contains(branch.optString("id")))) all.put(branch);
            }
            return all;
        }
        if (!savedRegions.equals("__none__")) for (String region : savedRegions.split(",")) regions.add(region);
        JSONObject customMap = new JSONObject();
        JSONArray custom = new JSONArray(prefs.getString("custom_regions_json", "[]"));
        for (int i = 0; i < custom.length(); i++) {
            JSONObject region = custom.optJSONObject(i);
            if (region != null) customMap.put(region.optString("id"), region);
        }

        JSONArray result = new JSONArray();
        for (int i = 0; i < branches.length(); i++) {
            JSONObject branch = branches.optJSONObject(i);
            if (branch == null) continue;
            String id = branch.optString("id");
            if (!explicit.isEmpty() && !explicit.contains(id)) continue;
            String canton = cantons.optString(id, "INT");
            boolean include = regions.contains(canton);
            for (String regionId : regions) {
                if (!regionId.startsWith("custom:")) continue;
                JSONObject customRegion = customMap.optJSONObject(regionId.substring(7));
                JSONArray stores = customRegion == null ? null : customRegion.optJSONArray("store_ids");
                if (stores != null) for (int j = 0; j < stores.length(); j++) if (id.equals(stores.optString(j))) include = true;
            }
            if (include) result.put(branch);
        }
        return result;
    }

    private Integer numericStock(Object raw) {
        if (raw instanceof Number) return Math.max(0, ((Number) raw).intValue());
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (value.matches("\\d+")) try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String fetchGuestToken() throws Exception {
        HttpURLConnection connection = open(BASE + "/authentication/public/v1/api/guest?authorizationNotRequired=true", null);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;
            return connection.getHeaderField("leshopch");
        } finally { connection.disconnect(); }
    }

    private JSONObject fetchAvailability(String uid, JSONArray costCenters, String token) throws Exception {
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < costCenters.length(); i++) values.add(costCenters.getString(i));
        String query = URLEncoder.encode(join(values, ","), "UTF-8");
        HttpURLConnection connection = open(BASE + "/store-availability/public/v2/availabilities/products/"
                + URLEncoder.encode(uid, "UTF-8") + "?costCenterIds=" + query, token);
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (code < 200 || code >= 300) throw new java.io.IOException("Migros HTTP " + code);
            return new JSONObject(body);
        } finally { connection.disconnect(); }
    }

    private HttpURLConnection open(String address, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept-Language", "de-CH,de;q=0.9");
        if (token != null) connection.setRequestProperty("leshopch", token);
        return connection;
    }

    private String readAsset(String name) throws Exception {
        try (InputStream input = app.getAssets().open(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder value = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
            return value.toString();
        }
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder value = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
            return value.toString();
        }
    }

    private String join(ArrayList<String> items, String separator) {
        StringBuilder value = new StringBuilder();
        for (String item : items) {
            if (value.length() > 0) value.append(separator);
            value.append(item);
        }
        return value.toString();
    }

    private void addSignalAndNotify(SharedPreferences prefs, String productId, JSONObject product,
            JSONObject branch, JSONObject old, JSONObject row, int quantity, long now) throws Exception {
        if (old == null) return;
        String oldStatus = old.optString("status");
        int oldQuantity = oldStatus.equals("out_of_stock") ? 0 : old.optInt("quantity", -1);
        boolean oldAvailable = oldStatus.equals("in_stock") || oldStatus.equals("estimated_in_stock");
        boolean currentAvailable = quantity > 0;
        String signal = "";
        if (!oldAvailable && currentAvailable) signal = "restock";
        else if (oldStatus.equals("estimated_in_stock") && currentAvailable && quantity > oldQuantity) signal = "up";
        else if (oldStatus.equals("estimated_in_stock") && quantity < oldQuantity) signal = "down";
        else if (oldStatus.equals("estimated_in_stock") && quantity == 0) signal = "down";
        if (signal.isEmpty()) {
            if (old.has("stock_signal")) {
                row.put("stock_signal", old.optString("stock_signal"));
                row.put("stock_previous_quantity", old.optInt("stock_previous_quantity", -1));
                row.put("stock_current_quantity", old.optInt("stock_current_quantity", -1));
                row.put("stock_delta_quantity", old.optInt("stock_delta_quantity", 0));
                row.put("stock_change_at", old.optLong("stock_change_at", 0));
            }
            return;
        }
        int delta = oldQuantity >= 0 ? quantity - oldQuantity : 0;
        row.put("stock_signal", signal);
        row.put("stock_previous_quantity", oldQuantity);
        row.put("stock_current_quantity", quantity);
        row.put("stock_delta_quantity", delta);
        row.put("stock_change_at", now);
        JSONArray events = new JSONArray(prefs.getString("stock_events_json", "[]"));
        JSONObject event = new JSONObject();
        event.put("product_id", productId);
        event.put("branch_id", branch.optString("id"));
        event.put("branch_name", branch.optString("name"));
        event.put("previous", oldQuantity);
        event.put("current", quantity);
        event.put("delta", delta);
        event.put("kind", signal);
        event.put("at", now);
        events.put(event);
        prefs.edit().putString("stock_events_json", events.toString()).commit();

        String prefKey = signal.equals("restock") ? "notify_restock" : signal.equals("up") ? "notify_stock_increase" : "notify_stock_decrease";
        if (!prefs.getBoolean(prefKey, signal.equals("restock") || signal.equals("up"))) return;
        if (prefs.getBoolean("notify_product_filter_enabled", false)) {
            String ids = prefs.getString("notify_product_ids", "");
            boolean found = false;
            for (String id : ids.split(",")) if (productId.equals(id)) found = true;
            if (!found) return;
        }
        postNotification(productId, product.optString("name", productId), branch.optString("name"), signal, delta);
    }

    private void postNotification(String productId, String product, String branch, String signal, int delta) {
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Pokémon Card Finder", NotificationManager.IMPORTANCE_DEFAULT));
        String title = signal.equals("restock") ? "Pokémon-Artikel wieder verfügbar" : signal.equals("up") ? "Bestand gestiegen" : "Bestand gesunken";
        String message = product + " · " + branch + (delta == 0 ? "" : " · " + (delta > 0 ? "+" : "") + delta + " Stück");
        Intent intent = new Intent(app, MainActivity.class).putExtra("open_product_id", productId);
        PendingIntent pending = PendingIntent.getActivity(app, (int) (System.currentTimeMillis() & 0x7fffffff), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(app, CHANNEL) : new Notification.Builder(app);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(pending).setAutoCancel(true);
        manager.notify("pcf:bg:" + System.currentTimeMillis(), 0, builder.build());
    }

    private void setState(SharedPreferences prefs, String state, boolean success) {
        SharedPreferences.Editor editor = prefs.edit().putString("background_refresh_state", state);
        if (success) editor.putLong("background_refresh_last_ms", System.currentTimeMillis());
        editor.commit();
    }
}
