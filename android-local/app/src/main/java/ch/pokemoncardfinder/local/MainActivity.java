package ch.pokemoncardfinder.local;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local-device Migros test: one product, all branches in the bundled roster. */
public class MainActivity extends Activity {
    private static final String PRODUCT_ID = "746658000000";
    private static final String PRODUCT_NAME = "Pokémon Booster Blister · assortiert";
    private TextView status, result, checkedAt;
    private ProgressBar spinner;
    private Button refresh;
    private WebView webView;
    private LinearLayout root;
    private JSONArray branches;
    private final Map<String, JSONObject> stockRecords = new LinkedHashMap<>();
    private boolean waitingForPage;
    private boolean queryRunning;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { branches = new JSONObject(readAsset("branches.json")).getJSONArray("branches"); }
        catch (Exception ex) { branches = new JSONArray(); }
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
                    status.setText("Migros-Seite geladen. Starte Filialabfrage …");
                    view.postDelayed(() -> startMigrosCheck(), 800);
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(1, 1));
        loadSavedRecords();
        refresh.setOnClickListener(v -> beginCheck());
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(Color.rgb(248, 248, 252));
        getWindow().setNavigationBarColor(Color.rgb(248, 248, 252));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(14));
        root.setBackgroundColor(Color.rgb(248, 248, 252));
        TextView title = new TextView(this);
        title.setText("Pokémon Card Finder"); title.setTextSize(23); title.setTextColor(Color.rgb(35, 32, 48)); title.setTypeface(null, 1);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Lokale Migros-Abfrage · Booster Blister · " + branches.length() + " Filialen"); subtitle.setTextSize(13); subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, params(dp(7)));
        refresh = new Button(this); refresh.setText("Alle Filialen jetzt abfragen");
        root.addView(refresh, params(dp(12)));
        LinearLayout progressLine = new LinearLayout(this); progressLine.setGravity(Gravity.CENTER_VERTICAL);
        spinner = new ProgressBar(this); spinner.setIndeterminate(true); spinner.setVisibility(View.GONE);
        progressLine.addView(spinner, new LinearLayout.LayoutParams(dp(22), dp(22)));
        status = new TextView(this); status.setText("Noch keine Abfrage auf diesem Handy."); status.setTextSize(13); status.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, -2, 1); statusParams.leftMargin = dp(8); progressLine.addView(status, statusParams);
        root.addView(progressLine, params(dp(10)));
        checkedAt = new TextView(this); checkedAt.setText("Die Anfrage geht direkt von diesem Handy an Migros."); checkedAt.setTextSize(11); checkedAt.setTextColor(Color.GRAY);
        root.addView(checkedAt, params(dp(5)));
        result = new TextView(this); result.setText("Filialergebnisse erscheinen hier."); result.setTextSize(13); result.setTextColor(Color.rgb(35, 32, 48));
        ScrollView resultsScroll = new ScrollView(this); resultsScroll.addView(result);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, 0, 1); resultParams.topMargin = dp(10); root.addView(resultsScroll, resultParams);
        TextView note = new TextView(this);
        note.setText("Testumfang: 1 Artikel in allen aktuell eingetragenen Fribourg- und Bern-Filialen. Migros-Bestände sind Momentaufnahmen; Ca.-Mengen sind Schätzungen."); note.setTextSize(10); note.setTextColor(Color.GRAY);
        root.addView(note, params(dp(8)));
        setContentView(root);
    }

    private void beginCheck() {
        if (branches.length() == 0) { status.setText("Filialliste konnte nicht geladen werden."); return; }
        queryRunning = true; getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refresh.setEnabled(false); spinner.setVisibility(View.VISIBLE); stockRecords.clear(); renderRecords();
        status.setText("Verbinde direkt mit Migros …");
        waitingForPage = true;
        webView.loadUrl("https://www.migros.ch/de/product/" + PRODUCT_ID);
    }

    private void startMigrosCheck() {
        String script = "(async()=>{const sleep=ms=>new Promise(r=>setTimeout(r,ms));const norm=s=>(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();"
          + "const branches=" + branches.toString() + ";const send=(method,o)=>window.FinderBridge[method](JSON.stringify(o));const seen=new Set();"
          + "const classify=raw=>{let m=raw.match(/(?:nur\\s*)?ca\\.?\\s*(\\d+)\\s*auf lager/i)||raw.match(/(\\d+)\\s*auf lager/i);if(m)return {status:'estimated_in_stock',quantity:Number(m[1]),source:raw};if(/nicht auf lager/i.test(raw))return {status:'out_of_stock',source:raw};if(/\\bauf lager\\b|verfugbar/i.test(norm(raw)))return {status:'in_stock',source:raw};return null};"
          + "const scan=body=>{const lines=body.split(/\\n+/).map(x=>x.trim()).filter(Boolean);for(const b of branches){if(seen.has(b.id))continue;const name=norm(b.name),addr=norm((b.address||'').split(',')[0]);let i=lines.findIndex(x=>norm(x)===name||norm(x).includes(name));if(i<0&&addr.length>5)i=lines.findIndex(x=>norm(x).startsWith(addr));if(i<0)continue;const raw=lines.slice(i,i+6).join(' | ');const found=classify(raw);if(found){seen.add(b.id);send('record',{branch_id:b.id,name:b.name,address:b.address,...found})}}};"
          + "let button=null;for(let n=0;n<120;n++){button=[...document.querySelectorAll('button')].find(b=>norm(b.innerText).includes('verfugbarkeit in filialen prufen'));if(button)break;await sleep(250)}"
          + "if(!button){send('failed',{error:'Migros-Verfügbarkeitsbutton fehlt',title:document.title,body:(document.body?.innerText||'').slice(0,800)});return}button.click();"
          + "let modal=null,input=null;for(let n=0;n<80;n++){modal=document.querySelector('#bottomSmallModal');input=document.querySelector('#store-search-autocomplete-form-input');if(modal&&input){break}await sleep(250)}"
          + "if(!input){send('failed',{error:'Filialsuche öffnet sich nicht',title:document.title});return}scan(modal.innerText||'');"
          + "const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;let searched=0;"
          + "for(const b of branches){if(seen.has(b.id))continue;searched++;send('progress',{message:'Prüfe Filiale '+searched+' / '+branches.length+': '+b.name+' …'});const before=modal.innerText||'';setter.call(input,b.address);input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));"
          + "let body=before;for(let n=0;n<60;n++){await sleep(250);body=modal.innerText||'';if(body!==before&&(norm(body).includes(norm(b.name))||norm(body).includes(norm((b.address||'').split(',')[0])))){await sleep(350);body=modal.innerText||body;break}}scan(body);"
          + "if(!seen.has(b.id)){seen.add(b.id);send('record',{branch_id:b.id,name:b.name,address:b.address,status:'unknown',source:'Migros hat in der Filialsuche keinen lesbaren Status geliefert'})}}"
          + "send('complete',{checked:branches.length,read:branches.length,successful:[...document.querySelectorAll('body')].length})})()";
        webView.evaluateJavascript(script, null);
    }

    private class ResultBridge {
        @JavascriptInterface public void progress(String json) {
            runOnUiThread(() -> { try { status.setText(new JSONObject(json).optString("message", "Prüfe Migros-Filialen …")); } catch (Exception ignored) {} });
        }
        @JavascriptInterface public void record(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject row = new JSONObject(json); stockRecords.put(row.getString("branch_id"), row);
                    getPreferences(MODE_PRIVATE).edit().putString("last_stock_json", new JSONObject(stockRecords).toString()).apply();
                    renderRecords();
                } catch (Exception ignored) {}
            });
        }
        @JavascriptInterface public void failed(String json) {
            runOnUiThread(() -> { try { finishError(new JSONObject(json).optString("error", "Migros-Abfrage fehlgeschlagen")); } catch (Exception ex) { finishError("Migros-Abfrage fehlgeschlagen."); } });
        }
        @JavascriptInterface public void complete(String json) {
            runOnUiThread(() -> {
                int checked = stockRecords.size(), unknown = 0, inStock = 0, out = 0;
                for (JSONObject row : stockRecords.values()) { String s=row.optString("status"); if(s.equals("unknown")) unknown++; else if(s.equals("out_of_stock")) out++; else inStock++; }
                String time=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date());
                status.setText("Abfrage beendet: " + checked + "/" + branches.length() + " Filialen · " + inStock + " verfügbar · " + out + " nicht auf Lager · " + unknown + " ohne lesbaren Status");
                checkedAt.setText("Migros-Abfrage " + time + " · lokal auf diesem Gerät gespeichert");
                getPreferences(MODE_PRIVATE).edit().putString("last_checked",time).apply(); finishBusy();
            });
        }
    }

    private void renderRecords() {
        if (result == null) return;
        StringBuilder text = new StringBuilder();
        for (int i=0;i<branches.length();i++) {
            JSONObject branch=branches.optJSONObject(i); if(branch==null) continue;
            JSONObject row=stockRecords.get(branch.optString("id"));
            text.append(row==null ? "◌  Nicht geprüft" : label(row)).append("\n");
            text.append(branch.optString("name")).append(" · ").append(branch.optString("address")).append("\n\n");
        }
        result.setText(text.toString());
    }
    private String label(JSONObject row) {
        String state=row.optString("status");
        if(state.equals("out_of_stock")) return "○  Nicht auf Lager";
        if(state.equals("estimated_in_stock")) return "●  Ca. " + row.optInt("quantity") + " auf Lager";
        if(state.equals("in_stock")) return "●  Auf Lager (Menge unbekannt)";
        return "?  Nicht eindeutig geprüft";
    }
    private void finishError(String message) { status.setText(message); finishBusy(); }
    private void finishBusy() { queryRunning=false; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); spinner.setVisibility(View.GONE); refresh.setEnabled(true); }
    private void loadSavedRecords() {
        try {
            JSONObject saved=new JSONObject(getPreferences(MODE_PRIVATE).getString("last_stock_json","{}"));
            for(int i=0;i<saved.names().length();i++){String id=saved.names().getString(i);stockRecords.put(id,saved.getJSONObject(id));}
            renderRecords(); String time=getPreferences(MODE_PRIVATE).getString("last_checked",null);
            if(time!=null) checkedAt.setText("Letzte lokale Abfrage: " + time);
        } catch(Exception ignored) { renderRecords(); }
    }
    private String readAsset(String name) throws Exception {
        BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open(name),"UTF-8"));
        StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);reader.close();return text.toString();
    }
    private LinearLayout.LayoutParams params(int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=top;return p; }
    private int dp(int v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
