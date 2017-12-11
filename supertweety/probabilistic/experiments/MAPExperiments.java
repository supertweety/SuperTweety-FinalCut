/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.probabilistic.experiments;

import ida.ilp.logic.Clause;
import ida.ilp.logic.Literal;
import ida.utils.Sugar;
import ida.utils.VectorUtils;
import supertweety.BinaryDataset;
import supertweety.possibilistic.PossibilisticLogicTheory;
import supertweety.possibilistic.PossibilisticUtils;
import supertweety.probabilistic.DensityEstimationTree;
import supertweety.probabilistic.DiscreteProbabilityDistribution;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Created by kuzelkao_cardiff on 26/02/16.
 */
public class MAPExperiments {

    private static Random random = new Random(2016);

    public static boolean atLeastSomePositiveEvidence = false;

    private static boolean[] randomMask(int length, int trues){
        return VectorUtils.randomBooleanVector(length, trues, random);
    }

    public static void mapExperiment(DiscreteProbabilityDistribution plt, BinaryDataset testSet, int evidenceSize,
                                       List<Double> hammingErrors_out, List<Double> hammingErrorBaselineAllTrue_out, List<Double> hammingErrorBaselineAllFalse_out,
                                       List<Double> exactMatches_out, List<Double> allTrueExactMatches_out, List<Double> allFalseExactMatches_out){
        List<Double> hammingErrors = new ArrayList<Double>();
        List<Double> allFalse = new ArrayList<Double>();
        List<Double> allTrue = new ArrayList<Double>();
        String[] attributes = testSet.attributes();
        double exactMatches = 0;
        double allTrueExactMatches = 0;
        double allFalseExactMatches = 0;
        for (boolean[] row : testSet.examples()){
            List<Literal> evidenceLiterals = new ArrayList<Literal>();
            List<Literal> queryLiterals = new ArrayList<Literal>();
            boolean[] evidenceMask = randomMask(row.length, evidenceSize);
            if (atLeastSomePositiveEvidence) {
                boolean positiveLiteralInEvidence = false;
                for (int j = 0; j < row.length; j++) {
                    if (evidenceMask[j] && row[j]) {
                        positiveLiteralInEvidence = true;
                        break;
                    }
                }
                if (!positiveLiteralInEvidence) {
                    continue;
                }
            }
            for (int i = 0; i < evidenceMask.length; i++){
                Literal literal = new Literal(attributes[i], !row[i]);
                if (evidenceMask[i]){
                    evidenceLiterals.add(literal);
                } else {
                    queryLiterals.add(literal);
                }
            }

            Set<Literal> solution = plt.mostProbableWorld(evidenceLiterals);
            double hammingError = 0;

            Set<Literal> mapState = solution;
            for (Literal queryLiteral : queryLiterals) {
                if (queryLiteral.isNegated()) {
                    if (mapState.contains(queryLiteral.negation())) {
                        hammingError++;
                    }
                } else {
                    if (!mapState.contains(queryLiteral)) {
                        hammingError++;
                    }
                }
            }
            //System.out.println("HE: "+hammingError+" :: "+Sugar.collectionDifference(mapState,evidenceLiterals)+" ev: "+evidenceLiterals);
            hammingErrors.add(hammingError);
            if (hammingError == 0){
                exactMatches++;
            }
            double allFalseError = 0, allTrueError = 0;
            for (int i = 0; i < row.length; i++){
                if (!evidenceMask[i]){
                    if (row[i]){
                        allFalseError++;
                    } else {
                        allTrueError++;
                    }
                }
            }
            allFalse.add(allFalseError);
            allTrue.add(allTrueError);

            if (allTrueError == 0){
                allTrueExactMatches++;
            }

            if (allFalseError == 0){
                allFalseExactMatches++;
            }
        }
        hammingErrors_out.add(VectorUtils.mean_Double(hammingErrors));
        hammingErrorBaselineAllTrue_out.add(VectorUtils.mean_Double(allFalse));
        hammingErrorBaselineAllFalse_out.add(VectorUtils.mean_Double(allTrue));
        exactMatches_out.add(exactMatches/(double)testSet.numExamples());
        allTrueExactMatches_out.add(allTrueExactMatches/(double)testSet.numExamples());
        allFalseExactMatches_out.add(allFalseExactMatches/(double)testSet.numExamples());
        //System.out.println("Exact match density: "+(exactMatches/testSet.numExamples()));
    }

