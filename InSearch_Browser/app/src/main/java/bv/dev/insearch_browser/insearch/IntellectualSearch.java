package bv.dev.insearch_browser.insearch;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import bv.lib.BVJUtils;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.morph.WordnetStemmer;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

    /*
        IntellectualSearch (android-independent)

    */

public class IntellectualSearch {
    public enum InitState {
        UnInitialized, Initialized, Initializing, Error
    }

    public interface ILog {
        void e(String msg); // error
        void w(String msg); // warning
        @SuppressWarnings("unused")
        void i(String msg); // info
        void d(String msg); // debug
    }

    // provides model files input streams
    public interface INLPDataProvider {
        class FileSystemNotAvailableException extends IOException {
            public FileSystemNotAvailableException(String detailMessage) {
                super(detailMessage);
            }
        }

        enum Models {
            EnToken, EnSent, EnPOSMaxEnt
        }

        File getPathToWordNetDict() throws FileSystemNotAvailableException;
        InputStream getInputStream(Models model) throws FileNotFoundException,
                FileSystemNotAvailableException;
    }

    public static class NotInitializedException extends Exception {
        public NotInitializedException(String detailMessage) {
            super(detailMessage);
        }
    }

    public enum TokenType {
        Word(1), Stopword(0.6), Punct(0.5);

        private final double weight;

        TokenType(double w) {
            weight = w;
        }

        public double getWeight() {
            return weight;
        }
    }

    public enum MatchType {
        PreciseMatch(1), StemMatch(0.9), SynsetMatch(0.8), NotMatch(0);

        private final double weight;

        MatchType(double w) {
            weight = w;
        }

        public double getWeight() {
            return weight;
        }
    }

    /*
    later: spanned string / strings ?
    refactor
    */
    public static class SearchResult {
        // can't make it precise now, see source content info
        // Also long phrases not always correctly highlighted by hl_func
        // see comments in search func
        public String phrase; // full phrase (from source text)
        public ArrayList<String> alTokens; // all tokens from source text fragment
        public ArrayList<TokenType> alTokenTypes; // token types info
        public ArrayList<MatchType> alMatchTypes;
        public ArrayList<String> alKeywords; // keywords (only required words from full phrase)
        public ArrayList<Integer> alTokenIndexInSQ; //indexes of tokens in search query if match, or -1

        public ArrayList<String> alSQTokens; // tokens from search query
        public ArrayList<Integer> alSQTokenIndexInRes; //indexes of tokens in search result if found, or -1
        public double weight;

        public String evaluation_log; // debug, in future maybe move to wrap / derived class
                                    // or just make private or pkg-accs
    }

    //---------------------------------------
    // static data
    private static InitState init_state = InitState.UnInitialized;
    private static ILog log = null;
    private static INLPDataProvider nlp_data_prov = null;

    private static SentenceDetectorME sent_dtc_me = null;
    private static TokenizerME tknz_me = null; // subclass of Tokenizer
    // maybe try oth pos tagger (PosTaggerME is slow..) - see bm, search
    private static POSTaggerME pos_tag_me = null;
    private static Dictionary wn_dict = null;
    private static WordnetStemmer wn_stem = null;

    private static final long time_chk_init = 200; // 1/5 a second

    // debug
    /* all false
    private static boolean write_log_detectSentME = false;
    private static boolean write_log_tokenizeME = false;
    private static boolean write_log_stemPorter = false;
    private static boolean write_log_stemSnowball = false;
    private static boolean write_log_stemWrap = false;
    private static boolean write_log_TagPosME = false;
    private static boolean write_log_stemJWI = false;
    private static boolean write_log_stemFirstJWI = false;
    private static boolean write_log_synsetJWI = false;
    private static boolean write_log_synsetFirstJWI = false;
    */
    /* all true
    private static boolean write_log_detectSentME = true;
    private static boolean write_log_tokenizeME = true;
    private static boolean write_log_stemPorter = true;
    private static boolean write_log_stemSnowball = true;
    private static boolean write_log_stemWrap = true;
    private static boolean write_log_TagPosME = true;
    private static boolean write_log_stemJWI = true;
    private static boolean write_log_stemFirstJWI = true;
    private static boolean write_log_synsetJWI = true;
    private static boolean write_log_synsetFirstJWI = true;
    */

    private static final boolean write_log_detectSentME = false;
    private static final boolean write_log_tokenizeME = false;
    private static final boolean write_log_stemPorter = false;
    private static final boolean write_log_stemSnowball = false;
    private static final boolean write_log_stemWrap = false;
    private static final boolean write_log_TagPosME = false;
    private static final boolean write_log_stemJWI = false;
    private static final boolean write_log_stemFirstJWI = false;
    private static final boolean write_log_synsetJWI = false;
    private static final boolean write_log_synsetFirstJWI = false;

    //---------------------------------------
    // instance data
    private SourceContent source_content = null;
    private boolean case_sensitive_results = false;

    //---------------------------------------

    public IntellectualSearch() {
    }

    @SuppressWarnings("unused")
    public IntellectualSearch(boolean caseSensitiveResults) {
        case_sensitive_results = caseSensitiveResults;
    }

    @SuppressWarnings("unused")
    public boolean isCaseSensitiveResults() {
        return case_sensitive_results;
    }

    @SuppressWarnings("unused")
    public void setCaseSensitiveResults(boolean caseSensitiveResults) {
        case_sensitive_results = caseSensitiveResults;
    }

    //---------------------------------------
    public static InitState getInitState() {
        return init_state; // immutable, safe to share
    }

    // add cancel init function (chk in thread?, see interrupt())
    public static boolean init(ILog logIn, INLPDataProvider nlpDataProviderIn) {
        if(logIn == null || nlpDataProviderIn == null) {
            throw new NullPointerException("IntellectualSearch.init : null arguments");
        }
        log = logIn;
        nlp_data_prov = nlpDataProviderIn;

        switch (init_state) {
            case UnInitialized:
                init_state = InitState.Initializing; // thread will start after some delay
                InitThread init_thr = new InitThread();
                // init_thr.setPriority(); set?
                init_thr.start();
                return true;
            case Error:
                // throw exception?
                // run REinit or partial init (some tools could be initialized)
                // or just set all to null and then init again (expensive?)
            case Initialized:
            case Initializing:
                return false;
            default:
                throw new RuntimeException("Error @ IntellectualSearch.init() : unknown init state");
        }
    }

