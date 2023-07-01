
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.lucene.analysis.en.EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

public class SearchEvalNPL {
    public static void main(String[] args) {
        String usage = "java org.apache.lucene.demo.SearchEvalMedline"
                + " [-search jm lambda | dir mu] [-indexing pathname] [-cut n] [-top m] "
                + "[-queries all | int1 | int1-int2]";
        String queries = "all";
        String indexing = "./index";
        String indexingModelStr = null;
        int cut = 0, top = 0;
        float lambdaormu = 0;
        String analyzerStr = "standard";
        String docsDirName = "./npl/query-text";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-indexing":
                    indexing = args[++i];
                    break;
                case "-cut":
                    cut = Integer.parseInt(args[++i]);
                    break;
                case "-top":
                    top = Integer.parseInt(args[++i]);
                    break;
                case "-queries": {
                    queries = args[++i];
                    break;
                }
                case "-search": {
                    indexingModelStr = args[++i];
                    lambdaormu = Float.parseFloat(args[++i]);
                    if (!(indexingModelStr.equals("jm") || indexingModelStr.equals("dir"))) {
                        throw new IllegalArgumentException("Search incorrecto: jm lambda, dir mu");
                    }
                    break;
                }
                case "-analyzer": {
                    analyzerStr = args[++i];
                    break;
                }
                case "-docs": {
                    docsDirName = args[++i];
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if (indexing == null || queries == null || indexingModelStr == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        Analyzer analyzer = AnalyzerSelector(analyzerStr);

        try {
            Path path = Paths.get(indexing);
            Directory indexPath = FSDirectory.open(path);
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            String modelName = null;
            LMJelinekMercerSimilarity jm = null;
            LMDirichletSimilarity dr = null;
            boolean jelinek = true;
            switch (indexingModelStr) {
                case "jm":
                    modelName = "lambda." + lambdaormu;
                    jm = new LMJelinekMercerSimilarity(lambdaormu);
                    iwc.setSimilarity(jm);
                    break;
                case "dir":
                    jelinek = false;
                    modelName = "mu." + (int) lambdaormu;
                    dr = new LMDirichletSimilarity(lambdaormu);
                    iwc.setSimilarity(dr);
                    break;
                default:
                    System.out.println("Search incorrecto");
                    System.exit(1);
            }
            IndexReader reader = DirectoryReader.open(indexPath);
            IndexSearcher searcher = new IndexSearcher(reader);
            if (jelinek) {
                searcher.setSimilarity(jm);
            } else {
                searcher.setSimilarity(dr);
            }
            QueryParser queryParser = new QueryParser("contents", analyzer);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(docsDirName));
            String contents = bufferedReader.lines().collect(Collectors.joining("\n"));
            bufferedReader.close();

            String[] subQueries = contents.split("/");
            for (int i = 0; i < subQueries.length; i++) {
                subQueries[i] = subQueries[i].replaceAll("\\r\\n|\\r|\\n", " ").trim();
            }

            //Interprete de queries
            int ini;
            int fin;
            if (queries.equals("all")) {
                ini = 1;
                fin = subQueries.length;
            } else {
                if (queries.contains("-")) {
                    String[] limits = queries.split("-");
                    ini = Integer.parseInt(limits[0]);
                    fin = Integer.parseInt(limits[1]);
                } else {
                    ini = Integer.parseInt(queries);
                    fin = ini + 1;
                }
            }

            //Archivo txt
            File outputFile = new File("npl." + indexingModelStr + "." + cut + ".hits." + modelName + ".q" + queries + ".txt");
            FileWriter writer = new FileWriter(outputFile);
            BufferedWriter buf = new BufferedWriter(writer);

            //Archivo csv
            File csv = new File("npl." + indexingModelStr + "." + cut + ".cut." + modelName + ".q" + queries + ".csv");
            FileWriter writerCsv = new FileWriter(csv);
            BufferedWriter bufCsv = new BufferedWriter(writerCsv);

            bufCsv.write("queryID, P@" + cut + ", Recall@n" + cut + ", RR" + cut + ", AP@n" + cut + "\n");
            HashMap<Integer, ArrayList<Integer>> queryRelevance = parseQueryRelevance("./npl/rlv-ass");

            int sumP = 0, acumP = 0, acumRec = 0, acumRR = 0, acumMap = 0;
            for (int i = ini; i < fin; i++) {
                int relevantRetrieved = 0;
                float sum = 0;
                IndexReader resultsReader = DirectoryReader.open(FSDirectory.open(path));
                subQueries[i] = subQueries[i].toLowerCase();
                TopDocs topDocs = searcher.search(queryParser.parse(subQueries[i]), Math.max(top, cut));

                ArrayList<Integer> relevantDocsID = queryRelevance.get(i);

                int firstRelevantRank = 0;

                for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                    if (relevantDocsID.contains(topDocs.scoreDocs[j].doc)) {
                        relevantRetrieved++;

                        if (firstRelevantRank == 0) {
                            firstRelevantRank = j + 1;
                        }

                        sumP += (float) relevantRetrieved / (j + 1);
                    }
                }

                float precision = (float) relevantRetrieved / topDocs.scoreDocs.length;
                float recall = (float) relevantRetrieved / relevantDocsID.size();
                float rr = (firstRelevantRank > 0) ? 1.0f / firstRelevantRank : 0;
                float apn = (relevantRetrieved > 0) ? (float) sumP / relevantRetrieved : 0;

                bufCsv.write(i + ",");
                bufCsv.write(String.format("%.5f", precision) + ",");
                bufCsv.write(String.format("%.5f", recall) + ",");
                bufCsv.write(String.format("%.5f", rr) + ",");
                bufCsv.write(String.format("%.5f", apn));

                System.out.println("Procesando query: " + i);
                buf.write("Procesando query: " + i + "\n");
                for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                    System.out.println("-> Documento " + topDocs.scoreDocs[j].doc + ":");
                    buf.write("-> Documento " + topDocs.scoreDocs[j].doc + ":" + "\n");
                    System.out.println("\tCampos indice:");
                    buf.write("\tCampos indice:" + "\n");
                    List<IndexableField> campos = resultsReader.document(topDocs.scoreDocs[j].doc).getFields();
                    for (IndexableField campo : campos) {
                        System.out.println("\t" + campo.name() + " = " + campo.stringValue());
                        buf.write("\t" + campo.name() + " = " + campo.stringValue() + "\n");
                    }
                    System.out.println("\tSCORE: " + String.format("%.3f", topDocs.scoreDocs[j].score));
                    buf.write("\tSCORE: " + String.format("%.3f", topDocs.scoreDocs[j].score) + "\n");
                    if (relevantDocsID.contains(topDocs.scoreDocs[j].doc)) {
                        System.out.println("\tES_RELEVANTE");
                        buf.write("\tES_RELEVANTE" + "\n");
                    } else {
                        System.out.println("\tNO_RELEVANTE");
                        buf.write("\tNO_RELEVANTE" + "\n");
                    }
                }
                sumP += (float) relevantRetrieved / topDocs.scoreDocs.length;
                System.out.println("\nMétricas para la query con cut " + cut + ": \n");
                buf.write("\nMétricas para la query con cut " + cut + ": \n");
                System.out.println("P@n es " + String.format("%.3f", (float) relevantRetrieved / topDocs.scoreDocs.length));
                buf.write("P@n es " + String.format("%.3f", (float) relevantRetrieved / topDocs.scoreDocs.length) + "\n");
                acumP += (float) relevantRetrieved / topDocs.scoreDocs.length;
                System.out.println("Recall@n es " + String.format("%.3f", (float) relevantRetrieved / relevantDocsID.size()));
                buf.write("Recall@n es " + String.format("%.3f", (float) relevantRetrieved / relevantDocsID.size()) + "\n");
                acumRec += (float) relevantRetrieved / relevantDocsID.size();
                System.out.println("RR es " + String.format("%.3f",  (firstRelevantRank > 0) ? 1.0f / firstRelevantRank : 0));
                buf.write("RR es " + String.format("%.3f",  (firstRelevantRank > 0) ? 1.0f / firstRelevantRank : 0) + "\n");
                acumRR +=  (firstRelevantRank > 0) ? 1.0f / firstRelevantRank : 0;
                System.out.println("AP@n es " + String.format("%.3f", (relevantRetrieved > 0) ? (float) sumP / relevantRetrieved : 0));
                buf.write("AP@n es " + String.format("%.3f", (relevantRetrieved > 0) ? (float) sumP / relevantRetrieved : 0) + "\n");
                if (!Float.isInfinite(sum))
                    acumMap += sum / relevantDocsID.size();
                System.out.println("**********************************************"); buf.write("***********************************************" + "\n");
                bufCsv.write("\n");
            }

            int totalQueries = fin - ini;
            System.out.println("Resultados promedio de las queries:");
            buf.write("Resultados promedio de las queries:");
            System.out.println("P@n promedio = " + String.format("%.3f", (float) acumP / totalQueries) + "\nRecall@n promedio = " + String.format("%.3f", (float) acumRec / totalQueries) +
                    "\nRR promedio = " + String.format("%.3f", (float) acumRR / totalQueries) + "\nAP@n promedio = " + String.format("%.3f", (float) acumMap / totalQueries) + "\n");
            buf.write("P@n promedio = " + String.format("%.3f", (float) acumP / totalQueries) + "\nRecall@n promedio = " + String.format("%.3f", (float) acumRec / totalQueries) +
                    "\nRR promedio = " + String.format("%.3f", (float) acumRR / totalQueries) + "\nAP@n promedio = " + String.format("%.3f", (float) acumMap / totalQueries) + "\n");

            bufCsv.write("Mean, " + String.format("%.3f", (float) acumP / totalQueries) + ", " + String.format("%.3f", (float) acumRec / totalQueries) + " , " + String.format("%.3f", (float) acumRR / totalQueries) + ", " + String.format("%.3f", (float) acumMap / totalQueries));
            //Cerramos
            bufCsv.close();
            buf.close();
            writerCsv.close();
            writer.close();

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<Integer> countLineScanner(String fileName, int idQuery) {
        ArrayList<Integer> relevantDocsID = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(fileName))) {
            boolean found = false;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (!found && line.equals(String.valueOf(idQuery))) {
                    found = true;
                    continue;
                }
                if (found) {
                    if (line.startsWith("/")) {
                        break;
                    }
                    String[] ids = line.split("\\s+");
                    for (String id : ids) {
                        if (!id.isEmpty()) {
                            relevantDocsID.add(Integer.valueOf(id));
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return relevantDocsID;
    }

    public static HashMap<Integer, ArrayList<Integer>> parseQueryRelevance(String fileName) {
        HashMap<Integer, ArrayList<Integer>> queryRelevance = new HashMap<>();
        try (Scanner scanner = new Scanner(new File(fileName))) {
            while (scanner.hasNextLine()) {
                int queryId = Integer.parseInt(scanner.nextLine().trim());
                ArrayList<Integer> relevantDocs = new ArrayList<>();
                String line;
                while (scanner.hasNextLine() && !(line = scanner.nextLine().trim()).equals("/")) {
                    String[] ids = line.split("\\s+");
                    for (String id : ids) {
                        if (!id.isEmpty()) {
                            relevantDocs.add(Integer.parseInt(id));
                        }
                    }
                }
                queryRelevance.put(queryId, relevantDocs);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return queryRelevance;
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
                CharArraySet stopWords = ENGLISH_STOP_WORDS_SET;
                analyzer = new StopAnalyzer(stopWords);
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

