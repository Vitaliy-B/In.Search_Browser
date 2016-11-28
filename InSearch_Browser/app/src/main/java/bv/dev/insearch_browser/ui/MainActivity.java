package bv.dev.insearch_browser.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import bv.dev.insearch_browser.R;
import bv.dev.insearch_browser.net.WebUtils;
import bv.dev.insearch_browser.net.WebpageLoader;

public class MainActivity extends AppCompatActivity {
    public static final String log_tag = "bv_log";

    static volatile boolean reloadPrefs = false; // package access, changed from settings activity

    private static final int CODE_READ_EXT_STORAGE_PM = 1;

    private EditText et_url = null;
    private EditText et_search = null;
    private WebView web_view = null;

    private SharedPreferences sprefs = null;
    private NetworkReceiver net_recv = null; // Broadcast Receiver
    private WebViewController webview_ctrlr = null;
    private ConnectionChecker conn_chkr = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* progress - does not work yet
        // must be called before adding content and calling super
        //getWindow().requestFeature(Window.FEATURE_PROGRESS);
        //requestWindowFeature(Window.FEATURE_PROGRESS);
        */
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /* progress - does not work yet
        setProgressBarVisibility(true);
        setProgressBarIndeterminate(true);
        setProgressBarIndeterminateVisibility(true);
        setProgress(60);
        */

        et_url = (EditText) findViewById(R.id.et_url);
        et_search = (EditText) findViewById(R.id.et_search);
        web_view = (WebView) findViewById(R.id.web_view);

        // Preferences and Intent
        // -------------
        conn_chkr = new ConnectionChecker(ConnectionChecker.net_type_any); // default, changed after prefs loaded
        loadPrefs();
        // webpage url
        if(getIntent() != null && getIntent().getData() != null) {
            et_url.setText(getIntent().getData().toString());
        } else {
            String pref_page_key = // open home or last page
                    sprefs.getBoolean(getString(R.string.net_sett_open_last_page_key), true) ?
                            getString(R.string.net_sett_last_page_key)
                            : getString(R.string.net_sett_home_page_key);
            et_url.setText(sprefs.getString(pref_page_key, getString(R.string.def_home_page)));
        }
        // init WebViewController
        webview_ctrlr = new WebViewController(this, web_view, et_url, conn_chkr);

        // Register BroadcastReceiver for any connectivity changes
        net_recv = new NetworkReceiver(); // register in onStart

        // check SD-card read permission to load NLP data
        // on lower api (before 23) is not needed and not available
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // can use not compat context for api >= 23..
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE /*api 16 */)
                    != PackageManager.PERMISSION_GRANTED) {
                /*
                if(shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // should show dialog
                }*/
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        CODE_READ_EXT_STORAGE_PM);
            }
        }
    }

    private void loadPrefs() {
        // if prefs was not set/changed, set to default
        PreferenceManager.setDefaultValues(this, R.xml.net_prefs, false);
        //getPreferences(MODE_PRIVATE); // not this for PreferenceActivity!
        sprefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(conn_chkr != null) { // do not create new, it's instance used in WVCtrlr
            conn_chkr.setPref_net_type(sprefs.getString(getString(R.string.net_sett_net_type_key),
                        ConnectionChecker.net_type_any));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(reloadPrefs) { // to allow load page if was not loaded due to settings
            Log.d(log_tag, "MainActivity.onStart() : Reloading settings");
            reloadPrefs = false;
            loadPrefs();
        }
        // there, because unregistered in onStop
        // it will be called just after registering
        registerReceiver(net_recv, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        webview_ctrlr.reinitSearchOnPage(et_url.getText().toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // save cur url
        String cur_url = et_url.getText().toString();
        String url_to_save = WebUtils.isValidUrl(cur_url) || WebUtils.isSpecialUrl(cur_url) ?
                cur_url : WebUtils.url_about_blank;
        sprefs.edit() // save current page
                .putString(getString(R.string.net_sett_last_page_key), url_to_save)
                .apply();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(net_recv != null) {
            // do not need to unregister on Pause, but if app is hidden, it should not receive nothing
            unregisterReceiver(net_recv);
        }
        cancelAll();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CODE_READ_EXT_STORAGE_PM) {
            // should be the length == 1 for this request (or empty)
            if(grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                // should show dialog..
                Toast.makeText(this, "READ_EXTERNAL_STORAGE permission not granted", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch(item.getItemId()) {
            case R.id.action_network_settings:
                startActivity(new Intent(this, NetworkSettingsActivity.class));
                return true;
            /* other.. */
        }
        /* single menu
        if(item.getItemId() == R.id.action_network_settings) {
            startActivity(new Intent(this, NetworkSettingsActivity.class));
            return true;
        }
        */
        return super.onOptionsItemSelected(item);
    }

    public void btnNavBackClick(View v) {
        Toast.makeText(this, "loading page..", Toast.LENGTH_SHORT).show();
        webview_ctrlr.goBack();
    }

    public void btnLoadClick(View v) {
        webview_ctrlr.loadPage(et_url.getText().toString());
    }

    public void btnSearchClick(View v) {
        webview_ctrlr.search(et_search.getText().toString());
    }

    private void cancelAll() {
        webview_ctrlr.cancelSearch();
        webview_ctrlr.cancelLoading();
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // connectivity changes, lost of connection or new conn available
            //Toast.makeText(context, "Connectivity changes", Toast.LENGTH_SHORT).show();
            Log.i(log_tag, "NetworkReceiver: Connectivity changes");
            // seems like it could be called multiple times in case of conn changes
            webview_ctrlr.loadPageIfNotLoaded(et_url.getText().toString());
        }
    }

    private class ConnectionChecker implements WebpageLoader.IConnectionChecker {
        public static final String net_type_wifi = "1";
        public static final String net_type_any = "0";
        private String pref_net_type = net_type_any;

        public ConnectionChecker(String pref_net_type) {
            this.pref_net_type = pref_net_type;
        }

        public void setPref_net_type(String pref_net_type) {
            this.pref_net_type = pref_net_type;
        }

        @Override
        public boolean connectionGranted() {
            ConnectivityManager conn_mng = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo actv_net = conn_mng.getActiveNetworkInfo();
            if(actv_net != null && actv_net.isConnected()) {
                switch (pref_net_type) {
                    case net_type_wifi:
                        return actv_net.getType() == ConnectivityManager.TYPE_WIFI;
                    case net_type_any:
                        return true;
                    default:
                        throw new RuntimeException("Unknown preference for network type");
                }
            }
            return false;
        }
    }
}
