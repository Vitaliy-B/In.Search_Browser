package bv.dev.insearch_browser.insearch;

import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by Belyak V. on 21.12.2015.
 *
 */

/*
   NLPDataProvider - provides model files input streams,
        does not need NLP libs, wraps work with filesystem
*/

public class NLPDataProvider implements IntellectualSearch.INLPDataProvider {
    private final File path_to_NLP = new File(getDirDocs(), "NLP");
    // "/storage/emulated/0/Documents/NLP/WordNet/3_0/dict" - dict dir example
    private final File path_to_wordnet_root = new File(path_to_NLP, "WordNet");
    //private final File path_to_wordnet_dir = new File(path_to_wordnet_root, "2_0");
    private final File path_to_wordnet_dir = new File(path_to_wordnet_root, "3_0");
    private final File path_to_wordnet_dict = new File(path_to_wordnet_dir, "dict");
    private final File path_to_models = new File(path_to_NLP, "OpenNLP_models");
    /* not used
    private final File file_model_tok_en = new File(path_to_models, "en-token.bin");
    private final File file_model_sent_en = new File(path_to_models, "en-sent.bin");
    private final File file_model_pos_maxent_en = new File(path_to_models, "en-pos-maxent.bin");
    */

    private static File getDirDocs() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) { // <19
            return new File(Environment.getExternalStorageDirectory(), "Documents");
        } else {
            return Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS);
        }
    }

    /* old way
    public enum Models {
        EnToken("en-token.bin"), EnSent("en-sent.bin"), EnPOSMaxEnt("en-pos-maxent.bin");

        private final String file_name;
        Models(String fileName) {
            file_name = fileName;
        }
        public String fileName() {
            return file_name;
        }
    }
    */

    private static String getModelFileName(Models model) throws FileNotFoundException {
        switch(model) {
            case EnToken:
                return "en-token.bin";
            case EnSent:
                return "en-sent.bin";
            case EnPOSMaxEnt:
                return "en-pos-maxent.bin";
            default:
                throw new FileNotFoundException("No match for file represented by " + model);
        }
    }

    @Override
    public File getPathToWordNetDict() throws FileSystemNotAvailableException {
        return path_to_wordnet_dict;
    }

    @Override
    public InputStream getInputStream(Models model) throws FileNotFoundException,
            FileSystemNotAvailableException {

        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                 || Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
             return new FileInputStream(new File(path_to_models, getModelFileName(model)));
        } else {
             throw new FileSystemNotAvailableException("External Storage not mounted");
        }
    }
}