    private static class InitThread extends Thread {
        // think about throwing and UncaughtExceptionHandler
        @Override
        public void run() {
            log.d("IntellectualSearch.InitThread ");
            //init_state = InitState.Initializing; // can it be not already set in future?

            InputStream in_sent_model = null;
            InputStream in_tok_model = null;
            InputStream in_pos_model = null;
            // if can't open model file should use mkdirs() for model path
            // and tell to load models there
            try {
                // Sentence detection
                //log.d("SentenceDetectorME ");
                in_sent_model = nlp_data_prov.getInputStream(INLPDataProvider.Models.EnSent); // exceptions
                sent_dtc_me = new SentenceDetectorME(new SentenceModel(in_sent_model)); // exceptions
                //log.d("SentenceDetectorME ready ");

                // TokenizerME
                //log.d("TokenizerME ");
                in_tok_model = nlp_data_prov.getInputStream(INLPDataProvider.Models.EnToken); // exceptions
                // subclass of Tokenizer
                tknz_me = new TokenizerME(new TokenizerModel(in_tok_model)); // exceptions
                //log.d("TokenizerME ready ");

                // POS maxent
                //log.d("POS maxent ");
                // cleared model file (fixed)
                in_pos_model = nlp_data_prov.getInputStream(INLPDataProvider.Models.EnPOSMaxEnt); // exception
                pos_tag_me = new POSTaggerME(new POSModel(in_pos_model)); // exception
                //log.d("POS maxent ready ");

                // JWI
                //log.d("JWI ");
                // IDictionary implementation
                wn_dict = new Dictionary(nlp_data_prov.getPathToWordNetDict());
                /*boolean wn_dict_opened =*/ wn_dict.open(); // exception
                //log.d("JWI Dictionary opened == " + wn_dict_opened); // exception
                wn_stem = new WordnetStemmer(wn_dict); // inits almost instantly
                //log.d("JWI ready ");

                // done
                init_state = InitState.Initialized;
                log.d("IntellectualSearch initialized ");
            } catch (IOException ioe) {
                log.e("IntellectualSearch.InitThread: Can't init: " + ioe.getMessage());
                //init_state = InitState.Error; // handled in finally
            } finally {
                if(init_state != InitState.Initialized) { // could be unhandled exception
                    init_state = InitState.Error;
                }
                BVJUtils.closeIgnoringIOE(in_tok_model);
                BVJUtils.closeIgnoringIOE(in_sent_model);
                BVJUtils.closeIgnoringIOE(in_pos_model);
            }
        }
    }

    private static class ElemsWithProbs {
        public String[] arElems;
        public double[] probs;
        public ElemsWithProbs(String[] arElems, double[] probs) {
            this.arElems = arElems;
            this.probs = probs;
        }
    }

    private static void toolInitChk(Object tool, boolean wait_init)
            throws  NotInitializedException {
        // try to wait for init
        while( (tool == null) && (init_state == InitState.Initializing) && wait_init) {
            try {
                Thread.sleep(time_chk_init);
            } catch (InterruptedException ignored) {}
        }
        if(tool == null) { // still not initialized
            throw new NotInitializedException("Error @ IntellectualSearch: tool not initialized (null) ");
        } // if tool != null -> ignoring init state, trying to work
    }

    private static String[] detectSentenceME(boolean wait_init, String text)
            throws  NotInitializedException {
        toolInitChk(sent_dtc_me, wait_init);

        // Sentence detection
        String ar_sentences[] = sent_dtc_me.sentDetect(text);
        if(write_log_detectSentME) {
            log.d("SentenceDetectorME: \n " + BVJUtils.toString(ar_sentences, "\n"));
        }
        // probabilities also available
        // also can use Span, see example tokenizer
        /*
        Span tokenSpans[] = tokenizer.tokenizePos(text);
        CharSequence token_0 = tokenSpans[0].getCoveredText(text);
        tokenSpans[0].getStart();
        tokenSpans[0].getEnd();
        tokenSpans[0].getProb();
        tokenSpans[0]...
        */
        return ar_sentences;
    }

    /* not used
    private static String[] tokenizeWhitespace(String sentence) {
        // WhitespaceTokenizer
        // do not require ahead of time initialization
        String ar_tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(sentence);
        log.d(" WhitespaceTokenizer: \n " + BVJUtils.toString(ar_tokens, null));

        return ar_tokens;
    }
    */

    private static ElemsWithProbs tokenizeME(boolean wait_init, String sentence)
            throws  NotInitializedException {
        toolInitChk(tknz_me, wait_init);

        // TokenizerME
        // better tokenize by sentences (depends on used model and its training)
        ElemsWithProbs ewp_res = new ElemsWithProbs(tknz_me.tokenize(sentence),
                tknz_me.getTokenProbabilities());
        if(write_log_tokenizeME) {
            log.d(" TokenizerME: \n " + BVJUtils.toString(ewp_res.arElems, null)
                    + " \n " + Arrays.toString(ewp_res.probs));
        }
        /* another way
        Span tokenSpans[] = tokenizer.tokenizePos(text);
        CharSequence token_0 = tokenSpans[0].getCoveredText(text);
        tokenSpans[0].getStart();
        tokenSpans[0].getEnd();
        tokenSpans[0].getProb();
        tokenSpans[0]...
        */

        return ewp_res;
    }

