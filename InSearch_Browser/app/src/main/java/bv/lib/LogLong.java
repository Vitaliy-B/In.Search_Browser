package bv.lib;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Belyak V. on 20.05.2016.
 *
 */

    /*
    Log class has limitations for max string's length to output (4*1024 bytes)
    Split string by last newline in range
    and output to Log on the specified level.
    returns numbers of parts, 0 if wrong args or error
     */
public class LogLong {
    /*
    Later: provide other methods from Log?
     */
    public static int e(String tag, String msg) {
        return logSplitByNL(Log.ERROR, tag, msg);
    }
    public static int w(String tag, String msg) {
        return logSplitByNL(Log.WARN, tag, msg);
    }
    public static int i(String tag, String msg) {
        return logSplitByNL(Log.INFO, tag, msg);
    }
    public static int d(String tag, String msg) {
        return logSplitByNL(Log.DEBUG, tag, msg);
    }
    public static int v(String tag, String msg) {
        return logSplitByNL(Log.VERBOSE, tag, msg);
    }

    public static int logSplitByNL(int priority, String tag, String msg) {
        final int max_length = 4000;
        ArrayList<Integer> al_marks = BVJUtils.markLastNewLinesInRange(msg, max_length);
        if(al_marks.size() < 2) { // incorrect args or function error
            Log.println(priority, tag, msg);
            return 0;
        }
        Log.println(priority, tag, msg.substring(al_marks.get(0), al_marks.get(1)));
        for(int mark_i = 1; mark_i < al_marks.size() - 1; mark_i++) {
            // each next new part will start from newline
            Log.println(priority, tag, "<part #" + (mark_i + 1) + "/" + (al_marks.size() - 1) + "> : "
                    + msg.substring(al_marks.get(mark_i), al_marks.get(mark_i + 1)));
        }
        return al_marks.size() - 1;
    }
}
