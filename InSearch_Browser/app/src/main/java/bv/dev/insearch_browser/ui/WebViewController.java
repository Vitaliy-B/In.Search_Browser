package bv.dev.insearch_browser.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;

import bv.dev.insearch_browser.R;
import bv.dev.insearch_browser.insearch.IntellectualSearch;
import bv.dev.insearch_browser.insearch.NLPDataProvider;
import bv.dev.insearch_browser.net.WebUtils;
import bv.dev.insearch_browser.net.Webpage;
import bv.dev.insearch_browser.net.WebpageLoader;
import bv.lib.BVJUtils;
import bv.lib.LogLong;

/**
 * Created by Belyak V. on 13.05.2016.
 *
 * Load pages in webview, go back/forward, search
 */
public class WebViewController {
    private static final String log_tag = MainActivity.log_tag;
    private static final int search_res_max_count = 100;

    private EditText et_url = null;
    private WebView web_view = null;

    private Context cntx = null;
    private WebpageLoader webpage_ldr = null;
    private WebpageReceiver webpage_recv = null;
    private WebHistory web_hist = null;
    private SearchInitializerTask si_task = null;
    private SearchTask search_task = null;
    private IntellectualSearch insearch = null; // one instance for each page

    @SuppressLint("SetJavaScriptEnabled")
    public WebViewController(Context context, WebView web_view_in, EditText et_url_in,
                             WebpageLoader.IConnectionChecker conn_chkr) {

        cntx = context;
        web_view = web_view_in;
        et_url = et_url_in;

        web_view.setWebViewClient(new SimpleWebViewClient()); // handle links, oth..
        web_view.setWebChromeClient(new SimpleWebChromeClient()); // progress (does not work), also some js functions
        web_view.getSettings().setJavaScriptEnabled(true); // js setup

        web_hist = new WebHistory();
        webpage_recv = new WebpageReceiver();
        //WebpageLoader initialization
        webpage_ldr = new WebpageLoader(conn_chkr);
        webpage_ldr.addWebpageReceiver(webpage_recv);

        // IntellectualSearch
        IntellectualSearch.init(new LogWrapper(), new NLPDataProvider()); // if initialized will skip
    }

    public void loadPage(String url_str) {
        cancelSearch();
        cancelSearchInit();
        insearch = null; // one instance for each page

        if(WebUtils.isValidUrl(url_str)) {
            Toast.makeText(cntx, "loading page..", Toast.LENGTH_SHORT).show();
            webpage_ldr.loadPage(url_str); // also invalidates stored webpage
            // and cancels prev page loading
            // but in webview page still will be visible
        } else if(WebUtils.isSpecialUrl(url_str)){
            web_view.loadUrl(url_str);
            web_hist.addHistoryItem(new Webpage(url_str)); // to be able go back
        } else{
            Toast.makeText(cntx, "Incorrect URL", Toast.LENGTH_SHORT).show();
            Log.i(log_tag, "WebViewController.loadPage: Incorrect URL ");
            web_view.loadUrl(WebUtils.url_about_blank);
            web_hist.addHistoryItem(new Webpage(WebUtils.url_about_blank)); // to be able go back
        }
    }

    public boolean loadPageIfNotLoaded(String url) {
        Webpage wp = web_hist.getCurrent();
        if(wp == null || ! wp.isLoaded() || ! wp.getUrl().equals(url)) {
            loadPage(url);
            return true;
        } else {
            Log.d(log_tag, "WebViewController.loadPageIfNotLoaded: Loading skipped, url = " + url);
            return false;
        }
    }

    @SuppressWarnings("unused")
    public boolean canGoFor(int steps) {
        return web_hist.canGoFor(steps);
    }

    @SuppressWarnings("unused")
    public boolean canGoForward() {
        return web_hist.canGoFor(1);
    }

    @SuppressWarnings("unused")
    public boolean canGoBack() {
        return web_hist.canGoFor(-1);
    }

