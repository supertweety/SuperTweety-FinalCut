/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.shorty;

import ida.ilp.logic.*;
import ida.ilp.logic.subsumption.Matching;
import ida.ilp.logic.subsumption.SpecialVarargPredicates;
import ida.utils.MutableDouble;
import ida.utils.Sugar;
import ida.utils.tuples.Pair;
import supertweety.RelationalDataset;
import supertweety.possibilistic.RelationalPossibilisticLogicTheory;
import temp.Yeast;

import java.util.*;

/**
 * Created by kuzelkao_cardiff on 06/02/17.
 */
public class Stratifier {

    private double minRelativeLikelihoodImprovement = 0;

    private int maxReinsertIters = 2;

    private int cardinality;

    private RelationalDataset dataset;

    private List<Clause> rules;

    private List<Clause> hardConstraints;

    private ModelCounterFactory modelCounterFactory = new ModelCounterFactory();


    public Stratifier(RelationalDataset dataset, List<Clause> rules, List<Clause> hardConstraints, int cardinality){
        this.dataset = dataset;
        this.cardinality = cardinality;
        this.hardConstraints = hardConstraints;
        this.rules = addAllDiffConstraints(rules);
    }

    public static RelationalPossibilisticLogicTheory postprune(RelationalPossibilisticLogicTheory rplt, RelationalDataset dataset, int cardinality, ModelCounterFactory modelCounterFactory){
        MutableDouble logLik = new MutableDouble();
        rplt.maximumLikelihoodWeightEstimation(dataset, cardinality, modelCounterFactory, logLik);
        rplt = rplt.copy();
        outerLoop: do {
            for (double weight : rplt.weights()) {
                for (Clause rule : rplt.level(weight)) {
                    RelationalPossibilisticLogicTheory copy = rplt.copy();
                    copy.remove(rule, weight);
                    MutableDouble logLik2 = new MutableDouble();
                    copy.maximumLikelihoodWeightEstimation(dataset, cardinality, modelCounterFactory, logLik2);
                    System.out.println(logLik+", new: "+logLik2);
                    if (logLik2.value() >= logLik.value()){
                        System.out.println("Removing " + rule);
                        rplt = copy;
                        logLik = logLik2;
                        continue outerLoop;
                    } else {
                        System.out.println("Not removing "+rule);
                    }
                }
            }
        } while (false);
        return rplt;
    }

    private List<Clause> addAllDiffConstraints(List<Clause> rules){
        List<Clause> retVal = new ArrayList<Clause>();
        for (Clause c : rules){
            Set<Variable> vars = c.variables();
            if (vars.size() <= cardinality) {
                Literal alldiff = new Literal(SpecialVarargPredicates.ALLDIFF, true, cardinality);
                int j = 0;
                for (Variable v : c.variables()) {
                    alldiff.set(v, j++);
                }
                if (j < cardinality){
                    for (Variable v : LogicUtils.freshVariables(vars, cardinality-vars.size())){
                        alldiff.set(v, j++);
                    }
                }
                retVal.add(new Clause(Sugar.union(c.literals(), alldiff)));
            }
        }
        return retVal;
    }

