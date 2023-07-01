import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

public class TrainingTestNPL {
    private static String path = null;
    private static int cut = 0, n1 = 0, n2 = 0, n3 = 0, n4 = 0;
    private static String metrica = null;
    private static boolean evaljm = false;
    private static boolean evaldir = false;

    private static final String DIR_NPL_DOC_TEXT = "./npl/doc-text";
    private static final String DIR_NPL_QUERY_TEXT = "./npl/query-text";
    private static final String DIR_NPL_RLV_ASS = "./npl/rlv-ass";

    public static void main(String[] args) {
        String usage = "java org.apache.lucene.demo.TraininingTestNPL"
                + " [-evaljm int1-int2 int3-int4] [-evaldir int1-int2 int3-int4] [-cut n] [-metrica P | R | MRR | MAP] [-indexin]";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-indexin":
                    path = args[++i];
                    break;
                case "-cut":
                    cut = Integer.parseInt(args[++i]);
                    break;
                case "-metrica":
                    metrica = args[++i];
                    break;
                case "-evaljm":
                case "-evaldir":
                    if (args[i].equals("-evaljm")) {
                        evaljm = true;
                    } else {
                        evaldir = true;
                    }
                    String[] params1 = args[++i].split("-");
                    if (params1.length == 2) {
                        n1 = Integer.parseInt(params1[0]);
                        n2 = Integer.parseInt(params1[1]);
                    }
                    String[] params2 = args[++i].split("-");
                    if (params2.length == 2) {
                        n3 = Integer.parseInt(params2[0]);
                        n4 = Integer.parseInt(params2[1]);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        if (path == null) {
            System.err.println("Usage: " + usage);
            System.exit(-2);
        }
        ArrayList<Integer> trainRelevantDocsID = SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, n1);
        ArrayList<Integer> testRelevantDocsID = SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, n3);