    private static String[] stemPorter(String[] ar_words, boolean to_lower_case) {
        // PorterStemmer
        // do not require ahead of time initialization
        PorterStemmer p_stem = new PorterStemmer();
        String ar_stems[] = new String[ar_words.length];
        StringBuilder log_output = new StringBuilder();
        for(int token_i = 0; token_i < ar_words.length; token_i++) {
            String cur_stem = p_stem.stem(ar_words[token_i]);
            ar_stems[token_i] = to_lower_case ? cur_stem.toLowerCase() : cur_stem;
            if(write_log_stemPorter) {
                log_output.append("\n").append(ar_words[token_i]).append(" -> ").append(ar_stems[token_i]);
            }
        }
        if(write_log_stemPorter) {
            log.d(" PorterStemmer: \n " + log_output.toString());
        }

        return ar_stems;
    }

    private static String[] stemSnowball(SnowballStemmer.ALGORITHM alg, String[] ar_words, boolean to_lower_case) {
        // SnowballStemmer
        // do not require ahead of time initialization
        SnowballStemmer s_stem = new SnowballStemmer(alg);
        String ar_stems[] = new String[ar_words.length];
        StringBuilder log_output = new StringBuilder();
        for(int token_i = 0; token_i < ar_words.length; token_i++) {
            String cur_stem = s_stem.stem(ar_words[token_i]).toString();
            ar_stems[token_i] = to_lower_case ? cur_stem.toLowerCase() : cur_stem;
            if(write_log_stemSnowball) {
                log_output.append("\n").append(ar_words[token_i]).append(" -> ").append(ar_stems[token_i]);
            }
        }
        if(write_log_stemSnowball) {
            log.d(" SnowballStemmer: \n " + log_output);
        }

        return ar_stems;
    }

    //after test use only one tool
    private static String[] stemWrap(String[] ar_words) {
        String[] ar_snowball = stemSnowball(SnowballStemmer.ALGORITHM.ENGLISH, ar_words, true);
        if(write_log_stemWrap) {
            String[] ar_porter = stemPorter(ar_words, true);
            StringBuilder log_out = new StringBuilder();
            if (Arrays.equals(ar_porter, ar_snowball)) {
                log_out.append(" stemWrap : arrays are equal");
            } else {
                log_out.append(" stemWrap : arrays are NOT equal : ");
                for (int stem_i = 0; stem_i < ar_porter.length; stem_i++) {
                    if (!ar_porter[stem_i].equals(ar_snowball[stem_i])) {
                        log_out.append("\n Porter's: \"").append(ar_porter[stem_i])
                                .append("\"; Snowball's: \"").append(ar_snowball[stem_i]).append("\"");
                    }
                }
            }
            log.d(log_out.toString());
        }

        return ar_snowball; // seems like snowball works better then simple Porter
    }

    private static ElemsWithProbs tagPosME(boolean wait_init, String ar_sentence[])
            throws  NotInitializedException {
        toolInitChk(pos_tag_me, wait_init);

        // POS maxent
        // maybe try oth pos tagger (PosTaggerME is slow..) - see bm, search
        /*
        All POS tags: {"``", "VB", "DT", ",", "''", "NNP", "VBZ", "CC", "NN", "RB", ".", "UH",
        "PRP", "MD", "PRP$", "IN", "VBP", "NNS", "WDT", "VBN", "JJR", ":", "WP", "VBD", "TO",
        "JJ", "WRB", "VBG", "EX", "CD", "RBR", "RP", "PDT", "NNPS", "POS", "-LRB-", "-RRB-",
        "JJS", "RBS", "FW", "WP$", "$", "SYM", "LS", "#"}
         */
        ElemsWithProbs ewp_res = new ElemsWithProbs(pos_tag_me.tag(ar_sentence), pos_tag_me.probs());

        if(write_log_TagPosME) {
            StringBuilder log_output = new StringBuilder();
            log_output.append(" POS maxent : \n ");
            //log_output.append(" All POS tags: ").append(BVJUtils.toString(pos_tag_me.getAllPosTags(), null));
            for (int tok_i = 0; tok_i < ar_sentence.length; tok_i++) {
                log_output.append("\n").append(ar_sentence[tok_i]).append(" -> ").append(ewp_res.arElems[tok_i])
                        .append(" [").append(ewp_res.probs[tok_i]).append("]");
            }
            log.d(log_output.toString());
        }

        /* more detailed approach
        // array of top POS tags (most probable) for each token
        Sequence top_tags[] = pos_tag.topKSequences(ar_tokens);
        // top POS for token # 0
        List<String> list_top_pos_0 = top_tags[0].getOutcomes();
        // their porbs
        double ar_top_pos_prob_0[] = top_tags[0].getProbs();
        */

        return ewp_res;
    }

    // returns lists of stems for each token
    // if POS specified each stem list contains only one element
    // !! can return empty stem for some words
    // so use only to find synsets
    private static List<List<String>> stemJWI(boolean wait_init, String ar_tokens[],
                                 POS ar_pos_jwi[]) throws  NotInitializedException {
        toolInitChk(wn_stem, wait_init);

        StringBuilder log_output = new StringBuilder();
        List<List<String>> list_res = new ArrayList<>(ar_tokens.length);
        if(write_log_stemJWI) {
            log_output.append(" stemJWI : \n");
        }
        for(int tok_i = 0; tok_i < ar_tokens.length; tok_i++) {
            List<String> list_stem = wn_stem.findStems(ar_tokens[tok_i], ar_pos_jwi[tok_i]);
            list_res.add(list_stem);
            if(write_log_stemJWI) {
                log_output.append("\n").append(ar_tokens[tok_i]).append("[").append(ar_pos_jwi[tok_i])
                        .append("] -> ");
                // if POS specified contains only one element
                for (String stem_str : list_stem) {
                    log_output.append(stem_str).append(" ; ");
                }
            }
        }
        if(write_log_stemJWI) {
            log.d(log_output.toString());
        }
        return list_res;
    }

