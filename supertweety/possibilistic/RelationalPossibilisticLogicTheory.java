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
import ida.ilp.logic.subsumption.Matching;
import ida.utils.*;
import ida.utils.collections.MultiMap;
import ida.utils.tuples.Pair;
import supertweety.SuperTweetyContradictionException;
import supertweety.logic.ApproximateModelCounter;
import supertweety.logic.ModelCounter;
import supertweety.logic.TheorySolver;
import supertweety.misc.Logging;

import supertweety.RelationalDataset;

import supertweety.shorty.ModelCounterFactory;

import java.io.Reader;
import java.math.BigInteger;
import java.util.*;

import static ida.utils.Sugar.collectionDifference;

/**
 * Created by kuzelkao_cardiff on 19/01/15.
 */
public class RelationalPossibilisticLogicTheory {

    //primary attributes:

    private Set<Clause> hardRules = new HashSet<Clause>();

    private MultiMap<Double, Clause> rules = new MultiMap<Double, Clause>();

    private double weightOfFalsity = 0;

    private Set<Constant> additionalConstants = new HashSet<Constant>();

    // auxiliary derived attributes:

    private TreeSet<Double> weights = new TreeSet<Double>();

    public static boolean USE_CACHING = false;

    private Boolean isConsistent = null;

    private final static Cache<Set<Clause>,Set<Literal>> cache = new Cache<Set<Clause>,Set<Literal>>();

    private final static Set<Literal> NIL_SOLUTION = new HashSet<Literal>();


    static {
        Logging.init();
    }

    public RelationalPossibilisticLogicTheory copy(){
        RelationalPossibilisticLogicTheory retVal = new RelationalPossibilisticLogicTheory();
        retVal.hardRules.addAll(this.hardRules);
        retVal.weights.addAll(this.weights);
        retVal.rules.putAll(this.rules);
        retVal.weightOfFalsity = this.weightOfFalsity();
        return retVal;
    }

    public RelationalPossibilisticLogicTheory(){}

    public RelationalPossibilisticLogicTheory(MultiMap<Double, Clause> rules) {
        this(rules, null);
    }

    public RelationalPossibilisticLogicTheory(List<Pair<Clause, Double>> rules){
        this(rules, null);
    }

    public RelationalPossibilisticLogicTheory(MultiMap<Double, Clause> rules, Set<Clause> hardRules) {
        this.set(rules);
        if (hardRules != null) {
            this.hardRules = hardRules;
        }
    }

    public RelationalPossibilisticLogicTheory(List<Pair<Clause, Double>> rules, Set<Clause> hardRules){
        MultiMap<Double,Clause> mm = new MultiMap<Double,Clause>();
        for (Pair<Clause,Double> rule : rules){
            mm.put(rule.s, rule.r);
        }
        this.set(mm);
        if (hardRules != null){
            this.hardRules = hardRules;
        }
    }

