package ch.pokemoncardfinder.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

/**
 * Separate Coop data source. Only positive store availability is confirmed.
 * The unofficial read-only Coop interface does not publish exact quantities.
 */
public class CoopActivity extends Activity {
    private static final String API = "https://www.coop.ch/rest/v2/coopathome";
    private static final String COOP_CITY_CATEGORY = "https://www.coop-city.ch/de/pokemon/c/BRAND_pokemon";
    private static final long AUTO_REFRESH_INTERVAL_MS = 90L * 60L * 1000L;
    private static final long NEW_PRODUCT_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String[][] START_PRODUCTS = {
        {"5757239","Pokémon Booster Scarlet & Violet","https://www.coop.ch/de/baby-kind/spielwaren/figuren/pokemon-booster--zufalls-pack-scarlet-violet/p/5757239"},
        {"5757281","Pokémon E Three Pack","https://www.coop.ch/de/baby-kind/spielwaren/gesellschaftsspiele/kartenspiele/pokemon-e-three-pack/p/5757281"},
        {"7416934","Pokémon Special Illustration Collection","https://www.coop.ch/de/baby-kind/spielwaren/gesellschaftsspiele/kartenspiele/pokemon-special-illustration-collection/p/7416934"}
    };
    // Approximate search centres; do not imply complete canton-level store coverage.
    private static final String[] AREAS = {"BE · Bern","FR · Freiburg","ZH · Zürich","AG · Aargau","BS · Basel","BL · Liestal",
        "LU · Luzern","SO · Solothurn","SG · St. Gallen","VD · Lausanne","GE · Genf","NE · Neuenburg","JU · Delsberg",
        "VS · Sitten","TI · Lugano","GR · Chur","TG · Frauenfeld","SZ · Schwyz","ZG · Zug","SH · Schaffhausen",
        "AR · Herisau","AI · Appenzell","GL · Glarus","NW · Stans","OW · Sarnen","UR · Altdorf"};
    private static final double[][] CENTRES = {
        {46.948,7.447},{46.807,7.162},{47.378,8.542},{47.391,8.048},{47.560,7.590},{47.484,7.735},
        {47.052,8.307},{47.208,7.538},{47.423,9.375},{46.519,6.632},{46.204,6.144},{46.990,6.931},{47.365,7.344},
        {46.233,7.360},{46.004,8.951},{46.850,9.532},{47.557,8.896},{47.021,8.653},{47.168,8.516},{47.697,8.635},
        {47.386,9.279},{47.332,9.409},{47.041,9.068},{46.957,8.367},{46.896,8.246},{46.881,8.644}
    };
    private static final int[] RADII = {2,3,5,10,15,20,30,50,100};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<JSONObject> products = new ArrayList<>();
    private final LinkedHashMap<String,JSONObject> stores = new LinkedHashMap<>();
    private final LinkedHashSet<String> chosenStores = new LinkedHashSet<>();
    private android.content.SharedPreferences prefs;
    private Spinner areaSpinner, radiusSpinner, productSpinner;
    private TextView message, summary, countLabel;
    private Button runButton;
    private LinearLayout list;
    private boolean working, visible, manualSelection, scanningCoopCity;
    private int apiDiscoveryMatches;
    private String apiDiscoveryError="";
    private WebView discoveryWebView;
    private int area, radius;
    private double latitude, longitude;
    private final Runnable autoCheck = new Runnable() {
        @Override public void run(){
            if(visible && !working && prefs.getBoolean("auto",false) &&
                    prefs.getLong("last_checked",0)>0 &&
                    System.currentTimeMillis()-prefs.getLong("last_checked",0)>=AUTO_REFRESH_INTERVAL_MS &&
                    BackgroundRefreshScheduler.isAllowedRefreshTime())
                check(savedSelection());
            if(visible)handler.postDelayed(this,60_000);
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("coop_finder",MODE_PRIVATE);
        area=Math.min(AREAS.length-1,Math.max(0,prefs.getInt("area",0)));
        radius=prefs.getInt("radius",15);
        latitude=Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToRawLongBits(CENTRES[area][0])));
        longitude=Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToRawLongBits(CENTRES[area][1])));
        try {
            JSONArray all=new JSONArray(prefs.getString("products","[]"));
            for(int i=0;i<all.length();i++)if(all.optJSONObject(i)!=null)mergeProduct(all.optJSONObject(i));
            JSONArray cache=new JSONArray(prefs.getString("stores","[]"));
            for(int i=0;i<cache.length();i++){JSONObject s=cache.optJSONObject(i);if(s!=null)stores.put(s.optString("id"),s);}
            JSONArray selected=new JSONArray(prefs.getString("selected_stores","[]"));
            for(int i=0;i<selected.length();i++)chosenStores.add(selected.optString(i));
            manualSelection=prefs.getBoolean("manual_selection",false);
        }catch(Exception ignored){}
        for(String[] seed:START_PRODUCTS)try {
            mergeProduct(new JSONObject().put("id",seed[0]).put("name",seed[1]).put("url",seed[2]));
        }catch(Exception ignored){}
        build();
        refreshProducts();
        render();
    }
    @Override protected void onResume(){super.onResume();visible=true;BackgroundRefreshScheduler.syncCoop(this,prefs.getBoolean("auto",false));handler.postDelayed(autoCheck,1000);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(autoCheck);super.onPause();}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView txt(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.DKGRAY);return t;}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(24));root.setBackgroundColor(Color.rgb(248,248,252));
        Button back=new Button(this);back.setText("← Migros");back.setOnClickListener(v->finish());root.addView(back);
        TextView heading=txt("Pokémon Card Finder · Coop",22);heading.setTypeface(null,1);root.addView(heading);
        String version="?";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        root.addView(txt("Version v"+version+" · Coop separat von Migros",11));
        root.addView(txt("Coop meldet hier nur verfügbare Filialen, keine Stückzahlen. Nicht aufgeführte Filialen sind ungeklärt, nicht bestätigt ausverkauft. Das Suchgebiet ist ein Radius um den gewählten Mittelpunkt, nicht der ganze Kanton.",11));
        areaSpinner=new Spinner(this);areaSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,AREAS));
        areaSpinner.setSelection(area);root.addView(areaSpinner);
        String[] rlabels=new String[RADII.length];int selectedRadius=0;
        for(int i=0;i<RADII.length;i++){rlabels[i]=RADII[i]+" km";if(radius==RADII[i])selectedRadius=i;}
        radiusSpinner=new Spinner(this);radiusSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rlabels));
        radiusSpinner.setSelection(selectedRadius);root.addView(radiusSpinner);
        areaSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            boolean first=true;
            @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int position,long id){
                if(first){first=false;return;}area=position;latitude=CENTRES[area][0];longitude=CENTRES[area][1];
                prefs.edit().putInt("area",area).apply();saveCentre();clearScope();render();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
        });
        radiusSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            boolean first=true;
            @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int position,long id){
                if(first){first=false;return;}radius=RADII[position];prefs.edit().putInt("radius",radius).apply();clearScope();render();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
        });
        Button map=new Button(this);map.setText("Radiuskarte / Mittelpunkt wählen");map.setOnClickListener(v->showMap());root.addView(map);
        Button chooseStores=new Button(this);chooseStores.setText("Einzelne Coop-Filialen auswählen");chooseStores.setOnClickListener(v->pickStores());root.addView(chooseStores);
        countLabel=txt("",11);root.addView(countLabel);
        Button discover=new Button(this);discover.setText("Neue Coop-Pokémon-Produkte suchen");discover.setOnClickListener(v->discoverProducts());root.addView(discover);
        Button add=new Button(this);add.setText("Coop-Produktlink / Artikelnummer hinzufügen");add.setOnClickListener(v->addProduct());root.addView(add);
        productSpinner=new Spinner(this);root.addView(productSpinner);
        productSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){render();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
        });
        runButton=new Button(this);runButton.setText("Ausgewählte Coop-Produkte prüfen");runButton.setOnClickListener(v->pickProducts());root.addView(runButton);
        Button auto=new Button(this);auto.setText("Alle 1.5 Std. (08–21, Mo–Sa): "+(prefs.getBoolean("auto",false)?"EIN":"AUS"));
        BackgroundRefreshScheduler.syncCoop(this,prefs.getBoolean("auto",false));
        auto.setOnClickListener(v->{boolean x=!prefs.getBoolean("auto",false);prefs.edit().putBoolean("auto",x).apply();
            BackgroundRefreshScheduler.syncCoop(CoopActivity.this,x);
            auto.setText("Alle 1.5 Std. (08–21, Mo–Sa): "+(x?"EIN":"AUS"));});
        root.addView(auto);
        message=txt("Coop wurde noch nicht live abgefragt.",12);root.addView(message);
        summary=txt("",12);root.addView(summary);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
        discoveryWebView=new WebView(this);discoveryWebView.getSettings().setJavaScriptEnabled(true);
        discoveryWebView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                if(!scanningCoopCity||url==null||!url.startsWith(COOP_CITY_CATEGORY))return;
                view.postDelayed(()->view.evaluateJavascript(
                    "(function(){var out=[];var seen={};document.querySelectorAll('a[href*=\\\"/p/\\\"]').forEach(function(a){var h=a.href||'';var m=h.match(/\\/p\\/(\\d+)/);if(!m||seen[m[1]])return;var card=a.closest('article')||a.closest('[class*=product]')||a.parentElement.parentElement;var text=((card&&card.innerText)||a.innerText||'').replace(/\\s+/g,' ').trim();if(!/pok[eé]mon/i.test(text))return;var img=card&&card.querySelector('img');var name=(a.innerText||text).replace(/\\s+/g,' ').trim();out.push({id:m[1],name:name,url:h,image_url:img?(img.currentSrc||img.src||''):''});seen[m[1]]=true;});return JSON.stringify(out);})()",
                    value->{try{
                        Object decoded=new org.json.JSONTokener(value).nextValue();
                        JSONArray rows=new JSONArray(decoded instanceof String?(String)decoded:"[]");
                        int added=0;
                        for(int i=0;i<rows.length();i++){
                            JSONObject raw=rows.optJSONObject(i);if(raw==null)continue;
                            JSONObject p=new JSONObject().put("id",raw.optString("id")).put("name",raw.optString("name"))
                                .put("url",raw.optString("url")).put("image_url",absoluteCoopImage(raw.optString("image_url")));
                            if(p.optString("id").matches("\\d{4,12}")&&!p.optString("name").isEmpty()){mergeProduct(p);added++;}
                        }
                        finishCoopCityScan(added,null);
                    }catch(Exception e){finishCoopCityScan(0,e.getMessage());}}),1200);
            }
        });
        root.addView(discoveryWebView,new LinearLayout.LayoutParams(1,1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
    }
    private void saveCentre(){
        prefs.edit().putLong("lat",Double.doubleToRawLongBits(latitude)).putLong("lon",Double.doubleToRawLongBits(longitude)).apply();
    }
    private void clearScope(){
        stores.clear();chosenStores.clear();manualSelection=false;
        prefs.edit().remove("stores").remove("selected_stores").putBoolean("manual_selection",false).apply();
    }
    private void setWorking(boolean value,String text){working=value;runButton.setEnabled(!value);if(text!=null)message.setText(text);}
    private void mergeProduct(JSONObject p){
        String id=p.optString("id");if(!id.matches("\\d{4,12}"))return;
        for(JSONObject old:products)if(old.optString("id").equals(id)){
            try{
                if(!p.optString("name").isEmpty())old.put("name",p.optString("name"));
                if(!p.optString("url").isEmpty())old.put("url",p.optString("url"));
                if(!p.optString("image_url").isEmpty())old.put("image_url",p.optString("image_url"));
                if(p.has("price"))old.put("price",p.optDouble("price"));
            }catch(Exception ignored){}
            return;
        }
        try{if(!p.has("new_until_ms"))p.put("new_until_ms",System.currentTimeMillis()+NEW_PRODUCT_WINDOW_MS);}catch(Exception ignored){}
        products.add(p);
    }
    private boolean isNewProduct(JSONObject p){return p!=null&&p.optLong("new_until_ms",0)>System.currentTimeMillis();}
    private String productLabel(JSONObject p){return p.optString("name")+" · "+p.optString("id")+(isNewProduct(p)?" · NEU":"");}
    private void saveProducts(){
        JSONArray result=new JSONArray();for(JSONObject p:products)result.put(p);
        prefs.edit().putString("products",result.toString()).apply();
    }
    private String productImageUrl(JSONObject raw){
        String[] direct={"image_url","imageUrl","image","picture","thumbnail"};
        for(String key:direct){String value=raw.optString(key,"");if(value.startsWith("http")||value.startsWith("//")||value.startsWith("/"))return absoluteCoopImage(value);}
        JSONArray images=raw.optJSONArray("images");
        if(images!=null)for(int i=0;i<images.length();i++){
            JSONObject item=images.optJSONObject(i);if(item==null)continue;
            String value=item.optString("url",item.optString("src",item.optString("image","")));
            if(!value.isEmpty())return absoluteCoopImage(value);
        }
        JSONObject image=raw.optJSONObject("image");
        if(image!=null){String value=image.optString("url",image.optString("src",""));if(!value.isEmpty())return absoluteCoopImage(value);}
        return "";
    }
    private String absoluteCoopImage(String value){
        if(value.startsWith("//"))return "https:"+value;
        if(value.startsWith("/"))return "https://www.coop.ch"+value;
        return value;
    }
    private void loadProductImage(ImageView target,String imageUrl){
        if(target==null)return;target.setTag(imageUrl);target.setImageDrawable(null);
        if(imageUrl==null||imageUrl.isEmpty())return;
        new Thread(()->{try(java.io.InputStream stream=new URL(imageUrl).openStream()){
            Bitmap bitmap=BitmapFactory.decodeStream(stream);if(bitmap!=null)runOnUiThread(()->{
                if(imageUrl.equals(target.getTag()))target.setImageBitmap(bitmap);
            });
        }catch(Exception ignored){}}).start();
    }
    private class CoopProductSpinnerAdapter extends ArrayAdapter<JSONObject>{
        CoopProductSpinnerAdapter(){super(CoopActivity.this,android.R.layout.simple_spinner_item,products);setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);}
        @Override public View getView(int position,View recycled,android.view.ViewGroup parent){return row(position,dp(54));}
        @Override public View getDropDownView(int position,View recycled,android.view.ViewGroup parent){return row(position,dp(64));}
        private View row(int position,int height){
            JSONObject product=getItem(position);LinearLayout box=new LinearLayout(CoopActivity.this);
            box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(android.view.Gravity.CENTER_VERTICAL);
            box.setPadding(dp(10),dp(4),dp(10),dp(4));
            ImageView image=new ImageView(CoopActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.rgb(242,242,247));box.addView(image,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView label=txt(productLabel(product),14);label.setMaxLines(2);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(12);box.addView(label,lp);
            loadProductImage(image,product.optString("image_url"));box.setMinimumHeight(height);return box;
        }
    }

    private void refreshProducts(){
        if(productSpinner==null)return;
        int selected=productSpinner.getSelectedItemPosition();
        productSpinner.setAdapter(new CoopProductSpinnerAdapter());
        if(selected>=0&&selected<products.size())productSpinner.setSelection(selected);
    }
    private JSONObject product(JSONObject raw){
        try{
            String id=raw.optString("code");if(!id.matches("\\d{4,12}"))return null;
            JSONObject p=new JSONObject().put("id",id).put("name",raw.optString("title",raw.optString("name","Pokémon "+id)));
            String path=raw.optString("categoryPathForTracking");
            if(!path.isEmpty())p.put("url","https://www.coop.ch/de/"+path.replaceAll("^/+|/+$","")+"/p/"+id);
            JSONObject price=raw.optJSONObject("price");if(price!=null&&price.has("value"))p.put("price",price.optDouble("value"));
            String image=productImageUrl(raw);if(!image.isEmpty())p.put("image_url",image);
            return p;
        }catch(Exception e){return null;}
    }
    private boolean relevant(JSONObject raw){
        String title=(raw.optString("name")+" "+raw.optString("title")).toLowerCase(Locale.ROOT);
        return (title.contains("pokemon")||title.contains("pokémon")) &&
            title.matches("(?s).*(booster|blister|tcg|sammelkarte|trading|card|karte|collection|kollektion|tin|deck|box|bundle|portfolio|trainer|display|sticker|three.pack).*");
    }
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    private JSONObject request(String path)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(API+path).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(16000);conn.setUseCaches(false);
        conn.setRequestProperty("User-Agent","Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148");
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Accept-Language","de-CH,de;q=0.9");
        try{
            int status=conn.getResponseCode();
            if(status==403||status==429||status==503)throw new Exception("Coop-Schnittstelle blockiert (HTTP "+status+")");
            if(status<200||status>=300)throw new Exception("Coop HTTP "+status);
            StringBuilder buffer=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"))){
                String line;while((line=reader.readLine())!=null){
                    buffer.append(line);if(buffer.length()>2_000_000)throw new Exception("Antwort zu gross");
                }
            }
            if(buffer.length()==0||buffer.charAt(0)=='<')throw new Exception("Kein lesbares JSON von Coop");
            return new JSONObject(buffer.toString());
        }finally{conn.disconnect();}
    }
    private void discoverProducts(){
        if(working)return;apiDiscoveryError="";setWorking(true,"Coop-Produktsuche läuft …");
        new Thread(()->{
            int matched=0;
            try{
                for(String keyword:Arrays.asList("Pokemon","Pokémon")){
                    for(int page=0;page<3;page++){
                        JSONObject response=request("/products/search/"+enc(keyword)+"?currentPage="+page+"&pageSize=50&query=availableOnline:false&language=de");
                        JSONArray rows=response.optJSONArray("products");
                        if(rows==null||rows.length()==0)break;
                        for(int i=0;i<rows.length();i++){
                            JSONObject raw=rows.optJSONObject(i);if(raw==null||!relevant(raw))continue;
                            JSONObject p=product(raw);if(p!=null){mergeProduct(p);matched++;}
                        }
                        if(rows.length()<50)break;
                        Thread.sleep(400);
                    }
                }
                int total=matched;
                runOnUiThread(()->{
                    apiDiscoveryMatches=total;scanningCoopCity=true;
                    message.setText("Prüfe zusätzlich die Coop-City-Pokémon-Kategorieseite …");
                    discoveryWebView.loadUrl(COOP_CITY_CATEGORY);
                    handler.postDelayed(()->{if(scanningCoopCity)finishCoopCityScan(0,"Zeitüberschreitung");},25000);
                });
            }catch(Exception e){
                int total=matched;String apiError=e.getMessage();
                runOnUiThread(()->{
                    apiDiscoveryMatches=total;apiDiscoveryError=apiError==null?"":apiError;
                    scanningCoopCity=true;message.setText("Coop-API eingeschränkt; prüfe zusätzlich die Coop-City-Kategorieseite …");
                    discoveryWebView.loadUrl(COOP_CITY_CATEGORY);
                    handler.postDelayed(()->{if(scanningCoopCity)finishCoopCityScan(0,"Zeitüberschreitung");},25000);
                });
            }
        }).start();
    }
    private void finishCoopCityScan(int cityMatches,String error){
        if(!scanningCoopCity)return;scanningCoopCity=false;saveProducts();refreshProducts();render();
        String suffix=error==null?"":(" · Coop City: "+error);
        String apiNote=apiDiscoveryError.isEmpty()?"":" · Coop-API: "+apiDiscoveryError;
        setWorking(false,"Coop-Suche abgeschlossen: "+apiDiscoveryMatches+" API-Treffer und "+cityMatches+
            " Artikel von Coop City verarbeitet; "+products.size()+" eindeutige Produkte gespeichert."+apiNote+suffix);
    }
    private void addProduct(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Coop-Link /p/5757239 oder 5757239");
        new AlertDialog.Builder(this).setTitle("Coop-Artikel hinzufügen").setView(input)
            .setNegativeButton("Abbrechen",null).setPositiveButton("Prüfen",(d,w)->{
                String entered=input.getText().toString().trim();
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:/p/|^)(\\d{4,12})(?:[/?#].*)?$").matcher(entered);
                if(!m.find()){message.setText("Bitte einen gültigen Coop-Link oder eine Artikelnummer angeben.");return;}
                String id=m.group(1);if(working)return;setWorking(true,"Lade Coop-Artikel "+id+" …");
                new Thread(()->{
                    try{
                        JSONObject p=product(request("/products/"+id+"?language=de"));
                        if(p==null)throw new Exception("Artikelnummer nicht gefunden");
                        runOnUiThread(()->{mergeProduct(p);saveProducts();refreshProducts();render();setWorking(false,"Artikel hinzugefügt: "+p.optString("name"));});
                    }catch(Exception e){runOnUiThread(()->setWorking(false,"Artikel nicht geladen: "+e.getMessage()));}
                }).start();
            }).show();
    }
    private LinkedHashSet<String> savedSelection(){
        LinkedHashSet<String> values=new LinkedHashSet<>();
        String csv=prefs.getString("product_selection","");
        if(!csv.isEmpty())values.addAll(Arrays.asList(csv.split(",")));
        if(values.isEmpty()&&!products.isEmpty()){
            int index=Math.max(0,Math.min(products.size()-1,productSpinner==null?0:productSpinner.getSelectedItemPosition()));
            values.add(products.get(index).optString("id"));
        }
        return values;
    }
    private void pickProducts(){
        if(working)return;
        LinkedHashSet<String> picked=savedSelection();
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);
        CheckBox all=new CheckBox(this);all.setText("Alle Produkte auswählen");all.setChecked(picked.size()==products.size());choices.addView(all);
        ArrayList<CheckBox> checks=new ArrayList<>();
        boolean[] syncing={false};
        for(JSONObject product:products){
            LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            CheckBox check=new CheckBox(this);check.setChecked(picked.contains(product.optString("id")));checks.add(check);
            check.setOnCheckedChangeListener((button,on)->{
                if(syncing[0])return;
                String id=product.optString("id");if(on)picked.add(id);else picked.remove(id);
                syncing[0]=true;all.setChecked(picked.size()==products.size());syncing[0]=false;
            });
            row.addView(check);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.rgb(242,242,247));row.addView(image,new LinearLayout.LayoutParams(dp(52),dp(52)));
            loadProductImage(image,product.optString("image_url"));
            TextView name=txt(productLabel(product),14);name.setMaxLines(2);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(8);row.addView(name,lp);
            choices.addView(row);
        }
        all.setOnCheckedChangeListener((button,on)->{
            if(syncing[0])return;
            syncing[0]=true;picked.clear();
            if(on)for(JSONObject product:products)picked.add(product.optString("id"));
            for(int i=0;i<checks.size();i++)checks.get(i).setChecked(on);
            syncing[0]=false;
        });
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(choices);
        new AlertDialog.Builder(this).setTitle("Coop-Produkte für die Abfrage")
            .setView(scroll).setNegativeButton("Abbrechen",null)
            .setPositiveButton("Abfragen",(dlg,which)->{
                if(picked.isEmpty()){message.setText("Mindestens ein Produkt auswählen.");return;}check(picked);
            }).show();
    }
    private String storeAddress(JSONObject raw){
        JSONObject address=raw.optJSONObject("address");
        if(address==null)return "";
        return (address.optString("line1")+" "+address.optString("postalCode")+" "+address.optString("town")).trim();
    }
    private LinkedHashMap<String,JSONObject> fetchStores(String productId,double lat,double lon,int radiusKm)throws Exception{
        LinkedHashMap<String,JSONObject> found=new LinkedHashMap<>();
        for(int page=0;page<6;page++){
            String url="/locations/searchAroundCoordinates?latitude="+lat+"&longitude="+lon+"&currentPage="+page+"&language=de";
            if(productId!=null)url+="&availabilityProductId="+enc(productId)+"&onlyWithAvailableProductId=true";
            JSONObject response=request(url);JSONArray rows=response.optJSONArray("locations");
            if(rows==null)rows=response.optJSONArray("stores");
            if(rows==null)throw new Exception("Coop hat keine Filialliste geliefert");
            for(int i=0;i<rows.length();i++){
                JSONObject raw=rows.optJSONObject(i);if(raw==null)continue;
                String id=raw.optString("vstId");
                JSONObject geo=raw.optJSONObject("geoPoint");
                if(id.isEmpty()||geo==null)continue;
                double y=geo.optDouble("latitude",Double.NaN),x=geo.optDouble("longitude",Double.NaN);
                if(Double.isNaN(y)||Double.isNaN(x)||distance(lat,lon,y,x)>radiusKm)continue;
                JSONObject store=new JSONObject();
                store.put("id",id);store.put("name",raw.optString("displayName",raw.optString("name","Coop "+id)));
                store.put("address",storeAddress(raw));store.put("lat",y);store.put("lon",x);
                found.put(id,store);
            }
            JSONObject pagination=response.optJSONObject("pagination");
            if(pagination==null || page+1>=pagination.optInt("totalPages",page+1))break;
            Thread.sleep(250);
        }
        return found;
    }
    private static double distance(double lat1,double lon1,double lat2,double lon2){
        double a=Math.sin(Math.toRadians(lat2-lat1)/2);a*=a;
        double b=Math.sin(Math.toRadians(lon2-lon1)/2);b*=b;
        return 12742*Math.asin(Math.min(1,Math.sqrt(a+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*b)));
    }
    private void saveStoreSelection(){
        JSONArray data=new JSONArray();for(String id:chosenStores)data.put(id);
        prefs.edit().putString("selected_stores",data.toString()).putBoolean("manual_selection",manualSelection).apply();
    }
    private void pickStores(){
        if(working)return;
        if(stores.isEmpty()){message.setText("Zuerst eine Coop-Abfrage starten, um Filialen im Radius zu laden.");return;}
        ArrayList<JSONObject> options=new ArrayList<>(stores.values());
        String[] labels=new String[options.size()+1];boolean[] checked=new boolean[labels.length];
        labels[0]="Alle Filialen im Suchradius auswählen";
        LinkedHashSet<String> draft=new LinkedHashSet<>(manualSelection?chosenStores:stores.keySet());
        checked[0]=draft.size()==stores.size();
        for(int i=0;i<options.size();i++){
            JSONObject item=options.get(i);
            labels[i+1]=item.optString("name")+" · "+item.optString("address");
            checked[i+1]=draft.contains(item.optString("id"));
        }
        new AlertDialog.Builder(this).setTitle("Coop-Abfrage-Filialen")
            .setMultiChoiceItems(labels,checked,(dlg,which,on)->{
                if(which==0){draft.clear();if(on)draft.addAll(stores.keySet());}
                else{String id=options.get(which-1).optString("id");if(on)draft.add(id);else draft.remove(id);}
                android.widget.ListView lv=((AlertDialog)dlg).getListView();
                lv.setItemChecked(0,draft.size()==stores.size());
                for(int i=0;i<options.size();i++)lv.setItemChecked(i+1,draft.contains(options.get(i).optString("id")));
            }).setNegativeButton("Abbrechen",null)
            .setPositiveButton("Speichern",(dlg,which)->{
                manualSelection=true;chosenStores.clear();chosenStores.addAll(draft);
                saveStoreSelection();render();
            }).show();
    }
    private void check(LinkedHashSet<String> selected){
        if(working||selected.isEmpty())return;
        LinkedHashSet<String> allowed=new LinkedHashSet<>(manualSelection?chosenStores:stores.keySet());
        // Empty allowed set with a fresh search means all discovered stores, unless user explicitly selected none.
        boolean restricted=manualSelection;
        final double y=latitude,x=longitude;final int r=radius;
        prefs.edit().putString("product_selection",android.text.TextUtils.join(",",selected)).apply();
        setWorking(true,"Prüfe Coop-Produkte und Filialen …");
        new Thread(()->{
            int completed=0;
            try{
                LinkedHashMap<String,JSONObject> fresh=fetchStores(null,y,x,r);
                if(fresh.isEmpty())throw new Exception("Keine lesbaren Coop-Filialen im Suchgebiet");
                runOnUiThread(()->{
                    stores.clear();stores.putAll(fresh);
                    JSONArray saved=new JSONArray();for(JSONObject item:fresh.values())saved.put(item);
                    prefs.edit().putString("stores",saved.toString()).apply();render();
                });
                JSONObject history=new JSONObject(prefs.getString("stock","{}"));
                for(String id:selected){
                    if(!hasProduct(id))continue;
                    String productName=nameFor(id);
                    final int n=completed+1;
                    runOnUiThread(()->message.setText("Coop: "+productName+" · "+n+"/"+selected.size()));
                    LinkedHashMap<String,JSONObject> positive=fetchStores(id,y,x,r);
                    JSONObject oldRows=history.optJSONObject(id);
                    if(oldRows==null)oldRows=new JSONObject();
                    JSONObject rows=new JSONObject(oldRows.toString());
                    for(Map.Entry<String,JSONObject> entry:fresh.entrySet()){
                        String sid=entry.getKey();
                        if(restricted&&!allowed.contains(sid))continue;
                        boolean nowAvailable=positive.containsKey(sid);
                        JSONObject old=oldRows.optJSONObject(sid);
                        // Only a positive response means "available"; lack of a match stays "unknown".
                        if(nowAvailable && old!=null && "not_reported".equals(old.optString("status")))
                            notifyChange(id,productName,entry.getValue().optString("name"));
                        rows.put(sid,new JSONObject().put("status",nowAvailable?"available":"not_reported")
                            .put("checked_at",System.currentTimeMillis()));
                    }
                    history.put(id,rows);
                    prefs.edit().putString("stock",history.toString())
                        .putLong("last_checked",System.currentTimeMillis()).apply();
                    completed++;
                    runOnUiThread(()->render());
                    Thread.sleep(400);
                }
                final int done=completed;
                runOnUiThread(()->{render();setWorking(false,"Coop-Abfrage abgeschlossen: "+done+" Produkte. Fehlende Verfügbarkeit bleibt ungeklärt.");});
            }catch(Exception e){
                runOnUiThread(()->{render();setWorking(false,"Coop-Abfrage nicht vollständig: "+e.getMessage()+". Alte Ergebnisse bleiben erhalten.");});
            }
        }).start();
    }
    private boolean hasProduct(String id){for(JSONObject p:products)if(id.equals(p.optString("id")))return true;return false;}
    private String nameFor(String id){for(JSONObject p:products)if(id.equals(p.optString("id")))return p.optString("name");return id;}
    private void notifyChange(String productId,String productName,String storeName){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        if(!prefs.getBoolean("alert",true))return;
        NotificationManager mgr=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        String channel="coop_pokemon_availability";
        if(Build.VERSION.SDK_INT>=26)mgr.createNotificationChannel(new NotificationChannel(channel,"Coop Pokémon-Verfügbarkeit",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Coop: neu als verfügbar gemeldet")
            .setContentText(productName+" · "+storeName).setAutoCancel(true);
        mgr.notify(("coop:"+productId+":"+storeName).hashCode()&0x7fffffff,b.build());
    }
    private void render(){
        if(list==null)return;
        countLabel.setText("Suchradius: "+radius+" km · Mittelpunkt "+String.format(Locale.ROOT,"%.4f, %.4f",latitude,longitude)
            +" · "+stores.size()+" zuletzt gefundene Filialen · "+(manualSelection?chosenStores.size()+" gezielt gewählt":"alle im Radius"));
        list.removeAllViews();
        int pos=productSpinner==null?0:productSpinner.getSelectedItemPosition();
        if(pos<0||pos>=products.size())return;
        JSONObject p=products.get(pos);
        String id=p.optString("id");
        JSONObject rowSet=null;try{rowSet=new JSONObject(prefs.getString("stock","{}")).optJSONObject(id);}catch(Exception ignored){}
        int positives=0,unknown=0,never=0;long last=prefs.getLong("last_checked",0);
        if(last>0)summary.setText("Letzte Coop-Abfrage: "+android.text.format.DateFormat.format("dd.MM.yyyy HH:mm",last)+" · Keine bestätigten Stückzahlen");
        else summary.setText("Bisher keine erfolgreich gespeicherte Coop-Abfrage.");
        if(p.has("price")){
            TextView price=txt("Coop-Preis laut zuletzt geladenem Produktdatensatz: CHF "+String.format(Locale.ROOT,"%.2f",p.optDouble("price")),12);
            list.addView(price);
        }
        if(!p.optString("url").isEmpty()){
            Button link=new Button(this);link.setText("Produkt auf coop.ch öffnen");
            link.setOnClickListener(v->{try{
                android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(p.optString("url")));
                startActivity(intent);
            }catch(Exception e){message.setText("Produktlink konnte nicht geöffnet werden.");}});
            list.addView(link);
        }
        for(JSONObject store:stores.values()){
            String sid=store.optString("id");
            if(manualSelection&&!chosenStores.contains(sid))continue;
            JSONObject stock=rowSet==null?null:rowSet.optJSONObject(sid);
            String state=stock==null?"never":stock.optString("status","never");
            if("available".equals(state))positives++;
            else if("not_reported".equals(state))unknown++;
            else never++;
            TextView item=txt(("available".equals(state)?"●  ":"○  ")+store.optString("name")
                +" · "+("available".equals(state)?"Verfügbar gemeldet":"not_reported".equals(state)?"Keine positive Meldung / unklar":"Noch ungeprüft")
                +"\n"+store.optString("address"),13);
            item.setPadding(dp(7),dp(7),dp(7),dp(7));
            if("available".equals(state))item.setTextColor(Color.rgb(23,133,56));
            list.addView(item);
        }
        summary.setText(summary.getText()+"\n"+positives+" verfügbar gemeldet · "+unknown+" ohne positive Meldung · "+never+" ungeprüft");
    }
    private void showMap(){
        final WebView web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.addJavascriptInterface(new MapBridge(),"CoopMap");
        JSONArray pins=new JSONArray();
        for(JSONObject s:stores.values())pins.put(s);
        String safePins=pins.toString().replace("</","<\\/");
        String html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>"+
            "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script></head><body style='margin:0'>"+
            "<div id='map' style='height:95vh'></div><script>const pins="+safePins+";"+
            "let m=L.map('map').setView(["+latitude+","+longitude+"],11);"+
            "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'© OpenStreetMap'}).addTo(m);"+
            "let marker=L.marker(["+latitude+","+longitude+"]).addTo(m),circle=L.circle(["+latitude+","+longitude+"],{radius:"+radius+"*1000}).addTo(m);"+
            "for(const s of pins){if(!s.lat||!s.lon)continue;"+
            "L.circleMarker([s.lat,s.lon],{radius:5,color:'#1b8f52'}).addTo(m).bindPopup(s.name||'Coop');}"+
            "m.on('click',e=>{marker.setLatLng(e.latlng);circle.setLatLng(e.latlng);CoopMap.pick(e.latlng.lat,e.latlng.lng);});"+
            "</script></body></html>";
        web.loadDataWithBaseURL("https://www.openstreetmap.org/",html,"text/html","UTF-8",null);
        new AlertDialog.Builder(this).setTitle("Coop-Radiuskarte · Tippen setzt Mittelpunkt")
            .setView(web).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(dialog,which)->{
                if(pendingMapCentre!=null){
                    latitude=pendingMapCentre[0];longitude=pendingMapCentre[1];saveCentre();clearScope();render();
                }
                pendingMapCentre=null;
            }).setOnCancelListener(d->pendingMapCentre=null).show();
    }
    private double[] pendingMapCentre;
    private class MapBridge{
        @JavascriptInterface public void pick(double lat,double lon){
            runOnUiThread(()->{if(lat>=45&&lat<=48&&lon>=5&&lon<=11)pendingMapCentre=new double[]{lat,lon};});
        }
    }
}