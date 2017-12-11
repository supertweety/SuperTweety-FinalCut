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
import ida.ilp.logic.HornClause;
import ida.ilp.logic.Literal;
import ida.utils.Combinatorics;
import ida.utils.Sugar;
import ida.utils.tuples.Pair;
import supertweety.RelationalDataset;
import supertweety.SuperTweetyContradictionException;
import supertweety.possibilistic.RelationalPossibilisticLogicTheory;
import supertweety.shorty.*;

import java.io.FileReader;
import java.util.*;

/**
 * Created by ondrejkuzelka on 11/02/17.
 */
public class MapExperiments {

    public static void uwcse(){
        int k = 3;

        RelationalDataset datasetTrain = new RelationalDataset(UWCSE.uwcse("ai","language","theory"));
        RelationalDataset datasetTest = new RelationalDataset(UWCSE.uwcse("graphics","systems"));

        ModelCounterFactory mcf = new ModelCounterFactory();


        TheoryLearner tl = new TheoryLearner(datasetTrain);
        tl.setMaxRuleLength(3);

        Pair<List<HornClause>,List<Clause>> learned = tl.learn();

        List<Clause> candidates = new ArrayList<Clause>();
        for (HornClause hc : learned.r){
            candidates.add(hc.toClause());
        }

        Stratifier stratifier = new Stratifier(datasetTrain, candidates, learned.s, k);

        RelationalPossibilisticLogicTheory ml = stratifier.stratify();

        System.out.println(ml);
        System.out.println("falsity weight: "+ml.weightOfFalsity());

        for (int i = 1; i < datasetTest.literals().size(); i++){
            //only positive assumed
            Set<Literal> groundTruth = Sugar.setFromCollections(datasetTest.literals());
            Clause evidence = new Clause(Combinatorics.randomCombination(Sugar.listFromCollections(datasetTest.literals()),i).toList());

            {
                Set<Literal> predicted;
                try {
                    predicted = ml.solve(evidence.literals()).r;
                } catch (SuperTweetyContradictionException stce) {
                    predicted = evidence.literals();
                }

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
                System.out.println("Hamming error: " + hammingError + ", baseline: " + hammingErrorBaseline);
            }
            {
                Set<Literal> predicted;
                try {
                    predicted = ml.solve(Double.POSITIVE_INFINITY, evidence.literals());
                    if (predicted == null){
                        predicted = evidence.literals();
                    }
                } catch (SuperTweetyContradictionException stce){
                    predicted = evidence.literals();
                }

                Set<Literal> positivePredicted = new HashSet<Literal>();
                Set<Literal> negativePredicted = new HashSet<Literal>();
                for (Literal l : predicted){
                    if (l.isNegated()){
                        negativePredicted.add(l);
                    } else {
                        positivePredicted.add(l);
                    }
                }

                System.out.println("predicted (hard rules only): "+predicted.size());

                int hammingError = 0;
                int hammingErrorBaseline = Sugar.setDifference(groundTruth, evidence.literals()).size();
                for (Literal l : groundTruth){
                    if (!positivePredicted.contains(l)){
                        hammingError++;
                    }
                }
                for (Literal l : positivePredicted){
                    if (!groundTruth.contains(l)){
                        hammingError++;
                    }
                }
                System.out.println("Hamming error (hard rules only): "+hammingError+", baseline: "+hammingErrorBaseline);
            }
        }
    }

    public static void yeast() throws Exception {
        int k = 3;

//        Dataset datasetTrain = new Dataset(UWCSE.uwcse("ai","language","theory"));
//        Dataset datasetTest = new Dataset(UWCSE.uwcse("graphics","systems"));

        RelationalDataset datasetTrain = new RelationalDataset(Yeast.yeast("train"));
        RelationalDataset datasetTest = new RelationalDataset(Yeast.yeast("test"));


        System.out.println("train \\cap test: "+Sugar.intersection(Sugar.setFromCollections(datasetTest.literals()), Sugar.setFromCollections(datasetTrain.literals())).size());

//        ModelCounterFactory mcf = new ModelCounterFactory();
//
//
//        TheoryLearner tl = new TheoryLearner(datasetTrain);
//        tl.setMaxRuleLength(4);
//
//        Pair<List<HornClause>,List<Clause>> learned = tl.learn();
//
//        List<Clause> candidates = new ArrayList<Clause>();
//        for (HornClause hc : learned.r){
//            candidates.add(hc.toClause());
//        }
//
//        Stratifier stratifier = new Stratifier(datasetTrain, candidates, learned.s, k);
//
//        RelationalPossibilisticLogicTheory ml = stratifier.stratify();

        RelationalPossibilisticLogicTheory ml = RelationalPossibilisticLogicTheory.read(new FileReader("/Users/kuzelkao_cardiff/Dropbox/Experiments/IJCAI17/protein/yeast.poss"));

//        RelationalPossibilisticLogicTheory ml = RelationalPossibilisticLogicTheory.read(new FileReader("/Users/kuzelkao_cardiff/Dropbox/Experiments/IJCAI17/protein/yeast.poss"));

        System.out.println(ml);
        System.out.println("falsity weight: "+ml.weightOfFalsity());

        for (int i = 1; i < datasetTest.literals().size(); i++){
            //only positive assumed
            Set<Literal> groundTruth = Sugar.setFromCollections(datasetTest.literals(), datasetTrain.literals());
            Clause evidence = new Clause(Sugar.union(
                    Combinatorics.randomCombination(Sugar.listFromCollections(datasetTest.literals()),i).toList(),
                    new HashSet<Literal>()//datasetTrain.literals()
                    ));

            {
                Set<Literal> predicted;
                try {
                    predicted = ml.solve(evidence.literals()).r;
                    Double level = ml.solve(evidence.literals()).s;
                    Collection<Literal> more = Sugar.<Literal>collectionDifference(predicted, evidence.literals());
                    System.out.println("level: "+level);
                } catch (SuperTweetyContradictionException stce) {
                    predicted = evidence.literals();
                }

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
                System.out.println("Hamming error: " + hammingError + ", baseline: " + hammingErrorBaseline);
            }
            {
                Set<Literal> predicted;
                try {
                    predicted = ml.solve(Double.POSITIVE_INFINITY, evidence.literals());
                    if (predicted == null){
                        predicted = evidence.literals();
                    }
                } catch (SuperTweetyContradictionException stce){
                    predicted = evidence.literals();
                }

                Set<Literal> positivePredicted = new HashSet<Literal>();
                Set<Literal> negativePredicted = new HashSet<Literal>();
                for (Literal l : predicted){
                    if (l.isNegated()){
                        negativePredicted.add(l);
                    } else {
                        positivePredicted.add(l);
                    }
                }

                System.out.println("predicted (hard rules only): "+predicted.size());

                int hammingError = 0;
                int hammingErrorBaseline = Sugar.setDifference(groundTruth, evidence.literals()).size();
                for (Literal l : groundTruth){
                    if (!positivePredicted.contains(l)){
                        hammingError++;
                    }
                }
                for (Literal l : positivePredicted){
                    if (!groundTruth.contains(l)){
                        hammingError++;
                    }
                }
                System.out.println("Hamming error (hard rules only): "+hammingError+", baseline: "+hammingErrorBaseline);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        yeast();
        //uwcse();
    }

}