        for (int idQuery = n1 + 1; idQuery <= n2; idQuery++)
            trainRelevantDocsID.addAll(SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, idQuery));

        for (int idQuery = n3 + 1; idQuery <= n4; idQuery++)
            testRelevantDocsID.addAll(SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, idQuery));

        if (evaljm)
            evaljm();
        else
            evaldir();
    }

    public static void evaljm() {
        try {
            Directory dir = FSDirectory.open(Path.of(path));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);

            BufferedReader bufferedReader = new BufferedReader(new FileReader(DIR_NPL_QUERY_TEXT));
            String contents = bufferedReader.lines().collect(Collectors.joining());
            String[] subQueries = contents.split("\\s*/");
            QueryParser parser;

            File csv = new File("npl.jm.training." + n1 + "-" + n2 + ".test." + n3 + "-" + n4 + "." + metrica + cut + ".training.csv");
            FileWriter escritor = new FileWriter(csv);
            BufferedWriter buf = new BufferedWriter(escritor);
            buf.write(metrica + "@" + cut + ", ");
            for (BigDecimal lambda2 = new BigDecimal("0.1"); lambda2.floatValue() <= 1; lambda2 = lambda2.add(new BigDecimal("0.1"))) {
                buf.write(lambda2 + "," + "\t");
            }
            buf.write("\n");

            float[] promedios = new float[10];

            ArrayList<Integer> trainRelevantDocsID;
            for (int i = n1; i < n2; i++) {

                trainRelevantDocsID = SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, i);

                buf.write(i + ", ");
                subQueries[i] = subQueries[i].toLowerCase();
                subQueries[i] = subQueries[i].substring(1);

                int j = 0;
                float sumPrecisiones = 0;
                for (float lambda = 0.1f; lambda <= 1.1; lambda += 0.1) {
                    if (lambda > 1)
                        lambda = 1;

                    iwc.setSimilarity(new LMJelinekMercerSimilarity(lambda));
                    searcher.setSimilarity(new LMJelinekMercerSimilarity(lambda));
                    parser = new QueryParser("contents", analyzer);

                    TopDocs topDocs = searcher.search(parser.parse(subQueries[i]), cut);

                    int relevantRetrieved = 0;
                    int rankingPosOfFirstRelevant = 0;
                    boolean firstRelevantFound = false;
                    for (ScoreDoc doc : topDocs.scoreDocs) {
                        if (trainRelevantDocsID.contains(doc.doc)) {
                            firstRelevantFound = true;
                            relevantRetrieved++;

                            sumPrecisiones += (float) relevantRetrieved / cut;
                        }
                        if (!firstRelevantFound) rankingPosOfFirstRelevant++;

                    }
                    System.out.print("Query: " + i + " Lambda: " + lambda);

                    if (metrica.equals("P")) {
                        System.out.println("TOP DOCS: " + topDocs.scoreDocs.length);
                        float precision = (float) relevantRetrieved / topDocs.scoreDocs.length;
                        System.out.println(" Con precision " + metrica + "@" + cut + " = " + precision);
                        buf.write(precision + "," + "\t");
                        precision += promedios[j];
                        System.out.println("PROMEDIOS J: " + promedios[j]);
                        promedios[j] = precision;
                    } else if (metrica.equals("R")) {
                        float recall = (float) relevantRetrieved / trainRelevantDocsID.size();
                        System.out.println(" Con recall " + metrica + "@" + cut + " = " + recall);
                        buf.write(String.format(Locale.US, "%.3f", recall) + "," + "\t");
                        recall += promedios[j];
                        promedios[j] = recall;
                    } else if (metrica.equals("MAP")) {
                        float map = sumPrecisiones / trainRelevantDocsID.size();
                        System.out.println(" Con MAP " + metrica + "@" + cut + " = " + map);
                        buf.write(String.format(Locale.US, "%.3f", map) + "," + "\t");
                        map += promedios[j];
                        promedios[j] = map;
                    } else if (metrica.equals("MRR")) {
                        float mrr = 1/(float) rankingPosOfFirstRelevant;
                        System.out.println(" Con MRR " + metrica + "@" + cut + " = " + mrr);
                        buf.write(String.format(Locale.US, "%.3f", mrr) + "," + "\t");
                        mrr += promedios[j];
                        promedios[j] = mrr;
                    }
                    j++;
                }
                buf.write("\n");
            }
            buf.write("Mean, ");
            System.out.print("Media de ");
            for (float valor : promedios) {
                buf.write(valor / (n2 - n1) + ", ");
                System.out.print(valor / (n2 - n1) + ", ");
            }
            buf.close();
            escritor.close();

            //TEST
            float lambda = getIndexOfLargest(promedios);
            lambda = (lambda + 1) / 10;
            searcher.setSimilarity(new LMJelinekMercerSimilarity(lambda));
            parser = new QueryParser("contents", analyzer);

            File testCSV = new File("npl.jm.training." + n1 + "-" + n2 + ".test." + n3 + "-" + n4 + "." + metrica + cut + ".test.csv");
            FileWriter escritor2 = new FileWriter(testCSV);
            BufferedWriter buf2 = new BufferedWriter(escritor2);

            buf2.write(lambda + ", " + metrica + "@" + cut + "\n");

            float mean = 0;
            float sumPrecisiones = 0;
            ArrayList<Integer> testRelevantDocsID;
            for (int i = n3; i < n4; i++) {

                testRelevantDocsID = SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, i);

                subQueries[i] = subQueries[i].toLowerCase();
                subQueries[i] = subQueries[i].substring(1);

                TopDocs topDocs = searcher.search(parser.parse(subQueries[i]), cut);

                int relevantRetrieved = 0;
                int rankingPosOfFirstRelevant = 0;
                boolean firstRelevantFound = false;
                for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                    if (testRelevantDocsID.contains(topDocs.scoreDocs[j].doc)) {
                        firstRelevantFound = true;
                        relevantRetrieved++;
                        sumPrecisiones += (float) relevantRetrieved / cut;
                    }
                    if (!firstRelevantFound) rankingPosOfFirstRelevant++;
                }

                buf2.write(i + ", ");
                if (metrica.equals("P")) {
                    float precision = (float) relevantRetrieved / topDocs.scoreDocs.length;
                    buf2.write(precision + "\n");
                    mean += precision;
                } else if (metrica.equals("R")) {
                    float recall = (float) relevantRetrieved / testRelevantDocsID.size();
                    buf2.write(String.format(Locale.US, "%.3f", recall) + "\n");
                    mean += recall;
                } else if (metrica.equals("MAP")) {
                    float map = sumPrecisiones / testRelevantDocsID.size(); // Meter calculo
                    System.out.println(" Con MAP " + metrica + "@" + cut + " = " + map);
                    buf2.write(String.format(Locale.US, "%.3f", map) + "," + "\n");
                } else if (metrica.equals("MRR")) {
                    float mrr = 1/(float) rankingPosOfFirstRelevant;
                    System.out.println(" Con MRR " + metrica + "@" + cut + " = " + mrr);
                    buf2.write(String.format(Locale.US, "%.3f", mrr) + "," + "\n");
                }
            }

            buf2.write("Mean, ");
            buf2.write(String.valueOf(mean / (n4 - n3)));

            buf2.close();
            escritor2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void evaldir() {
        try {
            Directory dir = FSDirectory.open(Path.of(path));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);

            BufferedReader bufferedReader = new BufferedReader(new FileReader(DIR_NPL_QUERY_TEXT));
            String contents = bufferedReader.lines().collect(Collectors.joining());
            String[] subQueries = contents.split("\\s*/");
            QueryParser parser;

            File csv = new File("npl.d.training." + n1 + "-" + n2 + ".test." + n3 + "-" + n4 + "." + metrica + cut + ".training.csv");
            FileWriter escritor = new FileWriter(csv);
            BufferedWriter buf = new BufferedWriter(escritor);
            buf.write(metrica + "@" + cut + ", ");
            buf.write("0,\t200,\t400,\t600,\t800,\t1000,\t1500,\t2000,\t2500,\t3000,\t4000");
            buf.write("\n");

            float[] promedios = new float[11];

            ArrayList<Integer> trainRelevantDocsID;
            for (int i = n1; i < n2; i++) {

                trainRelevantDocsID = SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, i);

                buf.write(i + ", ");
                subQueries[i] = subQueries[i].toLowerCase();
                subQueries[i] = subQueries[i].substring(1);
                int j = 0;
                float sumPrecisiones = 0;
                int incremento = 200;
                for (int mu = 0; mu <= 4000; mu += incremento) {
                    if (mu == 1000) incremento = 500;
                    if (mu == 3000) incremento = 1000;

                    iwc.setSimilarity(new LMDirichletSimilarity(mu));
                    searcher.setSimilarity(new LMDirichletSimilarity(mu));
                    parser = new QueryParser("contents", analyzer);

                    TopDocs topDocs = searcher.search(parser.parse(subQueries[i]), cut);

                    int relevantRetrieved = 0;
                    int rankingPosOfFirstRelevant = 0;
                    boolean firstRelevantFound = false;
                    for (ScoreDoc doc : topDocs.scoreDocs) {
                        if (trainRelevantDocsID.contains(doc.doc)) {
                            firstRelevantFound = true;
                            relevantRetrieved++;

                            sumPrecisiones += (float) relevantRetrieved / cut;
                        }
                        if (!firstRelevantFound) rankingPosOfFirstRelevant++;

                    }
                    System.out.println("QUERY: " + i + " MU: " + mu);

                    if (metrica.equals("P")) {
                        float precision = (float) relevantRetrieved / topDocs.scoreDocs.length;
                        System.out.println(" Con precision " + metrica + "@" + cut + " = " + precision);
                        buf.write(precision + "," + "\t");
                        precision += promedios[j];
                        promedios[j] = precision;
                    } else if (metrica.equals("R")) {
                        float recall = (float) relevantRetrieved / trainRelevantDocsID.size();
                        System.out.println(" Con recall " + metrica + "@" + cut + " = " + recall);
                        buf.write(String.format(Locale.US, "%.3f", recall) + "," + "\t");
                        recall += promedios[j];
                        promedios[j] = recall;
                    } else if (metrica.equals("MAP")) {
                        float map = sumPrecisiones / trainRelevantDocsID.size();
                        System.out.println(" Con MAP " + metrica + "@" + cut + " = " + map);
                        buf.write(String.format(Locale.US, "%.3f", map) + "," + "\t");
                        map += promedios[j];
                        promedios[j] = map;
                    } else if (metrica.equals("MRR")) {
                        float mrr = 1/(float) rankingPosOfFirstRelevant;
                        System.out.println(" Con MRR " + metrica + "@" + cut + " = " + mrr);
                        buf.write(String.format(Locale.US, "%.3f", mrr) + "," + "\t");
                        mrr += promedios[j];
                        promedios[j] = mrr;
                    }
                    j++;
                }
                buf.write("\n");
            }
            buf.write("Mean, ");
            System.out.print("Media de ");
            for (float valor : promedios) {
                buf.write(valor / (n2 - n1) + ", ");
                System.out.print(valor / (n2 - n1) + ", ");
            }
            buf.close();
            escritor.close();

            //EMPIEZA EL TEST
            float lambda = getIndexOfLargest(promedios);
            lambda = (lambda + 1) / 10;
            searcher.setSimilarity(new LMDirichletSimilarity(lambda));
            parser = new QueryParser("contents", analyzer);

            File testCSV = new File("npl.d.training." + n1 + "-" + n2 + ".test." + n3 + "-" + n4 + "." + metrica + cut + ".test.csv");
            FileWriter escritor2 = new FileWriter(testCSV);
            BufferedWriter buf2 = new BufferedWriter(escritor2);

            buf2.write(lambda + ", " + metrica + "@" + cut + "\n");

            float mean = 0;
            float sumPrecisiones = 0;
            ArrayList<Integer> testRelevantDocsID;
            for (int i = n3; i < n4; i++) {

                testRelevantDocsID = SearchEvalNPL.countLineScanner(DIR_NPL_RLV_ASS, i);

                subQueries[i] = subQueries[i].toLowerCase();
                subQueries[i] = subQueries[i].substring(1);

                TopDocs topDocs = searcher.search(parser.parse(subQueries[i]), cut);

                int relevantRetrieved = 0;
                int rankingPosOfFirstRelevant = 0;
                boolean firstRelevantFound = false;
                for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                    if (testRelevantDocsID.contains(topDocs.scoreDocs[j].doc)) {
                        firstRelevantFound = true;
                        relevantRetrieved++;
                        sumPrecisiones += (float) relevantRetrieved / cut;
                    }
                    if (!firstRelevantFound) rankingPosOfFirstRelevant++;
                }

                buf2.write(i + ", ");
                if (metrica.equals("P")) {
                    float precision = (float) relevantRetrieved / topDocs.scoreDocs.length;
                    buf2.write(precision + "\n");
                    mean += precision;
                } else if (metrica.equals("R")) {
                    float recall = (float) relevantRetrieved / testRelevantDocsID.size();
                    buf2.write(String.format(Locale.US, "%.3f", recall) + "\n");
                    mean += recall;
                } else if (metrica.equals("MAP")) {
                    float map = sumPrecisiones / testRelevantDocsID.size(); // Meter calculo
                    System.out.println(" Con MAP " + metrica + "@" + cut + " = " + map);
                    buf2.write(String.format(Locale.US, "%.3f", map) + "," + "\n");
                } else if (metrica.equals("MRR")) {
                    float mrr = 1/(float) rankingPosOfFirstRelevant;
                    System.out.println(" Con MRR " + metrica + "@" + cut + " = " + mrr);
                    buf2.write(String.format(Locale.US, "%.3f", mrr) + "," + "\n");
                }
            }

            buf2.write("Mean, ");
            buf2.write(String.valueOf(mean / (n4 - n3)));


            buf2.close();
            escritor2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

        public static int getIndexOfLargest(float[] array)
    {
        if ( array == null || array.length == 0 ) return -1; // null or empty

        int largest = 0;
        for ( int i = 1; i < array.length; i++ )
        {
            if ( array[i] > array[largest] ) largest = i;
        }
        return largest; // position of the first largest found
    }
}
