/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.possibilistic;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.LogTransformedPosynomial;
import com.joptimizer.optimizers.JOptimizer;
import com.joptimizer.optimizers.OptimizationRequest;
import ida.ilp.logic.Clause;
import ida.ilp.logic.Constant;
import ida.ilp.logic.Literal;
import ida.ilp.logic.LogicUtils;
import ida.utils.Cache;
import ida.utils.Sugar;
import ida.utils.VectorUtils;
import ida.utils.collections.DoubleCounters;
import ida.utils.collections.MultiMap;
import ida.utils.tuples.Pair;
import supertweety.BinaryDataset;
import supertweety.Globals;
import supertweety.SuperTweetyContradictionException;
import supertweety.logic.GroundTheorySolver;
import supertweety.logic.ModelCounter;
import supertweety.logic.TheorySolver;
import supertweety.misc.Logging;
import supertweety.probabilistic.DiscreteProbabilityDistribution;

import java.math.BigInteger;
import java.util.*;

import static ida.utils.Sugar.collectionDifference;
import static ida.utils.Sugar.union;
/**
 * Created by kuzelkao_cardiff on 19/01/15.
 */
public class PossibilisticLogicTheory implements DiscreteProbabilityDistribution {

    //primary attributes:

    private Set<Clause> hardRules = new HashSet<Clause>();

    private MultiMap<Double, Clause> rules = new MultiMap<Double, Clause>();

    private double weightOfFalsity = 0;

    private Set<Constant> additionalConstants = new HashSet<Constant>();

    private Set<Literal> additionalGroundAtoms = new HashSet<Literal>();

    // auxiliary derived attributes:

    private TreeSet<Double> weights = new TreeSet<Double>();

    private Boolean isGround = null;

    public static boolean USE_CACHING = false;

    private Boolean isConsistent = null;

    private final static Cache<Set<Clause>,Set<Literal>> cache = new Cache<Set<Clause>,Set<Literal>>();

    private final static Set<Literal> NIL_SOLUTION = new HashSet<Literal>();


    static {
        Logging.init();
    }

    public PossibilisticLogicTheory copy(){
        PossibilisticLogicTheory retVal = new PossibilisticLogicTheory();
        retVal.hardRules.addAll(this.hardRules);
        retVal.weights.addAll(this.weights);
        retVal.rules.putAll(this.rules);
        retVal.isGround = this.isGround;
        retVal.additionalGroundAtoms.addAll(this.additionalGroundAtoms);
        retVal.weightOfFalsity = this.weightOfFalsity();
        return retVal;
    }

    public PossibilisticLogicTheory(){}

    public PossibilisticLogicTheory(MultiMap<Double, Clause> rules) {
        this(rules, null);
    }

    public PossibilisticLogicTheory(List<Pair<Clause, Double>> rules){
        this(rules, null);
    }

    public PossibilisticLogicTheory(MultiMap<Double, Clause> rules, Set<Clause> hardRules) {
        this.set(rules);
        if (hardRules != null) {
            this.hardRules = hardRules;
        }
    }

    public PossibilisticLogicTheory(List<Pair<Clause, Double>> rules, Set<Clause> hardRules){
        MultiMap<Double,Clause> mm = new MultiMap<Double,Clause>();
        for (Pair<Clause,Double> rule : rules){
            mm.put(rule.s, rule.r);
        }
        this.set(mm);
        if (hardRules != null){
            this.hardRules = hardRules;
        }
    }

