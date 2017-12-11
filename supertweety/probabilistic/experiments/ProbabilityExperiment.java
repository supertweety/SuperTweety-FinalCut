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
import ida.ilp.logic.Constant;
import ida.ilp.logic.Literal;
import ida.utils.Sugar;
import ida.utils.VectorUtils;
import supertweety.Globals;
import supertweety.possibilistic.PossibilisticLogicTheory;
import supertweety.BinaryDataset;
import supertweety.probabilistic.DensityEstimationTree;

import java.io.FileReader;
import java.io.Reader;
import java.util.*;

/**
 * Created by kuzelkao_cardiff on 26/02/16.
 */
public class ProbabilityExperiment {

    private static Random random = new Random(2016);

    public static boolean atLeastSomePositiveEvidence = false;

    private static boolean[] randomMask(int length, int trues){
        return VectorUtils.randomBooleanVector(length, trues, random);
    }

    public static void probabilityExperiment(PossibilisticLogicTheory plt, BinaryDataset testSet, int querySize, int iterations,
                                             List<Double> predicted_out, List<Double> empirical_out){
        Constant x = Constant.construct("x");
        String[] attributes = testSet.attributes();

        int index = 0;
        for (boolean[] row : testSet.examples()){
            List<Clause> query = new ArrayList<Clause>();
            Set<Literal> queryLiterals = new HashSet<Literal>();
            boolean[] evidenceMask = randomMask(row.length, querySize);
            for (int i = 0; i < evidenceMask.length; i++){
                Literal literal = new Literal(attributes[i], !row[i], x);
                if (evidenceMask[i]){
                    query.add(new Clause(literal));
                    queryLiterals.add(literal);
                }
            }

            double predicted = plt.probability(query, Globals.modelCounterFactory.newInstance());
            predicted_out.add(predicted);

            double empirical = (testSet.count(queryLiterals)-1)/(double)testSet.numExamples();
            empirical_out.add(empirical);

            if (index++ >= iterations){
                break;
            }
        }
    }

    public static void setRandom(Random random){
        ProbabilityExperiment.random = random;
    }

    public static void main(String[] args) throws Exception {

        int iterations = 100;

        for (double d : new double[]{0.005,0.001}){
            String path = "/Users/kuzelkao_cardiff/Dropbox/Experiments/ECAI16/";
            String[] datasets = new String[]{"nltcs", "msnbc", "kdd", "plants"};

            for (String concretedatasetPath : datasets) {

                List<Double> predicted_out = new ArrayList<Double>();
                List<Double> empirical_out = new ArrayList<Double>();

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

                PossibilisticLogicTheory plt0 = tree.toPossibilisticLogic(false);

                int complexity0 = 0;
                for (Clause c : Sugar.flatten(plt0.toLevelList())) {
                    complexity0 += c.countLiterals();
                }

                System.out.println("SUM OF RULE LENGTHS: " + complexity0 + ", NUMBER OF RULES: " + Sugar.flatten(plt0.toLevelList()).size() + " VS " +
                        "SIZE OF THE TREE: " + tree.numberOfNodes() + ", NUMBER OF LEAVES: " + tree.numberOfLeaves() +
                        " VS SUM OF RULE LENGTHS W/O SIMPLIFICATION: " + notSimplifiedComplexity + ", NUMBER OF RULES WITHOUT SIMPLIFICATION: " + Sugar.flatten(notSimplifiedPlt.toLevelList()).size());


                BinaryDataset testSet = BinaryDataset.readCSV(new FileReader(path + concretedatasetPath + ".test.data.txt"));

                long m1 = System.currentTimeMillis();
                for (int e = 1; e < testSet.attributes().length; e++) {
                    System.out.print(".");
                    System.out.flush();
                    //the same seed for "everyone"
                    ProbabilityExperiment.random = new Random(2016);
                    probabilityExperiment(plt0, testSet, e, iterations, predicted_out, empirical_out);
                }
                long m2 = System.currentTimeMillis();
                System.out.println("Time for all Probs: " + (m2 - m1));
                System.out.println("\nPredicted    = " + predicted_out);
                System.out.println("\nEmpirical    = " + empirical_out);


            }
        }
    }

}