    // returns only first stem for each token
    // if POS correct, first should be only one
    // if POS not specified or wrong, first stem could be unsuitable
    // !! can return empty stem for some words
    // so use only to find synsets
    private static String[] stemFirstJWI(boolean wait_init, String ar_tokens[],
                                         POS ar_pos_jwi[]) throws  NotInitializedException {
        List<List<String>> list_stems = stemJWI(wait_init, ar_tokens, ar_pos_jwi);
        ArrayList<String> al_first_stems = new ArrayList<>(list_stems.size());
        for(List<String> lst_cur : list_stems) {
            if(write_log_stemFirstJWI && lst_cur.size() != 1) {
                    log.w("Warning @ stemFirstJWI : list of stems size == " + lst_cur.size()
                            + "; " + lst_cur.toString());
            }
            // to be safe and keep array size correct
            al_first_stems.add((lst_cur.size() > 0) ? lst_cur.get(0) : "");
        }

        // return (String[]) al_first_stems.toArray(); // array of Objects, not Strings, cant cast
        return al_first_stems.toArray(new String[al_first_stems.size()]);
    }

    //returns list of meanings for given word, each meaning - list of synset words
    private static ArrayList<ArrayList<String>> synsetJWI(boolean wait_init, String lemma, POS pos_jwi)
            throws NotInitializedException {
        // getIndexWord require specified POS, not null!!
        if(pos_jwi == null || lemma == null || lemma.isEmpty()) {
            return new ArrayList<>(0);
        }
        toolInitChk(wn_dict, wait_init);
        StringBuilder log_output = new StringBuilder();
        if(write_log_synsetJWI) {
            log_output.append(" synsetJWI : \n");
        }
        IIndexWord idx_word = wn_dict.getIndexWord(lemma, pos_jwi);
        // if pass wrong POS could return null
        if(idx_word == null) { // word not found
            if(write_log_synsetJWI) {
                log.d("synsetJWI :  word not found (unknown lemma or wrong POS) : " + lemma + "; pos = " + pos_jwi);
            }
            return new ArrayList<>(0);
        }
        List<IWordID> list_word_id = idx_word.getWordIDs(); // meanings
        ArrayList<ArrayList<String>> al2d_synsets = new ArrayList<>(list_word_id.size());
        for(IWordID w_id : list_word_id) { // find synsets for each meaning
            IWord word = wn_dict.getWord(w_id);
            if(write_log_synsetJWI) {
                log_output.append("\n ").append(w_id.getSynsetID())
                        .append(" [").append(word.getLemma()).append("] synset: ");
            }
            ISynset synset = word.getSynset();
            /* relations
            synset.getRelatedSynsets(Pointer.SIMILAR_TO) //< seems like only for adj, so see docs
            synset.getRelatedSynsets(Pointer.HYPONYM)
            synset.getRelatedSynsets(Pointer.HYPERNYM)
            */
            List<IWord> lst_words = synset.getWords();
            ArrayList<String> al_synset = new ArrayList<>(lst_words.size());
            for(IWord syn : lst_words) {
                al_synset.add(syn.getLemma());
                if(write_log_synsetJWI) {
                    log_output.append(syn.getLemma()).append(", ");
                }
            }
            al2d_synsets.add(al_synset);
        }
        if(write_log_synsetJWI) {
            log.d(log_output.toString());
        }
        return al2d_synsets;
    }

    private static String[] synsetFirstJWI(boolean wait_init, String lemma, POS pos_jwi)
            throws NotInitializedException {
        /*
        get synsets (first meaning exclude org word), if empty 2-nd;
        also del words with underline
        later maybe improve
         */
        ArrayList<ArrayList<String>> al2d_synsets = synsetJWI(wait_init, lemma, pos_jwi);
        if(al2d_synsets == null || al2d_synsets.isEmpty()) {
            return new String[0];
        }
        ArrayList<String> al_synsets_first = new ArrayList<>();
        for(ArrayList<String> list_cur : al2d_synsets) {
            for(String cur_word : list_cur ) {
                if(! (lemma.equals(cur_word) || cur_word.contains("_"))) {
                    // skip original word and not single words
                    al_synsets_first.add(cur_word);
                }
            }
            // if found something, enough (think first meaning's words should be most suitable)
            if(! al_synsets_first.isEmpty()) {
                break;
            }
        }
        if(write_log_synsetFirstJWI) {
            log.d("synsetFirstJWI: lemma = " + lemma
                    + "; first synsets: " + al_synsets_first.toString());
        }
        return al_synsets_first.toArray(new String[al_synsets_first.size()]);
    }

    /* test function, useless?
    private static void jwi_test(boolean wait_init, String ar_tokens[],
                                 POS ar_pos_jwi[]) throws  NotInitializedException {
        String[] ar_stems = stemFirstJWI(wait_init, ar_tokens, ar_pos_jwi);
        for(int stem_i = 0; stem_i < ar_stems.length; stem_i++) {
            //synsetJWI(wait_init, ar_stems[stem_i], ar_pos_jwi[stem_i]);
            synsetFirstJWI(wait_init, ar_stems[stem_i], ar_pos_jwi[stem_i]);
        }
        // logging must be enabled for functions
    }
    */

    private static POS posTreebankToJWI(String treebankPOS) {
        // see code example in bookmarks (SO/java-stanford-nlp-part-of-speech-labels)
        final String pref_noun = "NN";
        final String pref_verb = "VB";
        final String pref_verb_md = "MD";
        final String pref_adj = "JJ";
        final String pref_adv = "RB";
        final String pref_adv_wh = "WRB";
        //maybe need to handle other tags
        String upc_pos = treebankPOS.toUpperCase().trim(); // UPPERCASE
        // refactor to switch first 2 chars? last should be processed separately,
        // and should be created 2-chars copy of this prefix
        if(upc_pos.startsWith(pref_noun)) {
            return POS.NOUN;
        } else if(upc_pos.startsWith(pref_verb) || upc_pos.startsWith(pref_verb_md)) {
            return POS.VERB;
        } else if(upc_pos.startsWith(pref_adj)) {
            return POS.ADJECTIVE;
        } else if(upc_pos.startsWith(pref_adv) || upc_pos.startsWith(pref_adv_wh)) {
            return POS.ADVERB;
        } else {
            // now for all oth returns null - synsets could not be found (but stems could, maybe several)
            // to find synset should pass correct or possible POS
            return null;
        }
    }

