package ch.pokemoncardfinder.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Locally queries the Migros product dialog for all catalog products and bundled branches. */
public class MainActivity extends Activity {
    private static final String CATALOG_ASSET = "catalog.json";
    private static final long AUTO_REFRESH_INTERVAL_MS = 4L * 60L * 60L * 1000L;
    private TextView status, checkedAt, productTitle, productPrice, stockSummary;
    private ProgressBar loadingSpinner;
    private Button refresh;
    private Button notificationSettings;
    private Button regionPicker;
    private Button mapButton;
    private int discoveryPageIndex;
    private static final String[] DISCOVERY_URLS = {"https://www.migros.ch/de/brand/pokemon", "https://www.migros.ch/de/category/spiele-hobbys-partybedarf/spiele-puzzles/brett-kartenspiele/sammelkartenspiele"};
    private Spinner productPicker;
    private Spinner stockFilterPicker;
    private EditText branchSearch;
    private Switch autoRefreshToggle;
    private TextView autoRefreshInfo;
    private TextView subtitle;
    private ImageView productImage;
    private LinearLayout resultList;
    private WebView webView;
    private LinearLayout root;
    private JSONArray allBranches, branches, products;
    private final LinkedHashMap<String, String> regionNames = new LinkedHashMap<>();
    private final java.util.LinkedHashSet<String> selectedRegions = new java.util.LinkedHashSet<>();
    private int selectedProductIndex = 0, activeProductIndex = 0;
    private final Map<String, Map<String, JSONObject>> recordsByProduct = new LinkedHashMap<>();
    private final Map<String, String> checkedByProduct = new LinkedHashMap<>();
    private final Map<String, JSONObject> livePricesByProduct = new LinkedHashMap<>();
    private boolean waitingForPage;
    private boolean discoveringProducts;
    private boolean queryRunning;
    private boolean activityResumed;
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = this::checkAutomaticRefresh;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            allBranches = new JSONObject(readAsset("branches.json")).getJSONArray("branches");
            products = new JSONObject(readAsset(CATALOG_ASSET)).getJSONArray("products");
        } catch (Exception ex) { allBranches = new JSONArray(); products = new JSONArray(); }
        restoreBranchCoordinates();
        collectRegions();
        restoreSelectedRegions();
        applyRegionFilter();
        loadDiscoveredProducts();
        buildScreen();
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new ResultBridge(), "FinderBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (waitingForPage) {
                    waitingForPage = false;
                    if (discoveringProducts) {
                        status.setText("Suche Migros-Sortiment nach neuen Pokémon-Kartenartikeln …");
                        view.postDelayed(() -> scanProductCatalog(), 900);
                        return;
                    }
                    status.setText("Migros lädt Artikel " + (activeProductIndex+1) + "/" + products.length() + " …");
                    view.postDelayed(() -> startMigrosCheck(), 700);
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(1, 1));
        loadSavedRecords();
        refresh.setOnClickListener(v -> beginCheck(false));
        regionPicker.setOnClickListener(v -> showRegionPicker());
        notificationSettings.setOnClickListener(v -> showNotificationSettings());
        mapButton.setOnClickListener(v -> showMap());
        autoRefreshToggle.setChecked(getPreferences(MODE_PRIVATE).getBoolean("auto_refresh_enabled", true));
        autoRefreshToggle.setOnCheckedChangeListener((button, enabled) -> {
            getPreferences(MODE_PRIVATE).edit().putBoolean("auto_refresh_enabled", enabled).apply();
            if (!enabled) autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
            else scheduleAutomaticRefresh(0);
            updateAutoRefreshInfo();
        });
        ArrayList<String> names = new ArrayList<>();
        for (int i=0;i<products.length();i++) names.add(products.optJSONObject(i).optString("name"));
        productPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        productPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedProductIndex=position; renderProduct();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        branchSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderRecords(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        stockFilterPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { renderRecords(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        renderProduct();
    }

    @Override protected void onResume() {
        super.onResume(); activityResumed=true;
        if (autoRefreshToggle!=null&&autoRefreshToggle.isChecked()) scheduleAutomaticRefresh(0);
    }

    @Override protected void onPause() {
        activityResumed=false; autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        super.onPause();
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(Color.rgb(248,248,252)); getWindow().setNavigationBarColor(Color.rgb(248,248,252));
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(18),dp(16),dp(12)); root.setBackgroundColor(Color.rgb(248,248,252));
        TextView title=new TextView(this); title.setText("Pokémon Card Finder"); title.setTextSize(22); title.setTextColor(Color.rgb(35,32,48)); title.setTypeface(null,1); root.addView(title);
        subtitle=new TextView(this); subtitle.setText(products.length()+" Pokémon-TCG-Artikel · "+branches.length()+" Migros-Filialen · Anfrage direkt vom Handy"); subtitle.setTextSize(12); subtitle.setTextColor(Color.DKGRAY); root.addView(subtitle,params(dp(4)));
        regionPicker=new Button(this); regionPicker.setTextSize(11); root.addView(regionPicker,params(dp(4))); updateRegionUi();
        productPicker=new Spinner(this); root.addView(productPicker,params(dp(8)));
        LinearLayout card=new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setBackgroundColor(Color.WHITE); card.setPadding(dp(10),dp(8),dp(10),dp(8));
        productImage=new ImageView(this); productImage.setScaleType(ImageView.ScaleType.FIT_CENTER); card.addView(productImage,new LinearLayout.LayoutParams(dp(88),dp(78)));
        LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(0,-2,1); detailParams.leftMargin=dp(10); card.addView(details,detailParams);
        productTitle=new TextView(this); productTitle.setTextSize(14); productTitle.setTextColor(Color.rgb(35,32,48)); productTitle.setTypeface(null,1); details.addView(productTitle);
        productPrice=new TextView(this); productPrice.setTextSize(14); productPrice.setTextColor(Color.rgb(96,72,220)); details.addView(productPrice,params(dp(3)));
        root.addView(card,params(dp(8)));
        refresh=new Button(this); refresh.setText("Ausgewählte Filialen abfragen ("+branches.length()+")"); refresh.setEnabled(branches.length()>0); root.addView(refresh,params(dp(8)));
        LinearLayout progressLine=new LinearLayout(this); progressLine.setGravity(Gravity.CENTER_VERTICAL);
        loadingSpinner=new ProgressBar(this); loadingSpinner.setIndeterminate(true); loadingSpinner.setVisibility(View.GONE); progressLine.addView(loadingSpinner,new LinearLayout.LayoutParams(dp(22),dp(22)));
        status=new TextView(this); status.setText("Noch keine vollständige Live-Abfrage."); status.setTextSize(12); status.setTextColor(Color.DKGRAY); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(8);progressLine.addView(status,sp);root.addView(progressLine,params(dp(8)));
        checkedAt=new TextView(this); checkedAt.setText("Die Migros-Abfragen und gespeicherten Ergebnisse bleiben auf diesem Gerät."); checkedAt.setTextSize(10); checkedAt.setTextColor(Color.GRAY); root.addView(checkedAt,params(dp(3)));
        autoRefreshToggle=new Switch(this);autoRefreshToggle.setText("Automatisch alle 4 Stunden aktualisieren");autoRefreshToggle.setTextSize(11);root.addView(autoRefreshToggle,params(dp(2)));
        autoRefreshInfo=new TextView(this);autoRefreshInfo.setTextSize(9);autoRefreshInfo.setTextColor(Color.GRAY);root.addView(autoRefreshInfo,params(dp(1)));
        notificationSettings=new Button(this);notificationSettings.setText("Benachrichtigungen einstellen");root.addView(notificationSettings,params(dp(4)));
        mapButton=new Button(this);mapButton.setText("Filialkarte öffnen");root.addView(mapButton,params(dp(3)));
        stockSummary=new TextView(this); stockSummary.setTextSize(11); stockSummary.setTextColor(Color.rgb(60,56,72)); root.addView(stockSummary,params(dp(8)));
        LinearLayout filters=new LinearLayout(this); filters.setGravity(Gravity.CENTER_VERTICAL);
        branchSearch=new EditText(this); branchSearch.setSingleLine(true); branchSearch.setTextSize(12); branchSearch.setHint("Filiale oder Ort suchen");
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(0,dp(48),1); filters.addView(branchSearch,searchParams);
        stockFilterPicker=new Spinner(this); String[] filterOptions={"Alle", "Höchster Bestand", "Verfügbar", "Nicht auf Lager", "Nur online", "Nicht eindeutig", "Noch ungeprüft"};
        stockFilterPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filterOptions));
        LinearLayout.LayoutParams filterParams=new LinearLayout.LayoutParams(dp(150),-2); filterParams.leftMargin=dp(6); filters.addView(stockFilterPicker,filterParams); root.addView(filters,params(dp(4)));
        resultList=new LinearLayout(this); resultList.setOrientation(LinearLayout.VERTICAL); ScrollView scroll=new ScrollView(this);scroll.addView(resultList);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.topMargin=dp(8);root.addView(scroll,rp);
        TextView note=new TextView(this);note.setText(products.length()+" Artikel im lokalen Katalog; neue Treffer werden auf der öffentlichen Migros-Pokémon-Seite gesucht. Das ist keine garantierte schweizweite Sortimentserhebung. Bestände sind Momentaufnahmen, Ca.-Mengen sind Schätzungen.");note.setTextSize(9);note.setTextColor(Color.GRAY);root.addView(note,params(dp(6)));
        setContentView(root);
    }

    private void beginCheck(boolean automatic) {
        if(products.length()==0||branches.length()==0){status.setText("Bitte mindestens eine Region mit erfassten Filialen auswählen.");return;}
        if(queryRunning)return;
        getPreferences(MODE_PRIVATE).edit().putLong("auto_refresh_anchor_ms",System.currentTimeMillis()).apply();updateAutoRefreshInfo();
        queryRunning=true;activeProductIndex=0;selectedProductIndex=0;productPicker.setSelection(0);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderProduct();
        refresh.setEnabled(false);regionPicker.setEnabled(false);loadingSpinner.setVisibility(View.VISIBLE);
        discoveringProducts=true;discoveryPageIndex=0;waitingForPage=true;status.setText("Suche Migros-Marken- und Sammelkartenspielseiten nach neuen Pokémon-Artikeln; danach prüfe ich "+branches.length()+" Filialen in "+selectedRegionDescription()+" …");
        webView.loadUrl(DISCOVERY_URLS[discoveryPageIndex]);
    }

    private void scanProductCatalog(){
        String script="(async()=>{const norm=s=>(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase();for(let n=0;n<10;n++){window.scrollTo(0,document.body.scrollHeight);await new Promise(r=>setTimeout(r,250))}window.scrollTo(0,0);const rows=[];const seen=new Set();for(const a of document.querySelectorAll('a[href*=\"/de/product/\"]')){const m=a.href.match(/\\/de\\/product\\/(\\d{8,14})/);if(!m||seen.has(m[1]))continue;seen.add(m[1]);let card=a;for(let i=0;i<4&&card.parentElement;i++){if((card.innerText||'').length>180)break;card=card.parentElement}const img=a.querySelector('img')||card.querySelector('img');const name=(a.innerText||a.getAttribute('aria-label')||a.title||'').trim()||(img?.alt||'').trim()||(card.innerText||'').trim().split('\\n').filter(Boolean)[0]||'';const text=norm(name+' '+(card.innerText||''));if(!/pokemon/.test(text))continue;if(!/(booster|sammelkart|trading card|\\btcg\\b|karten|deck|sleeve|portfolio|\\btin\\b|blister|trainer|elite trainer|\\betb\\b|display)/.test(text))continue;const image=img?.currentSrc||img?.src||'';const priceMatch=(card.innerText||'').match(/(?:CHF|Fr\\.)\\s*([0-9]+[.,][0-9]{2})/i);const onlineOnly=/nur online|only online|online only/i.test(card.innerText||'');rows.push({id:m[1],name:name||('Pokémon Kartenartikel '+m[1]),image_url:image,product_url:'https://www.migros.ch/de/product/'+m[1],price_chf:priceMatch?Number(priceMatch[1].replace(',','.')):0,in_store_assortment:!onlineOnly,online_only:onlineOnly,discovered:true})}window.FinderBridge.discovered(JSON.stringify(rows))})()";
        webView.evaluateJavascript(script,null);
    }

    private void mergeDiscoveredProducts(JSONArray found){
        java.util.HashSet<String> existing=new java.util.HashSet<>();
        for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p!=null)existing.add(p.optString("id"));}
        for(int i=0;i<found.length();i++){JSONObject candidate=found.optJSONObject(i);if(candidate==null)continue;String id=candidate.optString("id");if(id.isEmpty()||existing.contains(id))continue;products.put(candidate);existing.add(id);}
        saveDiscoveredProducts();
        updateProductPicker();
    }

    private void updateProductPicker(){
        if(productPicker==null)return;ArrayList<String> names=new ArrayList<>();for(int i=0;i<products.length();i++)names.add(products.optJSONObject(i).optString("name"));
        productPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        if(subtitle!=null)subtitle.setText(products.length()+" Pokémon-TCG-Artikel · "+branches.length()+" Migros-Filialen · Katalog wird bei Aktualisierung ergänzt");
        if(selectedProductIndex<names.size())productPicker.setSelection(selectedProductIndex);
    }

    private void collectRegions(){
        regionNames.clear();
        for(int i=0;i<allBranches.length();i++){
            JSONObject branch=allBranches.optJSONObject(i);if(branch==null)continue;
            String id=branch.optString("region").trim();if(id.isEmpty())id="weitere";
            if(!regionNames.containsKey(id))regionNames.put(id,regionLabel(id));
        }
    }
    private String regionLabel(String id){
        if(id.equalsIgnoreCase("bern"))return "Bern";
        if(id.equalsIgnoreCase("fribourg")||id.equalsIgnoreCase("freiburg"))return "Fribourg";
        if(id.equalsIgnoreCase("weitere-schweiz"))return "Weitere Filialen Schweiz";
        String label=id.replace('_',' ').replace('-',' ');return label.isEmpty()?"Weitere":label.substring(0,1).toUpperCase(java.util.Locale.ROOT)+label.substring(1);
    }
    private void restoreSelectedRegions(){
        String saved=getPreferences(MODE_PRIVATE).getString("selected_regions","");
        if(saved.trim().isEmpty()){selectedRegions.addAll(regionNames.keySet());return;}
        for(String item:saved.split(","))if(regionNames.containsKey(item))selectedRegions.add(item);
    }
    private void applyRegionFilter(){
        branches=new JSONArray();
        for(int i=0;i<allBranches.length();i++){
            JSONObject branch=allBranches.optJSONObject(i);if(branch==null)continue;
            String id=branch.optString("region");if(selectedRegions.contains(id))branches.put(branch);
        }
        if(regionPicker!=null)updateRegionUi();
        if(subtitle!=null)subtitle.setText(products.length()+" Pokémon-TCG-Artikel · "+branches.length()+" Migros-Filialen ausgewählt · Anfrage direkt vom Handy");
        if(refresh!=null)refresh.setText("Ausgewählte Filialen abfragen ("+branches.length()+")");
        if(refresh!=null)refresh.setEnabled(!queryRunning&&branches.length()>0);
        renderRecords();
    }
    private String selectedRegionDescription(){
        if(selectedRegions.size()==regionNames.size())return "allen erfassten Regionen";
        ArrayList<String> names=new ArrayList<>();for(String id:regionNames.keySet())if(selectedRegions.contains(id))names.add(regionNames.get(id));
        return names.isEmpty()?"keiner Region":android.text.TextUtils.join(", ",names);
    }
    private void updateRegionUi(){
        if(regionPicker==null)return;
        regionPicker.setText("Regionen: "+selectedRegionDescription()+" · "+branches.length()+" Filialen");
        regionPicker.setEnabled(!queryRunning);
    }
    private void showRegionPicker(){
        if(queryRunning)return;
        ArrayList<String> ids=new ArrayList<>(regionNames.keySet());
        java.util.LinkedHashSet<String> draft=new java.util.LinkedHashSet<>(selectedRegions);
        CharSequence[] labels=new CharSequence[ids.size()+1];boolean[] checked=new boolean[ids.size()+1];
        labels[0]="Alle Regionen";checked[0]=draft.size()==ids.size();
        for(int i=0;i<ids.size();i++){labels[i+1]=regionNames.get(ids.get(i));checked[i+1]=draft.contains(ids.get(i));}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Migros-Regionen auswählen")
            .setMultiChoiceItems(labels,checked,(d,which,isChecked)->{
                if(which==0){draft.clear();if(isChecked)draft.addAll(ids);}
                else{String id=ids.get(which-1);if(isChecked)draft.add(id);else draft.remove(id);}
                android.widget.ListView list=((AlertDialog)d).getListView();
                list.setItemChecked(0,draft.size()==ids.size());
                for(int i=0;i<ids.size();i++)list.setItemChecked(i+1,draft.contains(ids.get(i)));
            }).setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->{
                selectedRegions.clear();selectedRegions.addAll(draft);
                getPreferences(MODE_PRIVATE).edit().putString("selected_regions",android.text.TextUtils.join(",",selectedRegions)).apply();
                applyRegionFilter();status.setText(branches.length()+" Migros-Filialen ausgewählt. Die nächste Abfrage prüft nur diese Filialen.");
            }).create();dialog.show();
    }

    private void saveDiscoveredProducts(){
        try{JSONArray discovered=new JSONArray();for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p!=null&&p.optBoolean("discovered"))discovered.put(p);}getPreferences(MODE_PRIVATE).edit().putString("discovered_products_json",discovered.toString()).apply();}catch(Exception ignored){}
    }

    private void loadDiscoveredProducts(){
        try{JSONArray saved=new JSONArray(getPreferences(MODE_PRIVATE).getString("discovered_products_json","[]"));java.util.HashSet<String> ids=new java.util.HashSet<>();for(int i=0;i<products.length();i++)ids.add(products.optJSONObject(i).optString("id"));for(int i=0;i<saved.length();i++){JSONObject p=saved.optJSONObject(i);if(p!=null&&!ids.contains(p.optString("id"))){products.put(p);ids.add(p.optString("id"));}}}catch(Exception ignored){}
    }
    private void restoreBranchCoordinates(){try{JSONObject saved=new JSONObject(getPreferences(MODE_PRIVATE).getString("branch_coordinate_cache","{}"));JSONArray ids=saved.names();if(ids==null)return;for(int i=0;i<ids.length();i++){String id=ids.getString(i);JSONArray coords=saved.getJSONArray(id);for(int j=0;j<allBranches.length();j++){JSONObject b=allBranches.optJSONObject(j);if(b!=null&&id.equals(b.optString("id"))){b.put("coordinates",coords);break;}}}}catch(Exception ignored){}}

    private void cacheBranchCoordinates(String id,double lat,double lon){try{JSONArray coords=new JSONArray().put(lat).put(lon);for(int i=0;i<allBranches.length();i++){JSONObject b=allBranches.optJSONObject(i);if(b!=null&&id.equals(b.optString("id"))){b.put("coordinates",coords);break;}}JSONObject cache=new JSONObject(getPreferences(MODE_PRIVATE).getString("branch_coordinate_cache","{}"));cache.put(id,coords);getPreferences(MODE_PRIVATE).edit().putString("branch_coordinate_cache",cache.toString()).apply();}catch(Exception ignored){}}

    private void showMap(){
        Map<String,JSONObject> productRows=recordsByProduct.get(currentProductId());JSONArray stores=new JSONArray();int mapped=0;
        for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);if(b==null)continue;JSONArray c=b.optJSONArray("coordinates");if(c==null||c.length()<2)continue;mapped++;JSONObject r=productRows==null?null:productRows.get(b.optString("id"));try{JSONObject p=new JSONObject();p.put("name",b.optString("name"));p.put("address",b.optString("address"));p.put("lat",c.optDouble(0));p.put("lon",c.optDouble(1));p.put("status",r==null?"unchecked":r.optString("status","unknown"));p.put("quantity",r==null?-1:r.optInt("quantity",-1));stores.put(p);}catch(Exception ignored){}}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);TextView info=new TextView(this);info.setPadding(dp(12),dp(8),dp(12),dp(8));info.setText(mapped+" von "+branches.length()+" ausgewählten Filialen haben gespeicherte Koordinaten. Live-Abfragen ergänzen Koordinaten aus Migros-Wegbeschreibungen.");box.addView(info);WebView map=new WebView(this);map.getSettings().setJavaScriptEnabled(true);map.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,String url){if(url.startsWith("https://www.google.com/maps/")){startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));return true;}return false;}});box.addView(map,new LinearLayout.LayoutParams(-1,dp(520)));
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#map{height:100%;margin:0;font:14px sans-serif}.bar{position:absolute;z-index:999;top:8px;left:8px;right:8px;background:white;padding:6px;border-radius:8px;box-shadow:0 1px 5px #777}select{width:100%;padding:8px}</style><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script></head><body><div id='map'></div><div class='bar'><select id='filter'><option value='all'>Alle kartierten Filialen</option><option value='available'>Verfügbar</option><option value='out_of_stock'>Nicht auf Lager</option><option value='unknown'>Nicht eindeutig</option><option value='unchecked'>Noch ungeprüft</option></select></div><script>const stores="+stores.toString()+";const map=L.map('map').setView([46.8,8.2],8);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);const layer=L.layerGroup().addTo(map);const color=s=>s==='in_stock'||s==='estimated_in_stock'?'#249448':s==='out_of_stock'?'#85858d':s==='unknown'?'#da8f19':'#9b9da5';function draw(){layer.clearLayers();const f=document.getElementById('filter').value;for(const b of stores){if(f==='available'&&!(b.status==='in_stock'||b.status==='estimated_in_stock'))continue;if(f!=='all'&&f!=='available'&&b.status!==f)continue;const q=b.quantity>0?' · ca. '+b.quantity+' auf Lager':'';const destination=encodeURIComponent(b.lat+','+b.lon);const popup='<b>'+b.name+'</b><br>'+b.address+'<br>'+b.status+q+'<br><a href=\"https://www.google.com/maps/dir/?api=1&destination='+destination+'\">Wegbeschreibung</a>';L.circleMarker([b.lat,b.lon],{radius:9,color:color(b.status),fillColor:color(b.status),fillOpacity:.9}).bindPopup(popup).addTo(layer)}}document.getElementById('filter').onchange=draw;draw();</script></body></html>";
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Migros-Filialkarte · "+productName(currentProductId())).setView(box).setPositiveButton("Schliessen",null).create();dialog.show();map.loadDataWithBaseURL("https://www.migros.ch/",html,"text/html","UTF-8",null);
    }

    private void loadProductPage(){
        JSONObject p=products.optJSONObject(activeProductIndex); if(p==null){finishError("Produktkatalog ist ungültig.");return;}
        String id=p.optString("id");status.setText("Artikel "+(activeProductIndex+1)+"/"+products.length()+": "+p.optString("name"));waitingForPage=true;
        webView.loadUrl("https://www.migros.ch/de/product/"+id);
    }

    private void startMigrosCheck() {
        JSONObject current=products.optJSONObject(activeProductIndex);String productId=current.optString("id");boolean onlineOnly=current.optBoolean("online_only",false);
        String progressProductLabel=(activeProductIndex+1)+"/"+products.length();
        String script="(async()=>{const sleep=ms=>new Promise(r=>setTimeout(r,ms));const norm=s=>(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();"
          +"const branches="+branches.toString()+";const productId="+JSONObject.quote(productId)+";const onlineOnly="+onlineOnly+";const send=(m,o)=>window.FinderBridge[m](JSON.stringify(o));const seen=new Set();"
          +"const amount=v=>{const n=Number(String(v||'').replace(/[^0-9,.]/g,'').replace(',','.'));return Number.isFinite(n)&&n>0?n:null};"
          +"let livePrice=null,originalPrice=null;const metas=['meta[property=\"product:price:amount\"]','meta[itemprop=\"price\"]','[itemprop=\"price\"]'];for(const s of metas){const e=document.querySelector(s);const n=amount(e?.content||e?.getAttribute('content')||e?.innerText);if(n){livePrice=n;break}}"
          +"if(!livePrice){for(const script of document.querySelectorAll('script[type=\"application/ld+json\"]')){try{const walk=o=>{if(!o)return;if(Array.isArray(o)){o.forEach(walk);return}if(typeof o==='object'){if(o.price!=null&&(/offer/i.test(String(o['@type']||''))||o.priceCurrency)){const n=amount(o.price);if(n&&!livePrice)livePrice=n}if(o.offers)walk(o.offers);for(const [k,v] of Object.entries(o))if(k!=='offers')walk(v)}};walk(JSON.parse(script.textContent))}catch(e){}}}"
          +"if(!livePrice){const e=document.querySelector('[data-testid*=\"price\"], [class*=\"price\"] [itemprop=\"price\"], [itemprop=\"price\"]');if(e)livePrice=amount(e.content||e.getAttribute('content')||e.innerText)}"
          +"if(livePrice){const old=document.querySelector('del, s, [class*=\"old-price\"], [class*=\"original-price\"], [data-testid*=\"old-price\"]');if(old)originalPrice=amount(old.innerText);send('livePrice',{product_id:productId,price:livePrice,original_price:originalPrice,source:'Migros-Produktseite'})}"
          +"if(onlineOnly){for(const b of branches)send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,status:'online_only',source:'Migros-Sortiment kennzeichnet den Artikel als nur online erhältlich'});send('complete',{product_id:productId,checked:branches.length});return}"
          +"const classify=raw=>{let m=raw.match(/(?:nur\\s*)?ca\\.?\\s*(\\d+)\\s*auf lager/i)||raw.match(/(\\d+)\\s*auf lager/i);if(m)return {status:'estimated_in_stock',quantity:Number(m[1]),source:raw};if(/nicht auf lager/i.test(norm(raw)))return {status:'out_of_stock',source:raw};if(/\\bauf lager\\b|verfugbar/i.test(norm(raw)))return {status:'in_stock',source:raw};return null};"
          +"const collectCoordinates=()=>{for(const a of modal.querySelectorAll('a[href]')){if(!a.href.includes('google')||!a.href.includes('destination='))continue;const m=a.href.match(/[?&]destination=([-0-9.]+)(?:%2C|,)([-0-9.]+)/i);if(!m)continue;let card=a;for(let k=0;k<2&&card.parentElement;k++)card=card.parentElement;const label=norm(card.innerText||a.parentElement?.innerText||'');const b=branches.find(x=>label.includes(norm(x.name)));if(b)send('coordinate',{branch_id:b.id,lat:Number(m[1]),lon:Number(m[2])})}};"
          +"const scan=body=>{const lines=body.split(/\\n+/).map(x=>x.trim()).filter(Boolean);for(const b of branches){if(seen.has(b.id))continue;const name=norm(b.name),addr=norm((b.address||'').split(',')[0]);let i=lines.findIndex(x=>norm(x)===name||norm(x).includes(name));if(i<0&&addr.length>5)i=lines.findIndex(x=>norm(x).startsWith(addr));if(i<0)continue;const raw=lines.slice(i,i+6).join(' | ');const found=classify(raw);if(found){seen.add(b.id);send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,...found})}}};"
          +"let button=null;for(let n=0;n<120;n++){button=[...document.querySelectorAll('button')].find(b=>norm(b.innerText).includes('verfugbarkeit in filialen prufen'));if(button)break;await sleep(250)}"
          +"if(!button){send('failed',{product_id:productId,error:'Migros-Verfügbarkeitsbutton fehlt',title:document.title,body:(document.body?.innerText||'').slice(0,700)});return}button.click();"
          +"let modal=null,input=null;for(let n=0;n<80;n++){modal=document.querySelector('#bottomSmallModal');input=document.querySelector('#store-search-autocomplete-form-input');if(modal&&input)break;await sleep(250)}if(!input){send('failed',{product_id:productId,error:'Filialsuche öffnet sich nicht',title:document.title});return}collectCoordinates();scan(modal.innerText||'');"
          +"const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;let searched=0;for(const b of branches){if(seen.has(b.id))continue;searched++;send('progress',{message:'Artikel "+progressProductLabel+" · Filiale '+searched+'/'+branches.length+': '+b.name+' …'});const before=modal.innerText||'';setter.call(input,b.address);input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));let body=before;"
          +"for(let n=0;n<60;n++){await sleep(250);body=modal.innerText||'';if(body!==before&&(norm(body).includes(norm(b.name))||norm(body).includes(norm((b.address||'').split(',')[0])))){await sleep(350);body=modal.innerText||body;break}}collectCoordinates();scan(body);if(!seen.has(b.id)){seen.add(b.id);send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,status:'unknown',source:'Migros hat keinen lesbaren Status geliefert'})}}"
          +"send('complete',{product_id:productId,checked:branches.length})})()";
        webView.evaluateJavascript(script,null);
    }

    private class ResultBridge {
        @JavascriptInterface public void progress(String json){runOnUiThread(()->{try{status.setText(new JSONObject(json).optString("message","Prüfe Migros-Filialen …"));}catch(Exception ignored){}});}
        @JavascriptInterface public void coordinate(String json){runOnUiThread(()->{try{JSONObject c=new JSONObject(json);cacheBranchCoordinates(c.getString("branch_id"),c.getDouble("lat"),c.getDouble("lon"));}catch(Exception ignored){}});}
        @JavascriptInterface public void discovered(String json){runOnUiThread(()->{try{JSONArray found=new JSONArray(json);mergeDiscoveredProducts(found);if(discoveryPageIndex+1<DISCOVERY_URLS.length){discoveryPageIndex++;status.setText("Suche weitere Kartenartikel auf der Migros-Sammelkartenspielseite …");waitingForPage=true;webView.loadUrl(DISCOVERY_URLS[discoveryPageIndex]);return;}discoveringProducts=false;activeProductIndex=0;selectedProductIndex=0;updateProductPicker();status.setText("Migros-Marken- und Sammelkartenspielseiten geprüft; prüfe jetzt "+products.length()+" Artikel in den Filialen …");loadProductPage();}catch(Exception ex){discoveringProducts=false;activeProductIndex=0;status.setText("Produktsuche nicht lesbar; prüfe die gespeicherten Artikel weiter …");loadProductPage();}});}
        @JavascriptInterface public void record(String json){runOnUiThread(()->{try{JSONObject row=new JSONObject(json);String productId=row.getString("product_id"),branchId=row.getString("branch_id");Map<String,JSONObject> productRows=recordsByProduct.get(productId);if(productRows==null){productRows=new LinkedHashMap<>();recordsByProduct.put(productId,productRows);}JSONObject old=productRows.get(branchId);String oldStatus=old==null?"":old.optString("status");int oldQty=old==null?-1:old.optInt("quantity",-1);if("unknown".equals(oldStatus)){oldStatus=old.optString("last_known_status",oldStatus);oldQty=old.optInt("last_known_quantity",oldQty);}String newStatus=row.optString("status");int newQty=row.optInt("quantity",-1);boolean oldAvailable=oldStatus.equals("in_stock")||oldStatus.equals("estimated_in_stock");boolean newAvailable=newStatus.equals("in_stock")||newStatus.equals("estimated_in_stock");String signal="";if(old!=null&&!oldAvailable&&newAvailable)signal="restock";else if(oldStatus.equals("estimated_in_stock")&&newStatus.equals("estimated_in_stock")&&newQty>oldQty)signal="up";else if(oldStatus.equals("estimated_in_stock")&&newStatus.equals("estimated_in_stock")&&newQty<oldQty)signal="down";if("unknown".equals(newStatus)&&old!=null){row.put("last_known_status",oldStatus);if(oldQty>=0)row.put("last_known_quantity",oldQty);}row.put("stock_signal",signal);row.put("checked_at",now());if(old!=null&&signal.equals("restock")&&notificationsEnabled("notify_restock"))postNotification("Restock erkannt",productName(productId)+" ist wieder verfügbar bei "+row.optString("name"),productId.hashCode()^branchId.hashCode());else if(old!=null&&signal.equals("up")&&notificationsEnabled("notify_stock_increase"))postNotification("Bestand gestiegen",productName(productId)+" · "+row.optString("name")+" · ca. "+newQty,productId.hashCode()^branchId.hashCode());productRows.put(branchId,row);saveLocal();if(productId.equals(currentProductId()))renderRecords();}catch(Exception ignored){}});}
        @JavascriptInterface public void livePrice(String json){runOnUiThread(()->{try{JSONObject incoming=new JSONObject(json);String id=incoming.getString("product_id");double current=incoming.getDouble("price");JSONObject old=livePricesByProduct.get(id);double previous=old==null?catalogPrice(id):old.optDouble("price",catalogPrice(id));boolean hasBaseline=previous>0;JSONObject saved=new JSONObject();saved.put("price",current);saved.put("checked_at",now());saved.put("source",incoming.optString("source"));if(hasBaseline&&Math.abs(current-previous)>=0.01){saved.put("previous_price",previous);saved.put("change",current<previous?"down":"up");if(old!=null){String direction=current<previous?"notify_price_drop":"notify_price_increase";if(notificationsEnabled(direction))postNotification(current<previous?"Preis gesunken":"Preis gestiegen",productName(id)+": CHF "+String.format(java.util.Locale.GERMANY,"%.2f",previous)+" → CHF "+String.format(java.util.Locale.GERMANY,"%.2f",current),id.hashCode());}}else saved.put("change","none");double original=incoming.optDouble("original_price",0);if(original>current){saved.put("promotion",true);saved.put("original_price",original);if(old!=null&&!old.optBoolean("promotion")&&notificationsEnabled("notify_promotion")&&(!hasBaseline||Math.abs(current-previous)<0.01))postNotification("Aktion erkannt",productName(id)+" · jetzt CHF "+String.format(java.util.Locale.GERMANY,"%.2f",current),id.hashCode());}livePricesByProduct.put(id,saved);saveLocal();if(id.equals(currentProductId()))renderProduct();}catch(Exception ignored){}});}
        @JavascriptInterface public void failed(String json){runOnUiThread(()->{try{JSONObject d=new JSONObject(json);finishError("Abfrage gestoppt bei "+productName(d.optString("product_id"))+": "+d.optString("error","Migros-Fehler"));}catch(Exception ex){finishError("Migros-Abfrage fehlgeschlagen.");}});}
        @JavascriptInterface public void complete(String json){runOnUiThread(()->{String id=products.optJSONObject(activeProductIndex).optString("id");checkedByProduct.put(id,now());saveLocal();activeProductIndex++;if(activeProductIndex<products.length()){loadProductPage();}else finishAll();});}
    }

    private boolean notificationsEnabled(String key){return getPreferences(MODE_PRIVATE).getBoolean(key,false);}
    private void postNotification(String title,String message,int id){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(manager==null)return;
        String channel="pokemon_inventory_alerts";if(Build.VERSION.SDK_INT>=26){manager.createNotificationChannel(new NotificationChannel(channel,"Pokémon Card Finder",NotificationManager.IMPORTANCE_DEFAULT));}
        Intent intent=new Intent(this,MainActivity.class);PendingIntent pending=PendingIntent.getActivity(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(pending).setAutoCancel(true);
        manager.notify(id,builder.build());
    }

    private void showNotificationSettings(){
        LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(dp(20),dp(4),dp(20),0);
        String[][] rows={{"notify_restock","Restock: Filiale ist wieder verfügbar"},{"notify_stock_increase","Bestand steigt"},{"notify_price_drop","Preis sinkt"},{"notify_price_increase","Preis steigt"},{"notify_promotion","Neue Rabattaktion"}};
        CheckBox[] boxes=new CheckBox[rows.length];for(int i=0;i<rows.length;i++){boxes[i]=new CheckBox(this);boxes[i].setText(rows[i][1]);boxes[i].setChecked(getPreferences(MODE_PRIVATE).getBoolean(rows[i][0],i==0||i==2||i==4));options.addView(boxes[i]);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Benachrichtigungen").setMessage("Du erhältst Hinweise nach einer Bestands- oder Preisprüfung.").setView(options).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{boolean any=false;android.content.SharedPreferences.Editor editor=getPreferences(MODE_PRIVATE).edit();for(int i=0;i<rows.length;i++){editor.putBoolean(rows[i][0],boxes[i].isChecked());any|=boxes[i].isChecked();}editor.apply();if(any&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},41);dialog.dismiss();}));dialog.show();
    }

    private void finishAll(){int total=0,unknown=0;for(Map<String,JSONObject> rows:recordsByProduct.values())for(String regionBranchId:selectedBranchIds())if(rows.containsKey(regionBranchId)){total++;if(rows.get(regionBranchId).optString("status").equals("unknown"))unknown++;}status.setText("Abfrage fertig · "+total+" von "+(products.length()*branches.length())+" ausgewählten Artikel-Filial-Datensätzen · "+unknown+" ohne lesbaren Status");checkedAt.setText("Zuletzt geprüft: "+now()+" · Ergebnisse lokal gespeichert");finishBusy();renderProduct();updateAutoRefreshInfo();scheduleAutomaticRefresh(0);}
    private void finishError(String message){status.setText(message+". Bisherige Ergebnisse bleiben lokal gespeichert.");finishBusy();updateAutoRefreshInfo();scheduleAutomaticRefresh(0);}
    private void finishBusy(){queryRunning=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);loadingSpinner.setVisibility(View.GONE);refresh.setEnabled(branches.length()>0);updateRegionUi();}
    private java.util.Set<String> selectedBranchIds(){java.util.HashSet<String> ids=new java.util.HashSet<>();for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);if(b!=null)ids.add(b.optString("id"));}return ids;}
    private void scheduleAutomaticRefresh(long delayOverride){autoRefreshHandler.removeCallbacks(autoRefreshRunnable);if(!activityResumed||autoRefreshToggle==null||!autoRefreshToggle.isChecked())return;long anchor=getPreferences(MODE_PRIVATE).getLong("auto_refresh_anchor_ms",0);long delay=delayOverride>0?delayOverride:(anchor==0?0:Math.max(0,anchor+AUTO_REFRESH_INTERVAL_MS-System.currentTimeMillis()));autoRefreshHandler.postDelayed(autoRefreshRunnable,delay);updateAutoRefreshInfo();}
    private void checkAutomaticRefresh(){if(!activityResumed||autoRefreshToggle==null||!autoRefreshToggle.isChecked())return;if(queryRunning){scheduleAutomaticRefresh(60_000);return;}long anchor=getPreferences(MODE_PRIVATE).getLong("auto_refresh_anchor_ms",0);if(anchor==0||System.currentTimeMillis()-anchor>=AUTO_REFRESH_INTERVAL_MS){status.setText("Automatische Live-Aktualisierung läuft …");beginCheck(true);}else scheduleAutomaticRefresh(0);}
    private void updateAutoRefreshInfo(){if(autoRefreshInfo==null)return;if(autoRefreshToggle==null||!autoRefreshToggle.isChecked()){autoRefreshInfo.setText("Automatische Aktualisierung ausgeschaltet.");return;}long anchor=getPreferences(MODE_PRIVATE).getLong("auto_refresh_anchor_ms",0);if(anchor==0){autoRefreshInfo.setText("Erste automatische Prüfung startet beim Öffnen der App. App geöffnet lassen.");return;}long next=anchor+AUTO_REFRESH_INTERVAL_MS;autoRefreshInfo.setText("Nächste Prüfung ab "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(next))+" · läuft bei geöffneter App; nach Rückkehr wird eine fällige Prüfung gestartet.");}
    private String currentProductId(){JSONObject p=products.optJSONObject(selectedProductIndex);return p==null?"":p.optString("id");}
    private String productName(String id){for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p.optString("id").equals(id))return p.optString("name");}return id;}
    private String now(){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date());}

    private void renderProduct(){if(products==null||products.length()==0||productTitle==null)return;JSONObject p=products.optJSONObject(selectedProductIndex);if(p==null)return;String id=p.optString("id");productTitle.setText(p.optString("name"));JSONObject live=livePricesByProduct.get(id);double shown=live==null?p.optDouble("price_chf",0):live.optDouble("price",p.optDouble("price_chf",0));String priceText=String.format(java.util.Locale.GERMANY,"CHF %.2f",shown);productPrice.setTextColor(Color.rgb(96,72,220));if(live==null)priceText+=" · Katalogpreis";else{String change=live.optString("change");if(change.equals("down")){priceText+="  ↓ Preis gesunken";productPrice.setTextColor(Color.rgb(36,148,72));}else if(change.equals("up")){priceText+="  ↑ Preis gestiegen";productPrice.setTextColor(Color.rgb(175,105,0));}else priceText+=" · Live geprüft";if(live.optBoolean("promotion"))priceText+=" · Aktion (vorher "+String.format(java.util.Locale.GERMANY,"CHF %.2f",live.optDouble("original_price"))+")";else if(change.equals("down"))priceText+=" · mögliche Rabattaktion";}productPrice.setText(priceText);loadImage(p.optString("image_url"));renderRecords();String checked=checkedByProduct.get(id);if(checked!=null)checkedAt.setText("Dieser Artikel zuletzt geprüft: "+checked+" · lokal gespeichert");}
    private void renderRecords(){
        if(resultList==null)return;
        Map<String,JSONObject> rows=recordsByProduct.get(currentProductId());
        String query=branchSearch==null?"":branchSearch.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        String filter=stockFilterPicker==null||stockFilterPicker.getSelectedItem()==null?"Alle":stockFilterPicker.getSelectedItem().toString();
        int available=0,outOfStock=0,unclear=0,unchecked=0,onlineOnly=0; List<JSONObject> visible=new ArrayList<>();
        for(int i=0;i<branches.length();i++){
            JSONObject b=branches.optJSONObject(i); if(b==null)continue;
            JSONObject row=rows==null?null:rows.get(b.optString("id")); String state=row==null?"unchecked":row.optString("status","unknown");
            if(state.equals("in_stock")||state.equals("estimated_in_stock"))available++;else if(state.equals("out_of_stock"))outOfStock++;else if(state.equals("unknown"))unclear++;else if(state.equals("online_only"))onlineOnly++;else unchecked++;
            String searchable=(b.optString("name")+" "+b.optString("address")).toLowerCase(java.util.Locale.ROOT);
            if(!searchable.contains(query)||!matchesFilter(filter,state))continue;
            visible.add(b);
        }
        if(stockSummary!=null)stockSummary.setText("Verfügbar: "+available+" · Nicht auf Lager: "+outOfStock+" · Nur online: "+onlineOnly+" · Unklar: "+unclear+" · Offen: "+unchecked);
        if(filter.equals("Höchster Bestand"))Collections.sort(visible,(a,b)->Integer.compare(stockQuantity(rows,b),stockQuantity(rows,a)));
        resultList.removeAllViews();
        if(visible.isEmpty()){TextView empty=new TextView(this);empty.setText("Keine Filiale passt zu Suche und Filter.");empty.setTextSize(12);empty.setPadding(dp(8),dp(12),dp(8),dp(12));resultList.addView(empty);return;}
        for(JSONObject branch:visible){String id=branch.optString("id");JSONObject row=rows==null?null:rows.get(id);String state=row==null?"unchecked":row.optString("status","unknown");LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);item.setBackgroundColor(Color.WHITE);item.setPadding(dp(10),dp(8),dp(10),dp(8));LinearLayout.LayoutParams itemParams=params(dp(5));resultList.addView(item,itemParams);
            View dot=new View(this);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(statusColor(state));dot.setBackground(circle);item.addView(dot,new LinearLayout.LayoutParams(dp(12),dp(12)));
            LinearLayout textColumn=new LinearLayout(this);textColumn.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,-2,1);textParams.leftMargin=dp(10);item.addView(textColumn,textParams);
            TextView availability=new TextView(this);availability.setText(label(row));availability.setTextSize(12);availability.setTextColor(statusColor(state));availability.setTypeface(null,1);textColumn.addView(availability);
            TextView branchText=new TextView(this);branchText.setText(branch.optString("name")+" · "+branch.optString("address"));branchText.setTextSize(11);branchText.setTextColor(Color.rgb(70,68,80));textColumn.addView(branchText);
            if(row!=null){String signal=row.optString("stock_signal");if(!signal.isEmpty()){TextView alert=new TextView(this);if(signal.equals("restock"))alert.setText("↗ RESTOCK · wieder verfügbar");else if(signal.equals("up"))alert.setText("↗ Bestand gestiegen");else alert.setText("↓ Bestand gesunken");alert.setTextSize(10);alert.setTextColor(signal.equals("down")?Color.rgb(175,105,0):Color.rgb(30,135,65));alert.setTypeface(null,1);textColumn.addView(alert);}}
        }
    }
    private boolean matchesFilter(String filter,String state){
        if(filter.equals("Höchster Bestand"))return state.equals("in_stock")||state.equals("estimated_in_stock");
        if(filter.equals("Verfügbar"))return state.equals("in_stock")||state.equals("estimated_in_stock");
        if(filter.equals("Nicht auf Lager"))return state.equals("out_of_stock");
        if(filter.equals("Nicht eindeutig"))return state.equals("unknown");
        if(filter.equals("Nur online"))return state.equals("online_only");
        if(filter.equals("Noch ungeprüft"))return state.equals("unchecked");
        return true;
    }
    private String label(JSONObject row){if(row==null)return "Noch nicht geprüft";String s=row.optString("status");if(s.equals("out_of_stock"))return "Nicht auf Lager";if(s.equals("estimated_in_stock"))return "Ca. "+row.optInt("quantity")+" auf Lager";if(s.equals("in_stock"))return "Auf Lager (Menge unbekannt)";if(s.equals("online_only"))return "Nur online erhältlich";return "Nicht eindeutig geprüft";}
    private int statusColor(String state){if(state.equals("in_stock")||state.equals("estimated_in_stock"))return Color.rgb(36,148,72);if(state.equals("out_of_stock"))return Color.rgb(130,132,140);if(state.equals("unknown"))return Color.rgb(218,143,25);if(state.equals("online_only"))return Color.rgb(96,72,220);return Color.rgb(185,188,195);}
    private int stockQuantity(Map<String,JSONObject> rows,JSONObject branch){JSONObject row=rows==null?null:rows.get(branch.optString("id"));return row!=null&&row.optString("status").equals("estimated_in_stock")?row.optInt("quantity",0):0;}
    private double catalogPrice(String id){for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p.optString("id").equals(id))return p.optDouble("price_chf",0);}return 0;}
    private void loadImage(String imageUrl){productImage.setImageDrawable(null);if(imageUrl==null||imageUrl.isEmpty())return;new Thread(()->{try{Bitmap bitmap=BitmapFactory.decodeStream(new URL(imageUrl).openStream());runOnUiThread(()->productImage.setImageBitmap(bitmap));}catch(Exception ignored){}}).start();}
    private void saveLocal(){try{JSONObject all=new JSONObject();for(String id:recordsByProduct.keySet())all.put(id,new JSONObject(recordsByProduct.get(id)));JSONObject times=new JSONObject(checkedByProduct);JSONObject prices=new JSONObject(livePricesByProduct);getPreferences(MODE_PRIVATE).edit().putString("all_stock_json",all.toString()).putString("all_checked_json",times.toString()).putString("live_prices_json",prices.toString()).apply();}catch(Exception ignored){}}
    private void loadSavedRecords(){try{JSONObject all=new JSONObject(getPreferences(MODE_PRIVATE).getString("all_stock_json","{}"));JSONArray keys=all.names();if(keys!=null)for(int i=0;i<keys.length();i++){String productId=keys.getString(i);JSONObject productRows=all.getJSONObject(productId);Map<String,JSONObject> rows=new LinkedHashMap<>();JSONArray branchKeys=productRows.names();if(branchKeys!=null)for(int j=0;j<branchKeys.length();j++){String branchId=branchKeys.getString(j);rows.put(branchId,productRows.getJSONObject(branchId));}recordsByProduct.put(productId,rows);}JSONObject times=new JSONObject(getPreferences(MODE_PRIVATE).getString("all_checked_json","{}"));JSONArray tkeys=times.names();if(tkeys!=null)for(int i=0;i<tkeys.length();i++){String id=tkeys.getString(i);checkedByProduct.put(id,times.getString(id));}JSONObject prices=new JSONObject(getPreferences(MODE_PRIVATE).getString("live_prices_json","{}"));JSONArray pkeys=prices.names();if(pkeys!=null)for(int i=0;i<pkeys.length();i++){String id=pkeys.getString(i);livePricesByProduct.put(id,prices.getJSONObject(id));}}catch(Exception ignored){}}
    private String readAsset(String name)throws Exception{BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open(name),"UTF-8"));StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);reader.close();return text.toString();}
    private LinearLayout.LayoutParams params(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=top;return p;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