    public static void setRandom(Random random){
        MAPExperiments.random = random;
    }

    public static void main(String[] args) throws Exception {

        for (double d : new double[]{0.005,0.001}){
            String path = "/Users/ondrejkuzelka/Dropbox/Experiments/ECAI16/";
            String[] datasets = new String[]{"nltcs", "plants", "msnbc", "kdd"};

            for (String concretedatasetPath : datasets) {

                List<Double> hammingErrors_out = new ArrayList<Double>();
                List<Double> hammingErrorsBaselineAllFalse_out = new ArrayList<Double>();
                List<Double> hammingErrorsBaselineAllTrue_out = new ArrayList<Double>();
                List<Double> exactMatches_out = new ArrayList<Double>();
                List<Double> allTrueExactMatches_out = new ArrayList<Double>();
                List<Double> allFalseExactMatches_out = new ArrayList<Double>();

                concretedatasetPath = concretedatasetPath + "/" + concretedatasetPath;
                Reader reader = new FileReader(path + concretedatasetPath + ".ts.data.txt");
                BinaryDataset dataset = BinaryDataset.readCSV(reader);

                DensityEstimationTree.fractionUsedForStructureLearning = 0.5;
                DensityEstimationTree tree = DensityEstimationTree.learn(dataset, Math.max(1, (int) (dataset.numExamples() * d)));
                System.out.println("TREE CONSTRUCTED... NODES = " + tree.numberOfNodes() + ", LEAVES = " + tree.numberOfLeaves());

                PossibilisticLogicTheory notSimplifiedPlt = tree.toPossibilisticLogic(false);

                int notSimplifiedComplexity = 0;
                for (Clause c : Sugar.flatten(notSimplifiedPlt.toLevelList())) {
                    notSimplifiedComplexity += c.countLiterals();
                }

                // NO COLLAPSE

                PossibilisticLogicTheory plt0 = tree.toPossibilisticLogic();

                int complexity0 = 0;
                for (Clause c : Sugar.flatten(plt0.toLevelList())) {
                    complexity0 += c.countLiterals();
                }

                System.out.println("SUM OF RULE LENGTHS: " + complexity0 + ", NUMBER OF RULES: " + Sugar.flatten(plt0.toLevelList()).size() + " VS " +
                        "SIZE OF THE TREE: " + tree.numberOfNodes() + ", NUMBER OF LEAVES: " + tree.numberOfLeaves() +
                        " VS SUM OF RULE LENGTHS W/O SIMPLIFICATION: " + notSimplifiedComplexity + ", NUMBER OF RULES WITHOUT SIMPLIFICATION: " + Sugar.flatten(notSimplifiedPlt.toLevelList()).size());


                BinaryDataset testSet = BinaryDataset.readCSV(new FileReader(path + concretedatasetPath + ".test.data.txt"));

                long m1 = System.currentTimeMillis();
                for (int e = 0; e < testSet.attributes().length; e++) {
                    System.out.print(".");
                    System.out.flush();
                    //the same seed for "everyone"
                    MAPExperiments.random = new Random(2016);
                    mapExperiment(plt0, testSet, e, hammingErrors_out, hammingErrorsBaselineAllFalse_out, hammingErrorsBaselineAllTrue_out, exactMatches_out, allTrueExactMatches_out, allFalseExactMatches_out);
                }
                long m2 = System.currentTimeMillis();
                System.out.println("Time for all MAPs: " + (m2 - m1));
                System.out.println("\nHammingErrors    = " + hammingErrors_out);
                System.out.println("\nHammingBaseTrue  = " + hammingErrorsBaselineAllTrue_out);
                System.out.println("\nHammingBaseFalse = " + hammingErrorsBaselineAllFalse_out);
                System.out.println("\nExactMatches    = " + exactMatches_out);
                System.out.println("\nAllTrueExactMatches    = " + allTrueExactMatches_out);
                System.out.println("\nAllFalseExactMatches    = " + allFalseExactMatches_out);

                // COLLAPSE (50%)

                hammingErrors_out.clear();
                exactMatches_out.clear();
                hammingErrorsBaselineAllFalse_out.clear();
                hammingErrorsBaselineAllTrue_out.clear();
                allTrueExactMatches_out.clear();
                allFalseExactMatches_out.clear();

                PossibilisticLogicTheory plt1 = PossibilisticUtils.collapse(plt0, complexity0 / 2);

                int complexity1 = 0;
                for (Clause c : Sugar.flatten(plt1.toLevelList())) {
                    complexity1 += c.countLiterals();
                }

                System.out.println("AFTER COLLAPSE (50%): SUM OF RULE LENGTHS: " + complexity1 + ", NUMBER OF RULES: " + Sugar.flatten(plt1.toLevelList()).size() + " VS " +
                        "SIZE OF THE TREE: " + tree.numberOfNodes() + ", NUMBER OF LEAVES: " + tree.numberOfLeaves() +
                        " VS SUM OF RULE LENGTHS W/O SIMPLIFICATION: " + notSimplifiedComplexity + ", NUMBER OF RULES WITHOUT SIMPLIFICATION: " + Sugar.flatten(notSimplifiedPlt.toLevelList()).size());

                m1 = System.currentTimeMillis();
                for (int e = 0; e < testSet.attributes().length; e++) {
                    System.out.print(".");
                    System.out.flush();
                    //the same seed for "everyone"
                    MAPExperiments.random = new Random(2016);
                    mapExperiment(plt1, testSet, e, hammingErrors_out, hammingErrorsBaselineAllFalse_out, hammingErrorsBaselineAllTrue_out, exactMatches_out, allTrueExactMatches_out, allFalseExactMatches_out);
                }
                m2 = System.currentTimeMillis();
                System.out.println("Time for all MAPs: " + (m2 - m1));
                System.out.println("\nHammingErrors    = " + hammingErrors_out);
                System.out.println("\nHammingBaseTrue  = " + hammingErrorsBaselineAllTrue_out);
                System.out.println("\nHammingBaseFalse = " + hammingErrorsBaselineAllFalse_out);
                System.out.println("\nExactMatches    = " + exactMatches_out);
                System.out.println("\nAllTrueExactMatches    = " + allTrueExactMatches_out);
                System.out.println("\nAllFalseExactMatches    = " + allFalseExactMatches_out);

                System.out.println("THE COLLAPSED THEORY (50%): ");
                System.out.println(plt1);


                // COLLAPSE (10%)

                hammingErrors_out.clear();
                exactMatches_out.clear();
                hammingErrorsBaselineAllFalse_out.clear();
                hammingErrorsBaselineAllTrue_out.clear();
                allTrueExactMatches_out.clear();
                allFalseExactMatches_out.clear();

                PossibilisticLogicTheory plt2 = PossibilisticUtils.collapse(plt0, complexity0 / 10);

                int complexity2 = 0;
                for (Clause c : Sugar.flatten(plt2.toLevelList())) {
                    complexity2 += c.countLiterals();
                }

                System.out.println("AFTER COLLAPSE (10%): SUM OF RULE LENGTHS: " + complexity2 + ", NUMBER OF RULES: " + Sugar.flatten(plt2.toLevelList()).size() + " VS " +
                        "SIZE OF THE TREE: " + tree.numberOfNodes() + ", NUMBER OF LEAVES: " + tree.numberOfLeaves() +
                        " VS SUM OF RULE LENGTHS W/O SIMPLIFICATION: " + notSimplifiedComplexity + ", NUMBER OF RULES WITHOUT SIMPLIFICATION: " + Sugar.flatten(notSimplifiedPlt.toLevelList()).size());

                m1 = System.currentTimeMillis();
                for (int e = 0; e < testSet.attributes().length; e++) {
                    System.out.print(".");
                    System.out.flush();
                    //the same seed for "everyone"
                    MAPExperiments.random = new Random(2016);
                    mapExperiment(plt2, testSet, e, hammingErrors_out, hammingErrorsBaselineAllFalse_out, hammingErrorsBaselineAllTrue_out, exactMatches_out, allTrueExactMatches_out, allFalseExactMatches_out);
                }
                m2 = System.currentTimeMillis();
                System.out.println("Time for all MAPs: " + (m2 - m1));
                System.out.println("\nHammingErrors    = " + hammingErrors_out);
                System.out.println("\nHammingBaseTrue  = " + hammingErrorsBaselineAllTrue_out);
                System.out.println("\nHammingBaseFalse = " + hammingErrorsBaselineAllFalse_out);
                System.out.println("\nExactMatches    = " + exactMatches_out);
                System.out.println("\nAllTrueExactMatches    = " + allTrueExactMatches_out);
                System.out.println("\nAllFalseExactMatches    = " + allFalseExactMatches_out);

                System.out.println("THE COLLAPSED THEORY (10%): ");
                System.out.println(plt2);

                System.out.println("\n\n---------------------------------------------------");
            }


            System.out.println("KL-BASED COLLAPSING:::");

            for (String concretedatasetPath : datasets) {

                List<Double> hammingErrors_out = new ArrayList<Double>();
                List<Double> hammingErrorsBaselineAllFalse_out = new ArrayList<Double>();
                List<Double> hammingErrorsBaselineAllTrue_out = new ArrayList<Double>();
                List<Double> exactMatches_out = new ArrayList<Double>();
                List<Double> allTrueExactMatches_out = new ArrayList<Double>();
                List<Double> allFalseExactMatches_out = new ArrayList<Double>();

                concretedatasetPath = concretedatasetPath + "/" + concretedatasetPath;
                Reader reader = new FileReader(path + concretedatasetPath + ".ts.data.txt");
                BinaryDataset dataset = BinaryDataset.readCSV(reader);

                DensityEstimationTree.fractionUsedForStructureLearning = 0.5;
                DensityEstimationTree tree = DensityEstimationTree.learn(dataset, Math.max(1, (int) (dataset.numExamples() * d)));
                System.out.println("TREE CONSTRUCTED... NODES = " + tree.numberOfNodes() + ", LEAVES = " + tree.numberOfLeaves());

                PossibilisticLogicTheory notSimplifiedPlt = tree.toPossibilisticLogic(false);

                int notSimplifiedComplexity = 0;
                for (Clause c : Sugar.flatten(notSimplifiedPlt.toLevelList())) {
                    notSimplifiedComplexity += c.countLiterals();
                }

                // NO COLLAPSE

                PossibilisticLogicTheory plt0 = tree.toPossibilisticLogic();

                int complexity0 = 0;
                for (Clause c : Sugar.flatten(plt0.toLevelList())) {
                    complexity0 += c.countLiterals();
                }

                System.out.println("SUM OF RULE LENGTHS: " + complexity0 + ", NUMBER OF RULES: " + Sugar.flatten(plt0.toLevelList()).size() + " VS " +
                        "SIZE OF THE TREE: " + tree.numberOfNodes() + ", NUMBER OF LEAVES: " + tree.numberOfLeaves() +
                        " VS SUM OF RULE LENGTHS W/O SIMPLIFICATION: " + notSimplifiedComplexity + ", NUMBER OF RULES WITHOUT SIMPLIFICATION: " + Sugar.flatten(notSimplifiedPlt.toLevelList()).size());


                BinaryDataset testSet = BinaryDataset.readCSV(new FileReader(path + concretedatasetPath + ".test.data.txt"));

                long m1 = System.currentTimeMillis();
                for (int e = 0; e < testSet.attributes().length; e++) {
                    System.out.print(".");
                    System.out.flush();
                    //the same seed for "everyone"
                    MAPExperiments.random = new Random(2016);
                    mapExperiment(plt0, testSet, e, hammingErrors_out, hammingErrorsBaselineAllFalse_out, hammingErrorsBaselineAllTrue_out, exactMatches_out, allTrueExactMatches_out, allFalseExactMatches_out);
                }
                long m2 = System.currentTimeMillis();
                System.out.println("Time for all MAPs: " + (m2 - m1));
                System.out.println("\nHammingErrors    = " + hammingErrors_out);
                System.out.println("\nHammingBaseTrue  = " + hammingErrorsBaselineAllTrue_out);
                System.out.println("\nHammingBaseFalse = " + hammingErrorsBaselineAllFalse_out);
                System.out.println("\nExactMatches    = " + exactMatches_out);
                System.out.println("\nAllTrueExactMatches    = " + allTrueExactMatches_out);
                System.out.println("\nAllFalseExactMatches    = " + allFalseExactMatches_out);

                // COLLAPSE (50%)

                hammingErrors_out.clear();
                exactMatches_out.clear();
                hammingErrorsBaselineAllFalse_out.clear();
                hammingErrorsBaselineAllTrue_out.clear();
                allTrueExactMatches_out.clear();
                allFalseExactMatches_out.clear();

                PossibilisticLogicTheory plt1 = PossibilisticUtils.collapseKL(plt0, complexity0 / 2);

                int complexity1 = 0;
                for (Clause c : Sugar.flatten(plt1.toLevelList())) {
                    complexity1 += c.countLiterals();
                }

                System.out.println("AFTER COLLAPSE (50%): SUM OF RULE LENGTHS: " + complexity1 + ", NUMBER OF RULES: " + Sugar.flatten(plt1.toLevelList()).size() + " VS " +
                        "SIZE OF THE TREE: " + tree.numberOfNodes() + ", NUMBER OF LEAVES: " + tree.numberOfLeaves() +
                        " VS SUM OF RULE LENGTHS W/O SIMPLIFICATION: " + notSimplifiedComplexity + ", NUMBER OF RULES WITHOUT SIMPLIFICATION: " + Sugar.flatten(notSimplifiedPlt.toLevelList()).size());

                m1 = System.currentTimeMillis();
                for (int e = 0; e < testSet.attributes().length; e++) {
                    System.out.print(".");
                    System.out.flush();
                    //the same seed for "everyone"
                    MAPExperiments.random = new Random(2016);
                    mapExperiment(plt1, testSet, e, hammingErrors_out, hammingErrorsBaselineAllFalse_out, hammingErrorsBaselineAllTrue_out, exactMatches_out, allTrueExactMatches_out, allFalseExactMatches_out);
                }
                m2 = System.currentTimeMillis();
                System.out.println("Time for all MAPs: " + (m2 - m1));
                System.out.println("\nHammingErrors    = " + hammingErrors_out);
                System.out.println("\nHammingBaseTrue  = " + hammingErrorsBaselineAllTrue_out);
                System.out.println("\nHammingBaseFalse = " + hammingErrorsBaselineAllFalse_out);
                System.out.println("\nExactMatches    = " + exactMatches_out);
                System.out.println("\nAllTrueExactMatches    = " + allTrueExactMatches_out);
                System.out.println("\nAllFalseExactMatches    = " + allFalseExactMatches_out);

                System.out.println("THE COLLAPSED THEORY (50%): ");
                System.out.println(plt1);


                // COLLAPSE (10%)

                hammingErrors_out.clear();
                exactMatches_out.clear();
                hammingErrorsBaselineAllFalse_out.clear();
                hammingErrorsBaselineAllTrue_out.clear();
                allTrueExactMatches_out.clear();
                allFalseExactMatches_out.clear();

                PossibilisticLogicTheory plt2 = PossibilisticUtils.collapseKL(plt0, complexity0 / 10);

                int complexity2 = 0;
                for (Clause c : Sugar.flatten(plt2.toLevelList())) {
                    complexity2 += c.countLiterals();
                }

                System.out.println("AFTER COLLAPSE (10%): SUM OF RULE LENGTHS: " + complexity2 + ", NUMBER OF RULES: " + Sugar.flatten(plt2.toLevelList()).size() + " VS " +
                        "SIZE OF THE TREE: " + tree.numberOfNodes() + ", NUMBER OF LEAVES: " + tree.numberOfLeaves() +
                        " VS SUM OF RULE LENGTHS W/O SIMPLIFICATION: " + notSimplifiedComplexity + ", NUMBER OF RULES WITHOUT SIMPLIFICATION: " + Sugar.flatten(notSimplifiedPlt.toLevelList()).size());

                m1 = System.currentTimeMillis();
                for (int e = 0; e < testSet.attributes().length; e++) {
                    System.out.print(".");
                    System.out.flush();
                    //the same seed for "everyone"
                    MAPExperiments.random = new Random(2016);
                    mapExperiment(plt2, testSet, e, hammingErrors_out, hammingErrorsBaselineAllFalse_out, hammingErrorsBaselineAllTrue_out, exactMatches_out, allTrueExactMatches_out, allFalseExactMatches_out);
                }
                m2 = System.currentTimeMillis();
                System.out.println("Time for all MAPs: " + (m2 - m1));
                System.out.println("\nHammingErrors    = " + hammingErrors_out);
                System.out.println("\nHammingBaseTrue  = " + hammingErrorsBaselineAllTrue_out);
                System.out.println("\nHammingBaseFalse = " + hammingErrorsBaselineAllFalse_out);
                System.out.println("\nExactMatches    = " + exactMatches_out);
                System.out.println("\nAllTrueExactMatches    = " + allTrueExactMatches_out);
                System.out.println("\nAllFalseExactMatches    = " + allFalseExactMatches_out);

                System.out.println("THE COLLAPSED THEORY (10%): ");
                System.out.println(plt2);

                System.out.println("\n\n---------------------------------------------------");
            }
        }
    }

}