    public RelationalPossibilisticLogicTheory stratify(){
        Matching matching = new Matching();
        List<Clause> stratification = new ArrayList<Clause>();
        Set<Clause> toInsert = new LinkedHashSet<Clause>();
        toInsert.addAll(this.rules);
        int reinsertIters = 0;
        double bestLogLik = Double.NEGATIVE_INFINITY;
        do {
            Set<Clause> nextToReinsert = new HashSet<Clause>();
            for (Clause rule : toInsert) {
                if (Globals.verbose) {
                    System.out.println("Trying to add: " + rule);
                }

                if (TheorySimplifier.isImplied(rule, this.hardConstraints, this.cardinality)){
                    if (Globals.verbose){
                        System.out.println("Rule "+rule+" is implied by the hard rules.");
                    }
                    continue;
                }

                List<Clause> bestCandidateStratification = null;
                MutableDouble logLik = new MutableDouble(){
                    public void set(double d){
                        super.set(d);
                        System.out.println("logLik: "+d);
                    }
                };
                int startI = 0;

                for (int i = stratification.size()-1; i >= 0; i--){
                    if (matching.subsumption(preprocessForSubsumptionCheck(stratification.get(i)), preprocessForSubsumptionCheck(rule))){
                        System.out.println(preprocessForSubsumptionCheck(stratification.get(i))+" subsumes "+preprocessForSubsumptionCheck(rule));
                        startI = i+1;
                        break;
                    }
                }
                if (Globals.verbose) {
                    System.out.println("startI: " + startI);
                }

                double currentBestLogLik = Double.NEGATIVE_INFINITY;
                for (int i = startI; i < stratification.size() + 1; i++) {
                    List<Clause> candidateStratification = new ArrayList<Clause>();
                    candidateStratification.addAll(stratification.subList(0, i));
                    candidateStratification.add(rule);
                    candidateStratification.addAll(stratification.subList(i, stratification.size()));
                    RelationalPossibilisticLogicTheory rplt = fromStratification(candidateStratification);
                    rplt.maximumLikelihoodWeightEstimation(dataset, cardinality, modelCounterFactory, logLik);
                    if (logLik.value() > currentBestLogLik) {
                        currentBestLogLik = logLik.value();
                        bestCandidateStratification = candidateStratification;
                    }
                }
                if (bestCandidateStratification != null && (1.0+minRelativeLikelihoodImprovement)*Math.abs(currentBestLogLik) < Math.abs(bestLogLik)) {
                    bestLogLik = currentBestLogLik;
                    stratification = bestCandidateStratification;
                    stratification = TheorySimplifier.removeDrownedLevels(stratification, hardConstraints, cardinality);

                    List<Clause> implied = TheorySimplifier.rulesImpliedByRankedKB(stratification, hardConstraints, cardinality);
                    for (Clause impliedClause : implied){
                        if (!TheorySimplifier.isImplied(impliedClause, hardConstraints, cardinality)) {
                            nextToReinsert.add(impliedClause);
                        }
                    }

                    stratification = TheorySimplifier.simplifyRankedKB(stratification, hardConstraints, cardinality);
                }
                System.out.println("Best stratification: " + bestLogLik);
                for (Clause c : stratification) {
                    System.out.println(c);
                }
                System.out.println("---");
            }
            toInsert = nextToReinsert;
            for (Clause c : nextToReinsert){
                System.out.println("to reinsert: "+c);
            }
            reinsertIters++;
        } while (!toInsert.isEmpty() && reinsertIters < maxReinsertIters);
        return fromStratification(stratification).maximumLikelihoodWeightEstimation(dataset, cardinality, modelCounterFactory);
    }

    private RelationalPossibilisticLogicTheory fromStratification(List<Clause> stratification){
        RelationalPossibilisticLogicTheory rplt = new RelationalPossibilisticLogicTheory();
        rplt.addAllHardRules(this.hardConstraints);
        double i = 1;
        for (Clause hc : stratification){
            rplt.addRule(hc, i++/stratification.size());
        }
        //System.out.println("fromStratification: "+rplt);
        return rplt;
    }

    private Clause preprocessForSubsumptionCheck(Clause c) {
        List<Literal> literals = new ArrayList<Literal>();
        for (Literal l : c.literals()) {
            if (l.predicate().equals(SpecialVarargPredicates.ALLDIFF)) {
                literals.add(l.negation());
            } else {
                Literal newLiteral = new Literal(((l.isNegated()) ? "~!" : "~") + l.predicate(), l.arity());
                for (int i = 0; i < newLiteral.arity(); i++) {
                    newLiteral.set(l.get(i), i);
                }
                literals.add(newLiteral);
            }
        }
        return new Clause(literals);
    }

    public static void main(String[] args) throws Exception {
        int k = 4;
        //Dataset dataset = new Dataset(UWCSE.uwcse());
        RelationalDataset dataset = new RelationalDataset(Yeast.yeast("train"));

        ModelCounterFactory mcf = new ModelCounterFactory();


        TheoryLearner tl = new TheoryLearner(dataset);
        tl.setMaxHardConstraintLength(2);
        tl.setCardinality(k);
        tl.setMaxRuleLength(4);
        //for Yeast
        tl.setMaxRuleLearningIters(1);

        tl.setDesiredNumExamples(10000);

        Pair<List<HornClause>,List<Clause>> learned = tl.learn();

        List<Clause> candidates = new ArrayList<Clause>();
        for (HornClause hc : learned.r){
            candidates.add(hc.toClause());
        }

        Stratifier stratifier = new Stratifier(dataset, candidates, learned.s, k);

        RelationalPossibilisticLogicTheory ml = stratifier.stratify();

        System.out.println(ml);
        System.out.println("falsity weight: "+ml.weightOfFalsity());

//        double match = dataset.approxMatchingSubsets(Sugar.list(Clause.parse("student(X),!@alldiff(X,Y,Z)")), k);
//        System.out.println("match: "+match);
//        System.out.println(match/Math.exp(Combinatorics.logBinomial(dataset.constants().size(), k))+" "+Math.exp(Combinatorics.logBinomial(dataset.constants().size(), k)));
    }
}
