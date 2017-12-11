/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package temp;

import ida.ilp.logic.Clause;
import ida.ilp.logic.Literal;
import ida.ilp.logic.LogicUtils;
import ida.ilp.logic.subsumption.Matching;
import ida.utils.Sugar;
import ida.utils.tuples.Pair;
import supertweety.SuperTweetyContradictionException;
import supertweety.RelationalDataset;
import supertweety.possibilistic.RelationalPossibilisticLogicTheory;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by kuzelkao_cardiff on 16/02/17.
 */
public class YeastEvaluation {

    public static Clause readEvidencePL(int i) throws Exception {
        List<Literal> literals = new ArrayList<Literal>();
        for (String line : Sugar.readLines(new FileReader("/Users/ondrejkuzelka/Dropbox/Experiments/IJCAI17/yeast/yeast/queries/" + "queries" + i + ".db"))){
            literals.addAll(LogicUtils.constantizeClause(new Clause(Literal.parseLiteral(line))).literals());
        }
        return new Clause(literals);
    }

    public static Clause readMLNPrediction(int i) throws Exception {
        List<Literal> literals = new ArrayList<Literal>();
        for (String line : Sugar.readLines(new FileReader("/Users/ondrejkuzelka/Dropbox/Experiments/IJCAI17/yeast/yeast/map/"+i+".db"))){
            line = line.replaceAll("\"", "");
            literals.add(Literal.parseLiteral(line));
        }
        return new Clause(literals);
    }

    public static void main(String[] args) throws Exception {

        RelationalDataset datasetTrain = new RelationalDataset(Yeast.yeast("train"));

        RelationalPossibilisticLogicTheory ml = RelationalPossibilisticLogicTheory.read(new FileReader("/Users/ondrejkuzelka/Dropbox/Experiments/IJCAI17/protein/yeast.poss"));


        Pair p = new Matching().allSubstitutions(Clause.parse("@alldiff(V0, V1, V3, V5),interaction(V3, V0), enzyme(V3, V1), protein_class(V3, V5), enzyme(V0, V1)"), new Clause(datasetTrain.literals()));

        RelationalDataset datasetTest = new RelationalDataset(Yeast.yeast("test"));




        System.out.println(ml);
        System.out.println("falsity weight: "+ml.weightOfFalsity());

        List<Double> hammingDifferences = new ArrayList<Double>();

        double hammingSumPL = 0;
        double hammingSumMLN = 0;
        for (int i = 1; i < 2000/*datasetTest.literals().size()*/; i++){
            //only positive assumed
            Set<Literal> groundTruth = Sugar.setFromCollections(datasetTest.literals());
            Clause evidence = readEvidencePL(i);


            {
                Set<Literal> predicted;
                try {
                    predicted = ml.solve(evidence.literals()).r;



                } catch (SuperTweetyContradictionException stce) {
                    predicted = evidence.literals();
                    System.out.println("Warning: violated hard rules!");
                }
//                Set<Literal> filteredPredictions = new HashSet<Literal>();
//                for (Literal l : Sugar.setDifference(predicted,evidence.literals())){
//                    if (ml.implies(evidence.literals(), l)){
//                        filteredPredictions.add(l);
//                    } else {
//
//                    }
//                }
//                predicted = Sugar.union(filteredPredictions, evidence.literals());


                Set<Literal> positivePredicted = new HashSet<Literal>();
                Set<Literal> negativePredicted = new HashSet<Literal>();
                for (Literal l : predicted) {
                    if (l.isNegated()) {
                        negativePredicted.add(l);
                    } else {
                        positivePredicted.add(l);
                    }
                }


                System.out.println("evidence: " + evidence.countLiterals());
                System.out.println("predicted: " + predicted.size());

                int hammingError = 0;
                int hammingErrorBaseline = Sugar.setDifference(groundTruth, evidence.literals()).size();
                for (Literal l : groundTruth) {
                    if (!positivePredicted.contains(l)) {
                        hammingError++;
                    }
                }
                for (Literal l : positivePredicted) {
                    if (!groundTruth.contains(l)) {
                        hammingError++;
                    }
                }
                double tp = Sugar.intersection(groundTruth,predicted).size();
                double fp = Sugar.setDifference(predicted, groundTruth).size();
                double fn = Sugar.setDifference(groundTruth, predicted).size();

                double precision = tp/(tp+fp);
                double recall = tp/(tp+fn);
                double f1 = 2*precision*recall/(precision+recall);

                hammingSumPL += hammingError;

                System.out.println("PL:  Hamming error: " + hammingError + ", baseline: " + hammingErrorBaseline+", TP: "+tp+", FP: "+fp+", f1: "+f1+", precision: "+precision+", recall: "+recall);
                System.out.println("FP: "+Sugar.setDifference(predicted, groundTruth));
            }

            {
                Set<Literal> predicted = LogicUtils.constantizeClause(readMLNPrediction(i)).literals();

                Set<Literal> positivePredicted = new HashSet<Literal>();
                Set<Literal> negativePredicted = new HashSet<Literal>();
                for (Literal l : predicted) {
                    if (l.isNegated()) {
                        negativePredicted.add(l);
                    } else {
                        positivePredicted.add(l);
                    }
                }


                System.out.println("evidence: " + evidence.countLiterals());
                System.out.println("predicted: " + predicted.size());

                int hammingError = 0;
                int hammingErrorBaseline = Sugar.setDifference(groundTruth, evidence.literals()).size();
                for (Literal l : groundTruth) {
                    if (!positivePredicted.contains(l)) {
                        hammingError++;
                    }
                }
                for (Literal l : positivePredicted) {
                    if (!groundTruth.contains(l)) {
                        hammingError++;
                    }
                }
                double tp = Sugar.intersection(groundTruth,predicted).size();
                double fp = Sugar.setDifference(predicted, groundTruth).size();
                double fn = Sugar.setDifference(groundTruth, predicted).size();

                double precision = tp/(tp+fp);
                double recall = tp/(tp+fn);
                double f1 = 2*precision*recall/(precision+recall);

                System.out.println("MLN:  Hamming error: " + hammingError + ", baseline: " + hammingErrorBaseline+", TP: "+tp+", FP: "+fp+", f1: "+f1+", precision: "+precision+", recall: "+recall);
                hammingSumMLN += hammingError;
            }
            hammingDifferences.add(hammingSumMLN-hammingSumPL);
            System.out.println("hamming difference: "+(hammingSumMLN-hammingSumPL));
            System.out.println("-------------");
        }
        System.out.println(hammingDifferences);
    }

}
