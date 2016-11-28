package bv.dev.insearch_browser.net;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bv.dev.insearch_browser.ui.MainActivity;
import bv.lib.BVJUtils;

/**
 * Created by Belyak V. on 18.12.2015.
 *
 */
public class WebpageLoader {
    private static final String log_tag = MainActivity.log_tag;
    private String page_loading_url = ""; // web page that loaded or loading

    //private Context cntx = null; //unused now
    private IConnectionChecker conn_chkr = null;
    private LinkedList<IWebpageReceiver> receivers = new LinkedList<>();

    private DownloadWebpageTask d_wp_task = null;
    private Webpage webpage = null;

    public WebpageLoader(/*Context context,*/ IConnectionChecker connChkr) {
        //cntx = context; // unused
        conn_chkr = connChkr;
    }

    public interface IConnectionChecker {
        boolean connectionGranted();
    }

    public interface IWebpageReceiver {
        void receive(Webpage webpage_res);
    }

    public Webpage getWebpage() {
        return webpage == null ? null : webpage.clone();
    }

    public void addWebpageReceiver(IWebpageReceiver webpageRec) {
        receivers.add(webpageRec);
    }

    private void sendToRecievers(Webpage result) {
        for(IWebpageReceiver rec : receivers) {
            rec.receive(result);
        }
    }

    // should not be used anymore - should be in WVController
    /*
    public void loadPageIfNotLoaded(String url) {
        if(! page_loading_url.equals(url)) {
            loadPage(url);
        }
    }*/

    public boolean cancelLoading() {
        return d_wp_task != null && d_wp_task.cancel(true);
    }

    public void loadPage(String url) {
        if(conn_chkr.connectionGranted()) {
            cancelLoading();
            page_loading_url = url;
            webpage = null;
            // each task can be launched only once
            d_wp_task = (DownloadWebpageTask) new DownloadWebpageTask().execute(url);
            //d_wp_task = (DownloadWebpageTask) new DownloadWebpageTask().execute((String[])null); // test
        } else {
            webpage = new Webpage(url);
            webpage.setResDescription("Connection not granted");
            sendToRecievers(webpage);
        }
    }

    //--------------------------------
    private class DownloadWebpageTask extends AsyncTask<String, Integer, Webpage> {
        //                                        <params, progress, result>
        private final String def_mime_type = "text/html";
        private final String def_charset = "UTF-8";
        private final int read_timeout = 10000;
        private final int connect_timeout = 15000;

        private String url_str = null;
        private Webpage webpage_new = null;
        private HttpURLConnection conn = null;
        private String mime_type = null;
        private String charset = null;

        public DownloadWebpageTask() { //set args if needed
            super();
        }

        @Override
        protected void onPreExecute() { // on UI thread
            super.onPreExecute();
            //Toast.makeText(cntx, "loading page..", Toast.LENGTH_SHORT).show(); // not affecting UI now
        }

        @Override
        protected void onProgressUpdate(Integer... values) { // on UI thread
            super.onProgressUpdate(values);
            /* unnecessary
            if(values != null && values.length > 0) {
                Log.d(log_tag, "WebpageLoader progress: " + values[0]);
            }
            */
        }

        @Override
        protected void onPostExecute(Webpage result) { // on UI thread
            super.onPostExecute(result);
            // send and set smth anyway (could be null or not loaded)
            webpage = result;
            sendToRecievers(result);
        }

        @Override
        protected void onCancelled(Webpage result) { // on UI thread
            //super.onCancelled(result); // api 11, empty
            // if canceled after doInBackground has finished
            // arg is result or null

            // do not change old webpage, cause in webview still will be shown old page
            Log.i(log_tag, "WebpageLoader: Page loading canceled "
                    + (result == null ? null : result.getUrl()));
            if(result == null) {
                result = new Webpage(page_loading_url);
            }
            result.setResDescription("Page loading canceled");
            page_loading_url = "";
            sendToRecievers(result);
        }

        @Override
        protected Webpage doInBackground(String... urls) { // on background thread
            if(urls == null || urls.length == 0) {
                return null;
            }
            url_str = urls[0];
            webpage_new = new Webpage(url_str);
            try {
                URL url = new URL(url_str); // MalformedURLException (extend IOE)
                conn = (HttpURLConnection) url.openConnection(); // IOE
                conn.setReadTimeout(read_timeout);
                conn.setConnectTimeout(connect_timeout);
                conn.connect(); // IOE
                if(isCancelled()) {
                    return webpage_new;
                }
                publishProgress(10);
                procResponseHeader();
                procResponseHeaderCT();
                if(isCancelled()) {
                    return webpage_new;
                }
                publishProgress(50);
                procSource();
                //---------
                // maybe should be used instead of mimeType, but think unnecessary now
                //String full_content_type = mimeType + "; " + charset_pref + charset;
                webpage_new.setMimeType(mime_type);
                //webpage_new.setCharset(charset); // now source decoded, charset changed
                webpage_new.setCharset("UTF-16");
                webpage_new.setLoaded(true);
                webpage_new.setResDescription("Loaded");
                Log.i(log_tag, "WebpageLoader > downloadWebpage: page loaded. mime-type="
                        + mime_type + "; charset=" + charset
                        + "; url = " + url_str);
                publishProgress(100);
                return webpage_new;
            } catch(IOException ioe) {
                Log.e(log_tag, "Unable to retrieve webpage " + url_str, ioe);
                procResponseHeader(); // to set code / msg
                if(TextUtils.isEmpty(webpage_new.getResDescription())) {
                    webpage_new.setResDescription(ioe.getMessage());
                }
                page_loading_url = ""; // was not loaded
                return webpage_new; // if was not loaded, isLoaded will be false
            } finally {
                if(conn != null) {
                    conn.disconnect();
                    conn = null;
                }
            }
        }

