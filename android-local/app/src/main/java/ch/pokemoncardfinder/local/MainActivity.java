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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
    private static final int REQUEST_BACKUP_EXPORT = 9301;
    private static final int REQUEST_BACKUP_IMPORT = 9302;
    private static final int BACKUP_MAX_BYTES = 50 * 1024 * 1024;
    private boolean backupBusy;
    private static final long AUTO_REFRESH_INTERVAL_MS = 4L * 60L * 60L * 1000L;
    private TextView status, checkedAt, productTitle, productPrice, stockSummary;
    private ProgressBar loadingSpinner;
    private Button refresh;
    private Button notificationSettings;
    private Button regionPicker;
    private Button branchScopePicker;
    private Button addProductButton;
    private JSONArray queryBranches = new JSONArray();
    private WebView radiusMapWebView;
    private boolean radiusMapReady;
    private double radiusCenterLat=46.8182, radiusCenterLon=8.2275;
    private int queryRadiusKm=5;
    // Distinguish consecutive map dialogs so late background callbacks stay safe.
    private volatile int radiusMapSession=0;
    private Button historyButton;
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
    private JSONObject branchCantons = new JSONObject();
    private JSONArray stockSnapshots = new JSONArray();
    private JSONArray stockEvents = new JSONArray();
    private final LinkedHashMap<String, JSONObject> customRegions = new LinkedHashMap<>();
    private static final String[] CANTON_CODES = {"AG","AI","AR","BL","BS","BE","FR","GE","GL","GR","JU","LU","NE","NW","OW","SH","SZ","SO","SG","TI","TG","UR","VD","VS","ZG","ZH","INT"};
    private static final String[] CANTON_NAMES = {"Aargau","Appenzell Innerrhoden","Appenzell Ausserrhoden","Basel-Landschaft","Basel-Stadt","Bern","Freiburg","Genf","Glarus","Graubünden","Jura","Luzern","Neuenburg","Nidwalden","Obwalden","Schaffhausen","Schwyz","Solothurn","St. Gallen","Tessin","Thurgau","Uri","Waadt","Wallis","Zug","Zürich","Weitere Standorte"};
    private final LinkedHashMap<String, String> regionNames = new LinkedHashMap<>();
    private final java.util.LinkedHashSet<String> selectedRegions = new java.util.LinkedHashSet<>();
    private int selectedProductIndex = 0, activeProductIndex = 0, productQueueCursor = 0;
    private boolean automaticCheck;
    private final ArrayList<Integer> productQueue = new ArrayList<>();
    private final ArrayList<String> failedProductChecks = new ArrayList<>();
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
            branchCantons = new JSONObject(readAsset("branch-cantons.json")).getJSONObject("branches");
        } catch (Exception ex) { allBranches = new JSONArray(); products = new JSONArray(); }
        restoreBranchCoordinates();
        loadCustomRegions();
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
                    status.setText("Migros lädt Artikel " + (productQueueCursor+1) + "/" + productQueue.size() + " …");
                    view.postDelayed(() -> startMigrosCheck(), 700);
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(1, 1));
        loadSavedRecords();
        refresh.setOnClickListener(v -> beginCheck(false));
        regionPicker.setOnClickListener(v -> showRegionPicker());
        branchScopePicker.setOnClickListener(v -> showBranchScopeDialog());
        addProductButton.setOnClickListener(v -> showAddProductDialog());
        notificationSettings.setOnClickListener(v -> showNotificationSettings());
        historyButton.setOnClickListener(v -> showStockHistory());
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
        openProductFromIntent(getIntent());
    }

    @Override protected void onResume() {
        super.onResume(); activityResumed=true;
        if (!backupBusy && autoRefreshToggle!=null&&autoRefreshToggle.isChecked()) scheduleAutomaticRefresh(0);
    }

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openProductFromIntent(intent);}
    private void openProductFromIntent(Intent intent){if(intent==null)return;String id=intent.getStringExtra("open_product_id");if(id==null)return;for(int i=0;i<products.length();i++)if(id.equals(products.optJSONObject(i).optString("id"))){selectedProductIndex=i;if(productPicker!=null)productPicker.setSelection(i);renderProduct();break;}intent.removeExtra("open_product_id");}

    @Override protected void onPause() {
        activityResumed=false; autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        super.onPause();
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(Color.rgb(248,248,252)); getWindow().setNavigationBarColor(Color.rgb(248,248,252));
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(18),dp(16),dp(12)); root.setBackgroundColor(Color.rgb(248,248,252));
        TextView title=new TextView(this); title.setText("Pokémon Card Finder"); title.setTextSize(22); title.setTextColor(Color.rgb(35,32,48)); title.setTypeface(null,1); root.addView(title);
        TextView versionBadge=new TextView(this); versionBadge.setText("App-Version: v"+appVersionName()); versionBadge.setTextSize(11); versionBadge.setTextColor(Color.rgb(78,76,100)); root.addView(versionBadge,params(dp(2)));
        subtitle=new TextView(this); subtitle.setText(products.length()+" Pokémon-TCG-Artikel · "+branches.length()+" Filialen in den gewählten Regionen"); subtitle.setTextSize(12); subtitle.setTextColor(Color.DKGRAY); root.addView(subtitle,params(dp(4)));
        Button coopButton=new Button(this); coopButton.setText("Coop Pokémon-Karten und Filialverfügbarkeit"); coopButton.setTextSize(12); coopButton.setOnClickListener(v->startActivity(new Intent(this,CoopActivity.class))); root.addView(coopButton,params(dp(5)));
        regionPicker=new Button(this); regionPicker.setTextSize(11); root.addView(regionPicker,params(dp(4))); updateRegionUi();
        branchScopePicker=new Button(this);branchScopePicker.setTextSize(11);root.addView(branchScopePicker,params(dp(4)));updateBranchScopeUi();
        Button quickRadiusMap=new Button(this);
        quickRadiusMap.setText("🗺 Radiuskarte öffnen · Filialen im Umkreis wählen");
        quickRadiusMap.setTextSize(14);
        quickRadiusMap.setTypeface(null,android.graphics.Typeface.BOLD);
        quickRadiusMap.setOnClickListener(v->prepareRadiusMap());
        root.addView(quickRadiusMap,params(dp(5)));
        productPicker=new Spinner(this); root.addView(productPicker,params(dp(5)));
        addProductButton=new Button(this); addProductButton.setText("Fehlendes Pokémon-Produkt hinzufügen"); addProductButton.setTextSize(11); root.addView(addProductButton,params(dp(5)));
        LinearLayout card=new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setBackgroundColor(Color.WHITE); card.setPadding(dp(10),dp(8),dp(10),dp(8));
        productImage=new ImageView(this); productImage.setScaleType(ImageView.ScaleType.FIT_CENTER); card.addView(productImage,new LinearLayout.LayoutParams(dp(88),dp(78)));
        LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(0,-2,1); detailParams.leftMargin=dp(10); card.addView(details,detailParams);
        productTitle=new TextView(this); productTitle.setTextSize(14); productTitle.setTextColor(Color.rgb(35,32,48)); productTitle.setTypeface(null,1); details.addView(productTitle);
        productPrice=new TextView(this); productPrice.setTextSize(14); productPrice.setTextColor(Color.rgb(96,72,220)); details.addView(productPrice,params(dp(3)));
        root.addView(card,params(dp(8)));
        refresh=new Button(this); refresh.setText("Ausgewählte Filialen abfragen ("+queryBranches.length()+")"); refresh.setEnabled(queryBranches.length()>0); root.addView(refresh,params(dp(8)));
        LinearLayout progressLine=new LinearLayout(this); progressLine.setGravity(Gravity.CENTER_VERTICAL);
        loadingSpinner=new ProgressBar(this); loadingSpinner.setIndeterminate(true); loadingSpinner.setVisibility(View.GONE); progressLine.addView(loadingSpinner,new LinearLayout.LayoutParams(dp(22),dp(22)));
        status=new TextView(this); status.setText("Noch keine vollständige Live-Abfrage."); status.setTextSize(12); status.setTextColor(Color.DKGRAY); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(8);progressLine.addView(status,sp);root.addView(progressLine,params(dp(8)));
        checkedAt=new TextView(this); checkedAt.setText("Die Migros-Abfragen und gespeicherten Ergebnisse bleiben auf diesem Gerät."); checkedAt.setTextSize(10); checkedAt.setTextColor(Color.GRAY); root.addView(checkedAt,params(dp(3)));
        autoRefreshToggle=new Switch(this);autoRefreshToggle.setText("Automatisch alle 4 Stunden aktualisieren");autoRefreshToggle.setTextSize(11);root.addView(autoRefreshToggle,params(dp(2)));
        autoRefreshInfo=new TextView(this);autoRefreshInfo.setTextSize(9);autoRefreshInfo.setTextColor(Color.GRAY);root.addView(autoRefreshInfo,params(dp(1)));
        notificationSettings=new Button(this);notificationSettings.setText("Benachrichtigungen einstellen");root.addView(notificationSettings,params(dp(4)));
        historyButton=new Button(this);historyButton.setText("7-Tage-Statistik & Änderungsverlauf");root.addView(historyButton,params(dp(3)));
        Button exportBackup=new Button(this);exportBackup.setText("Backup herunterladen · Migros + Coop");
        exportBackup.setOnClickListener(v->chooseBackupExport());root.addView(exportBackup,params(dp(4)));
        Button importBackup=new Button(this);importBackup.setText("Backup hochladen / wiederherstellen");
        importBackup.setOnClickListener(v->chooseBackupImport());root.addView(importBackup,params(dp(3)));
        mapButton=new Button(this);mapButton.setText("Filialkarte öffnen");root.addView(mapButton,params(dp(3)));
        stockSummary=new TextView(this); stockSummary.setTextSize(11); stockSummary.setTextColor(Color.rgb(60,56,72)); root.addView(stockSummary,params(dp(8)));
        LinearLayout filters=new LinearLayout(this); filters.setGravity(Gravity.CENTER_VERTICAL);
        branchSearch=new EditText(this); branchSearch.setSingleLine(true); branchSearch.setTextSize(12); branchSearch.setHint("Filiale oder Ort suchen");
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(0,dp(48),1); filters.addView(branchSearch,searchParams);
        stockFilterPicker=new Spinner(this); String[] filterOptions={"Alle", "Höchster Bestand", "Verfügbar", "Nicht auf Lager", "Nur online", "Nicht eindeutig", "Noch ungeprüft"};
        stockFilterPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filterOptions));
        LinearLayout.LayoutParams filterParams=new LinearLayout.LayoutParams(dp(150),-2); filterParams.leftMargin=dp(6); filters.addView(stockFilterPicker,filterParams); root.addView(filters,params(dp(4)));
        resultList=new LinearLayout(this); resultList.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(8);root.addView(resultList,rp);
        TextView note=new TextView(this);note.setText(products.length()+" Artikel im lokalen Katalog; neue Treffer werden auf der öffentlichen Migros-Pokémon-Seite gesucht. Das ist keine garantierte schweizweite Sortimentserhebung. Bestände sind Momentaufnahmen, Ca.-Mengen sind Schätzungen.");note.setTextSize(9);note.setTextColor(Color.GRAY);root.addView(note,params(dp(6)));
        ScrollView screenScroll=new ScrollView(this);screenScroll.setFillViewport(true);screenScroll.setVerticalScrollBarEnabled(true);screenScroll.setScrollbarFadingEnabled(false);screenScroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        setContentView(screenScroll);
    }

    private void beginCheck(boolean automatic) {
        applyBranchScope();
        if(queryBranches.length()==0){status.setText("Bitte mindestens eine Region und mindestens eine Abfrage-Filiale auswählen.");return;}
        if(queryRunning)return;
        automaticCheck=automatic;
        getPreferences(MODE_PRIVATE).edit().putLong("auto_refresh_anchor_ms",System.currentTimeMillis()).apply();updateAutoRefreshInfo();
        queryRunning=true;productQueue.clear();failedProductChecks.clear();productQueueCursor=0;activeProductIndex=0;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderProduct();refresh.setEnabled(false);regionPicker.setEnabled(false);loadingSpinner.setVisibility(View.VISIBLE);
        discoveringProducts=true;discoveryPageIndex=0;waitingForPage=true;status.setText("Suche zuerst nach neuen Pokémon-Artikeln bei Migros …");
        webView.loadUrl(DISCOVERY_URLS[discoveryPageIndex]);
    }

    private String appVersionName(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(PackageManager.NameNotFoundException ex){return "unbekannt";}
    }

    private void showAddProductDialog(){
        if(queryRunning)return;
        LinearLayout inputs=new LinearLayout(this);inputs.setOrientation(LinearLayout.VERTICAL);inputs.setPadding(dp(20),dp(8),dp(20),dp(4));
        EditText link=new EditText(this);link.setSingleLine(true);link.setTextSize(13);link.setHint("Migros-Link oder 12-stellige Artikelnummer");inputs.addView(link);
        EditText name=new EditText(this);name.setSingleLine(true);name.setTextSize(13);name.setHint("Artikelname (optional)");inputs.addView(name);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Pokémon-Produkt hinzufügen")
            .setView(inputs).setNegativeButton("Abbrechen",null).setPositiveButton("Hinzufügen",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String entered=link.getText().toString().trim();
            String id=entered.matches("\\d{8,14}")?entered:null;
            if(id==null){java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?i)^https?://(?:www\\.)?migros\\.ch/(?:de|fr|it|en)/product/(\\d{8,14})(?:[/?#].*)?$").matcher(entered);if(matcher.matches())id=matcher.group(1);}
            if(id==null){link.setError("Bitte einen gültigen Migros-Produktlink oder eine Artikelnummer eingeben.");return;}
            for(int i=0;i<products.length();i++)if(id.equals(products.optJSONObject(i).optString("id"))){selectedProductIndex=i;productPicker.setSelection(i);dialog.dismiss();status.setText("Artikel ist bereits im Katalog.");return;}
            String productName=name.getText().toString().trim();if(productName.isEmpty())productName="Pokémon-Artikel "+id;
            try{JSONObject p=new JSONObject();p.put("id",id);p.put("name",productName);p.put("product_url","https://www.migros.ch/de/product/"+id);p.put("price_chf",0);p.put("image_url","");p.put("in_store_assortment",true);p.put("branch_stock_checked",false);p.put("discovered",true);products.put(p);saveDiscoveredProducts();selectedProductIndex=products.length()-1;updateProductPicker();productPicker.setSelection(selectedProductIndex);dialog.dismiss();status.setText("Artikel hinzugefügt. Wähle ihn bei der nächsten Filialabfrage aus.");}
            catch(Exception ex){status.setText("Artikel konnte nicht gespeichert werden.");}
        }));
        dialog.show();
    }

    private void showProductSelectionDialog(){
        ArrayList<String> ids=new ArrayList<>(), names=new ArrayList<>();
        for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p!=null){ids.add(p.optString("id"));names.add(p.optString("name"));}}
        if(ids.isEmpty()){finishError("Keine Pokémon-Produkte im Katalog gefunden");return;}
        String saved=getPreferences(MODE_PRIVATE).getString("checked_product_ids","");
        java.util.LinkedHashSet<String> draft=new java.util.LinkedHashSet<>();
        if(saved.isEmpty()){int initial=Math.max(0,Math.min(selectedProductIndex,ids.size()-1));draft.add(ids.get(initial));}else for(String id:saved.split(","))if(ids.contains(id))draft.add(id);
        CharSequence[] labels=new CharSequence[names.size()+1];boolean[] checked=new boolean[labels.length];
        labels[0]="Alle Produkte";checked[0]=draft.size()==ids.size();
        for(int i=0;i<ids.size();i++){labels[i+1]=names.get(i);checked[i+1]=draft.contains(ids.get(i));}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Produkte für die Abfrage auswählen")
            .setMultiChoiceItems(labels,checked,(d,which,isChecked)->{
                if(which==0){draft.clear();if(isChecked)draft.addAll(ids);}else{String id=ids.get(which-1);if(isChecked)draft.add(id);else draft.remove(id);}
                android.widget.ListView list=((AlertDialog)d).getListView();list.setItemChecked(0,draft.size()==ids.size());
                for(int i=0;i<ids.size();i++)list.setItemChecked(i+1,draft.contains(ids.get(i)));
            }).setNegativeButton("Abbrechen",(d,w)->finishBusy()).setPositiveButton("Abfragen",null).create();
        dialog.setOnCancelListener(d->finishBusy());
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            if(draft.isEmpty()){status.setText("Wähle mindestens ein Produkt aus.");return;}
            getPreferences(MODE_PRIVATE).edit().putString("checked_product_ids",android.text.TextUtils.join(",",draft)).apply();
            dialog.dismiss();startSelectedProductChecks();
        }));
        dialog.show();
    }

    private void startSelectedProductChecks(){
        productQueue.clear();String saved=getPreferences(MODE_PRIVATE).getString("checked_product_ids","");
        java.util.HashSet<String> wanted=new java.util.HashSet<>();if(!saved.isEmpty())wanted.addAll(java.util.Arrays.asList(saved.split(",")));else if(products.length()>0)wanted.add(products.optJSONObject(Math.max(0,Math.min(selectedProductIndex,products.length()-1))).optString("id"));
        for(int i=0;i<products.length();i++)if(wanted.contains(products.optJSONObject(i).optString("id")))productQueue.add(i);
        if(saved.isEmpty()&&!productQueue.isEmpty())getPreferences(MODE_PRIVATE).edit().putString("checked_product_ids",products.optJSONObject(productQueue.get(0)).optString("id")).apply();
        if(productQueue.isEmpty()){finishError("Keine Produkte zur Abfrage ausgewählt");return;}
        productQueueCursor=0;activeProductIndex=productQueue.get(0);selectedProductIndex=activeProductIndex;productPicker.setSelection(selectedProductIndex);renderProduct();
        status.setText("Prüfe "+productQueue.size()+" ausgewählte Produkte in "+queryBranches.length()+" Filialen …");loadProductPage();
    }

    private void scanProductCatalog(){
        String script="(async()=>{const norm=s=>(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase();for(let n=0;n<10;n++){window.scrollTo(0,document.body.scrollHeight);await new Promise(r=>setTimeout(r,250))}window.scrollTo(0,0);const rows=[];const seen=new Set();for(const a of document.querySelectorAll('a[href*=\"/product/\"]')){const m=a.href.match(/\\/(?:de|fr|it|en)\\/product\\/(\\d{8,14})/);if(!m||seen.has(m[1]))continue;seen.add(m[1]);let card=a;for(let i=0;i<4&&card.parentElement;i++){if((card.innerText||'').length>180)break;card=card.parentElement}const img=a.querySelector('img')||card.querySelector('img');const name=(a.innerText||a.getAttribute('aria-label')||a.title||'').trim()||(img?.alt||'').trim()||(card.innerText||'').trim().split('\\n').filter(Boolean)[0]||'';const text=norm(name+' '+(card.innerText||''));if(!/pokemon/.test(text))continue;if(!/(booster|sammelkart|trading card|\\btcg\\b|karten|deck|sleeve|portfolio|\\btin\\b|blister|trainer|elite trainer|\\betb\\b|display|checklane|collection|kollektion|poster|sticker|box|bundle|album|three pack|premium|ex tin)/.test(text))continue;const image=img?.currentSrc||img?.src||'';const priceMatch=(card.innerText||'').match(/(?:CHF|Fr\\.)\\s*([0-9]+[.,][0-9]{2})/i);const onlineOnly=/nur online|only online|online only/i.test(card.innerText||'');rows.push({id:m[1],name:name||('Pokémon Kartenartikel '+m[1]),image_url:image,product_url:'https://www.migros.ch/de/product/'+m[1],price_chf:priceMatch?Number(priceMatch[1].replace(',','.')):0,in_store_assortment:!onlineOnly,online_only:onlineOnly,discovered:true})}window.FinderBridge.discovered(JSON.stringify(rows))})()";
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
        if(subtitle!=null)subtitle.setText(products.length()+" Pokémon-TCG-Artikel · "+branches.length()+" Filialen in den gewählten Regionen");
        if(selectedProductIndex<names.size())productPicker.setSelection(selectedProductIndex);
    }

    private void collectRegions(){
        regionNames.clear();
        int[] counts=new int[CANTON_CODES.length];
        for(int i=0;i<allBranches.length();i++){JSONObject b=allBranches.optJSONObject(i);if(b==null)continue;String c=branchCantons.optString(b.optString("id"),"INT");for(int j=0;j<CANTON_CODES.length;j++)if(CANTON_CODES[j].equals(c))counts[j]++;}
        for(int j=0;j<CANTON_CODES.length;j++)regionNames.put(CANTON_CODES[j],CANTON_CODES[j]+" · "+CANTON_NAMES[j]+" ("+counts[j]+")");
        for(String id:customRegions.keySet())regionNames.put("custom:"+id,customRegions.get(id).optString("name")+" · eigene Region");
    }
    private String regionLabel(String id){
        switch(id.toLowerCase(java.util.Locale.ROOT)){
            case "bern": return "Bern";
            case "fribourg":
            case "freiburg": return "Fribourg";
            case "aare": return "Aare (Bern, Aargau, Solothurn)";
            case "basel": return "Basel";
            case "geneve": return "Genf";
            case "vaud-lausanne": return "Waadt / Lausanne";
            case "neuchatel": return "Neuenburg";
            case "ostschweiz-st-gallen": return "Ostschweiz / St. Gallen";
            case "zentralschweiz": return "Zentralschweiz";
            case "ticino": return "Tessin";
            case "wallis": return "Wallis";
            case "zuerich": return "Zürich";
            case "weitere-schweiz": return "Weitere Filialen Schweiz";
            default:
                String label=id.replace('_',' ').replace('-',' ');
                return label.isEmpty()?"Weitere":label.substring(0,1).toUpperCase(java.util.Locale.ROOT)+label.substring(1);
        }
    }
    private void restoreSelectedRegions(){
        String saved=getPreferences(MODE_PRIVATE).getString("selected_regions",null);
        if(saved==null){selectedRegions.addAll(regionNames.keySet());return;}
        if(saved.equals("__none__"))return;
        if(saved.isEmpty()){selectedRegions.addAll(regionNames.keySet());return;}
        for(String item:saved.split(",")){
            if(regionNames.containsKey(item)){selectedRegions.add(item);continue;}
            for(int i=0;i<allBranches.length();i++){JSONObject b=allBranches.optJSONObject(i);if(b!=null&&item.equals(b.optString("region")))selectedRegions.add(branchCantons.optString(b.optString("id"),"INT"));}
        }
        if(selectedRegions.isEmpty())selectedRegions.addAll(regionNames.keySet());
    }
    private void applyRegionFilter(){
        branches=new JSONArray();
        for(int i=0;i<allBranches.length();i++){
            JSONObject branch=allBranches.optJSONObject(i);if(branch==null)continue;
            String id=branch.optString("id");String canton=branchCantons.optString(id,"INT");boolean include=selectedRegions.contains(canton);
            for(String regionId:selectedRegions)if(regionId.startsWith("custom:")){JSONObject region=customRegions.get(regionId.substring(7));JSONArray stores=region==null?null:region.optJSONArray("store_ids");if(stores!=null)for(int j=0;j<stores.length();j++)if(id.equals(stores.optString(j))){include=true;break;}}
            if(include)branches.put(branch);
        }
        if(regionPicker!=null)updateRegionUi();
        if(subtitle!=null)subtitle.setText(products.length()+" Pokémon-TCG-Artikel · "+branches.length()+" Filialen in den gewählten Regionen");
        applyBranchScope();
        if(refresh!=null)refresh.setText("Ausgewählte Filialen abfragen ("+queryBranches.length()+")");
        if(refresh!=null)refresh.setEnabled(!queryRunning&&queryBranches.length()>0);
        renderRecords();
    }
    private void applyBranchScope(){
        queryBranches=new JSONArray();
        String saved=getPreferences(MODE_PRIVATE).getString("query_branch_ids","");
        java.util.HashSet<String> ids=new java.util.HashSet<>();if(!saved.isEmpty())ids.addAll(java.util.Arrays.asList(saved.split(",")));
        for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);if(b!=null&&(saved.isEmpty()||ids.contains(b.optString("id"))))queryBranches.put(b);}
        updateBranchScopeUi();
    }
    private void updateBranchScopeUi(){
        if(branchScopePicker==null)return;
        String label=getPreferences(MODE_PRIVATE).getString("query_branch_scope_label","");
        String description=label.isEmpty()?(queryBranches.length()==branches.length()?"alle":"Auswahl"):label;
        branchScopePicker.setText("Filialen auswählen / Radiuskarte: "+description+" · "+queryBranches.length()+" / "+branches.length());
        branchScopePicker.setEnabled(!queryRunning);
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
            }).setNegativeButton("Abbrechen",null).setNeutralButton("Eigene Region erstellen",null).setPositiveButton("Übernehmen",(d,w)->{
                selectedRegions.clear();selectedRegions.addAll(draft);
                getPreferences(MODE_PRIVATE).edit().putString("selected_regions",selectedRegions.isEmpty()?"__none__":android.text.TextUtils.join(",",selectedRegions)).apply();
                applyRegionFilter();status.setText(branches.length()+" Migros-Filialen ausgewählt. Die nächste Abfrage prüft nur diese Filialen.");
            }).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{dialog.dismiss();showCustomRegionDialog();}));dialog.show();
    }

    private void showBranchScopeDialog(){
        if(queryRunning)return;
        if(branches.length()==0){status.setText("Wähle zuerst mindestens eine Region aus.");return;}
        ArrayList<String> ids=new ArrayList<>();CharSequence[] labels=new CharSequence[branches.length()+1];boolean[] checked=new boolean[labels.length];
        java.util.HashSet<String> selected=new java.util.HashSet<>();for(int i=0;i<queryBranches.length();i++){JSONObject b=queryBranches.optJSONObject(i);if(b!=null)selected.add(b.optString("id"));}
        labels[0]="Alle Filialen in den gewählten Regionen";checked[0]=queryBranches.length()==branches.length();
        for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);String id=b.optString("id");ids.add(id);labels[i+1]=b.optString("name")+" · "+b.optString("address");checked[i+1]=selected.contains(id);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Filialen für die Abfrage")
            .setMultiChoiceItems(labels,checked,(d,which,isChecked)->{
                if(which==0){selected.clear();if(isChecked)selected.addAll(ids);}else{String id=ids.get(which-1);if(isChecked)selected.add(id);else selected.remove(id);}
                android.widget.ListView list=((AlertDialog)d).getListView();list.setItemChecked(0,selected.size()==ids.size());
                for(int i=0;i<ids.size();i++)list.setItemChecked(i+1,selected.contains(ids.get(i)));
            }).setNegativeButton("Abbrechen",null).setNeutralButton("Radius auf Karte",null).setPositiveButton("Übernehmen",null).create();
        dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{dialog.dismiss();prepareRadiusMap();});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
                if(selected.isEmpty()){status.setText("Wähle mindestens eine Filiale aus.");return;}
                String saved="";
                if(selected.size()!=ids.size()){ArrayList<String> ordered=new ArrayList<>();for(String id:ids)if(selected.contains(id))ordered.add(id);saved=android.text.TextUtils.join(",",ordered);}
                getPreferences(MODE_PRIVATE).edit().putString("query_branch_ids",saved).putString("query_branch_scope_label",selected.size()==ids.size()?"Alle Filialen":"Einzelfilialen").apply();
                applyBranchScope();dialog.dismiss();status.setText(queryBranches.length()+" Filialen für die nächste Abfrage ausgewählt.");
            });
        });dialog.show();
    }

    private void prepareRadiusMap(){
        if(queryRunning){status.setText("Bitte zuerst die laufende Bestandsabfrage abschliessen.");return;}
        if(branches.length()==0){status.setText("Bitte zuerst einen Kanton oder eine Region wählen.");return;}
        // Opening the map should never wait for geocoding hundreds of branches.
        showRadiusMapDialog();
    }

    private void showRadiusMapDialog(){
        int[] radii={2,3,5,10,15,20,30,50};ArrayList<String> radiusLabels=new ArrayList<>();for(int radius:radii)radiusLabels.add(radius+" km");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        TextView hint=new TextView(this);hint.setPadding(dp(12),dp(8),dp(12),dp(8));hint.setText("Tippe auf die Karte, um den Mittelpunkt zu setzen. Wähle den Radius. Es werden ausschliesslich Filialen mit bekanntem Standort im Kreis abgefragt.");box.addView(hint);
        TextView coordinateInfo=new TextView(this);coordinateInfo.setTextSize(11);coordinateInfo.setPadding(dp(12),0,dp(12),dp(5));
        box.addView(coordinateInfo);
        updateRadiusCoordinateInfo(coordinateInfo,false);
        Spinner radiusPicker=new Spinner(this);radiusPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,radiusLabels));box.addView(radiusPicker);
        queryRadiusKm=getPreferences(MODE_PRIVATE).getInt("branch_scope_radius",5);for(int i=0;i<radii.length;i++)if(radii[i]==queryRadiusKm)radiusPicker.setSelection(i);
        radiusMapWebView=new WebView(this);radiusMapReady=false;radiusMapWebView.getSettings().setJavaScriptEnabled(true);radiusMapWebView.addJavascriptInterface(new RadiusMapBridge(),"RadiusBridge");box.addView(radiusMapWebView,new LinearLayout.LayoutParams(-1,dp(440)));
        double[] center=defaultRadiusMapCenter();
        String savedCentre=getPreferences(MODE_PRIVATE).getString("branch_scope_center","");
        try{String[] parts=savedCentre.split(",");if(parts.length==2){double y=Double.parseDouble(parts[0]),x=Double.parseDouble(parts[1]);if(y>45&&y<48&&x>5&&x<11)center=new double[]{y,x};}}catch(Exception ignored){}
        radiusCenterLat=center[0];radiusCenterLon=center[1];
        final int session=++radiusMapSession;
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#map{height:100%;margin:0}</style><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script></head><body><div id='map'></div><script>let map=L.map('map').setView(["+radiusCenterLat+","+radiusCenterLon+"],10);L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);let circle=null,centerMarker=null,km="+queryRadiusKm+";const setCenter=(lat,lon)=>{if(circle)map.removeLayer(circle);if(centerMarker)map.removeLayer(centerMarker);centerMarker=L.marker([lat,lon]).addTo(map);circle=L.circle([lat,lon],{radius:km*1000,color:'#1769d2',fillOpacity:.12}).addTo(map);window.RadiusBridge.center(JSON.stringify({lat:lat,lon:lon}))};window.setRadius=(value)=>{km=Number(value);if(centerMarker){let p=centerMarker.getLatLng();setCenter(p.lat,p.lng)}};map.on('click',e=>setCenter(e.latlng.lat,e.latlng.lng));setCenter("+radiusCenterLat+","+radiusCenterLon+");window.addStores=(rows)=>rows.forEach(s=>L.circleMarker([s.lat,s.lon],{radius:5,color:'#6750a4'}).bindPopup(s.name+'<br>'+s.address).addTo(map));</script></body></html>";
        radiusMapWebView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){radiusMapReady=true;JSONArray markers=new JSONArray();for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);JSONArray c=b==null?null:b.optJSONArray("coordinates");if(c!=null&&c.length()>=2){JSONObject marker=new JSONObject();try{marker.put("name",b.optString("name"));marker.put("address",b.optString("address"));marker.put("lat",c.optDouble(0));marker.put("lon",c.optDouble(1));markers.put(marker);}catch(Exception ignored){}}}view.evaluateJavascript("window.addStores("+markers.toString()+")",null);}});
        radiusMapWebView.loadDataWithBaseURL("https://www.openstreetmap.org/",html,"text/html","UTF-8",null);
        radiusPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){queryRadiusKm=radii[pos];if(radiusMapReady)radiusMapWebView.evaluateJavascript("window.setRadius("+queryRadiusKm+")",null);}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Abfrage im Umkreis").setView(box).setNegativeButton("Abbrechen",null).setPositiveButton("Filialen übernehmen",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{
            ArrayList<String> selected=new ArrayList<>();int unknown=0;
            for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);if(b==null)continue;JSONArray c=b.optJSONArray("coordinates");if(c==null||c.length()<2){unknown++;continue;}if(distanceKm(radiusCenterLat,radiusCenterLon,c.optDouble(0),c.optDouble(1))<=queryRadiusKm)selected.add(b.optString("id"));}
            if(selected.isEmpty()){new AlertDialog.Builder(this).setMessage("Im gewählten Umkreis sind noch keine Filialen mit bestätigten Koordinaten erfasst. Warte, bis weitere Filialstandorte geladen sind, oder vergrössere den Radius.").setPositiveButton("OK",null).show();return;}
            String saved=selected.size()==branches.length()?"":android.text.TextUtils.join(",",selected);
            String label="Radius "+queryRadiusKm+" km";getPreferences(MODE_PRIVATE).edit().putString("query_branch_ids",saved).putString("query_branch_scope_label",label).putString("branch_scope_center",radiusCenterLat+","+radiusCenterLon).putInt("branch_scope_radius",queryRadiusKm).apply();
            applyBranchScope();dialog.dismiss();status.setText(queryBranches.length()+" Filialen im Umkreis "+queryRadiusKm+" km ausgewählt"+(unknown>0?" · Achtung: "+unknown+" Filialen ohne bekannte Koordinaten wurden NICHT berücksichtigt.":"."));
        }));
        dialog.setOnDismissListener(d->radiusMapSession++);
        dialog.show();
        geocodeMissingRadiusBranches(session,coordinateInfo);
    }
    private void updateRadiusCoordinateInfo(TextView label,boolean working){
        int located=0;
        for(int i=0;i<branches.length();i++){
            JSONObject b=branches.optJSONObject(i);
            JSONArray coords=b==null?null:b.optJSONArray("coordinates");
            if(coords!=null&&coords.length()>=2)located++;
        }
        int missing=branches.length()-located;
        label.setText(located+" / "+branches.length()+" Filialstandorte kartiert"
            +(working&&missing>0?" · weitere Standorte werden im Hintergrund gesucht …":"")
            +(missing>0?"\n"+missing+" Standorte noch ohne Koordinaten; diese können erst nach Standortermittlung für den Radius berücksichtigt werden.":""));
    }

    private void geocodeMissingRadiusBranches(int session,TextView indicator){
        ArrayList<JSONObject> pending=new ArrayList<>();
        for(int i=0;i<branches.length();i++){
            JSONObject b=branches.optJSONObject(i);
            JSONArray coords=b==null?null:b.optJSONArray("coordinates");
            if(b!=null&&(coords==null||coords.length()<2))pending.add(b);
        }
        if(pending.isEmpty()){updateRadiusCoordinateInfo(indicator,false);return;}
        if(!android.location.Geocoder.isPresent()){
            indicator.setText("Keine Standortsuche verfügbar. Nur Filialen mit bekannten Koordinaten werden berücksichtigt.");return;
        }
        updateRadiusCoordinateInfo(indicator,true);
        final int limit=Math.min(80,pending.size());
        new Thread(()->{
            android.location.Geocoder geocoder=new android.location.Geocoder(this,java.util.Locale.forLanguageTag("de-CH"));
            for(int i=0;i<limit;i++){
                if(radiusMapSession!=session)return;
                JSONObject b=pending.get(i);
                String address=b.optString("address");
                if(address.isEmpty())continue;
                try{
                    java.util.List<android.location.Address> found=geocoder.getFromLocationName(address+", Schweiz",1);
                    if(found==null||found.isEmpty()||!found.get(0).hasLatitude()||!found.get(0).hasLongitude())continue;
                    double lat=found.get(0).getLatitude(),lon=found.get(0).getLongitude();
                    if(lat<45||lat>48||lon<5||lon>11)continue;
                    runOnUiThread(()->{
                        if(radiusMapSession!=session)return;
                        cacheBranchCoordinates(b.optString("id"),lat,lon);
                        try{
                            JSONObject pin=new JSONObject().put("name",b.optString("name"))
                                .put("address",b.optString("address")).put("lat",lat).put("lon",lon);
                            if(radiusMapReady&&radiusMapWebView!=null)
                                radiusMapWebView.evaluateJavascript("window.addStores(["+pin.toString()+"])",null);
                        }catch(Exception ignored){}
                        updateRadiusCoordinateInfo(indicator,true);
                    });
                }catch(Exception ignored){}
            }
            runOnUiThread(()->{
                if(radiusMapSession!=session)return;
                updateRadiusCoordinateInfo(indicator,false);
                if(pending.size()>limit)
                    indicator.append("\nFür weitere Standorte zuerst einen Kanton wählen und die Karte erneut öffnen.");
            });
        }).start();
    }

    private double[] defaultRadiusMapCenter(){double lat=0,lon=0;int n=0;for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);JSONArray c=b==null?null:b.optJSONArray("coordinates");if(c!=null&&c.length()>=2){lat+=c.optDouble(0);lon+=c.optDouble(1);n++;}}return n>0?new double[]{lat/n,lon/n}:new double[]{46.8,8.2};}
    private double distanceKm(double lat1,double lon1,double lat2,double lon2){double dLat=Math.toRadians(lat2-lat1),dLon=Math.toRadians(lon2-lon1);double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);return 6371.0*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));}
    private class RadiusMapBridge{@JavascriptInterface public void center(String json){runOnUiThread(()->{try{JSONObject p=new JSONObject(json);radiusCenterLat=p.getDouble("lat");radiusCenterLon=p.getDouble("lon");}catch(Exception ignored){}});}}

    private void loadCustomRegions(){try{JSONArray rows=new JSONArray(getPreferences(MODE_PRIVATE).getString("custom_regions_json","[]"));for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&!r.optString("id").isEmpty())customRegions.put(r.optString("id"),r);}}catch(Exception ignored){}}
    private void saveCustomRegions(){JSONArray rows=new JSONArray();for(JSONObject r:customRegions.values())rows.put(r);getPreferences(MODE_PRIVATE).edit().putString("custom_regions_json",rows.toString()).apply();}
    private void showCustomRegionDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),dp(4));
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Name der Region");box.addView(name,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Filiale oder Ort suchen");box.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(this);scroll.addView(choices);box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(360)));
        ArrayList<CheckBox> checks=new ArrayList<>();for(int i=0;i<allBranches.length();i++){JSONObject b=allBranches.optJSONObject(i);if(b==null)continue;CheckBox cb=new CheckBox(this);cb.setText(b.optString("name")+" · "+b.optString("address"));cb.setTag(b.optString("id"));cb.setTextSize(11);checks.add(cb);choices.addView(cb);}
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().trim().toLowerCase(java.util.Locale.ROOT);for(CheckBox cb:checks)cb.setVisibility(q.isEmpty()||cb.getText().toString().toLowerCase(java.util.Locale.ROOT).contains(q)?View.VISIBLE:View.GONE);}public void afterTextChanged(Editable e){}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Eigene Region").setView(box).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn->{String n=name.getText().toString().trim();JSONArray ids=new JSONArray();for(CheckBox cb:checks)if(cb.isChecked())ids.put(String.valueOf(cb.getTag()));if(n.isEmpty()){name.setError("Bitte einen Namen eingeben.");return;}if(ids.length()==0){search.setError("Wähle mindestens eine Filiale aus.");return;}String id=java.util.UUID.randomUUID().toString();JSONObject region=new JSONObject();try{region.put("id",id);region.put("name",n);region.put("store_ids",ids);}catch(Exception ignored){}customRegions.put(id,region);saveCustomRegions();regionNames.put("custom:"+id,n+" · eigene Region");selectedRegions.clear();selectedRegions.add("custom:"+id);getPreferences(MODE_PRIVATE).edit().putString("selected_regions","custom:"+id).apply();applyRegionFilter();status.setText("Eigene Region gespeichert · "+branches.length()+" Filialen ausgewählt.");dialog.dismiss();}));dialog.show();
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
        String id=p.optString("id");status.setText("Artikel "+(productQueueCursor+1)+"/"+productQueue.size()+": "+p.optString("name"));waitingForPage=true;
        webView.loadUrl("https://www.migros.ch/de/product/"+id);
    }

    private void startMigrosCheck() {
        JSONObject current=products.optJSONObject(activeProductIndex);String productId=current.optString("id");boolean onlineOnly=current.optBoolean("online_only",false);
        String progressProductLabel=(productQueueCursor+1)+"/"+productQueue.size();
        String script="(async()=>{const sleep=ms=>new Promise(r=>setTimeout(r,ms));const norm=s=>(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();"
          +"const branches="+queryBranches.toString()+";const productId="+JSONObject.quote(productId)+";const onlineOnly="+onlineOnly+";const send=(m,o)=>window.FinderBridge[m](JSON.stringify(o));const seen=new Set();"
          +"const amount=v=>{const n=Number(String(v||'').replace(/[^0-9,.]/g,'').replace(',','.'));return Number.isFinite(n)&&n>0?n:null};"
          +"let livePrice=null,originalPrice=null;const metas=['meta[property=\"product:price:amount\"]','meta[itemprop=\"price\"]','[itemprop=\"price\"]'];for(const s of metas){const e=document.querySelector(s);const n=amount(e?.content||e?.getAttribute('content')||e?.innerText);if(n){livePrice=n;break}}"
          +"if(!livePrice){for(const script of document.querySelectorAll('script[type=\"application/ld+json\"]')){try{const walk=o=>{if(!o)return;if(Array.isArray(o)){o.forEach(walk);return}if(typeof o==='object'){if(o.price!=null&&(/offer/i.test(String(o['@type']||''))||o.priceCurrency)){const n=amount(o.price);if(n&&!livePrice)livePrice=n}if(o.offers)walk(o.offers);for(const [k,v] of Object.entries(o))if(k!=='offers')walk(v)}};walk(JSON.parse(script.textContent))}catch(e){}}}"
          +"if(!livePrice){const e=document.querySelector('[data-testid*=\"price\"], [class*=\"price\"] [itemprop=\"price\"], [itemprop=\"price\"]');if(e)livePrice=amount(e.content||e.getAttribute('content')||e.innerText)}"
          +"if(livePrice){const old=document.querySelector('del, s, [class*=\"old-price\"], [class*=\"original-price\"], [data-testid*=\"old-price\"]');if(old)originalPrice=amount(old.innerText);send('livePrice',{product_id:productId,price:livePrice,original_price:originalPrice,source:'Migros-Produktseite'})}"
          +"if(onlineOnly){for(const b of branches)send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,status:'online_only',source:'Migros-Sortiment kennzeichnet den Artikel als nur online erhältlich'});send('complete',{product_id:productId,checked:branches.length});return}"
          +"const classify=raw=>{let m=raw.match(/(?:nur\\s*)?ca\\.?\\s*(\\d+)\\s*auf lager/i)||raw.match(/(\\d+)\\s*auf lager/i);if(m)return {status:'estimated_in_stock',quantity:Number(m[1]),source:raw};if(/nicht auf lager/i.test(norm(raw)))return {status:'out_of_stock',source:raw};if(/\\bauf lager\\b|verfugbar/i.test(norm(raw)))return {status:'in_stock',source:raw};return null};"
          +"const collectCoordinates=()=>{for(const a of modal.querySelectorAll('a[href]')){if(!a.href.includes('google')||!a.href.includes('destination='))continue;const m=a.href.match(/[?&]destination=([-0-9.]+)(?:%2C|,)([-0-9.]+)/i);if(!m)continue;let card=a;for(let k=0;k<2&&card.parentElement;k++)card=card.parentElement;const label=norm(card.innerText||a.parentElement?.innerText||'');const b=branches.find(x=>label.includes(norm(x.name)));if(b)send('coordinate',{branch_id:b.id,lat:Number(m[1]),lon:Number(m[2])})}};"
          +"const scan=body=>{const lines=body.split(/\\n+/).map(x=>x.trim()).filter(Boolean);for(const b of branches){if(seen.has(b.id))continue;const name=norm(b.name),addr=norm((b.address||'').split(',')[0]);let i=lines.findIndex(x=>norm(x)===name||norm(x).includes(name));if(i<0&&addr.length>5)i=lines.findIndex(x=>norm(x).startsWith(addr));if(i<0)continue;const raw=lines.slice(i,i+6).join(' | ');const found=classify(raw);if(found){seen.add(b.id);send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,...found})}}};"
          +"const labelOf=b=>norm([b.innerText,b.getAttribute('aria-label'),b.getAttribute('title')].filter(Boolean).join(' '));const isStoreAvailability=b=>{const t=labelOf(b);return (t.includes('verfugbarkeit')&&t.includes('filial'))||(t.includes('availability')&&t.includes('store'))};let button=null;for(let n=0;n<120;n++){button=[...document.querySelectorAll('button,[role=button]')].find(isStoreAvailability);if(button)break;await sleep(250)}"
          +"if(!button){send('failed',{product_id:productId,error:'Migros-Verfügbarkeitsbutton fehlt',title:document.title,body:(document.body?.innerText||'').slice(0,700)});return}button.click();"
          +"let modal=null,input=null;for(let n=0;n<80;n++){modal=document.querySelector('#bottomSmallModal');input=document.querySelector('#store-search-autocomplete-form-input');if(modal&&input)break;await sleep(250)}if(!input){send('failed',{product_id:productId,error:'Filialsuche öffnet sich nicht',title:document.title});return}collectCoordinates();scan(modal.innerText||'');"
          +"const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;let searched=0;for(const b of branches){if(seen.has(b.id))continue;searched++;send('progress',{message:'Artikel "+progressProductLabel+" · Filiale '+searched+'/'+branches.length+': '+b.name+' …'});const before=modal.innerText||'';setter.call(input,b.address);input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));let body=before;"
          +"for(let n=0;n<60;n++){await sleep(250);body=modal.innerText||'';if(body!==before&&(norm(body).includes(norm(b.name))||norm(body).includes(norm((b.address||'').split(',')[0])))){await sleep(350);body=modal.innerText||body;break}}collectCoordinates();scan(body);if(!seen.has(b.id)){seen.add(b.id);send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,status:'unknown',source:'Migros hat keinen lesbaren Status geliefert'})}}"
          +"send('complete',{product_id:productId,checked:branches.length})})().catch(e=>window.FinderBridge.failed(JSON.stringify({product_id:"+JSONObject.quote(productId)+",error:String(e&&e.message||e)})))";
        webView.evaluateJavascript(script,null);
    }

    private class ResultBridge {
        @JavascriptInterface public void progress(String json){runOnUiThread(()->{try{status.setText(new JSONObject(json).optString("message","Prüfe Migros-Filialen …"));}catch(Exception ignored){}});}
        @JavascriptInterface public void coordinate(String json){runOnUiThread(()->{try{JSONObject c=new JSONObject(json);cacheBranchCoordinates(c.getString("branch_id"),c.getDouble("lat"),c.getDouble("lon"));}catch(Exception ignored){}});}
        @JavascriptInterface public void discovered(String json){runOnUiThread(()->{try{JSONArray found=new JSONArray(json);mergeDiscoveredProducts(found);if(discoveryPageIndex+1<DISCOVERY_URLS.length){discoveryPageIndex++;status.setText("Suche weitere Kartenartikel auf der Migros-Sammelkartenspielseite …");waitingForPage=true;webView.loadUrl(DISCOVERY_URLS[discoveryPageIndex]);return;}discoveringProducts=false;updateProductPicker();if(automaticCheck)startSelectedProductChecks();else showProductSelectionDialog();}catch(Exception ex){discoveringProducts=false;if(automaticCheck)startSelectedProductChecks();else showProductSelectionDialog();}});}
        @JavascriptInterface public void record(String json){runOnUiThread(()->{try{
            JSONObject row=new JSONObject(json);String productId=row.getString("product_id"),branchId=row.getString("branch_id");
            Map<String,JSONObject> productRows=recordsByProduct.get(productId);if(productRows==null){productRows=new LinkedHashMap<>();recordsByProduct.put(productId,productRows);}
            JSONObject old=productRows.get(branchId);String oldStatus=old==null?"":old.optString("status");int oldQty=old==null?-1:old.optInt("quantity",-1);
            if("unknown".equals(oldStatus)){oldStatus=old.optString("last_known_status",oldStatus);oldQty=old.optInt("last_known_quantity",oldQty);}
            if("out_of_stock".equals(oldStatus))oldQty=0;
            String newStatus=row.optString("status");int newQty="out_of_stock".equals(newStatus)?0:row.optInt("quantity",-1);
            boolean oldAvailable=oldStatus.equals("in_stock")||oldStatus.equals("estimated_in_stock");boolean newAvailable=newStatus.equals("in_stock")||newStatus.equals("estimated_in_stock");
            String signal="";if(old!=null&&!oldAvailable&&newAvailable)signal="restock";else if(oldStatus.equals("estimated_in_stock")&&newStatus.equals("estimated_in_stock")&&newQty>oldQty)signal="up";else if(oldStatus.equals("estimated_in_stock")&&newStatus.equals("estimated_in_stock")&&newQty<oldQty)signal="down";else if(oldStatus.equals("estimated_in_stock")&&newStatus.equals("out_of_stock"))signal="down";
            if("unknown".equals(newStatus)&&old!=null){row.put("last_known_status",oldStatus);if(oldQty>=0)row.put("last_known_quantity",oldQty);}
            if(!signal.isEmpty()&&old!=null){row.put("stock_signal",signal);row.put("stock_previous_quantity",oldQty);row.put("stock_current_quantity",newQty);row.put("stock_delta_quantity",oldQty>=0&&newQty>=0?newQty-oldQty:0);row.put("stock_change_at",System.currentTimeMillis());
                JSONObject event=new JSONObject();event.put("product_id",productId);event.put("branch_id",branchId);event.put("branch_name",row.optString("name"));event.put("previous",oldQty);event.put("current",newQty);event.put("delta",oldQty>=0&&newQty>=0?newQty-oldQty:0);event.put("kind",signal);event.put("at",System.currentTimeMillis());stockEvents.put(event);pruneHistory();
                String delta=oldQty>=0&&newQty>=0?(newQty-oldQty>=0?"+":"")+(newQty-oldQty)+" Stück":"Bestand geändert";
                String title=signal.equals("restock")?"Wieder verfügbar":signal.equals("up")?"Bestand gestiegen":"Bestand gesunken";
                String message=productName(productId)+" · "+row.optString("name")+" · "+delta+" ("+oldQty+" → "+newQty+")";
                String pref=signal.equals("restock")?"notify_restock":signal.equals("up")?"notify_stock_increase":"notify_stock_decrease";
                if(notificationsEnabled(pref)&&productNotificationsEnabled(productId))postStockNotification(title,message,productId,branchId);
            }else if(old!=null&&old.has("stock_change_at")){
                row.put("stock_signal",old.optString("stock_signal"));row.put("stock_previous_quantity",old.optInt("stock_previous_quantity",-1));row.put("stock_current_quantity",old.optInt("stock_current_quantity",-1));row.put("stock_delta_quantity",old.optInt("stock_delta_quantity",0));row.put("stock_change_at",old.optLong("stock_change_at",0));
            }
            row.put("checked_at",now());productRows.put(branchId,row);saveLocal();if(productId.equals(currentProductId()))renderRecords();
        }catch(Exception ignored){}});}
        @JavascriptInterface public void livePrice(String json){runOnUiThread(()->{try{JSONObject incoming=new JSONObject(json);String id=incoming.getString("product_id");double current=incoming.getDouble("price");JSONObject old=livePricesByProduct.get(id);double previous=old==null?catalogPrice(id):old.optDouble("price",catalogPrice(id));boolean hasBaseline=previous>0;JSONObject saved=new JSONObject();saved.put("price",current);saved.put("checked_at",now());saved.put("source",incoming.optString("source"));if(hasBaseline&&Math.abs(current-previous)>=0.01){saved.put("previous_price",previous);saved.put("change",current<previous?"down":"up");if(old!=null){String direction=current<previous?"notify_price_drop":"notify_price_increase";if(notificationsEnabled(direction))postNotification(current<previous?"Preis gesunken":"Preis gestiegen",productName(id)+": CHF "+String.format(java.util.Locale.GERMANY,"%.2f",previous)+" → CHF "+String.format(java.util.Locale.GERMANY,"%.2f",current),id.hashCode());}}else saved.put("change","none");double original=incoming.optDouble("original_price",0);if(original>current){saved.put("promotion",true);saved.put("original_price",original);if(old!=null&&!old.optBoolean("promotion")&&notificationsEnabled("notify_promotion")&&(!hasBaseline||Math.abs(current-previous)<0.01))postNotification("Aktion erkannt",productName(id)+" · jetzt CHF "+String.format(java.util.Locale.GERMANY,"%.2f",current),id.hashCode());}livePricesByProduct.put(id,saved);saveLocal();if(id.equals(currentProductId()))renderProduct();}catch(Exception ignored){}});}
        @JavascriptInterface public void failed(String json){runOnUiThread(()->{try{JSONObject d=new JSONObject(json);handleProductFailure(d.optString("product_id"),d.optString("error","Migros-Fehler"));}catch(Exception ex){handleProductFailure("", "Migros-Abfrage fehlgeschlagen.");}});}
        @JavascriptInterface public void complete(String json){runOnUiThread(()->{String id=products.optJSONObject(activeProductIndex).optString("id");checkedByProduct.put(id,now());recordStockSnapshot(id);saveLocal();productQueueCursor++;if(productQueueCursor<productQueue.size()){activeProductIndex=productQueue.get(productQueueCursor);loadProductPage();}else finishAll();});}
    }

    private boolean notificationsEnabled(String key){return getPreferences(MODE_PRIVATE).getBoolean(key,key.equals("notify_restock")||key.equals("notify_stock_increase"));}
    private void postNotification(String title,String message,int id){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(manager==null)return;
        String channel="pokemon_inventory_alerts";if(Build.VERSION.SDK_INT>=26){manager.createNotificationChannel(new NotificationChannel(channel,"Pokémon Card Finder",NotificationManager.IMPORTANCE_DEFAULT));}
        long eventTime=System.currentTimeMillis();Intent intent=new Intent(this,MainActivity.class).setData(android.net.Uri.parse("pcf://notice/"+id+"/"+eventTime));PendingIntent pending=PendingIntent.getActivity(this,(int)(eventTime&0x7fffffff),intent,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(pending).setAutoCancel(true);
        manager.notify("pcf:"+id+":"+eventTime,0,builder.build());
    }

    private void showNotificationSettings(){
        LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(dp(20),dp(4),dp(20),0);
        String[][] rows={{"notify_restock","Wiederverfügbarkeit"},{"notify_stock_increase","Bestandssteigerungen"},{"notify_stock_decrease","Bestandsrückgänge"},{"notify_price_drop","Preis sinkt"},{"notify_price_increase","Preis steigt"},{"notify_promotion","Neue Rabattaktion"}};
        CheckBox[] boxes=new CheckBox[rows.length];for(int i=0;i<rows.length;i++){boxes[i]=new CheckBox(this);boxes[i].setText(rows[i][1]);boxes[i].setChecked(getPreferences(MODE_PRIVATE).getBoolean(rows[i][0],i==0||i==1));options.addView(boxes[i]);}
        TextView productLabel=new TextView(this);productLabel.setText("Produkte für Bestandsmeldungen");productLabel.setTypeface(null,1);productLabel.setPadding(0,dp(10),0,dp(4));options.addView(productLabel);
        boolean filtering=getPreferences(MODE_PRIVATE).getBoolean("notify_product_filter_enabled",false);String savedProducts=getPreferences(MODE_PRIVATE).getString("notify_product_ids","");java.util.HashSet<String> chosen=new java.util.HashSet<>();for(String id:savedProducts.split(","))if(!id.isEmpty())chosen.add(id);
        ArrayList<CheckBox> productBoxes=new ArrayList<>();for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p==null)continue;CheckBox cb=new CheckBox(this);cb.setText(p.optString("name"));cb.setTextSize(11);cb.setChecked(!filtering||chosen.contains(p.optString("id")));cb.setTag(p.optString("id"));productBoxes.add(cb);options.addView(cb);}
        Button test=new Button(this);test.setText("Testbenachrichtigung senden");options.addView(test);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Benachrichtigungen").setMessage("Hinweise werden nach einer Bestands- oder Preisprüfung auf diesem Gerät ausgelöst.").setView(options).setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",null).create();
        test.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},41);return;}postNotification("Pokémon Card Finder · Test","Benachrichtigungen sind auf diesem Gerät aktiv.",(int)(System.currentTimeMillis()&0x7fffffff));});
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{boolean any=false;android.content.SharedPreferences.Editor editor=getPreferences(MODE_PRIVATE).edit();for(int i=0;i<rows.length;i++){editor.putBoolean(rows[i][0],boxes[i].isChecked());any|=boxes[i].isChecked();}StringBuilder ids=new StringBuilder();for(CheckBox cb:productBoxes)if(cb.isChecked()){if(ids.length()>0)ids.append(',');ids.append(cb.getTag());}editor.putBoolean("notify_product_filter_enabled",true).putString("notify_product_ids",ids.toString()).apply();if(any&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},41);dialog.dismiss();}));dialog.show();
    }
    private boolean productNotificationsEnabled(String productId){if(!getPreferences(MODE_PRIVATE).getBoolean("notify_product_filter_enabled",false))return true;String ids=getPreferences(MODE_PRIVATE).getString("notify_product_ids","");for(String id:ids.split(","))if(productId.equals(id))return true;return false;}

    private void postStockNotification(String title,String message,String productId,String branchId){
        long eventTime=System.currentTimeMillis();String tag="pcf:"+productId+":"+branchId+":"+eventTime;
        Intent intent=new Intent(this,MainActivity.class).putExtra("open_product_id",productId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).setData(android.net.Uri.parse("pcf://stock/"+productId+"/"+branchId+"/"+eventTime));
        PendingIntent pending=PendingIntent.getActivity(this,(int)(eventTime&0x7fffffff),intent,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(manager==null)return;
        String channel="pokemon_inventory_alerts";if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(channel,"Pokémon Card Finder",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(pending).setAutoCancel(true);
        manager.notify(tag,0,builder.build());
    }

    private String currentScopeKey(){ArrayList<String> ids=new ArrayList<>(selectedRegions);Collections.sort(ids);String regionScope=android.text.TextUtils.join("|",ids);if(queryBranches.length()==branches.length())return regionScope;ArrayList<String> stores=new ArrayList<>();for(int i=0;i<queryBranches.length();i++){JSONObject b=queryBranches.optJSONObject(i);if(b!=null)stores.add(b.optString("id"));}Collections.sort(stores);return regionScope+"#stores:"+Integer.toHexString(stores.hashCode());}
    private void recordStockSnapshot(String productId){
        Map<String,JSONObject> rows=recordsByProduct.get(productId);int quantity=0,available=0,out=0;
        for(int i=0;i<queryBranches.length();i++){JSONObject b=queryBranches.optJSONObject(i);if(b==null)continue;JSONObject r=rows==null?null:rows.get(b.optString("id"));if(r==null)continue;String state=r.optString("status");if(state.equals("estimated_in_stock")){quantity+=Math.max(0,r.optInt("quantity",0));available++;}else if(state.equals("in_stock"))available++;else if(state.equals("out_of_stock"))out++;}
        try{JSONObject point=new JSONObject();point.put("product_id",productId);point.put("scope",currentScopeKey());point.put("at",System.currentTimeMillis());point.put("quantity",quantity);point.put("available",available);point.put("out",out);stockSnapshots.put(point);pruneHistory();}catch(Exception ignored){}
    }
    // Keep the complete local measurement history for backup/export. The chart and
    // seven-day activity view apply their date filters only while displaying data.
    private void pruneHistory() { }
    private void showStockHistory(){
        pruneHistory();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),dp(8));
        TextView heading=new TextView(this);heading.setText("Geschätzter Gesamtbestand · ausgewählte Filialen");heading.setTextSize(12);heading.setTypeface(null,1);box.addView(heading);
        HistoryChart chart=new HistoryChart();box.addView(chart,new LinearLayout.LayoutParams(-1,dp(190)));
        TextView eventsTitle=new TextView(this);eventsTitle.setText("Bestandsänderungen der letzten 7 Tage");eventsTitle.setTextSize(12);eventsTitle.setTypeface(null,1);eventsTitle.setPadding(0,dp(8),0,dp(4));box.addView(eventsTitle);
        LinearLayout events=new LinearLayout(this);events.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(this);scroll.addView(events);box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(250)));
        java.util.Set<String> visibleBranches=selectedBranchIds();ArrayList<JSONObject> sorted=new ArrayList<>();String pid=currentProductId();for(int i=0;i<stockEvents.length();i++){JSONObject e=stockEvents.optJSONObject(i);if(e!=null&&e.optLong("at")>=System.currentTimeMillis()-7L*24L*60L*60L*1000L&&pid.equals(e.optString("product_id"))&&visibleBranches.contains(e.optString("branch_id")))sorted.add(e);}
        Collections.sort(sorted,(a,b)->Long.compare(b.optLong("at"),a.optLong("at")));
        if(sorted.isEmpty()){TextView empty=new TextView(this);empty.setText("Noch keine Bestandsänderungen erfasst.");empty.setTextSize(11);events.addView(empty);}
        for(JSONObject e:sorted){int delta=e.optInt("delta");TextView line=new TextView(this);line.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(e.optLong("at")))+" · "+e.optString("branch_name")+"\n"+e.optInt("previous")+" → "+e.optInt("current")+" Stück ("+(delta>=0?"+":"")+delta+" Stück)");line.setTextSize(11);line.setPadding(0,dp(5),0,dp(5));line.setTextColor(delta>=0?Color.rgb(23,105,210):Color.rgb(180,70,78));events.addView(line);}
        new AlertDialog.Builder(this).setTitle(productName(pid)+" · Verlauf").setView(box).setPositiveButton("Schliessen",null).show();
    }
    private class HistoryChart extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        HistoryChart(){super(MainActivity.this);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float left=dp(35),right=getWidth()-dp(10),top=dp(12),bottom=getHeight()-dp(24);paint.setColor(Color.LTGRAY);paint.setStrokeWidth(dp(1));canvas.drawLine(left,top,left,bottom,paint);canvas.drawLine(left,bottom,right,bottom,paint);
            ArrayList<JSONObject> points=new ArrayList<>();long cutoff=System.currentTimeMillis()-7L*24L*60L*60L*1000L;for(int i=0;i<stockSnapshots.length();i++){JSONObject p=stockSnapshots.optJSONObject(i);if(p!=null&&p.optLong("at")>=cutoff&&p.optString("product_id").equals(currentProductId())&&p.optString("scope").equals(currentScopeKey()))points.add(p);}
            Collections.sort(points,(a,b)->Long.compare(a.optLong("at"),b.optLong("at")));paint.setTextSize(dp(9));paint.setColor(Color.GRAY);canvas.drawText("Stück",dp(3),top+dp(8),paint);if(points.isEmpty()){canvas.drawText("Die Grafik wird nach der nächsten Bestandsabfrage aufgebaut.",left+dp(4),top+dp(35),paint);return;}
            int max=1;for(JSONObject p:points)max=Math.max(max,p.optInt("quantity"));Path path=new Path();for(int i=0;i<points.size();i++){float x=points.size()==1?(left+right)/2:left+(right-left)*i/(points.size()-1);float y=bottom-(bottom-top)*points.get(i).optInt("quantity")/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setColor(Color.rgb(23,105,210));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);for(int i=0;i<points.size();i++){float x=points.size()==1?(left+right)/2:left+(right-left)*i/(points.size()-1);float y=bottom-(bottom-top)*points.get(i).optInt("quantity")/max;canvas.drawCircle(x,y,dp(3),paint);}paint.setColor(Color.GRAY);canvas.drawText(points.size()+" Messpunkte · aktueller Bestand ca. "+points.get(points.size()-1).optInt("quantity")+" Stück",left,bottom+dp(16),paint);
        }
    }

    private void handleProductFailure(String productId,String error){failedProductChecks.add(productName(productId)+": "+error);productQueueCursor++;if(productQueueCursor<productQueue.size()){activeProductIndex=productQueue.get(productQueueCursor);status.setText("Produkt konnte nicht geprüft werden; fahre mit der nächsten Abfrage fort …");loadProductPage();}else finishAll();}
    private void finishAll(){int total=0,unknown=0;for(Map<String,JSONObject> rows:recordsByProduct.values())for(String regionBranchId:selectedBranchIds())if(rows.containsKey(regionBranchId)){total++;if(rows.get(regionBranchId).optString("status").equals("unknown"))unknown++;}String failures=failedProductChecks.isEmpty()?"":" · Nicht geprüft: "+android.text.TextUtils.join("; ",failedProductChecks);status.setText("Abfrage fertig · "+total+" von "+(productQueue.size()*queryBranches.length())+" ausgewählten Artikel-Filial-Datensätzen · "+unknown+" ohne lesbaren Status"+failures);checkedAt.setText("Zuletzt geprüft: "+now()+" · Ergebnisse lokal gespeichert");finishBusy();renderProduct();updateAutoRefreshInfo();scheduleAutomaticRefresh(0);}
    private void finishError(String message){status.setText(message+". Bisherige Ergebnisse bleiben lokal gespeichert.");finishBusy();updateAutoRefreshInfo();scheduleAutomaticRefresh(0);}
    private void finishBusy(){queryRunning=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);loadingSpinner.setVisibility(View.GONE);refresh.setEnabled(queryBranches.length()>0);updateRegionUi();updateBranchScopeUi();}
    private java.util.Set<String> selectedBranchIds(){java.util.HashSet<String> ids=new java.util.HashSet<>();for(int i=0;i<queryBranches.length();i++){JSONObject b=queryBranches.optJSONObject(i);if(b!=null)ids.add(b.optString("id"));}return ids;}
    private void scheduleAutomaticRefresh(long delayOverride){autoRefreshHandler.removeCallbacks(autoRefreshRunnable);if(!activityResumed||autoRefreshToggle==null||!autoRefreshToggle.isChecked())return;long anchor=getPreferences(MODE_PRIVATE).getLong("auto_refresh_anchor_ms",0);long delay=delayOverride>0?delayOverride:(anchor==0?0:Math.max(0,anchor+AUTO_REFRESH_INTERVAL_MS-System.currentTimeMillis()));autoRefreshHandler.postDelayed(autoRefreshRunnable,delay);updateAutoRefreshInfo();}
    private void checkAutomaticRefresh(){if(backupBusy||!activityResumed||autoRefreshToggle==null||!autoRefreshToggle.isChecked())return;if(queryRunning){scheduleAutomaticRefresh(60_000);return;}long anchor=getPreferences(MODE_PRIVATE).getLong("auto_refresh_anchor_ms",0);if(anchor==0||System.currentTimeMillis()-anchor>=AUTO_REFRESH_INTERVAL_MS){status.setText("Automatische Live-Aktualisierung läuft …");beginCheck(true);}else scheduleAutomaticRefresh(0);}
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
        for(int i=0;i<queryBranches.length();i++){
            JSONObject b=queryBranches.optJSONObject(i); if(b==null)continue;
            JSONObject row=rows==null?null:rows.get(b.optString("id")); String state=row==null?"unchecked":row.optString("status","unknown");
            if(state.equals("in_stock")||state.equals("estimated_in_stock"))available++;else if(state.equals("out_of_stock"))outOfStock++;else if(state.equals("unknown"))unclear++;else if(state.equals("online_only"))onlineOnly++;else unchecked++;
            String searchable=(b.optString("name")+" "+b.optString("address")).toLowerCase(java.util.Locale.ROOT);
            if(!searchable.contains(query)||!matchesFilter(filter,state))continue;
            visible.add(b);
        }
        if(stockSummary!=null)stockSummary.setText("Abfrage-Auswahl · Verfügbar: "+available+" · Nicht auf Lager: "+outOfStock+" · Nur online: "+onlineOnly+" · Unklar: "+unclear+" · Offen: "+unchecked);
        if(filter.equals("Höchster Bestand"))Collections.sort(visible,(a,b)->Integer.compare(stockQuantity(rows,b),stockQuantity(rows,a)));
        resultList.removeAllViews();
        if(visible.isEmpty()){TextView empty=new TextView(this);empty.setText("Keine ausgewählte Abfrage-Filiale passt zu Suche und Filter.");empty.setTextSize(12);empty.setPadding(dp(8),dp(12),dp(8),dp(12));resultList.addView(empty);return;}
        for(JSONObject branch:visible){String id=branch.optString("id");JSONObject row=rows==null?null:rows.get(id);String state=row==null?"unchecked":row.optString("status","unknown");LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);item.setBackgroundColor(Color.WHITE);item.setPadding(dp(10),dp(8),dp(10),dp(8));LinearLayout.LayoutParams itemParams=params(dp(5));resultList.addView(item,itemParams);
            View dot=new View(this);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(statusColor(state));dot.setBackground(circle);item.addView(dot,new LinearLayout.LayoutParams(dp(12),dp(12)));
            LinearLayout textColumn=new LinearLayout(this);textColumn.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,-2,1);textParams.leftMargin=dp(10);item.addView(textColumn,textParams);
            TextView availability=new TextView(this);availability.setText(label(row));availability.setTextSize(12);availability.setTextColor(statusColor(state));availability.setTypeface(null,1);textColumn.addView(availability);
            TextView branchText=new TextView(this);branchText.setText(branch.optString("name")+" · "+branch.optString("address"));branchText.setTextSize(11);branchText.setTextColor(Color.rgb(70,68,80));textColumn.addView(branchText);
            if(row!=null){String signal=row.optString("stock_signal");long changedAt=row.optLong("stock_change_at",0);if(!signal.isEmpty()&&changedAt>0&&System.currentTimeMillis()-changedAt<24L*60L*60L*1000L){int delta=row.optInt("stock_delta_quantity",0),previous=row.optInt("stock_previous_quantity",-1),current=row.optInt("stock_current_quantity",-1);TextView alert=new TextView(this);String label=signal.equals("restock")?"↗ Wieder verfügbar":signal.equals("up")?"↗ Bestand gestiegen":"↓ Bestand gesunken";if(previous>=0&&current>=0)label+=" · "+(delta>=0?"+":"")+delta+" Stück ("+previous+" → "+current+")";alert.setText(label);alert.setTextSize(10);alert.setTextColor(signal.equals("down")?Color.rgb(180,70,78):Color.rgb(23,105,210));alert.setTypeface(null,1);textColumn.addView(alert);}}
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
    private android.content.SharedPreferences coopPreferences() {
        return getSharedPreferences("coop_finder",MODE_PRIVATE);
    }
    private void chooseBackupExport(){
        if(queryRunning||backupBusy){
            new AlertDialog.Builder(this).setMessage("Bitte zuerst die laufende Abfrage oder Sicherung abschliessen.").setPositiveButton("OK",null).show();return;
        }
        backupBusy=true;autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String stamp=new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm",java.util.Locale.ROOT).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE,"pokemon-card-finder-backup-"+stamp+".json");
        try{startActivityForResult(intent,REQUEST_BACKUP_EXPORT);}
        catch(Exception ex){backupBusy=false;showBackupError("Dateiauswahl nicht verfügbar: "+ex.getMessage());}
    }
    private void chooseBackupImport(){
        if(queryRunning||backupBusy){
            new AlertDialog.Builder(this).setMessage("Bitte zuerst die laufende Abfrage oder Sicherung abschliessen.").setPositiveButton("OK",null).show();return;
        }
        new AlertDialog.Builder(this).setTitle("Historische Daten wiederherstellen")
            .setMessage("Wähle eine zuvor exportierte JSON-Datei. Gespeicherte Bestands- und Verlaufsdaten von Migros und Coop werden zusammengeführt. Gleiche Messpunkte werden nicht doppelt importiert.")
            .setNegativeButton("Abbrechen",null).setPositiveButton("Backup auswählen",(d,w)->{
                backupBusy=true;autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");
                try{startActivityForResult(intent,REQUEST_BACKUP_IMPORT);}
                catch(Exception ex){backupBusy=false;showBackupError("Dateiauswahl nicht verfügbar: "+ex.getMessage());}
            }).show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_BACKUP_EXPORT&&requestCode!=REQUEST_BACKUP_IMPORT)return;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null){
            backupBusy=false;scheduleAutomaticRefresh(0);return;
        }
        final android.net.Uri destination=data.getData();
        final boolean export=requestCode==REQUEST_BACKUP_EXPORT;
        new Thread(()->{
            try{
                String message;
                if(export){
                    String backup=BackupManager.exportJson(getPreferences(MODE_PRIVATE),coopPreferences());
                    try(java.io.OutputStream stream=getContentResolver().openOutputStream(destination,"w")){
                        if(stream==null)throw new java.io.IOException("Die Zieldatei konnte nicht geöffnet werden.");
                        byte[] bytes=backup.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        stream.write(bytes);stream.flush();
                        message="Backup gespeichert ("+bytes.length/1024+" KB). Migros und Coop sind enthalten.";
                    }
                }else{
                    java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
                    try(java.io.InputStream stream=getContentResolver().openInputStream(destination)){
                        if(stream==null)throw new java.io.IOException("Datei nicht lesbar.");
                        byte[] chunk=new byte[8192];int count,total=0;
                        while((count=stream.read(chunk))!=-1){
                            total+=count;if(total>BACKUP_MAX_BYTES)throw new java.io.IOException("Backup grösser als 50 MB.");
                            buffer.write(chunk,0,count);
                        }
                    }
                    message=BackupManager.importJson(new String(buffer.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),
                        getPreferences(MODE_PRIVATE),coopPreferences());
                }
                final String result=message;
                runOnUiThread(()->{
                    backupBusy=false;
                    if(export)scheduleAutomaticRefresh(0);
                    new AlertDialog.Builder(this).setTitle(export?"Backup heruntergeladen":"Backup wiederhergestellt")
                        .setMessage(result+(export?"\n\nBewahre die JSON-Datei auch ausserhalb der App auf.":"\n\nDie App lädt die übernommenen Daten jetzt neu."))
                        .setPositiveButton("OK",(d,w)->{if(!export)recreate();}).show();
                });
            }catch(Exception ex){
                runOnUiThread(()->{backupBusy=false;showBackupError(ex.getMessage());scheduleAutomaticRefresh(0);});
            }
        }).start();
    }
    private void showBackupError(String message){
        new AlertDialog.Builder(this).setTitle("Backup fehlgeschlagen")
            .setMessage(message==null?"Die Datei konnte nicht verarbeitet werden.":message)
            .setPositiveButton("OK",null).show();
    }

    private void saveLocal(){try{JSONObject all=new JSONObject();for(String id:recordsByProduct.keySet())all.put(id,new JSONObject(recordsByProduct.get(id)));JSONObject times=new JSONObject(checkedByProduct);JSONObject prices=new JSONObject(livePricesByProduct);getPreferences(MODE_PRIVATE).edit().putString("all_stock_json",all.toString()).putString("all_checked_json",times.toString()).putString("live_prices_json",prices.toString()).putString("stock_snapshots_json",stockSnapshots.toString()).putString("stock_events_json",stockEvents.toString()).apply();}catch(Exception ignored){}}
    private void loadSavedRecords(){try{JSONObject all=new JSONObject(getPreferences(MODE_PRIVATE).getString("all_stock_json","{}"));JSONArray keys=all.names();if(keys!=null)for(int i=0;i<keys.length();i++){String productId=keys.getString(i);JSONObject productRows=all.getJSONObject(productId);Map<String,JSONObject> rows=new LinkedHashMap<>();JSONArray branchKeys=productRows.names();if(branchKeys!=null)for(int j=0;j<branchKeys.length();j++){String branchId=branchKeys.getString(j);rows.put(branchId,productRows.getJSONObject(branchId));}recordsByProduct.put(productId,rows);}JSONObject times=new JSONObject(getPreferences(MODE_PRIVATE).getString("all_checked_json","{}"));JSONArray tkeys=times.names();if(tkeys!=null)for(int i=0;i<tkeys.length();i++){String id=tkeys.getString(i);checkedByProduct.put(id,times.getString(id));}JSONObject prices=new JSONObject(getPreferences(MODE_PRIVATE).getString("live_prices_json","{}"));JSONArray pkeys=prices.names();if(pkeys!=null)for(int i=0;i<pkeys.length();i++){String id=pkeys.getString(i);livePricesByProduct.put(id,prices.getJSONObject(id));}stockSnapshots=new JSONArray(getPreferences(MODE_PRIVATE).getString("stock_snapshots_json","[]"));stockEvents=new JSONArray(getPreferences(MODE_PRIVATE).getString("stock_events_json","[]"));pruneHistory();}catch(Exception ignored){}}
    private String readAsset(String name)throws Exception{BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open(name),"UTF-8"));StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);reader.close();return text.toString();}
    private LinearLayout.LayoutParams params(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=top;return p;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
