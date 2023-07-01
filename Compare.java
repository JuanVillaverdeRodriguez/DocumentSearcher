import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Compare {public static void main(String[] args) throws IOException {
    String usage = "java CompareAlt"
            + " -result [results1 results2] -test [ t | wilcoxon alpha]" + "\n\n";
    String results1 = null;
    String results2 = null;
    String test = null;
    double alpha = -1;

    for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
            case "-results":
                results1 = args[++i];
                results2 = args[++i];
                break;
            case "-test": case "-wilcoxon":
                test = args[++i];
                alpha = Double.parseDouble(args[++i]);
                break;
            default:
                throw new IllegalArgumentException("unknown parameter " + args[i]);
        }
    }

    if (results1 == null || results2 == null || test == null) {
        System.err.println("Usage: " + usage);
        System.exit(1);
    }

    double[] atribPrimero = readCSV(results1);
    double[] atribSegundo = readCSV(results2);

    if (test.equals("t")) {
        TTest ttest = new TTest();
        double res = ttest.pairedTTest(atribPrimero, atribSegundo);
        if (alpha < res)
            System.out.println("se rechaza la hipotesis nula, no es satisfactorio");
        else
            System.out.println("satisfactorio, comparacion t: " + ttest.pairedTTest(atribPrimero, atribSegundo));
    } else {
        WilcoxonSignedRankTest wilc = new WilcoxonSignedRankTest();
        double res = wilc.wilcoxonSignedRank(atribPrimero, atribSegundo);
        if (alpha < res)
            System.out.println("se rechaza la hipotesis nula, no es satisfactorio");
        else
            System.out.println("satisfactorio, comparacion wilcoxon: " + wilc.wilcoxonSignedRank(atribPrimero, atribSegundo));
    }
}
    public static boolean isDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double[] readCSV(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        List<Double> values = new ArrayList<>();

        for (int i = 1; i < lines.size(); ++i) {
            String[] splitted = lines.get(i).split(",");
            for (String value : splitted) {
                if (value != null && value.trim().length() > 0 && isDouble(value)) {
                    values.add(Double.parseDouble(value));
                }
            }
        }

        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            result[i] = values.get(i);
        }
        return result;
    }
}