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
import ida.ilp.logic.special.IsoClauseWrapper;
import ida.ilp.logic.subsumption.Matching;
import ida.ilp.logic.subsumption.SpecialVarargPredicates;
import ida.ilp.logic.subsumption.SubsumptionEngineJ2;
import ida.utils.MutableDouble;
import ida.utils.Sugar;
import ida.utils.tuples.Pair;
import supertweety.RelationalDataset;
import temp.UWCSE;

import java.util.*;

/**
 * Created by ondrejkuzelka on 05/02/17.
 */
public class TheoryLearner {

    private boolean removeRulesWithSingletons = true;

    private int minSupport = 2;

    private int datasetMode;

    private int k = 3;

    private int maxRuleLength = 2;

    private int maxHardConstraintLength = 2;

    private int maxUnaryHardConstraintLength = 5;

    private int beamSize = 100;

    private int maxRuleLearningIters = 5;

    private int desiredNumExamples = 1000;

    private Random random = new Random(TheorySimplifier.class.getName().hashCode());

    private RelationalDataset dataset;

    private Clause db;

    private List<Clause> hardConstraints = new ArrayList<Clause>();

    public TheoryLearner(RelationalDataset dataset){
        this.dataset = dataset;
        this.datasetMode = dataset.datasetMode();
        this.db = new Clause(dataset.literals());
    }

    protected void learnHardConstraints(){
        UnaryConstraintLearner ucl = new UnaryConstraintLearner(this.db, this.getMaxUnaryHardConstraintLength());
        if (Globals.verbose){
            System.out.println("Searching for unary hard constraints...");
        }
        this.hardConstraints.addAll(ucl.learnRules());
        if (Globals.verbose){
            System.out.println("Searching for hard constraints...");
        }
        ShortConstraintLearner scl = new ShortConstraintLearner(this.dataset, getMaxHardConstraintLength(), Integer.MAX_VALUE);
        this.hardConstraints.addAll(scl.learnConstraints());
        if (Globals.verbose){
            System.out.println("Simplifying hard constraints...");
        }
        int maxVarsInClause = 0;
        for (Clause c : this.hardConstraints){
            maxVarsInClause = Math.max(maxVarsInClause, c.variables().size());
        }
        this.hardConstraints = TheorySimplifier.simplify(this.hardConstraints, maxVarsInClause);
    }

    protected List<HornClause> learnRules(){
        Set<IsoClauseWrapper> icws = new HashSet<IsoClauseWrapper>();
        for (Pair<String,Integer> predicate : this.dataset.allPredicates()) {
            if (Globals.verbose) {
                System.out.println("\nLearning rules for " + predicate.r + "/" + predicate.s + "...");
                System.out.println("\tBuilding positive examples...");
            }
            MutableDouble numPos = new MutableDouble(0);
            List<Literal> positive = this.positiveExamples(predicate.r, predicate.s, numPos);
            if (Globals.verbose) {
                System.out.println("\tBuilding negative examples...");
            }

            MutableDouble numNeg = new MutableDouble(0);
            List<Literal> negative = this.negativeExamples(predicate.r, predicate.s, numNeg);
            if (Globals.verbose) {
                System.out.println("Num pos examples: " + numPos + ", num neg examples: " + numNeg);
            }

            if (numNeg.value() > 0) {
                RuleLearner rl = new RuleLearner(discrDataset(positive, negative, numPos.value()/(double)positive.size(), numNeg.value()/(double)negative.size()), this.getK());
                rl.setMinSupport(this.minSupport);
                for (int length = 1; length <= this.getMaxRuleLength(); length++) {
                    if (Globals.verbose) {
                        System.out.println("Learning rules of maximum length " + length + "...");
                    }
                    List<HornClause> currentLengthCandidates = new ArrayList<HornClause>();

                    for (int j = 0; j < this.getMaxRuleLearningIters(); j++) {
                        HornClause hc = rl.beamSearch(new ArrayList<HornClause>()/*currentLengthCandidates*/, currentLengthCandidates, this.getBeamSize(), length);
                        if (hc.body().getLiteralsByPredicate(SpecialVarargPredicates.FALSE).isEmpty()) {
                            currentLengthCandidates.add(hc);
                            if (Globals.verbose) {
                                System.out.println("Added candidate rule: " + hc+" ");
                            }
                        } else {
                            break;
                        }
                    }

                    for (HornClause hc : currentLengthCandidates) {
                        //TODO - check accuracy threshold on validation sets
                        if (!removeRulesWithSingletons || !containsSingleton(hc, dataset.getMatchingObject().examples().get(0))) {
                            icws.add(new IsoClauseWrapper(hc.toClause()));
                        } else {
                            if (Globals.verbose) {
                                System.out.println("Rule not added because it contains a \"singleton\" variable (i.e. a variable which matches only one constant in the dataset).");
                            }
                        }
                    }
                }
            }
        }
        List<HornClause> retVal = new ArrayList<HornClause>();
        for (IsoClauseWrapper icw : icws){
            retVal.add(new HornClause(icw.getOriginalClause()));
        }
        return retVal;
    }