    public boolean goFor(int steps) {
        //web_view.clearHistory(); //maybe should clear history, cause it's useless now,
        // and its items will not be removed when going back manually

        Webpage wp = web_hist.goFor(steps);
        if(wp == null) {
            return false;
        } else {
            et_url.setText(wp.getUrl());
            webpage_recv.receive(wp, false);
            return true;
        }

        /* another way - if let webview load pages itself (do not override in WebViewClient)
        WebBackForwardList page_hist = web_view.copyBackForwardList();
        if(page_hist.getCurrentIndex() > 0) {
            String prev_url = page_hist.getItemAtIndex(page_hist.getCurrentIndex() - 1).getUrl();
            et_url.setText(prev_url);
            web_view.goBack();
            // it results to call SimpleWebViewClient.shouldOverrideUrlLoading
            // which got to tell load this url, or do load it itself.
            // if shouldOverr returned false (show in webview), when goBack called
            // shouldOverr does not called again, and item is removing from history
            // if shouldOverr returned true (load manually), when goBack called
            // shouldOverr called again, and item remains in history,
            // and new item is adding to end after prev page is loaded manually,
            // so can't go back more then 1 time, just switching between two last pages
        }*/
    }

    public boolean goBack() {
        return goFor(-1);
    }

    @SuppressWarnings("unused")
    public boolean goForward() {
        return goFor(1);
    }

    public void search(String search_query) {
        /* build-in search
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            web_view.findAllAsync(et_search.getText().toString());
        } else {
            web_view.findAll(et_search.getText().toString());
        }*/

        //!! think about removing search results (simply reload/refresh page?)

        // highlight may not work for trailing space and oth
        String input = search_query.trim();
        if(TextUtils.isEmpty(input)) {
            Toast.makeText(cntx, "Empty search query", Toast.LENGTH_SHORT).show();
            return;
        }

        //used only to chk if it was loaded
        Webpage webpage_src = webpage_ldr.getWebpage();
        if( webpage_src == null || !webpage_src.isLoaded() ) {
            Log.w(log_tag, "search: Webpage not loaded: " + webpage_src);
            Toast.makeText(cntx, "Webpage not loaded!", Toast.LENGTH_SHORT).show();
            return;
        }

        // useful now only for showing right msg
        // because if not initialized, insearch var will be null
        // handle oth states?
        IntellectualSearch.InitState istate = IntellectualSearch.getInitState();
        switch(istate) {
            case Initialized:
            case Initializing: // if initializing - procSrcContent is running or returned error
                Log.d(log_tag, "search : InSearch.InitState = " + istate);
                // can work
                break;
            case Error:
                // init again / reinit ?
            case UnInitialized:
            default:
                Log.e(log_tag, "Error @ search: invalid state of IntellectualSearch :"
                        + istate);
                Toast.makeText(cntx, "Error while initializing search", Toast.LENGTH_SHORT).show();
                return;
        }

        // wait for? (in doInBG)
        // webpage loaded must be checked before to show appropriate msg
        if(insearch == null) { //source not processed or still processing
            Log.w(log_tag, "search: insearch == null ");
            Toast.makeText(cntx, "Search not initialized yet", Toast.LENGTH_SHORT).show();
            return;
        }

        cancelSearch();
        search_task = new SearchTask(web_view);
        search_task.execute(input);
    }

    // think when cancel it? (new search, load page, ..)
    public boolean cancelSearch() {
        return search_task != null && search_task.cancel(true);
    }

    public void cancelLoading() {
        cancelSearchInit();
        webpage_ldr.cancelLoading();
    }

    public boolean reinitSearchOnPage(String url) { // url to compare with history,
        // if url in url-box has changed, but not loaded, do not load new nor old
        Webpage wp = web_hist.getCurrent();
        if(insearch == null) { // not initialized
            if(wp != null && wp.isLoaded() && wp.getUrl().equals(url)) {
                Log.d(log_tag, "WebViewController.reinitSearchOnPage() do : insearch == null; "
                        + "Webpage url = " + wp.getUrl());
                webpage_recv.initSearch(wp);
                return true;
            } else {
                Log.d(log_tag, "WebViewController.reinitSearchOnPage() skip : insearch == null;  Webpage "
                        + ((wp == null) ? "== null" :
                        ("is loaded = " + wp.isLoaded() + "; url = " + wp.getUrl())));
            }
        } else {
            Log.d(log_tag, "WebViewController.reinitSearchOnPage() skip : insearch != null");
        }
        return false;
    }

    //-------------------------------------

