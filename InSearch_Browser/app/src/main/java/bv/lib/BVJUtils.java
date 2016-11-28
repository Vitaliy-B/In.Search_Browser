package bv.lib;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by Belyak V. on 14.12.2015.
 * 2016.02.26 - fixes
 *
 */

public class BVJUtils {

    //to easily use in finally block, if try with resources not available
    public static boolean closeIgnoringIOE(Closeable ca) {
        if(ca != null) {
            try {
                ca.close();
                return true;
            } catch (IOException ignored) {}
        }
        return false;
    }

    // reads input stream, and closes it
    public static StringBuilder inputStreamToStrBldr(InputStream is) throws IOException {
        return readerToStrBldr(new InputStreamReader(is));
    }

    // reads input stream, and closes it
    public static StringBuilder inputStreamToStrBldr(InputStream is, String charset) throws IOException {
        return readerToStrBldr(new InputStreamReader(is, charset));
    }

    // reads from reader, and closes it
    public static StringBuilder readerToStrBldr(Reader rdr_in) throws IOException {
        StringBuilder data = new StringBuilder();
        BufferedReader rdr = null;
        try {
            rdr = new BufferedReader(rdr_in);
            final int buf_size = 1024;
            char buf[] = new char[buf_size];
            int data_read;
            while( (data_read = rdr.read(buf, 0, buf_size)) != -1) {
                data.append(buf, 0, data_read);
            }
        } finally {
            closeIgnoringIOE(rdr);
        }
        return data;
    }

    // reads input stream, and closes it
    public static byte[] inputStreamToByteAr(InputStream is) throws IOException {
        if(is == null) {
            return null;
        }
        ByteArrayOutputStream byte_ar_os = new ByteArrayOutputStream(); // does not need to be closed
        try {
            final int buf_size = 1024;
            byte buf[] = new byte[buf_size];
            int data_read;
            while( (data_read = is.read(buf, 0, buf_size)) != -1) {
                byte_ar_os.write(buf, 0, data_read);
            }
        } finally {
            closeIgnoringIOE(is);
        }
        return byte_ar_os.toByteArray();
    }

    // not for ARGB
    public static String RGBtoStr(int rgb_color) {
        // mask to get only RGB values, formatter to convert to hex 6 digits long + leading '#'
        return String.format("#%06X", 0xFFFFFF & rgb_color);
    }

    // not like Arrays.toString() - each element in quotes : {"el1", "el2"}
    public static String toString(Object[] ar, String custom_el_delim) {
        if(ar == null) {
            return null;
        }
        if(custom_el_delim == null) {
            custom_el_delim = ", ";
        }
        StringBuilder str_data = new StringBuilder();
        str_data.append("{");
        if(ar.length > 0){
            for(int ar_i = 0; ar_i < (ar.length - 1); ++ar_i) {
                str_data.append("\"").append(ar[ar_i]).append("\"").append(custom_el_delim);
            }
            str_data.append("\"").append(ar[ar.length-1]).append("\"");
        }
        str_data.append("}");
        return str_data.toString();
    }

    // not like Collection.toString() - each element in quotes : {"el1", "el2"}
    public static String toString(Collection<?> col, String custom_el_delim) {
        if(col == null) {
            return null;
        }
        if(custom_el_delim == null) {
            custom_el_delim = ", ";
        }
        StringBuilder str_data = new StringBuilder();
        str_data.append("{");
        Iterator<?> iter = col.iterator();
        while(iter.hasNext()) {
            str_data.append("\"").append(iter.next()).append("\"");
            if(iter.hasNext()) {
                str_data.append(custom_el_delim);
            }
        }
        str_data.append("}");
        return str_data.toString();
    }

    // Character does not have this method
    // limitation: after char#700 returns true for almost any character
    public static boolean isPunct(char ch) {
        return !(Character.isISOControl(ch) || Character.isWhitespace(ch) || Character.isLetterOrDigit(ch));
        // or use Pattern with regex "\\p{Punct}" ? - single punct character
    }

    // consist of punctuation only
    public static boolean isPunct(String token) {
        if(token == null || token.isEmpty()) {
            return false;
        }
        // or use Pattern with regex "\\p{Punct}" - single punct character
        for(int char_i = 0; char_i < token.length(); char_i++) {
            if(! isPunct(token.charAt(char_i))) {
                return false;
            }
        }
        return true;
    }

    /*
    Split string by last newline in range.
    return indexes for each part
    later: use spans? cons: constructing new string
     */
    public static ArrayList<Integer> markLastNewLinesInRange(String str, int maxRange) {
        if(str == null || str.length() == 0) {
            return new ArrayList<>();
        }
        ArrayList<Integer> al_marks = new ArrayList<>(str.length() / maxRange + 2); // integer parts
                                                        // + remainder + 1 for leading zero
        al_marks.add(0);
        int nl_val = '\n';
        while(true) {
            int last_mark = al_marks.get(al_marks.size()-1);
            int new_mark = last_mark + maxRange;
            if(new_mark >= str.length()) {
                al_marks.add(str.length());
                break;
            } else {
                new_mark = str.lastIndexOf(nl_val, new_mark);
                if(new_mark <= last_mark) {
                    new_mark = last_mark + maxRange;
                }
                al_marks.add(new_mark);
            }
        }
        return al_marks;
    }

    public static boolean equalsIgnoreCase(List<String> l_one, List<String> l_two) {
        if(l_one == l_two) {
            return true;
        }
        // if both are null handled above
        if( (l_one == null || l_two == null) || (l_one.size() != l_two.size())) {
            return false;
        }
        ListIterator<String> iter_one = l_one.listIterator();
        ListIterator<String> iter_two = l_two.listIterator();
        while(iter_one.hasNext()) { // second has same size
            if(! iter_one.next().equalsIgnoreCase(iter_two.next())) {
                return false;
            }
        }
        return true;
    }
}