    public static PossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification, List<Double> weights){
        return fromStratification(stratification, weights, null);
    }

    public static PossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification, List<Double> weights, Set<Clause> hardRules){
        MultiMap<Double,Clause> rules = new MultiMap<Double,Clause>();
        for (int i = stratification.size()-1; i >= 0; i--){
            rules.putAll(weights.get(i), stratification.get(i));
        }
        return new PossibilisticLogicTheory(rules, hardRules);
    }

    public static PossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification){
        return fromStratification(stratification, new HashSet<Clause>());
    }

    public static PossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification, Set<Clause> hardRules){
        MultiMap<Double,Clause> rules = new MultiMap<Double,Clause>();
        double d = stratification.size()+1;
        for (int i = stratification.size()-1; i >= 0; i--){
            rules.putAll((i+1)/d, stratification.get(i));
        }
        return new PossibilisticLogicTheory(rules, hardRules);
    }

    public static PossibilisticLogicTheory merge(PossibilisticLogicTheory bottom, PossibilisticLogicTheory top){
        List<Set<Clause>> levels = new ArrayList<Set<Clause>>();
        levels.addAll(bottom.toLevelList());
        levels.addAll(top.toLevelList());
        return fromStratification(levels);
    }

    public void setWeights(List<Double> newWeights, double newWeightOfFalsity){
        MultiMap<Double,Clause> newMM = new MultiMap<Double,Clause>();
        TreeSet<Double> newWeightsTreeSet = new TreeSet<Double>();
        int i = 0;
        for (double d : this.weights){
            newMM.putAll(newWeights.get(i), this.rules.get(d));
            newWeightsTreeSet.add(newWeights.get(i));
            i++;
        }
        this.rules = newMM;
        this.weights = newWeightsTreeSet;
        this.weightOfFalsity = newWeightOfFalsity;
    }

    private void set(MultiMap<Double,Clause> rules){
        for (Map.Entry<Double, Set<Clause>> entry : rules.entrySet()) {
            for (Clause c : entry.getValue()) {
                this.rules.put(entry.getKey(), c);
            }
            this.weights.add(entry.getKey());
        }
        this.isGround = null;
        this.isConsistent = null;
    }

    public void addRule(Clause rule, double weight){
        this.rules.put(weight, rule);
        this.weights.add(weight);
        if (!LogicUtils.isGround(rule)){
            this.isGround = Boolean.FALSE;
        }
        this.isConsistent = null;
    }

    public void addHardRule(Clause hardRule){
        this.hardRules.add(hardRule);
        if (!LogicUtils.isGround(hardRule)){
            this.isGround = false;
        }
        this.isConsistent = null;
    }

    public void remove(Clause rule, double weight){
        this.rules.remove(weight, rule);
        if (this.rules.get(weight).isEmpty()){
            this.weights.remove(weight);
        }
        this.isConsistent = null;
    }

    public void removeHardRule(Clause hardRule){
        this.hardRules.remove(hardRule);
        this.isGround = null;
        this.isConsistent = null;
    }

    public void remove(Clause rule){
        for (double weight : new ArrayList<Double>(this.weights)){
            this.remove(rule, weight);
        }
    }

    public void addAll(Collection<Clause> rules, double weight){
        for (Clause rule : rules){
            this.addRule(rule, weight);
        }
    }

    public void addAllHardRules(Collection<Clause> hardRules){
        for (Clause hardRule : hardRules){
            this.addHardRule(hardRule);
        }
    }

    public void addAdditionalGroundAtom(Literal literal){
        if (!LogicUtils.isGround(literal)){
            throw new IllegalArgumentException("The literal must be ground but the current one is not: "+literal);
        }
        if (literal.isNegated()){
            this.additionalGroundAtoms.add(literal.negation());
        } else {
            this.additionalGroundAtoms.add(literal);
        }
    }

    public void addAdditionalConstant(Constant constant){
        this.additionalConstants.add(constant);
    }

    public void addAdditionalConstants(Collection<Constant> constants){
        this.additionalConstants.addAll(constants);
    }

    public void addAllAdditionalGroundAtoms(Collection<Literal> additionalAtoms){
        for (Literal l : additionalAtoms){
            this.addAdditionalGroundAtom(l);
        }
    }

    public void setWeightOfFalsity(double weightOfFalsity){
        this.weightOfFalsity = weightOfFalsity;
    }

    // End of edit-methods...
    // and beginning of inference methods

    public Pair<Set<Literal>,Double> solve(Collection<Literal> evidence) {
        double[] levels = VectorUtils.toDoubleArray(this.rules.keySet());
        Arrays.sort(levels);
        int min = 0;
        int max = levels.length-1;
        Set<Literal> solution = null;
        double solutionLevel = Double.NaN;
        while (max >= min){
            int mid = (min+max)/2;
            Set<Literal> currentSolution = null;
            if ((currentSolution = this.solve(levels[mid], evidence)) != null){
                max = mid-1;
                solution = currentSolution;
                solutionLevel = levels[mid];
            } else {
                min = mid+1;
            }
        }
        //Maybe it completely drowns but it still satisfies the hard constraints...
        if (solution == null){
            solution = this.solve(Double.POSITIVE_INFINITY, evidence);
            if (solution == null){
                throw new SuperTweetyContradictionException("Hard rules of the theory are violated");
            } else {
                return new Pair<Set<Literal>,Double>(solution, Double.POSITIVE_INFINITY);
            }
        } else {
            return new Pair<Set<Literal>,Double>(solution, solutionLevel);
        }
    }

    private static double cached = 0, noncached = 0;

    private Set<Literal> solveSatProblem(Set<Clause> satProblem){
        Set<Literal> solution;
        if (USE_CACHING) {
            synchronized (cache) {
                solution = cache.get(satProblem);
            }
            if (solution != null) {
                cached++;
                if (solution == NIL_SOLUTION) {
                    return null;
                }
                return solution;
            } else {
                noncached++;
                if (!this.isGround()) {
                    Literal constantIntroduction = null;
                    if (!this.additionalConstants.isEmpty()){
                        constantIntroduction = new Literal("", additionalConstants.size());
                        int i = 0;
                        for (Constant c : additionalConstants){
                            constantIntroduction.set(c, i++);
                        }
                    }
                    TheorySolver ts = new TheorySolver();
                    solution = ts.solve(satProblem, Sugar.<Literal>set(), constantIntroduction == null ? Sugar.<Literal>set() : Sugar.<Literal>set(constantIntroduction));
                } else {
                    GroundTheorySolver gts = new GroundTheorySolver(satProblem);
                    solution = gts.solve();
                }
                synchronized (cache) {
                    if (solution == null) {
                        cache.put(satProblem, NIL_SOLUTION);
                    } else {
                        cache.put(satProblem, solution);
                    }
                }
                return solution;
            }
        } else {
            if (!this.isGround()) {
                Literal constantIntroduction = null;
                if (!this.additionalConstants.isEmpty()){
                    constantIntroduction = new Literal("", additionalConstants.size());
                    int i = 0;
                    for (Constant c : additionalConstants){
                        constantIntroduction.set(c, i++);
                    }
                }
                TheorySolver ts = new TheorySolver();
                solution = ts.solve(satProblem, Sugar.<Literal>set(), constantIntroduction == null ? Sugar.<Literal>set() : Sugar.<Literal>set(constantIntroduction));
            } else {
                GroundTheorySolver gts = new GroundTheorySolver(satProblem);
                solution = gts.solve();
            }
            return solution;
        }
    }

    public void removeRulesDirectlyFalsifiedByEvidence(Set<Literal> evidence){
        for (Clause c : Sugar.flatten(this.toLevelList())){
            if (isFalsified(c, evidence)) {
                this.remove(c);
            }
        }
    }

    public boolean implies(Collection<Literal> evidence, Literal literal){
        return implies(evidence, new Clause(literal));
    }

    public boolean implies(Collection<Literal> antecedent, Clause consequent){
        if (isTautology(consequent)){
            return true;
        }
        Pair<Set<Literal>,Double> aSolutionForEvidence = solve(antecedent);
        Set<Literal> solution = solveSatProblem(Sugar.union(this.getAlphaCut(aSolutionForEvidence.s), wrapLiteralsToClauses(antecedent), wrapLiteralsToClauses(LogicUtils.flipSigns(consequent).literals())));
        if (solution == null){
            return true;
        }
        return false;
    }

    public boolean isGround(){
        if (this.isGround == null){
            boolean ig = true;
            outerLoop: for (Map.Entry<Double,Set<Clause>> entry : this.rules.entrySet()){
                for (Clause c : entry.getValue()){
                    if (!LogicUtils.isGround(c)){
                        ig = false;
                        break outerLoop;
                    }
                }
            }
            for (Clause c : hardRules){
                if (!LogicUtils.isGround(c)){
                    ig = false;
                    break;
                }
            }
            this.isGround = ig;
        }
        return this.isGround;
    }

    private static boolean isTautology(Clause clause){
        for (Literal l : clause.literals()){
            if (clause.containsLiteral(l.negation())){
                return true;
            }
        }
        return false;
    }

    private boolean isCompletePossibleWorld(Collection<Literal> evidence){
        Set<Literal> literalsInEvidence = new HashSet<Literal>(evidence);
        Set<Literal> atomsInEvidence = new HashSet<Literal>();
        for (Literal literal : evidence){
            if (literal.isNegated()){
                atomsInEvidence.add(literal.negation());
            } else {
                atomsInEvidence.add(literal);
            }
        }
        if (literalsInEvidence.size() != atomsInEvidence.size()){
            return false;
        }
        Set<Literal> allAtoms = allAtoms();
        return allAtoms.equals(atomsInEvidence);
    }

    public Set<Literal> solve(double alpha, Collection<Literal> evidence){
        if (isCompletePossibleWorld(evidence)){
            Set<Literal> evidenceAsSet = Sugar.setFromCollections(evidence);
            if (LogicUtils.isModelOf(evidenceAsSet, Sugar.union(this.hardRules, this.getAlphaCut(alpha)))){
                return evidenceAsSet;
            } else {
                return null;
            }
        } else {
            return solveSatProblem(Sugar.union(this.hardRules, this.getAlphaCut(alpha), wrapLiteralsToClauses(evidence)));
        }
    }

    public boolean isConsistent(){
        if (this.isConsistent == null){
            Set<Literal> solution = this.solveSatProblem(Sugar.setFromCollections(this.hardRules, Sugar.flatten(this.rules.values())));
            this.isConsistent = solution != null;
        }
        return this.isConsistent;
    }

    // Beginning probabilistic methods...

    public double probability(Collection<Clause> query, ModelCounter modelCounter){
        PossibilisticLogicTheory plt = this.copy();
        plt.addAllHardRules(query);
        Set<Literal> allAtoms = union(allAtoms(Sugar.flatten(plt.toLevelList())), allAtoms(plt.hardRules()), plt.additionalGroundAtoms);
        List<Double> weights = new ArrayList<Double>();
        if (plt.isConsistent()){
            plt.addRule(new Clause(new HashSet<Literal>()), plt.weightOfFalsity());
        }
        weights.addAll(plt.weights());
        List<Double> logModelCounts = new ArrayList<Double>();
        for (double alpha : plt.weights()){
            List<Clause> alphaCut = plt.getAlphaCut(alpha);
            BigInteger modelCount = modelCounter.modelCount(alphaCut);
            logModelCounts.add(Sugar.logBigInteger(modelCount) / Math.log(2) + collectionDifference(allAtoms, allAtoms(alphaCut)).size());
        }
        logModelCounts.add(logModelCount(plt.hardRules(), modelCounter)+collectionDifference(allAtoms, allAtoms(plt.hardRules())).size());
        double retVal = 0;
        for (int i = 0; i < weights.size(); i++){
            retVal += (0.0-weights.get(i)) * (Math.pow(2, logModelCounts.get(i + 1))-Math.pow(2, logModelCounts.get(i)));
        }
        return retVal;
    }

    @Override
    public double density(Collection<Literal> possibleWorld) {
        Pair<Set<Literal>,Double> solution = this.solve(possibleWorld);
        Double lower = this.weights.lower(solution.s);
        if (lower == null){
            return -this.weightOfFalsity;
        }
        return -lower;
    }

    @Override
    public Set<Literal> mostProbableWorld(Collection<Literal> evidence) {
        return this.solve(evidence).r;
    }

    //as described in the ecai'16 paper
    public PossibilisticLogicTheory maximumLikelihoodWeightEstimation(BinaryDataset dataset, ModelCounter modelCounter){
        PossibilisticLogicTheory plt = PossibilisticLogicTheory.fromStratification(PossibilisticUtils.removeDrownedLevels(this.copy()).toLevelList(), hardRules());
        plt.addAllAdditionalGroundAtoms(this.additionalGroundAtoms);
        Set<Literal> allAtoms = union(allAtoms(Sugar.flatten(plt.toLevelList())), plt.additionalGroundAtoms);
        plt.addRule(new Clause(new HashSet<Literal>()), 0);

        //objective function:
        DoubleCounters<Double> eCounter = new DoubleCounters<Double>();
        for (int i = 0; i < dataset.numExamples(); i++) {
            Set<Literal> example = dataset.exampleAsSetOfLiterals(i);
            eCounter.add(plt.weights.lower(plt.solve(example).s), dataset.weight(i));
        }
        double[] E = new double[plt.weights().size()];
        double[] weightsArray = VectorUtils.toDoubleArray(plt.weights());
        for (int i = 0; i < E.length; i++){
            E[i] = eCounter.get(weightsArray[i]);
        }
        VectorUtils.multiply(E, -1.0/VectorUtils.sum(E));

        double[][] objectiveFunctionMonomialExponents = new double[1][];
        objectiveFunctionMonomialExponents[0] = E;
        ConvexMultivariateRealFunction objectiveFunction = new LogTransformedPosynomial(objectiveFunctionMonomialExponents, new double[]{0.0});

        //inequalities:

        ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[E.length];
        for (int i = 0; i < E.length-1; i++){
            double[][] exponents = new double[1][E.length];
            exponents[0][i] = -1.0;
            exponents[0][i+1] = 1.0;
            inequalities[i] = new LogTransformedPosynomial(exponents, new double[]{0.0});
        }
        //normalization inequality:

        double[] modelCounts = new double[E.length+1];

        int index = 0;
        for (double alpha : plt.weights()){
            List<Clause> alphaCut = plt.getAlphaCut(alpha);
            BigInteger modelCount = modelCounter.modelCount(alphaCut);
            modelCounts[index] = modelCount.doubleValue() * Math.pow(2.0, collectionDifference(allAtoms, allAtoms(alphaCut)).size());
            index++;
        }
        if (hardRules.isEmpty()){
            modelCounts[index] = Math.pow(2.0, collectionDifference(allAtoms(), allAtoms(plt.hardRules())).size());
        } else {
            modelCounts[index] = modelCounter.modelCount(plt.hardRules()).doubleValue() * Math.pow(2.0, collectionDifference(allAtoms, allAtoms(plt.hardRules())).size());
        }
        double[][] normalizationIneqExponents = new double[E.length][];
        double[] normalizationIneqCoeffs = new double[E.length];
        for (int i = 0; i < E.length; i++){
            double[] monomial = new double[E.length];
            monomial[i] = 1.0;
            normalizationIneqCoeffs[i] = Math.log(modelCounts[i+1]-modelCounts[i]);
            normalizationIneqExponents[i] = monomial;
        }
        inequalities[inequalities.length-1] = new LogTransformedPosynomial(normalizationIneqExponents, normalizationIneqCoeffs);

        //initialization point
        double[] init = VectorUtils.doubleSequence(E.length,1.0,-1.0);
        VectorUtils.multiply(init, 0.9/VectorUtils.sum(init));
        VectorUtils.multiply(init, 1.0/modelCounts[modelCounts.length-1]);
        for (int i = 0; i < init.length; i++){
            init[i] = Math.log(init[i]);
        }


        //optimization:
        OptimizationRequest or = new OptimizationRequest();
        or.setF0(objectiveFunction);
        or.setFi(inequalities);
        or.setInitialPoint(init);

        long m1 = System.nanoTime();
        JOptimizer opt = new JOptimizer();
        opt.setOptimizationRequest(or);
        long m2 = System.nanoTime();
        //System.out.println("Time for j-optimizer: "+(m2-m1)/1e6+"ms");

        try {
            int returnCode = opt.optimize();
        } catch (Exception e){
            throw new RuntimeException(e);
        }
        double[] sol = opt.getOptimizationResponse().getSolution();
        for (int i = 0; i < sol.length; i++){
            sol[i] = Math.exp(sol[i]);
        }
        PossibilisticLogicTheory retVal = new PossibilisticLogicTheory();
        retVal.addAllHardRules(plt.hardRules());
        retVal.addAllAdditionalGroundAtoms(plt.additionalGroundAtoms);

        index = 0;
        for (Set<Clause> level : plt.toLevelList()){
            if (index > 0){
                for (Clause rule : level){
                    retVal.addRule(rule, -sol[index]);
                }
            } else {
                retVal.setWeightOfFalsity(-sol[index]);
            }
            index++;
        }
        return retVal;
    }

    //"Getting" methods

    public Set<Literal> allAtomsInTheoryRules(){
        Set<Literal> retVal = new HashSet<Literal>();
        for (Map.Entry<Double,Set<Clause>> entry : this.rules.entrySet()){
            for (Clause c : entry.getValue()){
                for (Literal l : c.literals()){
                    if (l.isNegated()){
                        retVal.add(l.negation());
                    } else {
                        retVal.add(l);
                    }
                }
            }
        }
        for (Clause c : hardRules){
            for (Literal l : c.literals()){
                if (l.isNegated()){
                    retVal.add(l.negation());
                } else {
                    retVal.add(l);
                }
            }
        }
        return retVal;
    }

    public Set<Literal> getAdditionalGroundAtoms(){
        return this.additionalGroundAtoms;
    }

    public Set<Literal> allAtoms(){
        if (this.isGround()) {
            Set<Literal> retVal = allAtomsInTheoryRules();
            retVal.addAll(this.additionalGroundAtoms);
            return retVal;
        } else {
            //throw new UnsupportedOperationException("Not supported yet for lifted possibilistic logic theories.");
            return LogicUtils.allGroundAtoms(LogicUtils.predicates(this.flatten()), allConstants());
        }
    }

    public Set<Constant> allConstants(){
        Set<Constant> retVal = new HashSet<Constant>();
        retVal.addAll(additionalConstants);
        for (Literal l : additionalGroundAtoms){
            for (int i = 0; i < l.arity(); i++){
                if (l.get(i) instanceof Constant){
                    retVal.add((Constant)l.get(i));
                }
            }
        }
        for (Clause c : flatten()){
            retVal.addAll(LogicUtils.constants(c));
        }
        return retVal;
    }

    public Set<Clause> flatten(){
        Set<Clause> retVal = new HashSet<Clause>();
        retVal.addAll(this.hardRules());
        for (Map.Entry<Double,Set<Clause>> entry : this.rules.entrySet()){
            retVal.addAll(entry.getValue());
        }
        return retVal;
    }

    public List<Clause> getAlphaCut(double alpha){
        List<Clause> retVal = new ArrayList<Clause>();
        Double higher = new Double(alpha);
        while (higher != null) {
            retVal.addAll(rules.get(higher));
            higher = weights.higher(higher);
        }
        retVal.addAll(hardRules);
        return retVal;
    }

    public List<Clause> getStrictAlphaCut(double alpha){
        List<Clause> retVal = new ArrayList<Clause>();
        Double higher = new Double(alpha);
        higher = weights.higher(higher);
        while (higher != null) {
            retVal.addAll(rules.get(higher));
            higher = weights.higher(higher);
        }
        retVal.addAll(hardRules);
        return retVal;
    }

    public PossibilisticLogicTheory subtheory(double minNecessity){
        PossibilisticLogicTheory plt = new PossibilisticLogicTheory();
        for (Map.Entry<Double,Set<Clause>> entry : this.rules.entrySet()){
            if (entry.getKey() >= minNecessity){
                for (Clause rule : entry.getValue()){
                    plt.addRule(rule, entry.getKey());
                }
            }
        }
        plt.hardRules.addAll(this.hardRules);
        plt.addAllAdditionalGroundAtoms(this.additionalGroundAtoms);
        return plt;
    }

    public PossibilisticLogicTheory strictSubtheory(double minNecessity){
        PossibilisticLogicTheory plt = new PossibilisticLogicTheory();
        for (Map.Entry<Double,Set<Clause>> entry : this.rules.entrySet()){
            if (entry.getKey() > minNecessity){
                for (Clause rule : entry.getValue()){
                    plt.addRule(rule, entry.getKey());
                }
            }
        }
        plt.hardRules.addAll(this.hardRules);
        plt.addAllAdditionalGroundAtoms(this.additionalGroundAtoms);
        return plt;
    }

    /**
     *
     * @return levels of the possibilistic logic theory sorted from smallest weight to highest weight.
     */
    public ArrayList<Set<Clause>> toLevelList(){
        ArrayList<Set<Clause>> retVal = new ArrayList<Set<Clause>>();
        for (double alpha : this.weights()){
            retVal.add(Sugar.setFromCollections(level(alpha)));
        }
        return retVal;
    }

    public Set<Clause> hardRules(){
        return this.hardRules;
    }

    public List<Clause> level(double alpha){
        return Sugar.listFromCollections(this.rules.get(alpha));
    }

    //Beginning - weight-related "getters"

    public TreeSet<Double> weights(){
        return this.weights;
    }

    public double minWeight(){
        return this.weights.first();
    }

    public double maxWeight(){
        return this.weights.last();
    }

    public double weightOfFalsity(){
        return this.weightOfFalsity;
    }

    public double getNextLowerWeight(double weight){
        Double d = this.weights.lower(weight);
        if (d == null){
            return this.weightOfFalsity();
        } else {
            return d.doubleValue();
        }
    }

    //Utility functions

    private static double logModelCount(Collection<Clause> rules, ModelCounter modelCounter){
        BigInteger modelCount = modelCounter.modelCount(rules);
        return Sugar.logBigInteger(modelCount)/Math.log(2);
    }

    private static Set<Clause> wrapLiteralsToClauses(Collection<Literal> literals){
        Set<Clause> retVal = new HashSet<Clause>();
        for (Literal l : literals) {
            retVal.add(new Clause(l));
        }
        return retVal;
    }

    private static boolean isFalsified(Clause c, Set<Literal> evidence){
        for (Literal l : c.literals()){
            if (!evidence.contains(l.negation())){
                return false;
            }
        }
        return true;
    }

    private static Set<Literal> allAtoms(Collection<Clause> clauses){
        Set<Literal> retVal = new HashSet<Literal>();
        for (Clause c : clauses){
            for (Literal l : c.literals()){
                if (l.isNegated()){
                    retVal.add(l.negation());
                } else {
                    retVal.add(l);
                }
            }
        }
        return retVal;
    }

    // toString, equals, hashCode...

    public String toString(){
        StringBuilder sb = new StringBuilder();
        if (!this.hardRules.isEmpty()) {
            sb.append("---------------------\n" +
                    "Hard rules:\n");
            for (Clause hardRule : this.hardRules){
                sb.append(hardRule).append("\n");
            }
        }
        for (double level : Sugar.sortDesc(Sugar.listFromCollections(weights()))){
            sb.append("---------------------\nLevel "+level+"\n");
            for (Clause clause : level(level)) {
                sb.append(clause);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public int hashCode(){
        return this.rules.hashCode();
    }

    @Override
    public boolean equals(Object o){
        if (o instanceof PossibilisticLogicTheory){
            PossibilisticLogicTheory plt = (PossibilisticLogicTheory)o;
            return plt.rules.equals(this.rules) && plt.hardRules.equals(this.hardRules);
        }
        return false;
    }

    public int countNonHardRules(){
        return VectorUtils.sum(this.rules.sizes());
    }

    public static void main(String[] args){
        boolean[][] data = new boolean[][]{
                {true,true,true},{true,true,true},{true,true,false},{true,true,false},
                {true,false,true},{true,false,true},{true,false,true},
                {false,true,true},{false,true,true},
                {false,false,true}
        };
        String[] attributes = {"a", "b", "c"};
        BinaryDataset dataset = new BinaryDataset(data, attributes);

        ModelCounter mc = Globals.modelCounterFactory.newInstance();
        PossibilisticLogicTheory plt = new PossibilisticLogicTheory();
        //plt.addRule(Clause.parse("!a(),!b()"), 0.8);
        plt.addRule(Clause.parse("!a(),b()"), 0.85);
        plt.addRule(Clause.parse("a(),!b()"), 0.9);
        plt.addRule(Clause.parse("a(),b()"), 0.95);
        plt.addAdditionalGroundAtom(Literal.parseLiteral("c()"));

        //PossibilisticLogicTheory plt2 = PossibilisticLogicTheory.fromStratification(plt.toLevelList());
        PossibilisticLogicTheory mlplt = plt.maximumLikelihoodWeightEstimation(dataset, Globals.modelCounterFactory.newInstance());

        System.out.println(mlplt);

        Collection<Clause> query = Sugar.<Clause>list(
                Clause.parse("b()")
        );

        System.out.println(mlplt.probability(query, mc));


    }

}