    private class WebHistory {
        private ArrayList<Webpage> al_webpages = new ArrayList<>();
        private int cur_page_index = -1; // when add first will be 0

        public boolean canGoFor(int steps) {
            int new_index = cur_page_index + steps;
            return !al_webpages.isEmpty() && new_index >= 0 && new_index < al_webpages.size();
        }

        public Webpage goFor(int steps) {
            /*
            // debug
            Log.d(log_tag, "WebHistory.goFor() : steps == " + steps + "; cur_page_index == "
                    + cur_page_index + "; al_webpages.size() == " + al_webpages.size());
            */

            int new_index = cur_page_index + steps;
            if(!al_webpages.isEmpty() && new_index >= 0 && new_index < al_webpages.size()) {
                cur_page_index = new_index;
                return al_webpages.get(new_index);
            }
            return null;
        }

        public boolean addHistoryItem(Webpage wp) {
            /* because need to be able go back from bad page
            if(wp == null || !wp.isLoaded()) {
                return false;
            }*/
            if(cur_page_index < al_webpages.size() - 1) { // if current is not last, delete succeeding
                al_webpages.subList(cur_page_index + 1, al_webpages.size()).clear();
            }
            if( (cur_page_index >= 0 && cur_page_index == al_webpages.size() - 1)) { // last and not empty
                Webpage last_wp = al_webpages.get(cur_page_index);
                if(last_wp == null || ! last_wp.isLoaded()) { // not loaded
                    al_webpages.set(cur_page_index, wp);
                    return true;
                }
            }
            ++cur_page_index;
            return al_webpages.add(wp);
            //call after new page loaded: think about about:blank and oth..
        }

        public Webpage getCurrent() {
            return goFor(0);
        }
    }

    private class WebpageReceiver implements WebpageLoader.IWebpageReceiver {
        @Override
        public void receive(Webpage webpage_res) {
            receive(webpage_res, true);
        }

        public void receive(Webpage webpage_res, boolean saveToHistory) {
            if(saveToHistory) {
                web_hist.addHistoryItem(webpage_res);
            }
            initWebView(webpage_res);
            initSearch(webpage_res);
        }

        private void initWebView(Webpage webpage_res) {
            /*
            for some reason, getUrl returns url specified in historyUrl parameter.
            getOriginalUrl returns previous page url.
            history items getUrl and getOriginalUrl return url that was specified
                in historyUrl parameter (both the same).

            setting historyUrl to actual url fixes getUrl problem.
             */
            if(webpage_res != null && webpage_res.isLoaded()) {
                // charset after decoding is utf-16 - so it is not required, could be null
                web_view.loadDataWithBaseURL(webpage_res.getUrl(), webpage_res.getHtml(), webpage_res.getMimeType(),
                        webpage_res.getCharset(), webpage_res.getUrl());
            } else {
                StringBuilder msg = new StringBuilder();
                if(webpage_res == null) {
                    msg.append("Unable to retrieve webpage (null)");
                } else if(! TextUtils.isEmpty(webpage_res.getResDescription())) {
                    msg.append("Error : ").append(webpage_res.getResDescription());
                } else {
                    msg.append("Error ").append(webpage_res.getResponseCode()).append(" : ")
                            .append(webpage_res.getResponseMsg());
                    //.append("; url: ").append(wp.getUrl());
                }
                Toast.makeText(cntx, msg.toString(), Toast.LENGTH_LONG).show();
                web_view.loadUrl(WebUtils.url_not_loaded);
            }

            /*
            // debug
            StringBuilder log_out = new StringBuilder("WebViewController.WebViewInitializer.receive() : ");
            log_out.append("\n Webpage.getUrl == ").append(wp == null ? null : wp.getUrl())
                    .append("\n Webview: getUrl() = ").append(web_view.getUrl())
                    .append("\n getOriginalUrl = ").append(web_view.getOriginalUrl());
            // not used now
            WebBackForwardList bf_list = web_view.copyBackForwardList();
            log_out.append("\n WebBackForwardList.size = ").append(bf_list.getSize());
            for(int bf_i = 0; bf_i < bf_list.getSize(); bf_i++) {
                WebHistoryItem hist_item = bf_list.getItemAtIndex(bf_i);
                log_out.append("\n WebHistoryItem #").append(bf_i);
                if(hist_item == null) {
                    log_out.append(" = null");
                } else {
                    log_out.append("\n url = ").append(hist_item.getUrl())
                            .append("; OriginalUrl = ").append(hist_item.getOriginalUrl())
                            .append("; Title = ").append(hist_item.getTitle());
                }
            }
            Log.d(log_tag, log_out.toString());
            */
        }

