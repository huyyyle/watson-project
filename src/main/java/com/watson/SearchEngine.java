package com.watson;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryRescorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

import io.github.crew102.rapidrake.RakeAlgorithm;
import io.github.crew102.rapidrake.data.SmartWords;
import io.github.crew102.rapidrake.model.RakeParams;
import io.github.crew102.rapidrake.model.Result;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.IndexWordSet;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.dictionary.Dictionary;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.opennlp.OpenNLPLemmatizerFilterFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;

/*
 * This class is used to search through our indexed documents using 
 * Lucene's IndexSearcher. The class holds a variety of analyzers 
 * for different use cases.
 */
public class SearchEngine {
    private IndexSearcher searcher;
    private Analyzer analyzerV1;
    private Analyzer analyzerV2;
    private Analyzer analyzerV3;
    private Dictionary dict;
    Word2Vec vec;
    public SearchEngine(String index_name) throws IOException, JWNLException {
        searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File("indicies/" + index_name).toPath())));

        analyzerV1 = new StandardAnalyzer();
        analyzerV2 = new EnglishAnalyzer();
        analyzerV3 = CustomAnalyzer.builder()
            .withTokenizer("standard")
            .addTokenFilter("lowercase")
            .addTokenFilter("stop")
            .addTokenFilter("englishPossessive")
            .addTokenFilter(OpenNLPLemmatizerFilterFactory.class, "dictionary", "en-lemmatizer.dict", "lemmatizerModel", "en-lemmatizer.bin")
            .build();

        dict = Dictionary.getDefaultResourceInstance();
        vec = WordVectorSerializer.readWord2VecModel("pathToSaveModel.txt");
        }

    /**
     * Simple search using the standard analyzer.
     * @param query : The query itself
     * @param n : Used to determine how many documents to return (n = 3; top 3 documents)
     * @return : Top n documents
     * @throws Exception
     */
    public ArrayList<Document> searchV1(String query, int n) throws Exception {
        QueryParser parser = new QueryParser("content", analyzerV1);
        TopDocs results = searcher.search(parser.parse(query), n);
        ArrayList<Document> documents = new ArrayList<Document>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
        }
        return documents;
    }

    /**
     * Basically V1, but with a different similarity function.
     * @param query : The query itself
     * @param n : Used to determine how many documents to return (n = 3; top 3 documents)
     * @return : Top n documents
     * @throws Exception
     */
    public ArrayList<Document> searchV1_1(String query, int n) throws Exception {
        LMDirichletSimilarity lmd = new LMDirichletSimilarity();
        searcher.setSimilarity(lmd);

        QueryParser parser = new QueryParser("content", analyzerV1);
        TopDocs results = searcher.search(parser.parse(query), n);
        ArrayList<Document> documents = new ArrayList<Document>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
        }
        return documents;
    }

    /**
     * Simple search using the English analyzer.
     * @param query : The query itself
     * @param n : Used to determine how many documents to return (n = 3; top 3 documents)
     * @return : Top n documents
     * @throws Exception
     */
    public ArrayList<Document> searchV2(String query, int n) throws Exception {
        QueryParser parser = new QueryParser("content", analyzerV2);
        TopDocs results = searcher.search(parser.parse(query), n);
        ArrayList<Document> documents = new ArrayList<Document>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
        }
        return documents;
    }

    /**
     * Same as original, but recalcultes scores for capital queries.
     * @param query : The query itself
     * @param n : Used to determine how many documents to return (n = 3; top 3 documents)
     * @return : Top n documents
     * @throws Exception
     */
    public ArrayList<Document> searchV2_1(String query, int n) throws Exception {
        //if query string contains the word "capital"
        int oldN = n;
        if (n < 5) {
            n = 5;}

        QueryParser parser = new QueryParser("content", analyzerV2);
        TopDocs results = searcher.search(parser.parse(query), n);
        ArrayList<Document> documents = new ArrayList<Document>();
        

        if (query.toLowerCase().contains("capital")) {
            //get all capitals from capitalCities.txt as a string
            String capitalCities = new String(Files.readAllBytes(Paths.get("dataset/capitalCities.txt")));
            //0.22 weight is a magic number. Best number that works for the dataset. Overfitting?
            results = QueryRescorer.rescore(searcher, results, parser.parse(capitalCities), 0.22, n);
        }

        int count = 0;
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
            count++;
            if (count == oldN) {
                break;
            }
        }

        return documents;
    }

    /**
     * Same as original, but recalcultes scores for capital queries and uses a different similarity function.
     * @param query : The query itself
     * @param n : Used to determine how many documents to return (n = 3; top 3 documents)
     * @return : Top n documents
     * @throws Exception
     */
    public ArrayList<Document> searchV2_2(String query, int n) throws Exception {
        //if query string contains the word "capital"
        int oldN = n;
        if (n < 5) {
            n = 5;}

        LMDirichletSimilarity classic = new LMDirichletSimilarity();
        searcher.setSimilarity(classic);

        QueryParser parser = new QueryParser("content", analyzerV2);
        TopDocs results = searcher.search(parser.parse(query), n);
        ArrayList<Document> documents = new ArrayList<Document>();
        

        if (query.toLowerCase().contains("capital")) {
            //get all capitals from capitalCities.txt as a string
            String capitalCities = new String(Files.readAllBytes(Paths.get("dataset/capitalCities.txt")));
            //0.22 weight is a magic number. Used to be 0.22, but now 1 works better with LMD.
            results = QueryRescorer.rescore(searcher, results, parser.parse(capitalCities), 1, n);
        }

        int count = 0;
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
            count++;
            if (count == oldN) {
                break;
            }
        }

        return documents;
    }

    /**
     * Uses a different similarity function and tries to rescore the query using keywords.
     * @param query : The query itself
     * @param topic : Takes in the topic of the query
     * @param n : Used to determine how many documents to return (n = 3; top 3 documents)
     * @return Top n documents
     * @throws Exception
     */
    public ArrayList<Document> searchV2_3(String query, String topic, int n) throws Exception {
        //if query string contains the word "capital"
        int oldN = n;
        if (n < 5) {
            n = 5;}

        LMDirichletSimilarity classic = new LMDirichletSimilarity();
        searcher.setSimilarity(classic);

        String preQuery = queryBuilderV2(query, topic);

        QueryParser parser = new QueryParser("content", analyzerV2);
        TopDocs results = searcher.search(parser.parse(preQuery), n);
        ArrayList<Document> documents = new ArrayList<Document>();
        

        if (query.toLowerCase().contains("capital")) {
            //get all capitals from capitalCities.txt as a string
            String capitalCities = new String(Files.readAllBytes(Paths.get("dataset/capitalCities.txt")));
            //1 weight is a magic number. Best number that works for the dataset. Overfitting?
            results = QueryRescorer.rescore(searcher, results, parser.parse(capitalCities), 1, n);
        }

        results = QueryRescorer.rescore(searcher, results, parser.parse(modelExpansion(preQuery, 2)), 0.18, n);

        int count = 0;
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
            count++;
            if (count == oldN) {
                break;
            }
        }

        return documents;
    }

    /**
     * Simple search using the custom analyzer (with lemmatization).
     * @param query : The query itself
     * @param n : Used to determine how many documents to return (n = 3; top 3 documents)
     * @return : Top n documents
     * @throws Exception
     */
    public ArrayList<Document> searchV3(String query, int n) throws Exception {
        QueryParser parser = new QueryParser("content", analyzerV3);

        TopDocs results = searcher.search(parser.parse(query), n);
        ArrayList<Document> documents = new ArrayList<Document>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            documents.add(searcher.doc(scoreDoc.doc));
        }
        return documents;
    }

    /**
     * Use the RAKE algorithm to extract keywords from the query and use them to expand the query.
     * @param query : The query itself
     * @return : A string that contains important keywords based on the query
     * @throws IOException
     */
    public String keywordExtract(String query) throws IOException {
        // Create an object to hold algorithm parameters
        String[] stopWords = new SmartWords().getSmartWords(); 
        String[] stopPOS = {"VB", "VBD", "VBG", "VBN", "VBP", "VBZ"}; 
        int minWordChar = 1;
        boolean shouldStem = true;
        String phraseDelims = "[-,.?():;\"!/]"; 
        RakeParams params = new RakeParams(stopWords, stopPOS, minWordChar, shouldStem, phraseDelims);
        
        // Create a RakeAlgorithm object
        String POStaggerURL = "NLPmodels/en-pos-maxent.bin"; // The path to your POS tagging model
        String SentDetecURL = "NLPmodels/en-sent.bin"; // The path to your sentence detection model
        RakeAlgorithm rakeAlg = new RakeAlgorithm(params, POStaggerURL, SentDetecURL);
        
        // Call the rake method
        Result result = rakeAlg.rake(query);
        String[] keywords = result.getFullKeywords();

        String keywordString = "";
        for (String keyword : keywords) {
            keywordString += keyword + " ";
        }
        return keywordString;
    }

    /**
     * Use the OpenNLP library to extract names from the query and use them to expand the query.
     * @param query : The query itself
     * @return : A string that contains important names based on the query
     * @throws IOException
     */
    public String personNameExtract(String query) throws IOException {
        //Loading the tokenizer model 
        InputStream inputStreamTokenizer = new FileInputStream("NLPmodels/en-token.bin");
        TokenizerModel tokenModel = new TokenizerModel(inputStreamTokenizer); 
        //Instantiating the TokenizerME class 
        TokenizerME tokenizer = new TokenizerME(tokenModel); 
        //Tokenizing the sentence in to a string array 
        String tokens[] = tokenizer.tokenize(query); 
        //Loading the NER-person model 
        InputStream inputStreamNameFinder = new FileInputStream("NLPmodels/en-ner-person.bin");       
        TokenNameFinderModel model = new TokenNameFinderModel(inputStreamNameFinder);
        //Instantiating the NameFinderME class 
        NameFinderME nameFinder = new NameFinderME(model);       
        //Finding the names in the sentence 
        Span nameSpans[] = nameFinder.find(tokens);        
        String retval = "";
        //Printing the names and their spans in a sentence 
        for(Span s: nameSpans)        
            retval += tokens[s.getStart()] + " ";
        return retval;
    }

    /**
     * Use the OpenNLP library to extract locations from the query and use them to expand the query.
     * @param query : The query itself
     * @return : A string that contains important locations based on the query
     * @throws IOException
     */
    public String locationNameExtract(String query) throws IOException {
        //Loading the tokenizer model 
        InputStream inputStreamTokenizer = new FileInputStream("NLPmodels/en-token.bin");
        TokenizerModel tokenModel = new TokenizerModel(inputStreamTokenizer); 
        //Instantiating the TokenizerME class 
        TokenizerME tokenizer = new TokenizerME(tokenModel); 
        //Tokenizing the sentence in to a string array 
        String tokens[] = tokenizer.tokenize(query); 
        //Loading the NER-person model 
        InputStream inputStreamNameFinder = new FileInputStream("NLPmodels/en-ner-location.bin");       
        TokenNameFinderModel model = new TokenNameFinderModel(inputStreamNameFinder);
        //Instantiating the NameFinderME class 
        NameFinderME nameFinder = new NameFinderME(model);       
        //Finding the names in the sentence 
        Span nameSpans[] = nameFinder.find(tokens);        
        String retval = "";
        //Printing the names and their spans in a sentence 
        for(Span s: nameSpans)        
            retval += tokens[s.getStart()] + " ";
        return retval;
    }

    /**
     * Use the OpenNLP library to extract organizations from the query and use them to expand the query.
     * @param query : The query itself
     * @return : A string that contains important organizations based on the query
     * @throws IOException
     */
    public String organizationNameExtract(String query) throws IOException {
        //Loading the tokenizer model 
        InputStream inputStreamTokenizer = new FileInputStream("NLPmodels/en-token.bin");
        TokenizerModel tokenModel = new TokenizerModel(inputStreamTokenizer); 
        //Instantiating the TokenizerME class 
        TokenizerME tokenizer = new TokenizerME(tokenModel); 
        //Tokenizing the sentence in to a string array 
        String tokens[] = tokenizer.tokenize(query); 
        //Loading the NER-person model 
        InputStream inputStreamNameFinder = new FileInputStream("NLPmodels/en-ner-organization.bin");       
        TokenNameFinderModel model = new TokenNameFinderModel(inputStreamNameFinder);
        //Instantiating the NameFinderME class 
        NameFinderME nameFinder = new NameFinderME(model);       
        //Finding the names in the sentence 
        Span nameSpans[] = nameFinder.find(tokens);        
        String retval = "";
        //Printing the names and their spans in a sentence 
        for(Span s: nameSpans)        
            retval += tokens[s.getStart()] + " ";
        return retval;
    }

    /**
     * Use the OpenNLP library to extract organizations from the query and use them to expand the query.
     * @param query : The query itself
     * @return : A string that contains important dates based on the query
     * @throws IOException
     */
    public String dateExtract(String query) throws IOException {
        //Loading the tokenizer model 
        InputStream inputStreamTokenizer = new FileInputStream("NLPmodels/en-token.bin");
        TokenizerModel tokenModel = new TokenizerModel(inputStreamTokenizer); 
        //Instantiating the TokenizerME class 
        TokenizerME tokenizer = new TokenizerME(tokenModel); 
        //Tokenizing the sentence in to a string array 
        String tokens[] = tokenizer.tokenize(query); 
        //Loading the NER-person model 
        InputStream inputStreamNameFinder = new FileInputStream("NLPmodels/en-ner-date.bin");       
        TokenNameFinderModel model = new TokenNameFinderModel(inputStreamNameFinder);
        //Instantiating the NameFinderME class 
        NameFinderME nameFinder = new NameFinderME(model);       
        //Finding the names in the sentence 
        Span nameSpans[] = nameFinder.find(tokens);        
        String retval = "";
        //Printing the names and their spans in a sentence 
        for(Span s: nameSpans)        
            retval += tokens[s.getStart()] + " ";
        return retval;
    }

    /**
     * Used wordnet to expand the query.
     * @param query : The query itself
     * @param cap : User inputs how many synonyms they want to return
     * @return : A string of synonyms based off the query 
     * @throws IOException
     */
    public String synonymExpansion(String query, int cap) throws IOException, JWNLException {
        String retVal = "";
        String[] words = query.split(" ");
                
        for (int i = 0; i < words.length; i++) {
            IndexWordSet temp = null;
            IndexWord[] temp2 = null;

            temp = dict.lookupAllIndexWords(words[i]); 
            temp2 = temp.getIndexWordArray();

            for (int j = 0; j < 1; j++) {
                // How many synonyms we want to return
                try{IndexWord found = synonymExpansionHelper(temp2[j], words[i]);
                PointerTargetNodeList hypernyms = PointerUtils.getDirectHypernyms(found.getSenses().get(0));
                String str = hypernyms.toString();
                String substring = str.substring(str.indexOf("Words: ") + 7, str.indexOf(" --"));
                String[] synonymWords = substring.split(", ");

                // In case the cap is too high for the amount of synonyms there are
                if (synonymWords.length < cap) {cap = synonymWords.length;}

                // Iterate through the list of synonyms
                for (int k = 0; k < cap; k++) {
                    retVal += synonymWords[k] + " ";
                }
                } catch(Exception e) {}
            }
        }
        
        return retVal;
    }

    /**
     * Used word2vec to expand the query.
     * @param query : The query itself
     * @param n : User can change how many related words to return per word in the query
     * @return : A string of all related words
     * @throws IOException
     */
    public String modelExpansion(String query, int n) throws FileNotFoundException {
        query = query.toLowerCase();
        String retVal = "";
        String[] words = query.split(" " );
        for (int i = 0; i < words.length; i++) {
            Collection<String> nst = vec.wordsNearest(words[i], n);
            for (String word : nst) {
                retVal += word + " ";
            }
        }
        return retVal;
    }

    /**
     * Used word2vec to expand the query.
     * @param word : The word that we want to find similar terms for
     * @param n : How many words to return based on user input
     * @return : A string of n related words based on word
     * @throws IOException
     */
    public String wordsNearest(String word, int n) {
        Collection<String> lst = vec.wordsNearest(word, n);
        return lst.toString();
    }

    public IndexWord synonymExpansionHelper(IndexWord indexWord, String word) throws JWNLException {
        return dict.lookupIndexWord(indexWord.getPOS(), word);
    }

    /**
     * Below are several ways we have build the query string. 
     */

    /**
     * Simplest query builder, just adds the topic to the query.
     * @param query : The query itself 
     * @param topic : The topic of the current question/query
     * @return - We end up returning the concatenation of the query and topic
     * @throws IOException
     */
    public String queryBuilderV1(String query, String topic) throws IOException {
        return query + " " + topic;
    }

    /**
     * Adds the topic and the keywords to the query.
     * @param query : The query itself 
     * @param topic : The topic of the current question/query
     * @return - We end up returning the concatenation of the query and topic
     * @throws IOException
     */
    public String queryBuilderV2(String query, String topic) throws IOException {
        return query + " " + topic + " " + keywordExtract(query);
    }

    /**
     * Adds the topic, keywords, and synonyms to the query.
     * @param query : The query itself 
     * @param topic : The topic of the current question/query
     * @return - We end up returning the concatenation of the query and topic
     * @throws IOException
     */
    public String queryBuilderV3(String query, String topic) throws IOException, JWNLException {
        return query + " " + topic + " " + keywordExtract(query) + modelExpansion(query, 1);
    }
}