    public static RelationalPossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification, List<Double> weights){
        return fromStratification(stratification, weights, null);
    }

    public static RelationalPossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification, List<Double> weights, Set<Clause> hardRules){
        MultiMap<Double,Clause> rules = new MultiMap<Double,Clause>();
        for (int i = stratification.size()-1; i >= 0; i--){
            rules.putAll(weights.get(i), stratification.get(i));
        }
        return new RelationalPossibilisticLogicTheory(rules, hardRules);
    }

    public static RelationalPossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification){
        return fromStratification(stratification, new HashSet<Clause>());
    }

    public static RelationalPossibilisticLogicTheory fromStratification(List<? extends Collection<Clause>> stratification, Set<Clause> hardRules){
        MultiMap<Double,Clause> rules = new MultiMap<Double,Clause>();
        double d = stratification.size()+1;
        for (int i = stratification.size()-1; i >= 0; i--){
            rules.putAll((i+1)/d, stratification.get(i));
        }
        return new RelationalPossibilisticLogicTheory(rules, hardRules);
    }

    public static RelationalPossibilisticLogicTheory merge(RelationalPossibilisticLogicTheory bottom, RelationalPossibilisticLogicTheory top){
        List<Set<Clause>> levels = new ArrayList<Set<Clause>>();
        levels.addAll(bottom.toLevelList());
        levels.addAll(top.toLevelList());
        return fromStratification(levels);
    }


    private void set(MultiMap<Double,Clause> rules){
        for (Map.Entry<Double, Set<Clause>> entry : rules.entrySet()) {
            for (Clause c : entry.getValue()) {
                this.rules.put(entry.getKey(), c);
            }
            this.weights.add(entry.getKey());
        }
        this.isConsistent = null;
    }

    public void addRule(Clause rule, double weight){
        this.rules.put(weight, rule);
        this.weights.add(weight);
        this.isConsistent = null;
    }

    public void addHardRule(Clause hardRule){
        this.hardRules.add(hardRule);
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

    public void addAdditionalConstant(Constant constant){
        this.additionalConstants.add(constant);
    }

    public void addAdditionalConstants(Collection<Constant> constants){
        this.additionalConstants.addAll(constants);
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
                Literal constantIntroduction = null;
                if (!this.additionalConstants.isEmpty()){
                    constantIntroduction = new Literal("", additionalConstants.size());
                    int i = 0;
                    for (Constant c : additionalConstants){
                        constantIntroduction.set(c, i++);
                    }
                }
                newTS().solve(satProblem, Sugar.<Literal>set(), constantIntroduction == null ? Sugar.<Literal>set() : Sugar.<Literal>set(constantIntroduction));
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
            Literal constantIntroduction = null;
            if (!this.additionalConstants.isEmpty()){
                constantIntroduction = new Literal("", additionalConstants.size());
                int i = 0;
                for (Constant c : additionalConstants){
                    constantIntroduction.set(c, i++);
                }
            }

            solution = newTS().solve(satProblem, Sugar.<Literal>set(), constantIntroduction == null ? Sugar.<Literal>set() : Sugar.<Literal>set(constantIntroduction));
            return solution;
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

    private static boolean isTautology(Clause clause){
        for (Literal l : clause.literals()){
            if (clause.containsLiteral(l.negation())){
                return true;
            }
        }
        return false;
    }

    private TheorySolver newTS(){
        TheorySolver ts = new TheorySolver();
        ts.setSubsumptionMode(Matching.OI_SUBSUMPTION);
        //ts.setMode(TheorySolver.GROUND_ALL);
        return ts;
    }

    public Set<Literal> solve(double alpha, Collection<Literal> evidence){
        return solveSatProblem(Sugar.union(this.hardRules, this.getAlphaCut(alpha), wrapLiteralsToClauses(evidence)));
    }

    public boolean isConsistent(){
        if (this.isConsistent == null){
            Set<Literal> solution = this.solveSatProblem(Sugar.setFromCollections(this.hardRules, Sugar.flatten(this.rules.values())));
            this.isConsistent = solution != null;
        }
        return this.isConsistent;
    }

    // Beginning probabilistic methods...

    public double density(Collection<Literal> possibleWorld) {
        Pair<Set<Literal>,Double> solution = this.solve(possibleWorld);
        Double lower = this.weights.lower(solution.s);
        if (lower == null){
            return -this.weightOfFalsity;
        }
        return -lower;
    }

    public Set<Literal> mostProbableWorld(Collection<Literal> evidence) {
        return this.solve(evidence).r;
    }

    private Map<Double,Double> countExamples(RelationalDataset dataset, int k){
        Map<Double,Double> cummulative = new HashMap<Double, Double>();
        TreeSet<Double> treeSet = new TreeSet<Double>(this.weights);
        for (double weight : treeSet){
            //we assume that the hard rules are always satisfied in the data (after all they are assumed to be learned from the same data)
            List<Clause> rules = Sugar.listFromCollections(this.getAlphaCut(weight));
            cummulative.put(weight, Math.max(0, dataset.approxMatchingSubsets(rules, k)));
        }
        Map<Double,Double> retVal = new HashMap<Double, Double>();
        cummulative.put(Double.POSITIVE_INFINITY, Math.exp(Combinatorics.logBinomial(dataset.constants().size(), k)));
        treeSet.add(Double.POSITIVE_INFINITY);
        for (double weight : treeSet){
            if (Double.isFinite(weight)) {
                retVal.put(weight, cummulative.get(treeSet.higher(weight))-cummulative.get(weight));
            }
        }
        retVal.put(this.weightOfFalsity(), cummulative.get(treeSet.higher(this.weightOfFalsity())));
        return retVal;
    }

    public RelationalPossibilisticLogicTheory maximumLikelihoodWeightEstimation(RelationalDataset dataset, int k, ModelCounterFactory mcf){
        return maximumLikelihoodWeightEstimation(dataset, k, mcf, new MutableDouble(Double.NaN));
    }

    public RelationalPossibilisticLogicTheory maximumLikelihoodWeightEstimation(RelationalDataset dataset, int k, ModelCounterFactory mcf, MutableDouble logLikelihood_out){
        RelationalPossibilisticLogicTheory plt = RelationalPossibilisticLogicTheory.fromStratification(this.removeDrownedLevels(k).toLevelList(), hardRules());
        RelationalPossibilisticLogicTheory pltWithoutHardRules = RelationalPossibilisticLogicTheory.fromStratification(this.removeDrownedLevels(k).toLevelList());
        plt.addRule(new Clause(new HashSet<Literal>()), 0);

        //objective function:
        Map<Double,Double> eCounter = pltWithoutHardRules.countExamples(dataset, k);
        //System.out.println("eCounter: "+eCounter);
        double[] E = new double[plt.weights().size()];
        double[] weightsArray = VectorUtils.toDoubleArray(plt.weights());
        for (int i = 0; i < E.length; i++){
            E[i] = eCounter.get(weightsArray[i]);
        }
        double[] matchingSubsetCounts = VectorUtils.copyArray(E);
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

        double[] logModelCounts = new double[E.length+1];

        int index = 0;
        for (double alpha : plt.weights()){
            List<Clause> alphaCut = plt.getAlphaCut(alpha);
            ApproximateModelCounter amc = mcf.create(alphaCut, plt.freshConstants(k), plt.allAtoms(k));
            logModelCounts[index] = amc.logApproxCount()/*+collectionDifference(plt.allAtoms(k), plt.allAtoms(alphaCut, k)).size()*/;
            index++;
        }
        if (plt.hardRules.isEmpty()){
            logModelCounts[index] = collectionDifference(plt.allAtoms(k), plt.allAtoms(plt.hardRules(), k)).size();
        } else {
            ApproximateModelCounter amc = mcf.create(plt.hardRules(), plt.freshConstants(k), plt.allAtoms(k));
            logModelCounts[index] = amc.logApproxCount()/*+collectionDifference(plt.allAtoms(k), plt.allAtoms(this.hardRules(), k)).size()*/;
        }

        //coping with estimation "noise"
        for (int i = 0; i < logModelCounts.length-1; i++){
            if (logModelCounts[i] > logModelCounts[i+1]){
                VectorUtils.swap(logModelCounts, i, i+1);
            }
        }

        double[][] normalizationIneqExponents = new double[E.length][];
        double[] normalizationIneqCoeffs = new double[E.length];
        for (int i = 0; i < E.length; i++){
            double[] monomial = new double[E.length];
            monomial[i] = 1.0;
            normalizationIneqCoeffs[i] = /*Math.log(Math.pow(2,logModelCounts[i+1])-Math.pow(2,logModelCounts[i]));*/Sugar.approximateLogExpMinusExp(logModelCounts[i+1]*Math.log(2),logModelCounts[i]*Math.log(2));
            normalizationIneqExponents[i] = monomial;
        }
        inequalities[inequalities.length-1] = new LogTransformedPosynomial(normalizationIneqExponents, normalizationIneqCoeffs);

        //initialization point
        double[] init = VectorUtils.doubleSequence(E.length,1.0,-1.0);
        VectorUtils.multiply(init, 0.9/VectorUtils.sum(init));
        VectorUtils.multiply(init, 1.0/Math.exp(logModelCounts[logModelCounts.length-1])*Math.log(2));
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
            //throw new RuntimeException(e);
            System.out.println("Warning: numerical optimization failed for: \n"+this.toString());
            logLikelihood_out.set(Double.NEGATIVE_INFINITY);
            return null;
        }
        double[] sol = opt.getOptimizationResponse().getSolution();
        for (int i = 0; i < sol.length; i++){
            sol[i] = Math.exp(sol[i]);
        }

        double logLik = 0;

        RelationalPossibilisticLogicTheory retVal = new RelationalPossibilisticLogicTheory();
        retVal.addAllHardRules(plt.hardRules());

        index = 0;
        for (Set<Clause> level : plt.toLevelList()){
            if (index > 0){
                for (Clause rule : level){
                    retVal.addRule(rule, -sol[index]);
                }
            } else {
                retVal.setWeightOfFalsity(-sol[index]);
            }

            logLik += matchingSubsetCounts[index]*Math.log(sol[index]);

            index++;
        }
        logLikelihood_out.set(logLik);
        return retVal;
    }

    private Set<Constant> freshConstants(int num){
        Set<Constant> retVal = new HashSet<Constant>();
        for (int i = 0; i < num; i++){
            retVal.add(Constant.construct(String.valueOf(i)));
        }
        return retVal;
    }

    //"Getting" methods

    private Set<Literal> allAtoms(int k){
        return LogicUtils.allGroundAtoms(LogicUtils.predicates(this.flatten(), true), freshConstants(k));
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

    public RelationalPossibilisticLogicTheory subtheory(double minNecessity){
        RelationalPossibilisticLogicTheory plt = new RelationalPossibilisticLogicTheory();
        for (Map.Entry<Double,Set<Clause>> entry : this.rules.entrySet()){
            if (entry.getKey() >= minNecessity){
                for (Clause rule : entry.getValue()){
                    plt.addRule(rule, entry.getKey());
                }
            }
        }
        plt.hardRules.addAll(this.hardRules);
        return plt;
    }

    public RelationalPossibilisticLogicTheory strictSubtheory(double minNecessity){
        RelationalPossibilisticLogicTheory plt = new RelationalPossibilisticLogicTheory();
        for (Map.Entry<Double,Set<Clause>> entry : this.rules.entrySet()){
            if (entry.getKey() > minNecessity){
                for (Clause rule : entry.getValue()){
                    plt.addRule(rule, entry.getKey());
                }
            }
        }
        plt.hardRules.addAll(this.hardRules);
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

    //weight-related "getters"

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

    private Set<Literal> allAtoms(Collection<Clause> clauses, int k){
        return LogicUtils.allGroundAtoms(LogicUtils.predicates(clauses, true), freshConstants(k));
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
        if (o instanceof RelationalPossibilisticLogicTheory){
            RelationalPossibilisticLogicTheory plt = (RelationalPossibilisticLogicTheory)o;
            return plt.rules.equals(this.rules) && plt.hardRules.equals(this.hardRules);
        }
        return false;
    }

    public RelationalPossibilisticLogicTheory removeDrownedLevels(int cardinality){
        if (this.weights().isEmpty()){
            return this;
        }
        Literal constantIntroduction = new Literal("", Sugar.listFromCollections(freshConstants(cardinality)));
        Pair<Set<Literal>,Double> solution = this.solve(Sugar.<Literal>set(constantIntroduction));
        if (this.minWeight() == solution.s){
            return this;
        } else {
            RelationalPossibilisticLogicTheory subtheory = this.subtheory(solution.s);
            subtheory.setWeightOfFalsity(this.getNextLowerWeight(solution.s));
            return subtheory;
        }
    }

    public static RelationalPossibilisticLogicTheory read(Reader reader) throws Exception {
        final int HARD_RULES = 1, SOFT_RULES = 2;
        int state = 0;
        double weight = 0;
        RelationalPossibilisticLogicTheory retVal = new RelationalPossibilisticLogicTheory();
        for (String line : Sugar.readLines(reader)){
            line = line.trim();
            if (line.startsWith("---") || line.length() == 0){
                continue;
            } else if (line.toLowerCase().startsWith("hard rules:")){
                state = HARD_RULES;
            } else if (line.toLowerCase().startsWith("level")){
                state = SOFT_RULES;
                weight = Double.parseDouble(line.substring("level".length()).trim());
            } else if (line.toLowerCase().startsWith("falsity weight:")){
                retVal.setWeightOfFalsity(Double.parseDouble(line.substring("falsity weight:".length()).trim()));
            } else {
                if (state == HARD_RULES){
                    retVal.addHardRule(Clause.parse(line));
                } else {
                    retVal.addRule(Clause.parse(line), weight);
                }
            }
        }
        return retVal;
    }



}
