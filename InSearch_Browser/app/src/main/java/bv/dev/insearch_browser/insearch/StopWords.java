package bv.dev.insearch_browser.insearch;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * Created by Belyak V. on 28.12.2015.
 *
 */
public class StopWords {
    /* see
    http://www.ranks.nl/stopwords
    http://www.textfixer.com/resources/common-english-words.txt
    http://library.stanford.edu/blogs/digital-library-blog/2011/12/stopwords-searchworks-be-or-not-be

    !!!
    http://www.ranks.nl/stopwords/russian
    and oth
     */

    private static final String[] ar_sw_general = {"a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren\'t", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can\'t", "cannot", "could", "couldn\'t", "did", "didn\'t", "do", "does", "doesn\'t", "doing", "don\'t", "down", "during", "each", "few", "for", "from", "further", "had", "hadn\'t", "has", "hasn\'t", "have", "haven\'t", "having", "he", "he\'d", "he\'ll", "he\'s", "her", "here", "here\'s", "hers", "herself", "him", "himself", "his", "how", "how\'s", "i", "i\'d", "i\'ll", "i\'m", "i\'ve", "if", "in", "into", "is", "isn\'t", "it", "it\'s", "its", "itself", "let\'s", "me", "more", "most", "mustn\'t", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours 	ourselves", "out", "over", "own", "same", "shan\'t", "she", "she\'d", "she\'ll", "she\'s", "should", "shouldn\'t", "so", "some", "such", "than", "that", "that\'s", "the", "their", "theirs", "them", "themselves", "then", "there", "there\'s", "these", "they", "they\'d", "they\'ll", "they\'re", "they\'ve", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasn\'t", "we", "we\'d", "we\'ll", "we\'re", "we\'ve", "were", "weren\'t", "what", "what\'s", "when", "when\'s", "where", "where\'s", "which", "while", "who", "who\'s", "whom", "why", "why\'s", "with", "won\'t", "would", "wouldn\'t", "you", "you\'d", "you\'ll", "you\'re", "you\'ve", "your", "yours", "yourself", "yourselves"};
    //private static String[] ar_sw_google_old = {"I", "a", "about", "an", "are", "as", "at", "be", "by", "com", "for", "from", "how", "in", "is", "it", "of", "on", "or", "that", "the", "this", "to", "was", "what", "when", "where", "who", "will", "with", "the", "www"};
    private static final TreeSet<String> set_sw = new TreeSet<>(Arrays.asList(ar_sw_general));

    public static boolean isStopWord(String word) {
        return set_sw.contains(word.toLowerCase().trim());
    }

}

