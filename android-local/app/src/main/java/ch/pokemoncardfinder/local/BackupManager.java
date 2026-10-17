package ch.pokemoncardfinder.local;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline, portable Migros + Coop backup.
 * Preserve preference value types and merge histories instead of replacing newer local data.
 * No cloud storage, network calls or storage permission required (Android document picker).
 */
public final class BackupManager {
    private static final String MARKER = "pokemon-card-finder-backup";
    private static final int FORMAT = 1;
    private BackupManager() {}

    public static String exportJson(SharedPreferences migros, SharedPreferences coop) throws JSONException {
        return new JSONObject()
            .put("format", MARKER)
            .put("format_version", FORMAT)
            .put("created_at_ms", System.currentTimeMillis())
            .put("migros", pack(migros.getAll()))
            .put("coop", pack(coop.getAll()))
            .toString(2);
    }

    public static String importJson(String raw, SharedPreferences migros, SharedPreferences coop)
        throws JSONException {
        JSONObject backup = new JSONObject(raw);
        if (!MARKER.equals(backup.optString("format")) || backup.optInt("format_version",-1) != FORMAT)
            throw new JSONException("Unbekanntes Backup-Format. Erwartet wird Pokémon Card Finder Backup v1.");
        Map<String,Object> incomingMigros = unpack(backup.getJSONObject("migros"));
        Map<String,Object> incomingCoop = unpack(backup.getJSONObject("coop"));
        if (incomingMigros.isEmpty() && incomingCoop.isEmpty())
            throw new JSONException("Die Datei enthält keine gespeicherten Daten.");
        Map<String,?> oldMigros = migros.getAll();
        Map<String,?> oldCoop = coop.getAll();
        // Validate and merge all JSON values before committing anything.
        Map<String,Object> mergedMigros = merge(oldMigros,incomingMigros,false);
        Map<String,Object> mergedCoop = merge(oldCoop,incomingCoop,true);
        if (!write(mergedCoop,coop))
            throw new JSONException("Der Coop-Speicher konnte nicht geschrieben werden.");
        if (!write(mergedMigros,migros)) {
            write(oldCoop,coop); // best-effort rollback
            throw new JSONException("Der Migros-Speicher konnte nicht geschrieben werden.");
        }
        return "Migros: " + incomingMigros.size() + " gesicherte Datenbereiche · Coop: "
            + incomingCoop.size() + " Datenbereiche. Historische Messpunkte wurden zusammengeführt.";
    }

    private static JSONObject pack(Map<String,?> prefs) throws JSONException {
        JSONObject out = new JSONObject();
        for (Map.Entry<String,?> e : prefs.entrySet()) {
            Object value=e.getValue();
            JSONObject field=new JSONObject();
            if (value instanceof String) field.put("type","string").put("value",value);
            else if (value instanceof Boolean) field.put("type","boolean").put("value",value);
            else if (value instanceof Integer) field.put("type","int").put("value",value);
            else if (value instanceof Long) field.put("type","long").put("value",value);
            else if (value instanceof Float) field.put("type","float").put("value",value);
            else if (value instanceof Set) {
                JSONArray items=new JSONArray();
                for (Object item : (Set<?>)value) if(item instanceof String)items.put(item);
                field.put("type","string_set").put("value",items);
            } else continue;
            out.put(e.getKey(),field);
        }
        return out;
    }

    private static Map<String,Object> unpack(JSONObject prefs) throws JSONException {
        Map<String,Object> out=new LinkedHashMap<>();
        JSONArray keys=prefs.names();if(keys==null)return out;
        for(int i=0;i<keys.length();i++){
            String key=keys.getString(i);
            JSONObject field=prefs.getJSONObject(key);
            String type=field.getString("type");
            Object value=field.get("value");
            switch(type){
                case "string":
                    if(!(value instanceof String))throw new JSONException("Ungültiger Textwert: "+key);
                    out.put(key,value);break;
                case "boolean":
                    if(!(value instanceof Boolean))throw new JSONException("Ungültiger Wahrheitswert: "+key);
                    out.put(key,value);break;
                case "int":
                    if(!(value instanceof Number))throw new JSONException("Ungültige Zahl: "+key);
                    out.put(key,((Number)value).intValue());break;
                case "long":
                    if(!(value instanceof Number))throw new JSONException("Ungültige Zahl: "+key);
                    out.put(key,((Number)value).longValue());break;
                case "float":
                    if(!(value instanceof Number))throw new JSONException("Ungültige Zahl: "+key);
                    out.put(key,((Number)value).floatValue());break;
                case "string_set":
                    JSONArray input=field.getJSONArray("value");
                    Set<String> set=new HashSet<>();
                    for(int j=0;j<input.length();j++)set.add(input.getString(j));
                    out.put(key,set);break;
                default: throw new JSONException("Unbekannter Datentyp: "+type);
            }
        }
        return out;
    }