    private static POS[] posTreebankToJWI(String[] treebankPOS) {
        POS[] ar_pos_jwi = new POS[treebankPOS.length];
        for(int pos_i = 0; pos_i < treebankPOS.length; pos_i++) {
            ar_pos_jwi[pos_i] = posTreebankToJWI(treebankPOS[pos_i]);
        }
        return ar_pos_jwi;
    }

    private static class SourceContent {
        public String sourceText;
        // tokens and oth now not linked to source text
        // can't build precise source text fragment from tokens, because
        // don't know where and how much whitespaces were
        // Also long phrases not always correctly highlighted by hl_func
        public String[] arSentences;
        // to work with layers, their elements must be "linked"
        // easiest way - same number of elements
        // so instead of deleting smth maybe better "replace" or put empty element
        // top layer - as is, what to return as result
        // lower layers - optimized for search, but must be linked with top (same length)
        // \/ by sentences \/
        public String[][] arSentTokens; // layer 1 - full, precise text
        public TokenType[][] arSentTokenTypes;
        public String[][] arSentStems; // layer 2 - stems lowercase; puncts and stopwords replaced by ""
    }

    // just process and init, should be called from bg thread
    // later provide ability to cancel or implement as separate thread
    public void procSourceText(boolean wait_init, String sourceText)
            throws NotInitializedException {
        /*
        pick out:
        - sentences
        - tokensME
        - stem Snowball (wrap) + remove punct
         */
        source_content = null; // not valid now
        String[] ar_sentences = detectSentenceME(wait_init, sourceText);
        String[][] ar_sent_tokens = new String[ar_sentences.length][];
        String[][] ar_sent_stems = new String[ar_sentences.length][];
        TokenType[][] ar_sent_tok_types = new TokenType[ar_sentences.length][];
        // better tokenize by sentences
        for(int sent_i = 0; sent_i < ar_sentences.length; sent_i++) {
            ElemsWithProbs ewp_sent_tokens = tokenizeME(wait_init, ar_sentences[sent_i]);
            ar_sent_tokens[sent_i] = ewp_sent_tokens.arElems;
            ar_sent_stems[sent_i] = stemWrap(ar_sent_tokens[sent_i]);
            ar_sent_tok_types[sent_i] = new TokenType[ar_sent_tokens[sent_i].length];
            // check token type
            for(int word_i = 0; word_i < ar_sent_tokens[sent_i].length; word_i++) {
                if(BVJUtils.isPunct(ar_sent_tokens[sent_i][word_i])) {
                    ar_sent_tok_types[sent_i][word_i] = TokenType.Punct;
                    ar_sent_stems[sent_i][word_i] = "";
                } else if(StopWords.isStopWord(ar_sent_tokens[sent_i][word_i])) {
                    ar_sent_tok_types[sent_i][word_i] = TokenType.Stopword;
                    ar_sent_stems[sent_i][word_i] = "";
                } else {
                    ar_sent_tok_types[sent_i][word_i] = TokenType.Word;
                }
            }
        }

        SourceContent sc = new SourceContent();
        sc.sourceText = sourceText;
        sc.arSentences = ar_sentences;
        sc.arSentTokens = ar_sent_tokens;
        sc.arSentTokenTypes = ar_sent_tok_types;
        sc.arSentStems = ar_sent_stems;
        source_content = sc; // null or fully initialized

        //DEBUG
        log.d("procSourceText done. source_content: arSentences # "
                + source_content.arSentences.length);
        /* unnecessary as long as used tools generate logs
        log.d("procSourceText: source_content.arSentences = \n"
                + BVJUtils.toString(source_content.arSentences, "\n"));
        log.d("procSourceText: source_content.arTokens = \n"
                + BVJUtils.toString(source_content.arTokens, null));
        log.d("procSourceText: source_content.arStems = \n"
                + BVJUtils.toString(source_content.arStems, null));
        */
        /*
        log.d("procSourceText: tokens | token types | stems:");
        for(int sent_i = 0; sent_i < ar_sent_tokens.length; sent_i++) {
            for (int word_i = 0; word_i < ar_sent_tokens[sent_i].length; word_i++) {
                log.d("\n \"" + ar_sent_tokens[sent_i][word_i] + "\" \t \""
                        + ar_sent_tok_types[sent_i][word_i] + "\" \t \""
                        + ar_sent_stems[sent_i][word_i] + "\"");
            }
        }
        log.d("procSourceText: results printed");
        */
    }

    private static class SearchQuery {
        public String sourceQuery;
        // to work with layers, their elements must be "linked"
        // easiest way - same number of elements
        // so instead of deleting smth maybe better "replace" or put empty element
        // top layer - as is
        // lower layers - optimized for search, but must be linked with top (same length)
        public String[] arTokens;       // layer 1 - full, precise query
        public TokenType[] arTokenTypes;
        public String[] arStems;        // layer 2 - stems SB; puncts and stopwords replaced by ""
        // layer 3 - synsets (stems SB) : synsets for stems JWI
        // array of synsets for each token
        // each synset array contains stems of synsets for concrete token
        // for puncts / absent words synsets array is null / empty
        public TreeSet<String>[] arSynsetStemsSet; // array because want to get concrete size allocated
        //public String[][] arSynsetStems; //old way
    }

