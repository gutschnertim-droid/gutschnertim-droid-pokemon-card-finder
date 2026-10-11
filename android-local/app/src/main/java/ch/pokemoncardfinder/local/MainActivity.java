package ch.pokemoncardfinder.local;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private TextView status, checkedAt, productTitle, productPrice, stockSummary;
    private ProgressBar loadingSpinner;
    private Button refresh;
    private Spinner productPicker;
    private Spinner stockFilterPicker;
    private EditText branchSearch;
    private ImageView productImage;
    private LinearLayout resultList;
    private WebView webView;
    private LinearLayout root;
    private JSONArray branches, products;
    private int selectedProductIndex = 0, activeProductIndex = 0;
    private final Map<String, Map<String, JSONObject>> recordsByProduct = new LinkedHashMap<>();
    private final Map<String, String> checkedByProduct = new LinkedHashMap<>();
    private final Map<String, JSONObject> livePricesByProduct = new LinkedHashMap<>();
    private boolean waitingForPage;
    private boolean queryRunning;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            branches = new JSONObject(readAsset("branches.json")).getJSONArray("branches");
            products = new JSONObject(readAsset(CATALOG_ASSET)).getJSONArray("products");
        } catch (Exception ex) { branches = new JSONArray(); products = new JSONArray(); }
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
                    status.setText("Migros lädt Artikel " + (activeProductIndex+1) + "/" + products.length() + " …");
                    view.postDelayed(() -> startMigrosCheck(), 700);
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(1, 1));
        loadSavedRecords();
        refresh.setOnClickListener(v -> beginCheck());
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

    private void buildScreen() {
        getWindow().setStatusBarColor(Color.rgb(248,248,252)); getWindow().setNavigationBarColor(Color.rgb(248,248,252));
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(18),dp(16),dp(12)); root.setBackgroundColor(Color.rgb(248,248,252));
        TextView title=new TextView(this); title.setText("Pokémon Card Finder"); title.setTextSize(22); title.setTextColor(Color.rgb(35,32,48)); title.setTypeface(null,1); root.addView(title);
        TextView subtitle=new TextView(this); subtitle.setText(products.length()+" Pokémon-TCG-Artikel · "+branches.length()+" Migros-Filialen · Anfrage direkt vom Handy"); subtitle.setTextSize(12); subtitle.setTextColor(Color.DKGRAY); root.addView(subtitle,params(dp(4)));
        productPicker=new Spinner(this); root.addView(productPicker,params(dp(8)));
        LinearLayout card=new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setBackgroundColor(Color.WHITE); card.setPadding(dp(10),dp(8),dp(10),dp(8));
        productImage=new ImageView(this); productImage.setScaleType(ImageView.ScaleType.FIT_CENTER); card.addView(productImage,new LinearLayout.LayoutParams(dp(88),dp(78)));
        LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(0,-2,1); detailParams.leftMargin=dp(10); card.addView(details,detailParams);
        productTitle=new TextView(this); productTitle.setTextSize(14); productTitle.setTextColor(Color.rgb(35,32,48)); productTitle.setTypeface(null,1); details.addView(productTitle);
        productPrice=new TextView(this); productPrice.setTextSize(14); productPrice.setTextColor(Color.rgb(96,72,220)); details.addView(productPrice,params(dp(3)));
        root.addView(card,params(dp(8)));
        refresh=new Button(this); refresh.setText("Alle Artikel in allen Filialen abfragen"); root.addView(refresh,params(dp(8)));
        LinearLayout progressLine=new LinearLayout(this); progressLine.setGravity(Gravity.CENTER_VERTICAL);
        loadingSpinner=new ProgressBar(this); loadingSpinner.setIndeterminate(true); loadingSpinner.setVisibility(View.GONE); progressLine.addView(loadingSpinner,new LinearLayout.LayoutParams(dp(22),dp(22)));
        status=new TextView(this); status.setText("Noch keine vollständige Live-Abfrage."); status.setTextSize(12); status.setTextColor(Color.DKGRAY); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(8);progressLine.addView(status,sp);root.addView(progressLine,params(dp(8)));
        checkedAt=new TextView(this); checkedAt.setText("Die Migros-Abfragen und gespeicherten Ergebnisse bleiben auf diesem Gerät."); checkedAt.setTextSize(10); checkedAt.setTextColor(Color.GRAY); root.addView(checkedAt,params(dp(3)));
        stockSummary=new TextView(this); stockSummary.setTextSize(11); stockSummary.setTextColor(Color.rgb(60,56,72)); root.addView(stockSummary,params(dp(8)));
        LinearLayout filters=new LinearLayout(this); filters.setGravity(Gravity.CENTER_VERTICAL);
        branchSearch=new EditText(this); branchSearch.setSingleLine(true); branchSearch.setTextSize(12); branchSearch.setHint("Filiale oder Ort suchen");
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(0,dp(48),1); filters.addView(branchSearch,searchParams);
        stockFilterPicker=new Spinner(this); String[] filterOptions={"Alle", "Höchster Bestand", "Verfügbar", "Nicht auf Lager", "Nicht eindeutig", "Noch ungeprüft"};
        stockFilterPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,filterOptions));
        LinearLayout.LayoutParams filterParams=new LinearLayout.LayoutParams(dp(150),-2); filterParams.leftMargin=dp(6); filters.addView(stockFilterPicker,filterParams); root.addView(filters,params(dp(4)));
        resultList=new LinearLayout(this); resultList.setOrientation(LinearLayout.VERTICAL); ScrollView scroll=new ScrollView(this);scroll.addView(resultList);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.topMargin=dp(8);root.addView(scroll,rp);
        TextView note=new TextView(this);note.setText("Katalog: 7 im Datensatz erfasste Artikel für PLZ 1700; das ist keine schweizweite Sortimentserhebung. Migros-Bestände sind Momentaufnahmen, Ca.-Mengen sind Schätzungen.");note.setTextSize(9);note.setTextColor(Color.GRAY);root.addView(note,params(dp(6)));
        setContentView(root);
    }

    private void beginCheck() {
        if(products.length()==0||branches.length()==0){status.setText("Produkt- oder Filialliste fehlt.");return;}
        queryRunning=true;activeProductIndex=0;selectedProductIndex=0;productPicker.setSelection(0);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderProduct();
        refresh.setEnabled(false);loadingSpinner.setVisibility(View.VISIBLE);loadProductPage();
    }
    private void loadProductPage(){
        JSONObject p=products.optJSONObject(activeProductIndex); if(p==null){finishError("Produktkatalog ist ungültig.");return;}
        String id=p.optString("id");status.setText("Artikel "+(activeProductIndex+1)+"/"+products.length()+": "+p.optString("name"));waitingForPage=true;
        webView.loadUrl("https://www.migros.ch/de/product/"+id);
    }

    private void startMigrosCheck() {
        String productId=products.optJSONObject(activeProductIndex).optString("id");
        String progressProductLabel=(activeProductIndex+1)+"/"+products.length();
        String script="(async()=>{const sleep=ms=>new Promise(r=>setTimeout(r,ms));const norm=s=>(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();"
          +"const branches="+branches.toString()+";const productId="+JSONObject.quote(productId)+";const send=(m,o)=>window.FinderBridge[m](JSON.stringify(o));const seen=new Set();"
          +"const amount=v=>{const n=Number(String(v||'').replace(/[^0-9,.]/g,'').replace(',','.'));return Number.isFinite(n)&&n>0?n:null};"
          +"let livePrice=null,originalPrice=null;const metas=['meta[property=\"product:price:amount\"]','meta[itemprop=\"price\"]','[itemprop=\"price\"]'];for(const s of metas){const e=document.querySelector(s);const n=amount(e?.content||e?.getAttribute('content')||e?.innerText);if(n){livePrice=n;break}}"
          +"if(!livePrice){for(const script of document.querySelectorAll('script[type=\"application/ld+json\"]')){try{const walk=o=>{if(!o)return;if(Array.isArray(o)){o.forEach(walk);return}if(typeof o==='object'){if(o.price!=null&&(/offer/i.test(String(o['@type']||''))||o.priceCurrency)){const n=amount(o.price);if(n&&!livePrice)livePrice=n}if(o.offers)walk(o.offers);for(const [k,v] of Object.entries(o))if(k!=='offers')walk(v)}};walk(JSON.parse(script.textContent))}catch(e){}}}"
          +"if(!livePrice){const e=document.querySelector('[data-testid*=\"price\"], [class*=\"price\"] [itemprop=\"price\"], [itemprop=\"price\"]');if(e)livePrice=amount(e.content||e.getAttribute('content')||e.innerText)}"
          +"if(livePrice){const old=document.querySelector('del, s, [class*=\"old-price\"], [class*=\"original-price\"], [data-testid*=\"old-price\"]');if(old)originalPrice=amount(old.innerText);send('livePrice',{product_id:productId,price:livePrice,original_price:originalPrice,source:'Migros-Produktseite'})}"
          +"const classify=raw=>{let m=raw.match(/(?:nur\\s*)?ca\\.?\\s*(\\d+)\\s*auf lager/i)||raw.match(/(\\d+)\\s*auf lager/i);if(m)return {status:'estimated_in_stock',quantity:Number(m[1]),source:raw};if(/nicht auf lager/i.test(norm(raw)))return {status:'out_of_stock',source:raw};if(/\\bauf lager\\b|verfugbar/i.test(norm(raw)))return {status:'in_stock',source:raw};return null};"
          +"const scan=body=>{const lines=body.split(/\\n+/).map(x=>x.trim()).filter(Boolean);for(const b of branches){if(seen.has(b.id))continue;const name=norm(b.name),addr=norm((b.address||'').split(',')[0]);let i=lines.findIndex(x=>norm(x)===name||norm(x).includes(name));if(i<0&&addr.length>5)i=lines.findIndex(x=>norm(x).startsWith(addr));if(i<0)continue;const raw=lines.slice(i,i+6).join(' | ');const found=classify(raw);if(found){seen.add(b.id);send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,...found})}}};"
          +"let button=null;for(let n=0;n<120;n++){button=[...document.querySelectorAll('button')].find(b=>norm(b.innerText).includes('verfugbarkeit in filialen prufen'));if(button)break;await sleep(250)}"
          +"if(!button){send('failed',{product_id:productId,error:'Migros-Verfügbarkeitsbutton fehlt',title:document.title,body:(document.body?.innerText||'').slice(0,700)});return}button.click();"
          +"let modal=null,input=null;for(let n=0;n<80;n++){modal=document.querySelector('#bottomSmallModal');input=document.querySelector('#store-search-autocomplete-form-input');if(modal&&input)break;await sleep(250)}if(!input){send('failed',{product_id:productId,error:'Filialsuche öffnet sich nicht',title:document.title});return}scan(modal.innerText||'');"
          +"const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;let searched=0;for(const b of branches){if(seen.has(b.id))continue;searched++;send('progress',{message:'Artikel "+progressProductLabel+" · Filiale '+searched+'/'+branches.length+': '+b.name+' …'});const before=modal.innerText||'';setter.call(input,b.address);input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));let body=before;"
          +"for(let n=0;n<60;n++){await sleep(250);body=modal.innerText||'';if(body!==before&&(norm(body).includes(norm(b.name))||norm(body).includes(norm((b.address||'').split(',')[0])))){await sleep(350);body=modal.innerText||body;break}}scan(body);if(!seen.has(b.id)){seen.add(b.id);send('record',{product_id:productId,branch_id:b.id,name:b.name,address:b.address,status:'unknown',source:'Migros hat keinen lesbaren Status geliefert'})}}"
          +"send('complete',{product_id:productId,checked:branches.length})})()";
        webView.evaluateJavascript(script,null);
    }

    private class ResultBridge {
        @JavascriptInterface public void progress(String json){runOnUiThread(()->{try{status.setText(new JSONObject(json).optString("message","Prüfe Migros-Filialen …"));}catch(Exception ignored){}});}
        @JavascriptInterface public void record(String json){runOnUiThread(()->{try{JSONObject row=new JSONObject(json);String productId=row.getString("product_id"),branchId=row.getString("branch_id");Map<String,JSONObject> productRows=recordsByProduct.get(productId);if(productRows==null){productRows=new LinkedHashMap<>();recordsByProduct.put(productId,productRows);}JSONObject old=productRows.get(branchId);String oldStatus=old==null?"":old.optString("status");int oldQty=old==null?-1:old.optInt("quantity",-1);if("unknown".equals(oldStatus)){oldStatus=old.optString("last_known_status",oldStatus);oldQty=old.optInt("last_known_quantity",oldQty);}String newStatus=row.optString("status");int newQty=row.optInt("quantity",-1);boolean oldAvailable=oldStatus.equals("in_stock")||oldStatus.equals("estimated_in_stock");boolean newAvailable=newStatus.equals("in_stock")||newStatus.equals("estimated_in_stock");String signal="";if(old!=null&&!oldAvailable&&newAvailable)signal="restock";else if(oldStatus.equals("estimated_in_stock")&&newStatus.equals("estimated_in_stock")&&newQty>oldQty)signal="up";else if(oldStatus.equals("estimated_in_stock")&&newStatus.equals("estimated_in_stock")&&newQty<oldQty)signal="down";if("unknown".equals(newStatus)&&old!=null){row.put("last_known_status",oldStatus);if(oldQty>=0)row.put("last_known_quantity",oldQty);}row.put("stock_signal",signal);row.put("checked_at",now());productRows.put(branchId,row);saveLocal();if(productId.equals(currentProductId()))renderRecords();}catch(Exception ignored){}});}
        @JavascriptInterface public void livePrice(String json){runOnUiThread(()->{try{JSONObject incoming=new JSONObject(json);String id=incoming.getString("product_id");double current=incoming.getDouble("price");JSONObject old=livePricesByProduct.get(id);double previous=old==null?catalogPrice(id):old.optDouble("price",catalogPrice(id));JSONObject saved=new JSONObject();saved.put("price",current);saved.put("checked_at",now());saved.put("source",incoming.optString("source"));if(Math.abs(current-previous)>=0.01){saved.put("previous_price",previous);saved.put("change",current<previous?"down":"up");}else saved.put("change","none");double original=incoming.optDouble("original_price",0);if(original>current){saved.put("promotion",true);saved.put("original_price",original);}livePricesByProduct.put(id,saved);saveLocal();if(id.equals(currentProductId()))renderProduct();}catch(Exception ignored){}});}
        @JavascriptInterface public void failed(String json){runOnUiThread(()->{try{JSONObject d=new JSONObject(json);finishError("Abfrage gestoppt bei "+productName(d.optString("product_id"))+": "+d.optString("error","Migros-Fehler"));}catch(Exception ex){finishError("Migros-Abfrage fehlgeschlagen.");}});}
        @JavascriptInterface public void complete(String json){runOnUiThread(()->{String id=products.optJSONObject(activeProductIndex).optString("id");checkedByProduct.put(id,now());saveLocal();activeProductIndex++;if(activeProductIndex<products.length()){loadProductPage();}else finishAll();});}
    }

    private void finishAll(){int total=0,unknown=0;for(Map<String,JSONObject> rows:recordsByProduct.values()){total+=rows.size();for(JSONObject row:rows.values())if(row.optString("status").equals("unknown"))unknown++;}status.setText("Abfrage fertig · "+total+" von "+(products.length()*branches.length())+" Artikel-Filial-Datensätzen · "+unknown+" ohne lesbaren Status");checkedAt.setText("Zuletzt geprüft: "+now()+" · Ergebnisse lokal gespeichert");finishBusy();renderProduct();}
    private void finishError(String message){status.setText(message+". Bisherige Ergebnisse bleiben lokal gespeichert.");finishBusy();}
    private void finishBusy(){queryRunning=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);loadingSpinner.setVisibility(View.GONE);refresh.setEnabled(true);}
    private String currentProductId(){JSONObject p=products.optJSONObject(selectedProductIndex);return p==null?"":p.optString("id");}
    private String productName(String id){for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p.optString("id").equals(id))return p.optString("name");}return id;}
    private String now(){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date());}

    private void renderProduct(){if(products==null||products.length()==0||productTitle==null)return;JSONObject p=products.optJSONObject(selectedProductIndex);if(p==null)return;String id=p.optString("id");productTitle.setText(p.optString("name"));JSONObject live=livePricesByProduct.get(id);double shown=live==null?p.optDouble("price_chf",0):live.optDouble("price",p.optDouble("price_chf",0));String priceText=String.format(java.util.Locale.GERMANY,"CHF %.2f",shown);productPrice.setTextColor(Color.rgb(96,72,220));if(live==null)priceText+=" · Katalogpreis";else{String change=live.optString("change");if(change.equals("down")){priceText+="  ↓ Preis gesunken";productPrice.setTextColor(Color.rgb(36,148,72));}else if(change.equals("up")){priceText+="  ↑ Preis gestiegen";productPrice.setTextColor(Color.rgb(175,105,0));}else priceText+=" · Live geprüft";if(live.optBoolean("promotion"))priceText+=" · Aktion (vorher "+String.format(java.util.Locale.GERMANY,"CHF %.2f",live.optDouble("original_price"))+")";else if(change.equals("down"))priceText+=" · mögliche Rabattaktion";}productPrice.setText(priceText);loadImage(p.optString("image_url"));renderRecords();String checked=checkedByProduct.get(id);if(checked!=null)checkedAt.setText("Dieser Artikel zuletzt geprüft: "+checked+" · lokal gespeichert");}
    private void renderRecords(){
        if(resultList==null)return;
        Map<String,JSONObject> rows=recordsByProduct.get(currentProductId());
        String query=branchSearch==null?"":branchSearch.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        String filter=stockFilterPicker==null||stockFilterPicker.getSelectedItem()==null?"Alle":stockFilterPicker.getSelectedItem().toString();
        int available=0,outOfStock=0,unclear=0,unchecked=0; List<JSONObject> visible=new ArrayList<>();
        for(int i=0;i<branches.length();i++){
            JSONObject b=branches.optJSONObject(i); if(b==null)continue;
            JSONObject row=rows==null?null:rows.get(b.optString("id")); String state=row==null?"unchecked":row.optString("status","unknown");
            if(state.equals("in_stock")||state.equals("estimated_in_stock"))available++;else if(state.equals("out_of_stock"))outOfStock++;else if(state.equals("unknown"))unclear++;else unchecked++;
            String searchable=(b.optString("name")+" "+b.optString("address")).toLowerCase(java.util.Locale.ROOT);
            if(!searchable.contains(query)||!matchesFilter(filter,state))continue;
            visible.add(b);
        }
        if(stockSummary!=null)stockSummary.setText("Verfügbar: "+available+" · Nicht auf Lager: "+outOfStock+" · Unklar: "+unclear+" · Offen: "+unchecked);
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
        if(filter.equals("Noch ungeprüft"))return state.equals("unchecked");
        return true;
    }
    private String label(JSONObject row){if(row==null)return "Noch nicht geprüft";String s=row.optString("status");if(s.equals("out_of_stock"))return "Nicht auf Lager";if(s.equals("estimated_in_stock"))return "Ca. "+row.optInt("quantity")+" auf Lager";if(s.equals("in_stock"))return "Auf Lager (Menge unbekannt)";return "Nicht eindeutig geprüft";}
    private int statusColor(String state){if(state.equals("in_stock")||state.equals("estimated_in_stock"))return Color.rgb(36,148,72);if(state.equals("out_of_stock"))return Color.rgb(130,132,140);if(state.equals("unknown"))return Color.rgb(218,143,25);return Color.rgb(185,188,195);}
    private int stockQuantity(Map<String,JSONObject> rows,JSONObject branch){JSONObject row=rows==null?null:rows.get(branch.optString("id"));return row!=null&&row.optString("status").equals("estimated_in_stock")?row.optInt("quantity",0):0;}
    private double catalogPrice(String id){for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(p.optString("id").equals(id))return p.optDouble("price_chf",0);}return 0;}
    private void loadImage(String imageUrl){productImage.setImageDrawable(null);if(imageUrl==null||imageUrl.isEmpty())return;new Thread(()->{try{Bitmap bitmap=BitmapFactory.decodeStream(new URL(imageUrl).openStream());runOnUiThread(()->productImage.setImageBitmap(bitmap));}catch(Exception ignored){}}).start();}
    private void saveLocal(){try{JSONObject all=new JSONObject();for(String id:recordsByProduct.keySet())all.put(id,new JSONObject(recordsByProduct.get(id)));JSONObject times=new JSONObject(checkedByProduct);JSONObject prices=new JSONObject(livePricesByProduct);getPreferences(MODE_PRIVATE).edit().putString("all_stock_json",all.toString()).putString("all_checked_json",times.toString()).putString("live_prices_json",prices.toString()).apply();}catch(Exception ignored){}}
    private void loadSavedRecords(){try{JSONObject all=new JSONObject(getPreferences(MODE_PRIVATE).getString("all_stock_json","{}"));JSONArray keys=all.names();if(keys!=null)for(int i=0;i<keys.length();i++){String productId=keys.getString(i);JSONObject productRows=all.getJSONObject(productId);Map<String,JSONObject> rows=new LinkedHashMap<>();JSONArray branchKeys=productRows.names();if(branchKeys!=null)for(int j=0;j<branchKeys.length();j++){String branchId=branchKeys.getString(j);rows.put(branchId,productRows.getJSONObject(branchId));}recordsByProduct.put(productId,rows);}JSONObject times=new JSONObject(getPreferences(MODE_PRIVATE).getString("all_checked_json","{}"));JSONArray tkeys=times.names();if(tkeys!=null)for(int i=0;i<tkeys.length();i++){String id=tkeys.getString(i);checkedByProduct.put(id,times.getString(id));}JSONObject prices=new JSONObject(getPreferences(MODE_PRIVATE).getString("live_prices_json","{}"));JSONArray pkeys=prices.names();if(pkeys!=null)for(int i=0;i<pkeys.length();i++){String id=pkeys.getString(i);livePricesByProduct.put(id,prices.getJSONObject(id));}}catch(Exception ignored){}}
    private String readAsset(String name)throws Exception{BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open(name),"UTF-8"));StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);reader.close();return text.toString();}
    private LinearLayout.LayoutParams params(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=top;return p;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
