package ch.pokemoncardfinder.local;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

/** One-product, one-branch proof that Migros can be queried directly from this phone. */
public class MainActivity extends Activity {
    private static final String PRODUCT_ID = "746658000000";
    private static final String PRODUCT_NAME = "Pokémon Booster Blister · assortiert";
    private static final String BRANCH_NAME = "Bern - Marktgasse";
    private static final String BRANCH_ADDRESS = "Marktgasse 46, 3011 Bern";
    private TextView status, result, checkedAt;
    private ProgressBar spinner;
    private Button refresh;
    private WebView webView;
    private LinearLayout root;
    private boolean waitingForPage;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        webView.addJavascriptInterface(new ResultBridge(), "FinderBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (waitingForPage) {
                    waitingForPage = false;
                    status.setText("Migros-Seite geladen. Öffne die Filialverfügbarkeit …");
                    view.postDelayed(() -> startMigrosCheck(), 800);
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(1, 1));
        loadSavedResult();
        refresh.setOnClickListener(v -> beginCheck());
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(20));
        root.setBackgroundColor(Color.rgb(248, 248, 252));
        TextView title = new TextView(this);
        title.setText("Pokémon Card Finder"); title.setTextSize(24); title.setTextColor(Color.rgb(35, 32, 48)); title.setTypeface(null, 1);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Lokaler Live-Test · 1 Produkt · 1 Migros-Filiale"); subtitle.setTextSize(14); subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, params(0, dp(10)));
        TextView product = new TextView(this);
        product.setText(PRODUCT_NAME); product.setTextSize(17); product.setTypeface(null, 1); product.setTextColor(Color.rgb(35, 32, 48));
        root.addView(product, params(0, dp(18)));
        TextView branch = new TextView(this);
        branch.setText("Migros " + BRANCH_NAME + "\n" + BRANCH_ADDRESS); branch.setTextSize(14); branch.setTextColor(Color.DKGRAY);
        root.addView(branch, params(0, dp(20)));
        refresh = new Button(this); refresh.setText("Migros jetzt abfragen");
        root.addView(refresh, params(0, dp(8)));
        LinearLayout progressLine = new LinearLayout(this); progressLine.setGravity(Gravity.CENTER_VERTICAL);
        spinner = new ProgressBar(this); spinner.setIndeterminate(true); spinner.setVisibility(View.GONE);
        progressLine.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
        status = new TextView(this); status.setText("Noch keine Live-Abfrage ausgeführt."); status.setTextSize(14); status.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, -2, 1); statusParams.leftMargin = dp(9); progressLine.addView(status, statusParams);
        root.addView(progressLine, params(0, dp(20)));
        result = new TextView(this); result.setText("Ergebnis erscheint hier."); result.setTextSize(20); result.setTypeface(null, 1); result.setTextColor(Color.rgb(96, 72, 220));
        root.addView(result, params(0, dp(8)));
        checkedAt = new TextView(this); checkedAt.setText("Bestand wird direkt auf diesem Handy abgefragt."); checkedAt.setTextSize(12); checkedAt.setTextColor(Color.GRAY);
        root.addView(checkedAt, params(0, dp(16)));
        TextView note = new TextView(this);
        note.setText("Dies ist zuerst nur ein Test für eine Filiale. Die Migros-Bestandsanzeige ist eine Momentaufnahme und kann ungefähr sein."); note.setTextSize(12); note.setTextColor(Color.GRAY);
        root.addView(note);
        setContentView(root);
    }

    private void beginCheck() {
        refresh.setEnabled(false); spinner.setVisibility(View.VISIBLE);
        status.setText("Verbinde direkt mit Migros …");
        String url = "https://www.migros.ch/de/product/" + PRODUCT_ID;
        waitingForPage = true;
        webView.loadUrl(url);
        getPreferences(MODE_PRIVATE).edit().putString("last_product", PRODUCT_NAME).apply();
    }

    private void startMigrosCheck() {
        String script = "(async()=>{const sleep=ms=>new Promise(r=>setTimeout(r,ms));const norm=s=>(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,' ').trim();"
          + "const send=o=>window.FinderBridge.result(JSON.stringify(o));"
          + "let button=null;for(let n=0;n<120;n++){button=[...document.querySelectorAll('button')].find(b=>norm(b.innerText).includes('verfugbarkeit in filialen prufen'));if(button)break;await sleep(250)}"
          + "if(!button){send({ok:false,error:'Migros-Verfügbarkeitsbutton nicht gefunden',title:document.title,body:(document.body?.innerText||'').slice(0,900)});return}"
          + "button.click();let modal=null,input=null;for(let n=0;n<80;n++){modal=document.querySelector('#bottomSmallModal');input=document.querySelector('#store-search-autocomplete-form-input');if(modal&&input&&modal.innerText.trim())break;await sleep(250)}"
          + "if(!input){send({ok:false,error:'Filialsuche öffnet sich nicht',title:document.title});return}"
          + "const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input," + JSONObject.quote(BRANCH_ADDRESS) + ");input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));"
          + "let body='';for(let n=0;n<80;n++){await sleep(250);body=modal.innerText||'';const t=norm(body);if(t.includes(norm(" + JSONObject.quote(BRANCH_NAME) + "))||t.includes(norm('Marktgasse 46')))break}"
          + "const lines=body.split(/\\n+/).map(x=>x.trim()).filter(Boolean);let i=lines.findIndex(x=>norm(x).includes(norm(" + JSONObject.quote(BRANCH_NAME) + "))||norm(x).includes(norm('Marktgasse 46')));"
          + "if(i<0){send({ok:false,error:'Filiale in Migros-Ergebnis nicht gefunden',dialog:body.slice(0,1200)});return}const raw=lines.slice(i,i+6).join(' | ');"
          + "let m=raw.match(/(?:nur\\s*)?ca\\.?\\s*(\\d+)\\s*auf lager/i)||raw.match(/(\\d+)\\s*auf lager/i);"
          + "if(m){send({ok:true,status:'estimated_in_stock',quantity:m[1],source:raw});return}if(/nicht auf lager/i.test(raw)){send({ok:true,status:'out_of_stock',source:raw});return}if(/auf lager|verfugbar/i.test(raw)){send({ok:true,status:'in_stock',source:raw});return}"
          + "send({ok:false,error:'Migros zeigt für diese Filiale keinen lesbaren Status',dialog:raw})})()";
        webView.evaluateJavascript(script, null);
        // WebView scripts may be slow; this only updates the user-facing status.
        status.setText("Suche die Filiale im Migros-Verfügbarkeitsfenster …");
        status.postDelayed(() -> {
            if (!refresh.isEnabled()) finishError("Keine Antwort innerhalb von 45 Sekunden. Prüfe Internetverbindung und Migros-Seite.");
        }, 45000);
    }

    private class ResultBridge {
        @JavascriptInterface public void result(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject data = new JSONObject(json);
                    if (!data.optBoolean("ok")) {
                        String detail = data.optString("error", "Abfrage fehlgeschlagen");
                        String page = data.optString("title", "");
                        finishError(detail + (page.isEmpty() ? "" : " · Seite: " + page));
                        return;
                    }
                    String state = data.optString("status");
                    String shown = state.equals("out_of_stock") ? "Nicht auf Lager" : state.equals("estimated_in_stock") ? "Ca. " + data.optString("quantity") + " auf Lager" : "Auf Lager (Menge unbekannt)";
                    String now = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date());
                    result.setText(shown); status.setText("Live-Abfrage erfolgreich."); checkedAt.setText("Abgefragt am " + now + "\nQuelle: Migros-Produktseite · " + BRANCH_NAME);
                    getPreferences(MODE_PRIVATE).edit().putString("last_result", shown).putString("last_checked", now).apply();
                    finishBusy();
                } catch (Exception ex) { finishError("Antwort konnte nicht ausgewertet werden."); }
            });
        }
    }

    private void finishError(String message) { status.setText(message); finishBusy(); }
    private void finishBusy() { spinner.setVisibility(View.GONE); refresh.setEnabled(true); }
    private void loadSavedResult() {
        String saved = getPreferences(MODE_PRIVATE).getString("last_result", null);
        if (saved != null) { result.setText(saved); checkedAt.setText("Letzter erfolgreicher Test: " + getPreferences(MODE_PRIVATE).getString("last_checked", "")); }
    }
    private LinearLayout.LayoutParams params(int width, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width == 0 ? -1 : width, -2); p.topMargin = top; return p;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