        private void initSearch(Webpage webpage_res) {
            if(webpage_res != null && webpage_res.isLoaded()) {
                cancelSearchInit();
                si_task = (SearchInitializerTask) new SearchInitializerTask().execute(webpage_res);
            }
        }
    }

    // move together to initSearch() ?
    private boolean cancelSearchInit() {
        return si_task != null && si_task.cancel(true);
    }

    @SuppressWarnings("deprecation")
    private Spanned html2spanned(String html) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return Html.fromHtml(html);
        } else {
            return Html.fromHtml(html, 0);
        }
    }

    private class SearchInitializerTask extends AsyncTask<Webpage, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Webpage... params) {
            if(params == null || params.length == 0) {
                Log.e(log_tag, "Error @ SearchInitializerTask.doInBackground: Webpage not set");
                return false;
            }
            IntellectualSearch insearch_new = new IntellectualSearch();

            //getTextFromHTML info:
            //webpage.html - encoded source
            // source code without tag symbols, but with all markup keywords
            //Html.escapeHtml(source_html); // api 16
            // formatted html page, like webview
            //Html.fromHtml(source_html);
            // formatted html page, like webview :
            // page main content without tags (almost clear)
            // cyrillic encoding is not always ok (first encode source correct?)
            //SpannableStringBuilder spansb = new SpannableStringBuilder(Html.fromHtml(source_html));
            // toString - no formatting
            //spansb.toString()
            // try tag handler ? (Html.fromHtml(result, null, null)))
            // urls can be found as URLSpan-s in Spanned

            // use spans?
            // review content - handle tags? get source using js?
            String source_text = html2spanned(params[0].getHtml()).toString();

            //Log.d(log_tag, "SearchInitializerTask: webpage content: \n" + source_text);

            if(isCancelled()) {
                //insearch = null; //made before load page
                return false;
            }

            try {
                insearch_new.procSourceText(true, source_text);
                insearch = insearch_new;
                return true;
            } catch (IntellectualSearch.NotInitializedException nie) {
                Log.e(log_tag, "Error @ SearchInitializerTask : NotInitializedException", nie);
                //insearch = null; //made before load page
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean res) {
            if(! res) {
                Toast.makeText(cntx, "Can't initialize search on page",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class SearchTask extends AsyncTask<String, Void, ArrayList<IntellectualSearch.SearchResult>> {
        private WebView web_view_target;

        public SearchTask(WebView web_view_target) {
            this.web_view_target = web_view_target;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // insearch state checked by caller
        }

        @Override
        protected ArrayList<IntellectualSearch.SearchResult> doInBackground(String... params) {
            if(params == null || params.length == 0) {
                Log.e(log_tag, "Error @ SearchTask.doInBackground: query not set");
                return null;
            }
            // update progress?
            // chk canceled?
            String query = params[0];
            ArrayList<IntellectualSearch.SearchResult> al_search_res = null;
            try {
                al_search_res = insearch.search(query, true, search_res_max_count);
            } catch (IntellectualSearch.NotInitializedException nie) {
                Log.e(log_tag, "Error @ SearchTask : NotInitializedException : ", nie);
                //return null; // res == null in this case
            }
            return al_search_res;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            // implement?
        }

        @Override
        protected void onCancelled(ArrayList<IntellectualSearch.SearchResult> res) {
            Log.d(log_tag, "SearchTask canceled");
        }

        @Override
        protected void onPostExecute(ArrayList<IntellectualSearch.SearchResult> al_res) {
            // should be completed
            // ..
            if(al_res == null) {
                Toast.makeText(cntx, "Error while initializing search", Toast.LENGTH_LONG).show();
                return;
            }
            if(al_res.size() == 0) {
                Toast.makeText(cntx, "Nothing found..", Toast.LENGTH_LONG).show();
                return;
            }

            /* java 8
            TreeSet<String> keywords = new TreeSet<>(String::compareToIgnoreCase);
            /* // lambda, can be replaced by method ref
            TreeSet<String> keywords = new TreeSet<>((String s1, String s2) ->
            {return s1.compareToIgnoreCase(s2);});
            */
            TreeSet<String> keywords = new TreeSet<>(new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    return lhs.compareToIgnoreCase(rhs);
                }
            });
            //HashSet<String> keywords = new HashSet<>(); // old way - can't filter ignoring case
            for(int sres_i = 0; sres_i < al_res.size(); sres_i++) {
                // highlight results
                // it could not work with strings long enough ..
                highlightText(web_view_target, al_res.get(sres_i).phrase);
                keywords.addAll(al_res.get(sres_i).alKeywords); // skip duplicates
            }
            for(String cur_kw : keywords) {
                highlightKeyword(web_view_target, cur_kw, false);
            }

            /*
            if use keywords - skip duplicates

            how to build precise phrase? see insearch
            how to highlight/show/output full phrase ??
            - highlight func (provide search result?)
            - webview search
            - show list (dialog)
            - write log..
             */
        }
    }

    // base class also provides some functionality for js
    private class SimpleWebChromeClient extends WebChromeClient {
        // try test again?
        /* progress - not work
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            //Activity.setProgress
            // maybe 100 ??
            setProgress(newProgress * 1000); // normalize scale
        }
        */
    }

    private class SimpleWebViewClient extends WebViewClient {
        @Override @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(log_tag, "SimpleWebViewClient.shouldOverrideUrlLoading: url == " + url);
            et_url.setText(url);
            loadPage(url);
            //false - do not override, show in webview (true - do not show)
            return true;
        }

        @Override @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if(request.isForMainFrame()) {
                // maybe also should check if scheme is http/https, it could be another for some subframe
                return shouldOverrideUrlLoading(view, request.getUrl().toString());
            } else {
                Log.d(log_tag, "SimpleWebViewClient.shouldOverrideUrlLoading() !isForMainFrame : url = "
                        + request.getUrl());
                return super.shouldOverrideUrlLoading(view, request); // or return false?
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Log.d(log_tag, "SimpleWebViewClient : page loaded. url = " + url);

            // JS functions to highlight custom words
            InputStream is_do_hl = cntx.getResources().openRawResource(R.raw.dohighlight);
            // original function
            //InputStream is_hl_srch = getResources().openRawResource(R.raw.highlightsearchterms);
            // modified to return result (found or not), so is slower
            InputStream is_hl_srch = cntx.getResources().openRawResource(R.raw.highlightsearchterms_res);
            try {
                String js_pref = "javascript: ";
                StringBuilder js_func_do_hl = new StringBuilder(js_pref)
                        .append(BVJUtils.inputStreamToStrBldr(is_do_hl));
                StringBuilder js_func_hl_srch = new StringBuilder(js_pref)
                        .append(BVJUtils.inputStreamToStrBldr(is_hl_srch));
                // injecting js code
                loadJS(web_view, js_func_do_hl.toString());
                loadJS(web_view, js_func_hl_srch.toString());
            } catch(IOException ioe) {
                Toast.makeText(cntx, "Cann't initialize text highlighing",
                        Toast.LENGTH_SHORT).show();
                Log.e(log_tag, "Error @ SimpleWebViewClient.onPageFinished : " +
                        "Cann't initialize text highlighing");
            } finally {
                BVJUtils.closeIgnoringIOE(is_do_hl);
                BVJUtils.closeIgnoringIOE(is_hl_srch);
            }
        }

        @Override @SuppressWarnings("deprecation") // for api < 23
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Toast.makeText(cntx, "Error " + errorCode + " : " + description,
                    Toast.LENGTH_SHORT).show();
            Log.e(log_tag, "SimpleWebViewClient.onReceivedError() : code = " + errorCode
                    + ";\n url = " + failingUrl + ";\n description: " + description);
        }

        @Override @TargetApi(Build.VERSION_CODES.M)
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if(request.isForMainFrame()) {
                onReceivedError(view, error.getErrorCode(), error.getDescription().toString(),
                        request.getUrl().toString());
            } else {
                Log.w(log_tag, "SimpleWebViewClient.onReceivedError() !isForMainFrame : code = "
                        + error.getErrorCode() + ";\n url = " + request.getUrl()
                        + ";\n description: " + error.getDescription());
            }

        }

        @Override @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            Toast.makeText(cntx, "Error " + errorResponse.getStatusCode()
                    + " : " + errorResponse.getReasonPhrase(), Toast.LENGTH_SHORT).show();
            Log.e(log_tag, "SimpleWebViewClient.onReceivedHttpError() : code =  "
                    + errorResponse.getStatusCode() + "; phrase = " + errorResponse.getReasonPhrase());
        }
    }

    private void loadJS(WebView web_view_target, String js_code) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            web_view_target.loadUrl(js_code);
        } else {
            ValueCallback<String> vc = new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    Log.d(log_tag, "loadJS().onReceiveValue : " + value);
                }
            };
            web_view_target.evaluateJavascript(js_code, vc);
        }
        // provide return value from callback?
    }

    // wrapper for highlightSearchTerms.js
    // highlights ignoring case
    private boolean highlightText(WebView web_view_target, String text, boolean treat_as_phrase,
                                  boolean show_js_warnings, String font_color, String bg_color) {
        // fix: it could not work with strings long enough ..
        if(TextUtils.isEmpty(text)) {
            return false;
        }
        // replace <"> to <\"> , otherwise js function call code could be invalid
        // if use <'>, not <">, should replace it..
        text = text.replace("\"", "\\\"");
        // construct js function call
        StringBuilder js_code = new StringBuilder();
        js_code.append(cntx.getString(R.string.js_hl_srch_beg))
                .append("\"").append(text).append("\", ")
                .append(treat_as_phrase).append(", ")
                .append(show_js_warnings).append(", ")
                .append(cntx.getString(R.string.js_hl_srch_arg_font_1_beg)).append(font_color)
                .append(cntx.getString(R.string.js_hl_srch_arg_font_1_mid)).append(bg_color)
                .append(cntx.getString(R.string.js_hl_srch_arg_font_1_end)).append(", ")
                .append(cntx.getString(R.string.js_hl_srch_arg_font_2))
                .append(cntx.getString(R.string.js_hl_srch_end));
        Log.d(log_tag, "highlightText: js code = \"" + js_code + "\"");
        // calling js code from android
        loadJS(web_view_target, js_code.toString());
        return true;

        /*
        To highlight full-words only wrap by spaces, use treatAsPhrase = true
        (if do not - script will hang), and search words one by one.
        For ex.: short words, as 'a' and oth..
        But think about punctuation and oth.. ( ' and,' - no trailing space)
        */
    }

    private void highlightText(WebView web_view_target, String text) {
        highlightText(web_view_target, text, true, true,
                BVJUtils.RGBtoStr(ContextCompat.getColor(cntx, R.color.blue)),
                BVJUtils.RGBtoStr(ContextCompat.getColor(cntx, R.color.yellow)));

        /* deprecated getColor
        highlightText(web_view_target, text, true, true,
                BVJUtils.RGBtoStr(cntx.getResources().getColor(R.color.blue)),
                BVJUtils.RGBtoStr(cntx.getResources().getColor(R.color.yellow)));
         */
    }

    private void highlightKeyword(WebView web_view_target, String word, boolean full_words) {
        if(full_words) {
            word = " " + word.trim() + " ";
        }
        highlightText(web_view_target, word, true, true,
                BVJUtils.RGBtoStr(ContextCompat.getColor(cntx, R.color.purple_text)),
                BVJUtils.RGBtoStr(ContextCompat.getColor(cntx, R.color.green)));

        /* deprecated getColor
        highlightText(web_view_target, word, true, true,
                BVJUtils.RGBtoStr(cntx.getResources().getColor(R.color.purple_text)),
                BVJUtils.RGBtoStr(cntx.getResources().getColor(R.color.green)));
         */
    }

    // for IntellectualSearch
    private static class LogWrapper implements IntellectualSearch.ILog {
        @Override
        public void e(String msg) {
            LogLong.e(log_tag, msg);
        }
        @Override
        public void w(String msg) {
            LogLong.w(log_tag, msg);
        }
        @Override
        public void i(String msg) {
            LogLong.i(log_tag, msg);
        }
        @Override
        public void d(String msg) {
            LogLong.d(log_tag, msg);
        }
    }

}
