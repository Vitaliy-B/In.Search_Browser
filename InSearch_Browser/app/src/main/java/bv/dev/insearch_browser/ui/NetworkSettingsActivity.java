package bv.dev.insearch_browser.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import java.util.ArrayList;

import bv.dev.insearch_browser.R;


public class NetworkSettingsActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    //public static final String log_tag = MainActivity.log_tag; // unused

    private static final boolean always_old_prefs = false;

    private PreferenceFragment pref_fragm_main = null;
    private SharedPreferences sh_pref = null;
    private boolean useOldPrefs = always_old_prefs;

    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    // use variable instead of direct use
    private static boolean useOldPrefs(Context cntx) {
        return (always_old_prefs && !isXLargeTablet(cntx))
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        useOldPrefs = useOldPrefs(this);
        sh_pref = PreferenceManager.getDefaultSharedPreferences(this);
        if(useOldPrefs) {
            setupOldPrefs();
        } else {
            setupFragmentPrefs();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // require Fragment's PreferenceScreen to be initialized,
        // (Fragment's onCreate won't be called until Activity's onCreate will return)
        // so is should be called from another place then fragment initialization
        initSummaries();
    }

    @Override @SuppressWarnings("deprecation")
    protected void onResume() {
        super.onResume();
        sh_pref.registerOnSharedPreferenceChangeListener(this);
    }

    @Override @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        // to save system resources.
        sh_pref.unregisterOnSharedPreferenceChangeListener(this);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private Preference getPref(PreferenceFragment pf, String key) {
        return pf.findPreference(key);
    }

    @Override @SuppressWarnings("deprecation")
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        MainActivity.reloadPrefs = true;
        // or use Preference.OnPreferenceChangeListener instead of this
        if(useOldPrefs) {
            setSummary(findPreference(key), sharedPreferences);
        } else if(pref_fragm_main != null) {
            setSummary(getPref(pref_fragm_main, key), sharedPreferences);
        }
    }

    private void setSummary(Preference pref, SharedPreferences sharedPreferences) {
        if(pref == null) {
            return;
        }
        if(pref instanceof ListPreference) {
            ListPreference list_pref = (ListPreference) pref;
            list_pref.setSummary(list_pref.getEntry());
        } else {
            try {
                pref.setSummary(sharedPreferences.getString(pref.getKey(), ""));
            } catch (ClassCastException ignored) { // if not string for this key
            /*LogLong.e(log_tag, "ClassCastException @"
                    + " NetworkSettingsActivity.onSharedPreferenceChanged : " + cce.getMessage());
                    */
            }
        }
    }

    private ArrayList<Preference> getAllPrefs(Preference pref) {
        ArrayList<Preference> al_prefs = new ArrayList<>();
        if(pref instanceof PreferenceGroup) { // PreferenceScreen or -Category
            PreferenceGroup pg = (PreferenceGroup) pref;
            for(int pg_i = 0; pg_i < pg.getPreferenceCount(); pg_i++) {
                Preference pref_cur = pg.getPreference(pg_i);
                // could be simplified to just call itself, no check, but will be slower
                if(pref_cur instanceof PreferenceGroup) { // PreferenceScreen or -Category
                    al_prefs.addAll(getAllPrefs(pref_cur));
                } else {
                    al_prefs.add(pref_cur);
                }
            }
        } else {
            al_prefs.add(pref);
        }
        return al_prefs;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private PreferenceScreen getPrefScreen(PreferenceFragment pf) {
        return (pf == null) ? null : pf.getPreferenceScreen();
    }

    @SuppressWarnings("deprecation")
    private void initSummaries() {
        PreferenceScreen ps = useOldPrefs ? getPreferenceScreen() : getPrefScreen(pref_fragm_main);
        if(ps == null) {
            return;
        }
        ArrayList<Preference> al_prefs = getAllPrefs(ps);
        for(int pref_i = 0; pref_i < al_prefs.size(); pref_i++) {
            setSummary(al_prefs.get(pref_i), sh_pref);
        }
    }

    @SuppressWarnings("deprecation")
    private void setupOldPrefs() {
        //deprecated code for old platforms < 11

        /* added in xml
        addPreferencesFromResource(R.xml.net_pref_top); // because can't get prefs screen before added some prefs..
        PreferenceCategory ctg_basic = new PreferenceCategory(this);
        // ctg_basic.setIcon(android.R.drawable.ic_menu_preferences); //api 11
        ctg_basic.setTitle(getString(R.string.net_sett_ctg_basic));
        // if not sure that prefScreen already created (for ex, by addPreferencesFromResource())
        // then good idea to check getPreferenceScreen() != null
        getPreferenceScreen().addPreference(ctg_basic); // deprecated
        // or use resource with intents to open appropriate prefs xml:
        // https://developer.android.com/guide/topics/ui/settings.html
        */
        addPreferencesFromResource(R.xml.net_prefs);
        //initSummaries(); // now in onPostCreate
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupFragmentPrefs() {
        pref_fragm_main = new BasicPreferenceFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, pref_fragm_main, "BasicPreferenceFragment_tag")
                .commit();

        // now in onPostCreate - need to return control to let Fragment's onCreate be called
        //initSummaries();
    }

    /* useless - only one header is present
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onBuildHeaders(List<Header> target) {
        if(! useOldPrefs) {
            loadHeadersFromResource(R.xml.net_pref_headers, target);
        }
    }*/

    // required if settings are presented by PreferenceFragment-s
    @Override
    protected boolean isValidFragment(String fragmentName) {
        return BasicPreferenceFragment.class.getName().equals(fragmentName);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class BasicPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.net_prefs);
        }
    }
}
