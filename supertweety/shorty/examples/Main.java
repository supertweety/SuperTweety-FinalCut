/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.shorty.examples;

import ida.ilp.logic.Clause;
import ida.utils.MutableDouble;
import supertweety.possibilistic.RelationalPossibilisticLogicTheory;
import supertweety.RelationalDataset;
import supertweety.shorty.ModelCounterFactory;
import supertweety.shorty.ShortConstraintLearner;
import supertweety.shorty.UnaryConstraintLearner;
import temp.UWCSE;

/**
 * Created by kuzelkao_cardiff on 08/12/17.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int k = 3;
        RelationalDataset dataset = new RelationalDataset(UWCSE.uwcse());
        ModelCounterFactory mcf = new ModelCounterFactory();

        UnaryConstraintLearner ucl = new UnaryConstraintLearner(new Clause(dataset.literals()), 4);
        ShortConstraintLearner scl = new ShortConstraintLearner(dataset, 2, Integer.MAX_VALUE);


        RelationalPossibilisticLogicTheory rplt = new RelationalPossibilisticLogicTheory();
        rplt.addAllHardRules(ucl.learnRules());
        rplt.addAllHardRules(scl.learnConstraints());


        rplt.addRule(Clause.parse("student(V0), !@alldiff(V0, V2, V3)"), 0.1);
        MutableDouble logLik = new MutableDouble(Double.NaN);
        RelationalPossibilisticLogicTheory ml = rplt.maximumLikelihoodWeightEstimation(dataset, k, mcf, logLik);
        System.out.println("logLik: "+logLik);

        rplt.addRule(Clause.parse("!tempAdvisedBy(V2, V0), Faculty(V0), !@alldiff(V0, V2, V3)"), 0.2);
        ml = rplt.maximumLikelihoodWeightEstimation(dataset, k, mcf, logLik);
        System.out.println("logLik: "+logLik);

        System.out.println(ml);
        System.out.println("falsity weight: "+ml.weightOfFalsity());

//        double match = dataset.approxMatchingSubsets(Sugar.list(Clause.parse("student(X),!@alldiff(X,Y,Z)")), k);
//        System.out.println("match: "+match);
//        System.out.println(match/Math.exp(Combinatorics.logBinomial(dataset.constants().size(), k))+" "+Math.exp(Combinatorics.logBinomial(dataset.constants().size(), k)));
    }

}