    private static Map<String,Object> merge(Map<String,?> current, Map<String,Object> incoming,
                                             boolean coop) throws JSONException {
        Map<String,Object> result=new HashMap<>();
        for(Map.Entry<String,?> item:current.entrySet())result.put(item.getKey(),item.getValue());
        for(Map.Entry<String,Object> item:incoming.entrySet()){
            String key=item.getKey();Object older=item.getValue(), newer=result.get(key);
            if(newer==null){result.put(key,older);continue;}
            if(!(older instanceof String)||!(newer instanceof String)){
                if("last_checked".equals(key) && older instanceof Number && newer instanceof Number)
                    result.put(key,Math.max(((Number)older).longValue(),((Number)newer).longValue()));
                else if(!key.equals("auto_refresh_anchor_ms"))result.put(key,older);
                // Restore backed-up preferences, but do not replace currently retained histories.
                continue;
            }
            String previous=(String)older, present=(String)newer;
            if(!coop && ("all_stock_json".equals(key) || "all_checked_json".equals(key)
                || "live_prices_json".equals(key) || "branch_coordinate_cache".equals(key))) {
                result.put(key,mergeObject(previous,present,"all_stock_json".equals(key)));
            } else if(coop && "stock".equals(key)) {
                result.put(key,mergeObject(previous,present,true));
            } else if(key.equals("stock_snapshots_json")||key.equals("stock_events_json")){
                result.put(key,mergeHistory(previous,present,key));
            } else if(key.equals("discovered_products_json")||key.equals("custom_regions_json")
                || (coop && (key.equals("products")||key.equals("stores")))) {
                result.put(key,mergeIdList(previous,present));
            } else {
                // User settings from the backup are restored explicitly, except for ongoing
                // refresh schedule anchors, which should follow the currently running app.
                if(!key.equals("auto_refresh_anchor_ms"))result.put(key,older);
            }
        }
        return result;
    }

    // Keep the more recent/current entry for matching IDs. Unknown older IDs are restored.
    private static String mergeObject(String imported,String current,boolean nested) throws JSONException {
        JSONObject restored=new JSONObject(imported), existing=new JSONObject(current);
        JSONObject combined=new JSONObject(restored.toString());
        JSONArray keys=existing.names();if(keys==null)return combined.toString();
        for(int i=0;i<keys.length();i++){
            String id=keys.getString(i);
            JSONObject oldNested=combined.optJSONObject(id), newNested=existing.optJSONObject(id);
            if(nested && oldNested!=null && newNested!=null) {
                JSONObject entries=new JSONObject(oldNested.toString());
                JSONArray rowKeys=newNested.names();
                if(rowKeys!=null)for(int j=0;j<rowKeys.length();j++){
                    String subId=rowKeys.getString(j);
                    Object oldRow=entries.opt(subId), newRow=newNested.get(subId);
                    if(oldRow instanceof JSONObject && newRow instanceof JSONObject){
                        long olderAt=((JSONObject)oldRow).optLong("checked_at",0);
                        long latestAt=((JSONObject)newRow).optLong("checked_at",0);
                        // Numeric Coop timestamps can be compared. Migros uses localized
                        // formatted date strings; there, prefer the current device's row.
                        if(olderAt>0 && latestAt>0 && olderAt>latestAt)continue;
                    }
                    entries.put(subId,newRow);
                }
                combined.put(id,entries);
            } else combined.put(id,existing.get(id));
        }
        return combined.toString();
    }

    private static String mergeIdList(String imported,String current) throws JSONException {
        LinkedHashMap<String,Object> rows=new LinkedHashMap<>();
        for(String text:new String[]{imported,current}){
            JSONArray data=new JSONArray(text);
            for(int i=0;i<data.length();i++){
                JSONObject row=data.getJSONObject(i);String id=row.optString("id");
                if(id.isEmpty())throw new JSONException("Ein Sicherungseintrag hat keine Artikel-/Regions-ID.");
                rows.put(id,row);
            }
        }
        JSONArray combined=new JSONArray();for(Object row:rows.values())combined.put(row);
        return combined.toString();
    }

    private static String mergeHistory(String imported,String current,String key) throws JSONException {
        JSONArray combined=new JSONArray();
        Set<String> seen=new HashSet<>();
        for(String text:new String[]{imported,current}){
            JSONArray data=new JSONArray(text);
            for(int i=0;i<data.length();i++){
                JSONObject row=data.getJSONObject(i);
                String identity= row.optString("product_id")+"|"+row.optString("at")+"|"
                    + ("stock_events_json".equals(key)
                        ? row.optString("branch_id")+"|"+row.optString("kind")+"|"
                          + row.optString("previous")+"|"+row.optString("current")
                        : row.optString("scope"));
                if(seen.add(identity))combined.put(row);
            }
        }
        return combined.toString();
    }

    private static boolean write(Map<String,?> values,SharedPreferences prefs){
        SharedPreferences.Editor edit=prefs.edit();
        for(Map.Entry<String,?> item:values.entrySet()){
            String key=item.getKey();Object v=item.getValue();
            if(v instanceof String)edit.putString(key,(String)v);
            else if(v instanceof Boolean)edit.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)edit.putInt(key,(Integer)v);
            else if(v instanceof Long)edit.putLong(key,(Long)v);
            else if(v instanceof Float)edit.putFloat(key,(Float)v);
            else if(v instanceof Set){
                Set<String> items=new HashSet<>();
                for(Object value:(Set<?>)v)if(value instanceof String)items.add((String)value);
                edit.putStringSet(key,items);
            }
        }
        return edit.commit();
    }
}