    private SearchQuery procSearchQuery(boolean wait_init, String search_query)
            throws NotInitializedException {
        // assume that query is one sentence
        ElemsWithProbs ewp_tokens = tokenizeME(wait_init, search_query);
        TokenType[] ar_tok_types = new TokenType[ewp_tokens.arElems.length];
        // when filtering stopwords, important to do not make stems array empty
        String[] ar_stems_f_p = stemWrap(ewp_tokens.arElems); // will filter puncts
        String[] ar_stems_f_psw = Arrays.copyOf(ar_stems_f_p, ar_stems_f_p.length); // will filter puncts and stopwords
        // for synsets
        POS[] ar_pos_jwi = posTreebankToJWI(tagPosME(wait_init, ewp_tokens.arElems).arElems);
        // !! can contain empty stem for some words
        String[] ar_stems_jwi = stemFirstJWI(wait_init, ewp_tokens.arElems, ar_pos_jwi);
        //String[][] ar_synset_stems = new String[ewp_tokens.arElems.length][]; // old way
        @SuppressWarnings("unchecked") // array because want to get concrete size allocated
        TreeSet<String>[] ar_synset_stems_set = new TreeSet[ewp_tokens.arElems.length];
        for(int word_i = 0; word_i < ewp_tokens.arElems.length; word_i++) {
            // find synsets;
            // check token types, replace punctuation and stopwords in stems SB;
            if(BVJUtils.isPunct(ewp_tokens.arElems[word_i])) {
                ar_tok_types[word_i] = TokenType.Punct;
                ar_stems_f_p[word_i] = "";
                ar_stems_f_psw[word_i] = "";
                ar_synset_stems_set[word_i] = null; // null for punct and stopwords
            } else if(StopWords.isStopWord(ewp_tokens.arElems[word_i])) {
                ar_tok_types[word_i] = TokenType.Stopword;
                // do not replace in ar_stems_f_p
                ar_stems_f_psw[word_i] = "";
                ar_synset_stems_set[word_i] = null; // null for punct and stopwords
            } else {
                ar_tok_types[word_i] = TokenType.Word;
                // find synsets
                if(ar_stems_jwi[word_i] == null || ar_stems_jwi[word_i].isEmpty()) {
                    ar_synset_stems_set[word_i] = new TreeSet<>(); // empty for word absent in dict
                } else {
                    ar_synset_stems_set[word_i] = new TreeSet<>(Arrays.asList(stemWrap(
                            synsetFirstJWI(wait_init, ar_stems_jwi[word_i], ar_pos_jwi[word_i]))));
                }
            }
        }

        // after deleting stopwords stems empty?
        boolean not_empty_f_psw = false;
        for(String cur_stem : ar_stems_f_psw) {
            if(! cur_stem.isEmpty()) {
                not_empty_f_psw = true;
                break;
            }
        }

        SearchQuery sque = new SearchQuery();
        sque.sourceQuery = search_query;
        sque.arTokens = ewp_tokens.arElems;
        sque.arTokenTypes = ar_tok_types;
        sque.arStems = not_empty_f_psw ? ar_stems_f_psw : ar_stems_f_p;
        sque.arSynsetStemsSet = ar_synset_stems_set;

        log.d("procSearchQuery: \n Tokens = " + BVJUtils.toString(ewp_tokens.arElems, null)
                + "\n TokenTypes = " + Arrays.toString(ar_tok_types)
                + "\n Stems = " + BVJUtils.toString(sque.arStems, null)
                + "\n SynsetStems = " + Arrays.toString(ar_synset_stems_set));

        return sque;
    }

    private static boolean equalsIgnoreCaseNotNullEmpty(String one, String two) {
        return (one != null) && (! one.isEmpty()) /*&& (two != null) && (! two.isEmpty()) */ // redundant
                && one.equalsIgnoreCase(two);
    }

    private static double evaluateSearchResult(SearchResult sres) {
        final int links_depth = 3;
        final int max_distance = 10;
        final double link_delta = 0.01;

        StringBuilder log_out = new StringBuilder();
        log_out.append("\n evaluateSearchResult: \n");

        double weight = 0; // sum(type * position)
        for(int elem_i = 0; elem_i < sres.alTokens.size(); elem_i++) {
            log_out.append("\n----------\n elem_i = ").append(elem_i)
                    .append("; elem = ").append(sres.alTokens.get(elem_i));
            // type weight
            double w_type = sres.alTokenTypes.get(elem_i).getWeight()
                    * sres.alMatchTypes.get(elem_i).getWeight();
            /*
            if precise match - use weight of token type,
            else use weight of match type.
            but precise match weight == 1,
            and for other match types token can't be nothing else word (weight == 1).
            so can avoid checking conditions
             */
            log_out.append("\n w_type = ").append(w_type);

            // position weight
            double w_position = 1; // mult(link_n)
            if(w_type != 0) { // otherwise do not need to calculate (w_type * w_pos = 0)
                int elem_in_sq = sres.alTokenIndexInSQ.get(elem_i);
                if(elem_in_sq >= 0) { // present in sq
                    // links to left and right neighbors for given depth
                    for(int link_i = -links_depth; link_i <= links_depth; link_i++) {
                        if(link_i == 0) {
                            // link to current word itself, so should skip.
                            // it should not change weight even if calculated,
                            // so can use it to check if everything is calculated correct.
                            // but if some word appears more then 1 time,
                            // it will influence result reducing weight of all duplicates
                            // (except the last)
                            continue;
                        }
                        log_out.append("\n link_i = ").append(link_i);
                        // weight of link to cur neighbor
                        double w_link_n_nb = 1; // = coef ^ dist_diff
                        int elem_nb_in_sq = elem_in_sq + link_i; // neighbor in SQ
                        if(elem_nb_in_sq >= 0 && elem_nb_in_sq < sres.alSQTokens.size()) {
                            // present link of this depth
                            int dist_nb_sq = Math.abs(link_i); // by definition
                            int dist_dif = max_distance;
                            int elem_nb_sq_in_res = sres.alSQTokenIndexInRes.get(elem_nb_in_sq);
                            if(elem_nb_sq_in_res >= 0) { // present in res
                                int dist_nb_res = Math.abs(elem_nb_sq_in_res - elem_i);
                                log_out.append(" | dist_nb_res = ").append(dist_nb_res);
                                dist_dif = Math.min(Math.abs(dist_nb_res - dist_nb_sq), max_distance);
                            } // else - absent element - use max_distance
                            log_out.append(" | dist_dif = ").append(dist_dif);
                            double coef = (1.0 - link_delta) + (dist_nb_sq - 1.0) * (link_delta / links_depth);
                            log_out.append(" | coef = ").append(coef);
                            w_link_n_nb = Math.pow(coef, dist_dif);
                        } // else - absent link, ignore ( = 1 )
                        log_out.append(" | w_link_n_nb = ").append(w_link_n_nb);
                        w_position *= w_link_n_nb;
                        log_out.append(" | w_position = ").append(w_position);
                    }
                } else { // absent in sq
                    w_position = 0;
                }
            }
            log_out.append("\n w_position = ").append(w_position);
            log_out.append("\n w_type * w_position = ").append(w_type * w_position);
            weight += w_type * w_position;
        }
        log_out.append("\n weight = ").append(weight);
        sres.weight = weight;
        sres.evaluation_log = log_out.toString();
        //log.d(log_out.toString()); // better store in result
        return weight;
    }

