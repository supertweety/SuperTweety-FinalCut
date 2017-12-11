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
import ida.utils.Sugar;
import ida.utils.tuples.Pair;
import supertweety.possibilistic.PossibilisticLogicTheory;
import supertweety.BinaryDataset;
import supertweety.probabilistic.DensityEstimationTree;

import java.io.FileReader;
import java.io.Reader;
import java.util.Random;
import java.util.Set;

/**
 * Created by kuzelkao_cardiff on 16/03/16.
 */
public class AnimalsExperiment {

    public static void main(String[] args) throws Exception {
        Reader reader = new FileReader("/Users/kuzelkao_cardiff/Dropbox/Experiments/ECAI16/animals.csv");
        BinaryDataset dataset = BinaryDataset.readCSV(reader);
        Pair<BinaryDataset,BinaryDataset> split = dataset.randomSplit(0.75, new Random(2016));
        DensityEstimationTree et = DensityEstimationTree.learn(split.r, 1);
        PossibilisticLogicTheory plt = et.toPossibilisticLogic();
        System.out.println(plt);
        Set<Clause> hardRules = Sugar.<Clause>set(
                Clause.parse("!Black(x),!White(x)"),
                Clause.parse("!Black(x),!Blue(x)"),
                Clause.parse("!Black(x),!Brown(x)"),
                Clause.parse("!Black(x),!Gray(x)"),
                Clause.parse("!Black(x),!Yellow(x)"),
                Clause.parse("!Black(x),!Orange(x)"),
                Clause.parse("!Blue(x),!White(x)"),
                Clause.parse("!Blue(x),!Brown(x)"),
                Clause.parse("!Blue(x),!Gray(x)"),
                Clause.parse("!Blue(x),!Yellow(x)"),
                Clause.parse("!Blue(x),!Orange(x)"),
                Clause.parse("!Brown(x),!White(x)"),
                Clause.parse("!Brown(x),!Gray(x)"),
                Clause.parse("!Brown(x),!Yellow(x)"),
                Clause.parse("!Brown(x),!Orange(x)"),
                Clause.parse("!Gray(x),!White(x)"),
                Clause.parse("!Gray(x),!Yellow(x)"),
                Clause.parse("!Gray(x),!Orange(x)"),
                Clause.parse("!White(x),!Yellow(x)"),
                Clause.parse("!White(x),!Orange(x)"),
                Clause.parse("!Yellow(x),!Orange(x)")
                );
        for (Clause hardRule : hardRules) {
            System.out.println(hardRule);
        }
//        for (DefaultRule rule : DefaultsExtractor.extractSystemPDefaults(plt, hardRules, 2)){
//            System.out.println(rule+" cp:"+DensityEstimationTree.conditionalProbability(rule, split.r));
//        }


    }

}
