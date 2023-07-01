import java.io.*;
import java.nio.file.Paths;
import java.util.Date;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import static org.apache.lucene.analysis.en.EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

public class IndexNPL {

    public static void main(String[] args) {

        String usage = "Usage: IndexNPL [-openmode OPENMODE] [-index PATHNAME] [-docs PATHNAME] [-indexingmodel JM | DIR] [-analyzer ANALYZER]";
        String openmodeStr = "create_or_append";
        String indexDirname = "index";
        String docsDirname = null;
        String indexingModelStr = "jm";
        String analyzerStr = "standard";
        float lambdaormu = 0.1f;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-openmode":
                    openmodeStr = args[++i];
                    break;
                case "-index":
                    indexDirname = args[++i];
                    break;
                case "-docs":
                    docsDirname = args[++i];
                    break;
                case "-indexingmodel":
                    indexingModelStr = args[++i];
                    lambdaormu = Float.parseFloat(args[++i]);
                    break;
                case "-analyzer":
                    analyzerStr = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if (docsDirname == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        OpenMode openmode = OpenMode.CREATE_OR_APPEND;
        switch (openmodeStr) {
            case "append":
                openmode = OpenMode.APPEND;
                break;
            case "create":
                openmode = OpenMode.CREATE;
                break;
            case "create_or_append":
                break;
            default:
                System.err.println("Unknown openmode: " + openmodeStr);
                System.exit(1);
        }

        Similarity indexingModel = null;
        if (indexingModelStr.equals("jm")) {
            indexingModel = new LMJelinekMercerSimilarity(lambdaormu);
        } else if (indexingModelStr.equals("dir")) {
            indexingModel = new LMDirichletSimilarity(lambdaormu);
        } else {
            System.err.println("Unknown indexing model: " + indexingModelStr);
            System.exit(1);
        }

        Analyzer analyzer = AnalyzerSelector(analyzerStr);

        Date init = new Date();

        try {
            System.out.println("Indexing to directory '" + indexDirname + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexDirname));
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(openmode); // Set the OpenMode
            iwc.setSimilarity(indexingModel); // Set the Similarity (indexing model)

            BufferedReader bufferedReader = new BufferedReader(new FileReader(docsDirname));

            String contents = bufferedReader.lines().collect(Collectors.joining("\n"));
            bufferedReader.close();
            String[] subContents = contents.split("\\s*/\n"); // Adjusted split pattern
            IndexWriter iWriter = new IndexWriter(dir, iwc);

            for (int i = 0; i < subContents.length; i++) {
                String docContent = subContents[i].trim(); // Trim leading and trailing whitespace
                if (!docContent.isEmpty()) {
                    indexDoc(iWriter, docContent, i + 1);
                }
            }

            iWriter.close();

            Date end = new Date();
            try (IndexReader reader = DirectoryReader.open(dir)) {
                System.out.println("Indexed " + reader.numDocs() + " documents in " + (end.getTime() - init.getTime())
                        + " milliseconds");
            }

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }
    }
    static void indexDoc(IndexWriter writer, String subFileCont, int subFileDocID) throws IOException {
        Document doc = new Document();

        doc.add(new StringField("docIDNpl", String.valueOf(subFileDocID), Field.Store.YES));

        doc.add(new TextField("contents", subFileCont.substring(String.valueOf(subFileDocID).length()), Field.Store.YES));

        if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            System.out.println("adding " + "doc" + subFileDocID);
            writer.addDocument(doc);
        } else {
            System.out.println("updating " + "doc" + subFileDocID);
            writer.updateDocument(new Term("path", "doc" + subFileDocID), doc);
        }
    }

    public static Analyzer AnalyzerSelector( String analyzerStr) {
        Analyzer analyzer = new StandardAnalyzer();
        switch (analyzerStr) {
            case "standard":
                break;
            case "whitespace":
                analyzer = new WhitespaceAnalyzer();
                break;
            case "simple":
                analyzer = new SimpleAnalyzer();
                break;
            case "stop":
                analyzer = new StopAnalyzer(ENGLISH_STOP_WORDS_SET);
                break;
            case "keyword":
                analyzer = new KeywordAnalyzer();
                break;
            case "english":
                analyzer = new EnglishAnalyzer();
                break;
            case "french":
                analyzer = new FrenchAnalyzer();
                break;
            case "spanish":
                analyzer = new SpanishAnalyzer();
                break;
            case "german":
                analyzer = new GermanAnalyzer();
                break;
            case "italian":
                analyzer = new ItalianAnalyzer();
                break;
            default:
                System.err.println("Unknown analyzer: " + analyzerStr);
                System.exit(1);
                break;
        }
        return analyzer;
    }
}