    private static class SearchResultWeightComparator implements Comparator<SearchResult> {
        @Override
        public int compare(SearchResult lsr, SearchResult rsr) {
            return Double.compare(rsr.weight, lsr.weight); // from highest to lowest
        }
    }
    // it could also be sorted by occurrence or alphabetically..


    // returns new list (filtered, evaluated, sorted)
    private ArrayList<SearchResult> postProcessResults(ArrayList<SearchResult> al_search_res,
                                                       int max_res_count) {
        /*
         maybe removing duplicates before evaluation is more efficient, need to test.
         duplicates not always placed nearby after sorting by weights:
         the situation appears because of different letters case and
         for different words with same weight (and even for phrases with words with same weight).
         but if evaluate and sort before deleting duplicates, can check (compare)
         only results with same weight, not each with each.
        */

        // remove duplicates
        ArrayList<SearchResult> al_res_clean = new ArrayList<>();
        int duplicates_count = 0;
        for(int sr_i = 0; sr_i < al_search_res.size(); sr_i++) {
            /* do not delete extra results before sorting by it's weight!!
            if(al_res_clean.size() >= max_res_count) { // if zero will be empty..
                break;
            }
            */
            SearchResult cur_sr = al_search_res.get(sr_i);
            boolean duplicate = false;
            for(int sr_cl_i = 0; sr_cl_i < al_res_clean.size(); sr_cl_i++) {
                SearchResult cur_cl_sr = al_res_clean.get(sr_cl_i);
                if(case_sensitive_results ?
                        /* old way - compare lists.
                        cur_sr.alTokens.equals(cur_cl_sr.alTokens) :
                        BVJUtils.equalsIgnoreCase(cur_sr.alTokens, cur_cl_sr.alTokens)) {
                        */
                        cur_sr.phrase.equals(cur_cl_sr.phrase) :
                        cur_sr.phrase.equalsIgnoreCase(cur_cl_sr.phrase)) {
                    duplicate = true;
                    ++duplicates_count;
                    break;
                }
            }
            if(! duplicate) {
                al_res_clean.add(cur_sr);
            }
        }
        log.d("search res duplicates count == " + duplicates_count);

        for(int sres_i = 0; sres_i < al_res_clean.size(); sres_i++) {
            evaluateSearchResult(al_res_clean.get(sres_i));
        }
        Collections.sort(al_res_clean, new SearchResultWeightComparator());

        if(al_res_clean.size() > max_res_count) { // delete extra with least weight
            al_res_clean.subList(max_res_count, al_res_clean.size()).clear();
        }

        return al_res_clean;
    }