    private RelationalDataset discrDataset(List<Literal> positive, List<Literal> negative, double posWeight, double negWeight){
        RelationalDataset retVal = new RelationalDataset(this.db, positive, negative, this.datasetMode);
        retVal.setWeights(posWeight, negWeight);
        return retVal;
    }

    private List<Literal> positiveExamples(String predicateName, int arity, MutableDouble estimatedCount){
        List<Literal> retVal = new ArrayList<Literal>();
        for (Literal l : this.db.getLiteralsByPredicate(predicateName)){
            if (l.arity() == arity){
                retVal.add(l);
            }
        }
        if (estimatedCount != null){
            estimatedCount.set(retVal.size());
        }
        if (retVal.size() > this.getDesiredNumExamples()){
            Collections.shuffle(retVal, this.random);
            retVal = retVal.subList(0, this.getDesiredNumExamples());
        }
        return retVal;
    }

    private List<Literal> negativeExamples(String predicateName, int arity, MutableDouble estimatedCount){
        if (this.datasetMode == RelationalDataset.SUBSAMPLING_MODE){
            Set<Literal> possibleExamples = new HashSet<Literal>();
            possibleExamples.addAll(RelationalDataset.possibleExamples(new Clause(dataset.subsampledDatasets()[0].literals()), predicateName, arity, this.hardConstraints, this.getDesiredNumExamples(), this.random, estimatedCount));
            Set<Literal> setDifference = Sugar.setDifference(possibleExamples, this.db.literals());
            if (estimatedCount != null){
                estimatedCount.set(estimatedCount.value()*setDifference.size()/(double)possibleExamples.size());
            }
            return Sugar.listFromCollections(setDifference);
        } /*else if (this.datasetMode == Dataset.SUBSET_MODE) {
            List<Literal> possibleExamples = Dataset.possibleExamples(this.db, predicateName, arity, this.hardConstraints, this.getDesiredNumExamples(), this.random, estimatedCount);
            possibleExamples = Sugar.listDifference(possibleExamples, this.db.literals());
            return possibleExamples;
        } */ else {
            throw new IllegalArgumentException();
        }
    }

    //singleton = variable which matches only one constant in the example
    private boolean containsSingleton(HornClause rule, SubsumptionEngineJ2.ClauseE db){
        Matching m = new Matching();
        SubsumptionEngineJ2.ClauseC c = m.createClauseC(rule.body());
        //System.out.println(rule.body());
        for (Variable v : rule.body().variables()) {
            Pair<Term[],List<Term[]>> subs = m.getEngine().allSolutions(c, db, 2, 1, v);
            if (subs.s.size() == 1){
                return true;
            }
        }
        return false;
    }

    public Pair<List<HornClause>,List<Clause>> learn(){
        this.learnHardConstraints();
        List<HornClause> rules = this.learnRules();
        return new Pair<List<HornClause>,List<Clause>>(rules, this.hardConstraints);
    }

    public void setDatasetMode(int datasetMode){
        this.datasetMode = datasetMode;
    }


    public int getK() {
        return k;
    }

    public void setCardinality(int k) {
        this.k = k;
    }

    public int getMaxRuleLength() {
        return maxRuleLength;
    }

    public void setMaxRuleLength(int maxRuleLength) {
        this.maxRuleLength = maxRuleLength;
    }

    public int getMaxHardConstraintLength() {
        return maxHardConstraintLength;
    }

    public void setMaxHardConstraintLength(int maxHardConstraintLength) {
        this.maxHardConstraintLength = maxHardConstraintLength;
    }

    public int getMaxUnaryHardConstraintLength() {
        return maxUnaryHardConstraintLength;
    }

    public void setMaxUnaryHardConstraintLength(int maxUnaryHardConstraintLength) {
        this.maxUnaryHardConstraintLength = maxUnaryHardConstraintLength;
    }

    public int getBeamSize() {
        return beamSize;
    }

    public void setBeamSize(int beamSize) {
        this.beamSize = beamSize;
    }

    public int getMaxRuleLearningIters() {
        return maxRuleLearningIters;
    }

    public void setMaxRuleLearningIters(int maxRuleLearningIters) {
        this.maxRuleLearningIters = maxRuleLearningIters;
    }

    public int getDesiredNumExamples() {
        return desiredNumExamples;
    }

    public void setDesiredNumExamples(int desiredNumExamples) {
        this.desiredNumExamples = desiredNumExamples;
    }

    public void setMinSupport(int minSupport) {
        this.minSupport = minSupport;
    }

    public static void main(String[] args){
        Clause db = UWCSE.uwcse();
        RelationalDataset dataset = new RelationalDataset(db, RelationalDataset.SUBSAMPLING_MODE);
        TheoryLearner tl = new TheoryLearner(dataset);
        tl.learnHardConstraints();
        tl.learnRules();
    }
}