        private void procResponseHeader() {
            //---  proc response header
            try { // because also called from catch block
                if(conn == null) { // could be null if MailformedURLException before conn initialized
                    Log.i(log_tag, "WebpageLoader > downloadWebpage: HttpURLConnection == null");
                    return;
                }
                webpage_new.setResponseCode(conn.getResponseCode()); // IOE
                webpage_new.setResponseMsg(conn.getResponseMessage()); // IOE
                Log.i(log_tag, "WebpageLoader > downloadWebpage: response code = "
                        + webpage_new.getResponseCode()
                        + "; response msg = " + webpage_new.getResponseMsg());
            } catch(IOException ioe) {
                //if(TextUtils.isEmpty(webpage_new.getResDescription())) { // useless now
                webpage_new.setResDescription(ioe.getMessage());
                //}
                Log.e(log_tag, "WebpageLoader > downloadWebpage: error while processing response header : "
                        + ioe.getMessage());
            }
        }

        private void procResponseHeaderCT() {
            //---  proc response header content type
            final String charset_pref = "charset=";
            String content_type = conn.getContentType(); //could be like "text/html; charset=utf-8" or "text/html"
            if(content_type != null) {
                String ar_content_type[] = content_type.split(";");
                if(ar_content_type.length > 0) {
                    mime_type = ar_content_type[0].trim();
                }
                for(int token_i = 1; token_i < ar_content_type.length; token_i++) { // zero must be mime type
                    String cur_token = ar_content_type[token_i].trim();
                    if(cur_token.toLowerCase().startsWith(charset_pref)) {
                        charset = cur_token.substring(charset_pref.length());
                        break;
                    }
                }
            }
        }

        // used for choosing charset and mime-type
        private String chooseValue(String src_one, String src_two, String def, String display_name,
                                   String info) {
            StringBuilder log_str = new StringBuilder("chooseValue: ").append(display_name).append(" ");
            String res = src_one;
            if(TextUtils.isEmpty(src_one)) {
                if(! TextUtils.isEmpty(src_two)) {
                    res = src_two;
                    log_str.append("source #2 used; ");
                } else {
                    res = def;
                    log_str.append("default used; ");
                }
            } else {
                log_str.append("source #1 used; ");
                if( (! TextUtils.isEmpty(src_two))
                        && (! src_one.equals(src_two)) ) {
                    log_str.append("sources not match! : src_one = ").append(src_one)
                            .append("; src_two = ").append(src_two)
                            .append("; def = ").append(def)
                            .append("; res = ").append(res).append("; info: ").append(info);
                } else {
                    log_str.append("sources match; ");
                }
            }
            Log.d(log_tag, log_str.toString());
            return res;
        }

        private void procSource() throws IOException {
            InputStream is = null;
            try {
                // -------- proc source
                // if response header's charset or content type empty - set from meta tag in html source
                // it could be like: <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=windows-1251">
                if(TextUtils.isEmpty(mime_type) || TextUtils.isEmpty(charset)) {
                    is = conn.getInputStream(); // IOE
                    byte ar_bytes_raw[] = BVJUtils.inputStreamToByteAr(is);
                    StringBuilder html_data_raw = BVJUtils.inputStreamToStrBldr(
                            new ByteArrayInputStream(ar_bytes_raw));

                    // another (simpler) regex (takes only charset)
                    //String regex_cont_type = "<meta[^>]+charset=['\"]?(.*?)['\"]?[\\/\\s>]";
                    //----------
                    // contains two groups: for content type (optional) and charset (required)
                    String regex_cont_type =
                            "<meta(?!\\s*(?:name|value)\\s*=)" +
                                    "(?:[^>]*?content\\s*=[\\s\"']*)?" +
                                    "([^>]*?)[\\s\"';]*charset\\s*=[\\s\"']*([^\\s\"'/>]*)";
                    // zero group is all regex
                    int regex_ct_mime_type_group = 1; // optional group
                    int regex_ct_charset_group = 2; // mandatory group
                    Pattern pattern_cont_type = Pattern.compile(regex_cont_type, Pattern.CASE_INSENSITIVE);
                    Matcher match_html_raw = pattern_cont_type.matcher(html_data_raw);
                    String meta_mime_type = null;
                    String meta_charset = null;

                    if(match_html_raw.find()) {
                        try {
                            meta_mime_type = match_html_raw.group(regex_ct_mime_type_group); // optional group
                            meta_charset = match_html_raw.group(regex_ct_charset_group);  // mandatory group
                        } catch (IllegalStateException ise) {
                            Log.w(log_tag, "WebpageLoader > downloadWebpage: Matching exception: ", ise);
                        }
                    } else {
                        Log.d(log_tag, "WebpageLoader > downloadWebpage: " +
                                "Content type and charset matching not found. url = " + url_str);
                    }

                    // choose what to use (response header, meta, default)
                    mime_type = chooseValue(mime_type, meta_mime_type, def_mime_type,
                            "mime-type", "url = " + url_str);
                    charset = chooseValue(charset, meta_charset, def_charset, "charset",
                            "url = " + url_str);

                    webpage_new.setHtml(BVJUtils.inputStreamToStrBldr(
                            new ByteArrayInputStream(ar_bytes_raw), charset).toString());
                } else { // mime and charset present in response header
                    is = conn.getInputStream(); // IOE
                    webpage_new.setHtml(BVJUtils.inputStreamToStrBldr(is, charset).toString());
                }
            } finally {
                if (is != null) {
                    is.close(); // IOE
                }
            }
        }

    } // DownloadWebpageTask
    //---------------------

}
