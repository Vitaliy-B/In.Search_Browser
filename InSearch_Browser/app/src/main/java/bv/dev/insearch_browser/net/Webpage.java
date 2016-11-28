package bv.dev.insearch_browser.net;

/**
 * Created by Belyak V. on 17.05.2016.
 *
 */
public class Webpage implements Cloneable {
    private String url;
    private String mimeType;
    private String charset;
    private String html;
    private int responseCode;
    private String responseMsg;
    private String resDescription;
    private boolean loaded;

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }
    @SuppressWarnings("unused")
    public void setUrl(String url) {
        this.url = url;
    }
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    public void setCharset(String charset) {
        this.charset = charset;
    }
    public void setHtml(String html) {
        this.html = html;
    }
    public void setResponseMsg(String responseMsg) {
        this.responseMsg = responseMsg;
    }
    public void setResDescription(String resDescription) {
        this.resDescription = resDescription;
    }
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public String getUrl() {
        return url;
    }
    public String getMimeType() {
        return mimeType;
    }
    public String getCharset() {
        return charset;
    }
    public String getHtml() {
        return html;
    }
    public int getResponseCode() {
        return responseCode;
    }
    public String getResponseMsg() {
        return responseMsg;
    }
    public String getResDescription() {
        return resDescription;
    }
    public boolean isLoaded() {
        return loaded;
    }

    public Webpage() {
        url = "";
        mimeType = "";
        charset = "";
        html = "";
        responseCode = -2; // default, -1 if no response code
        responseMsg = "";
        resDescription = "";
        loaded = false;
    }

    public Webpage(String url) {
        this();
        this.url = url;
        //loaded = false; set by default constructor
    }

    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    @Override
    protected Webpage clone() {
        try {
            return (Webpage) super.clone(); // enough for primitives and Strings
        } catch (CloneNotSupportedException cnse) {
            throw new RuntimeException(cnse);
        }
    }
}
