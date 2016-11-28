package bv.dev.insearch_browser.net;

import android.util.Patterns;

/**
 * Created by Belyak V. on 13.05.2016.
 *
 */
public class WebUtils {
    public static final String url_protocol_about = "about";
    public static final String url_about_blank = url_protocol_about + ":blank";
    public static final String url_not_loaded = url_about_blank;

    public static boolean isValidUrl(String url_str) {
        return url_str != null && Patterns.WEB_URL.matcher(url_str).matches();
    }

    public static boolean isSpecialUrl(String url_str) {
        if(url_str == null) {
            return false;
        }
        switch(url_str) {
            // special urls
            case url_about_blank:
                //..
                return true;
            // general
            default:
                return false;
        }
    }
}