    /*
    OUTPUT:
    sorted by weight list of results
    */
    public ArrayList<SearchResult> search(String query, boolean wait_init, int max_res_count)
            throws NotInitializedException{
        log.d("IntellectualSearch.search() start");
        if(source_content == null) {
            throw new NotInitializedException("IntellectualSearch instance not initialized : " +
                    "source text not processed");
        }
        SearchQuery sque = procSearchQuery(wait_init, query);
        ArrayList<SearchResult> al_search_res = new ArrayList<>();
        // current search res data
        ArrayList<String> al_keywords_cur = new ArrayList<>();
        ArrayList<String> al_tokens_cur = new ArrayList<>();
        ArrayList<TokenType> al_tok_type_cur = new ArrayList<>();
        ArrayList<MatchType> al_match_type_cur = new ArrayList<>();
        ArrayList<Integer> al_tok_in_sq = new ArrayList<>();
        ArrayList<Integer> al_sq_tok_in_res = new ArrayList<>();
        for(int al_sq_i = 0; al_sq_i < sque.arTokens.length; al_sq_i++) {
            al_sq_tok_in_res.add(-1);
        }
        int complete_size = 0; // of current search res
        int tokens_aft_end = 0;
        final int tokens_aft_end_max = 3;
        // source sentences
        for(int sc_sent_i = 0; sc_sent_i < source_content.arSentTokens.length; sc_sent_i++) {
            // source sentence tokens
            for(int sc_word_i = 0; sc_word_i < source_content.arSentTokens[sc_sent_i].length; sc_word_i++) {
                String sc_tok_cur = source_content.arSentTokens[sc_sent_i][sc_word_i];
                TokenType sc_tok_type_cur = source_content.arSentTokenTypes[sc_sent_i][sc_word_i];
                String sc_stem_cur = source_content.arSentStems[sc_sent_i][sc_word_i];
                boolean match_found = false;
                // query tokens
                for(int sq_word_i = 0; sq_word_i < sque.arTokens.length; sq_word_i++) {
                    String sq_tok_cur = sque.arTokens[sq_word_i];
                    String sq_stem_cur = sque.arStems[sq_word_i];
                    TreeSet<String> sq_synset_cur = sque.arSynsetStemsSet[sq_word_i];
                    // matching cur word
                    if(equalsIgnoreCaseNotNullEmpty(sc_tok_cur, sq_tok_cur)) {
                        match_found = true;
                        // top layer (tokens) - precise match
                        al_match_type_cur.add(MatchType.PreciseMatch);
                        al_tok_in_sq.add(sq_word_i);
                        al_sq_tok_in_res.set(sq_word_i, al_match_type_cur.size() - 1);
                        break;
                    } else if(equalsIgnoreCaseNotNullEmpty(sc_stem_cur, sq_stem_cur)) {
                        match_found = true;
                        // lower layer - stems
                        al_match_type_cur.add(MatchType.StemMatch);
                        al_tok_in_sq.add(sq_word_i);
                        al_sq_tok_in_res.set(sq_word_i, al_match_type_cur.size() - 1);
                        break;
                    } else if( (sq_synset_cur != null) && (! sq_synset_cur.isEmpty())
                            && sq_synset_cur.contains(sc_stem_cur)) {
                        match_found = true;
                        // lower layer - synsets
                        al_match_type_cur.add(MatchType.SynsetMatch);
                        al_tok_in_sq.add(sq_word_i);
                        al_sq_tok_in_res.set(sq_word_i, al_match_type_cur.size() - 1);
                        break;
                    }
                    /*
                    if(match_found) { // for cur word query matched
                        break; // now useless
                    } */
                } // query

                if(match_found) {
                    if(sc_tok_type_cur == TokenType.Word) { // not punct not stopword
                        al_keywords_cur.add(sc_tok_cur);
                    }
                    complete_size = al_match_type_cur.size();
                    tokens_aft_end = 0; // new ending
                } else { // query reviewed, no matches
                    if (complete_size > 0) { // result not empty
                        al_match_type_cur.add(MatchType.NotMatch);
                        al_tok_in_sq.add(-1);
                        // too much noise puncts sometimes
                        //if(sc_tok_type_cur != TokenType.Punct) {
                        tokens_aft_end++;
                        //}
                    }
                }

                // match found or result not empty
                if(complete_size > 0) {
                    // copy current to res
                    al_tokens_cur.add(sc_tok_cur);
                    al_tok_type_cur.add(sc_tok_type_cur);

                    // form result
                    if(tokens_aft_end > tokens_aft_end_max // too much mismatch
                            // or current is last element
                            || (sc_sent_i == source_content.arSentTokens.length - 1
                            && sc_word_i == source_content.arSentTokens[sc_sent_i].length - 1)) {
                        SearchResult s_res = new SearchResult();
                        // trim extra words from end
                        al_tokens_cur.subList(complete_size, al_tokens_cur.size()).clear();
                        al_tok_type_cur.subList(complete_size, al_tok_type_cur.size()).clear();
                        al_match_type_cur.subList(complete_size, al_match_type_cur.size()).clear();
                        al_tok_in_sq.subList(complete_size, al_tok_in_sq.size()).clear();
                        // al_keywords_cur // do not trim, it has other size

                        s_res.alTokens = al_tokens_cur;
                        s_res.alTokenTypes = al_tok_type_cur;
                        s_res.alMatchTypes = al_match_type_cur;
                        s_res.alKeywords = al_keywords_cur;
                        s_res.alTokenIndexInSQ = al_tok_in_sq;
                        s_res.alSQTokenIndexInRes = al_sq_tok_in_res;
                        // maybe do not copy, create unmodifiableList only once and save ref
                        // but it will be UnmodifiableList -> List, not ArrayList
                        s_res.alSQTokens = new ArrayList<>(Arrays.asList(sque.arTokens));

                        /*
                        how to build precise phrase? but first find out why not all search results are correctly
                        highlighted (problem in phrase or in js function, or other).
                        - !! spans indexes instead of cut sentences/tokens
                        - search in appropriate sentence for found tokens
                            (by regexp: <concrete word> <ws*> <concrete word> .. ).
                            but in this case search results maybe should be limited by sentence bounds,
                            because trying to restore text from sentences is another unwanted task
                        */
                        StringBuilder sb_phrase = new StringBuilder();
                        for(int tok_i = 0; tok_i < al_tokens_cur.size(); tok_i++) {
                            sb_phrase.append(al_tokens_cur.get(tok_i));
                            if( !(tok_i + 1 >= al_tokens_cur.size())
                                    && (al_tok_type_cur.get(tok_i + 1) != TokenType.Punct)) {
                                sb_phrase.append(" "); // ws
                            }
                        }
                        s_res.phrase = sb_phrase.toString();
                        s_res.weight = 0;
                        al_search_res.add(s_res);

                        // clean
                        al_tokens_cur = new ArrayList<>();
                        al_tok_type_cur = new ArrayList<>();
                        al_match_type_cur = new ArrayList<>();
                        al_keywords_cur = new ArrayList<>();
                        al_tok_in_sq = new ArrayList<>();
                        al_sq_tok_in_res = new ArrayList<>(); // create new, do not use ref to prev
                        for(int al_sq_i = 0; al_sq_i < sque.arTokens.length; al_sq_i++) {
                            al_sq_tok_in_res.add(-1);
                        }
                        complete_size = 0;
                        tokens_aft_end = 0;
                    }
                }
            } // words
        } // sents
        //---------------
        log.d("IntellectualSearch.search(): All results count: " + al_search_res.size());

        al_search_res = postProcessResults(al_search_res, max_res_count);

        // debug:
        StringBuilder log_out = new StringBuilder();
        log.d("IntellectualSearch.search(): Final results count: " + al_search_res.size());
        for(int s_res_i = 0; s_res_i < al_search_res.size(); s_res_i++) {
            SearchResult sr_cur = al_search_res.get(s_res_i);
            ArrayList<String> al_tok_cur_res = sr_cur.alTokens;
            log_out.append("\n ---------- \n ********** \n Search Result #").append(s_res_i)
                    .append("\n Tokens : ").append(BVJUtils.toString(al_tok_cur_res, null))
                    .append("\n TokenTypes : ").append(sr_cur.alTokenTypes)
                    .append("\n MatchTypes : ").append(sr_cur.alMatchTypes)
                    .append("\n Keywords : ").append(sr_cur.alKeywords)
                    .append("\n TokenIndexInSQ : ").append(sr_cur.alTokenIndexInSQ)
                    .append("\n SQTokenIndexInRes : ").append(sr_cur.alSQTokenIndexInRes)
                    .append("\n Phrase : ").append(sr_cur.phrase)
                    .append("\n Weight : ").append(sr_cur.weight);
                    //.append("\n Evaluation log : ").append(sr_cur.evaluation_log); // too verbose
        }
        log.d(log_out.toString());
        //------------

        log.d("IntellectualSearch.search() done");
        return al_search_res;
    }
}