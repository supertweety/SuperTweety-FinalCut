package supertweety.shorty;/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import ida.ilp.logic.*;
import ida.ilp.logic.special.IsoClauseWrapper;
import ida.ilp.logic.subsumption.SpecialVarargPredicates;
import ida.utils.Sugar;
import ida.utils.collections.MultiMap;
import ida.utils.tuples.Pair;
import supertweety.RelationalDataset;

import java.util.*;

/**
 * Created by kuzelkao_cardiff on 25/01/17.
 */
public class RuleLearner {

    public final static int STANDARD_MODE = 1, ZIG_ZAG_MODE = 2;

    private int minSupport = 2;

    private int zig = 2, zag = 1;

    private int k;

    private RelationalDataset dataset;

    private Set<Pair<String,Integer>> allAllowedPredicates;

    private boolean connectedOnly = true;

    private Saturator saturator;

    private static Random random = new Random(RuleLearner.class.getName().hashCode());

    public RuleLearner(RelationalDataset dataset, int k){
        this.dataset = dataset;
        this.k = k;
        this.allAllowedPredicates = dataset.allPredicates();
    }

    public HornClause beamSearch(List<HornClause> currentRules, int beamSize, int maxSize){
        return beamSearch(currentRules, null, beamSize, maxSize, STANDARD_MODE);
    }

    public HornClause beamSearch(List<HornClause> currentRules, List<HornClause> forbidden, int beamSize, int maxSize){
        return beamSearch(currentRules, forbidden, beamSize, maxSize, STANDARD_MODE);
    }

    private HornClause beamSearch(List<HornClause> currentRules, List<HornClause> forbidden, int beamSize, int maxSize, int mode){
        Set<Pair<String,Integer>> queryPredicates = this.dataset.queryPredicates();

        List<HornClause> current = new ArrayList<HornClause>();
        if (queryPredicates.isEmpty()) {
            current.add(new HornClause(new Clause()));
            current.add(new HornClause(null, new Clause(new Literal(SpecialVarargPredicates.FALSE))));
        } else {
            for (Pair<String,Integer> predicate : queryPredicates){
                current.add(new HornClause(new Clause(LogicUtils.newLiteral(predicate.r, predicate.s))));
                current.add(new HornClause(LogicUtils.newLiteral(predicate.r, predicate.s), new Clause(new Literal(SpecialVarargPredicates.FALSE))));
            }
        }
        Pair<HornClause,Double> best = new Pair<HornClause,Double>();
        selectTop(current, currentRules, beamSize, best);

        MultiMap<HornClause,Literal> badRefinements = new MultiMap<HornClause, Literal>();


        for (HornClause hc : current){
            badRefinements.putAll(hc, new HashSet<Literal>());
        }

        if (mode == STANDARD_MODE) {
            for (int i = 1; i <= maxSize; i++) {
                List<HornClause> candidates = new ArrayList<HornClause>();
                for (HornClause old : current) {
                    List<HornClause> newCandidates = refinementsUp(old, badRefinements.get(old));
                    Set<Literal> set = badRefinements.remove(old);
                    for (HornClause newCand : newCandidates) {
                        candidates.add(newCand);
                        badRefinements.putAll(newCand, set);
                    }
                }
//                System.out.println("candidates with at least one match: " + candidates.size());
                candidates = filterIsomorphic(candidates);
                if (forbidden != null){
                    candidates = removeContainingForbidden(candidates, forbidden);
                }

                Pair<HornClause, Double> previousBest = new Pair(best.r, best.s);
                List<HornClause> top = selectTop(candidates, currentRules, beamSize, best);
                //history.putAll(i, top);
                current = top;
//                for (HornClause hc : top) {
//                    System.out.println("in top: " + hc);
//                }
                System.out.println("Best so far: " + best + " " + previousBest);
            }
        } else if (mode == ZIG_ZAG_MODE){
            for (int i = 1; i <= maxSize; i++) {
                for (int j = 0; j < zig; j++) {
                    List<HornClause> candidates = new ArrayList<HornClause>();
                    for (HornClause old : current) {
                        List<HornClause> newCandidates = refinementsUp(old, badRefinements.get(old));
                        Set<Literal> set = badRefinements.remove(old);
                        for (HornClause newCand : newCandidates) {
                            candidates.add(newCand);
                            badRefinements.putAll(newCand, set);
                        }
                    }
                    candidates = filterIsomorphic(candidates);
                    if (forbidden != null){
                        candidates = removeContainingForbidden(candidates, forbidden);
                    }
                    current = selectTop(candidates, currentRules, beamSize, best);
                }
                for (int j = 0; j < zag; j++) {
                    List<HornClause> candidates = new ArrayList<HornClause>();
                    for (HornClause old : current) {
                        List<HornClause> newCandidates = refinementsDown(old);
                        for (HornClause newCand : newCandidates){
                            candidates.add(newCand);
                        }
                    }
                    candidates = filterIsomorphic(candidates);
                    if (forbidden != null){
                        candidates = removeContainingForbidden(candidates, forbidden);
                    }
                    current = selectTop(candidates, currentRules, beamSize, best);
                }

                for (HornClause hc : current) {
                    System.out.println("in top: " + hc);
                }
                System.out.println("Best so far: " + best);
            }
        }
        System.out.println("acc: "+best.s);
        return best.r;
    }

    private List<HornClause> selectTop(List<HornClause> candidatesList, List<HornClause> currentClauses, int num, Pair<HornClause, Double> outBest){
        List<Pair<HornClause,Double>> pairs = new ArrayList<Pair<HornClause, Double>>();
        for (HornClause hc : candidatesList){
            double acc = dataset.accuracy(Sugar.<HornClause>append(currentClauses, hc), k);
            pairs.add(new Pair<HornClause, Double>(hc, acc));
        }
        Collections.shuffle(pairs, this.random);
        Collections.sort(pairs, new Comparator<Pair<HornClause, Double>>() {
            @Override
            public int compare(Pair<HornClause, Double> o1, Pair<HornClause, Double> o2) {
                return -o1.s.compareTo(o2.s);
            }
        });
        List<HornClause> retVal = new ArrayList<HornClause>();
        for (int i = 0; i < Math.min(num, pairs.size()); i++){
            retVal.add(pairs.get(i).r);
        }
        if (retVal.size() > 0 && outBest != null && (outBest.r == null || outBest.s < pairs.get(0).s)){
            outBest.set(retVal.get(0), pairs.get(0).s);
        }
        return retVal;
    }

    private List<HornClause> refinementsUp(HornClause hc, Set<Literal> badRefinements){
        Set<IsoClauseWrapper> set = new HashSet<IsoClauseWrapper>();
        for (Pair<String,Integer> predicate : allAllowedPredicates){
            for (HornClause newHc : refinements(hc, predicate, badRefinements)) {
                set.add(new IsoClauseWrapper(newHc.toClause()));
            }
        }
        List<HornClause> retVal = new ArrayList<HornClause>();
        for (IsoClauseWrapper icw : set){
            retVal.add(new HornClause(icw.getOriginalClause()));
        }
        return retVal;
    }

    private List<HornClause> filterIsomorphic(Collection<HornClause> coll){
        Set<IsoClauseWrapper> set = new HashSet<IsoClauseWrapper>();
        for (HornClause newHc : coll) {
            set.add(new IsoClauseWrapper(newHc.toClause()));
        }
        List<HornClause> retVal = new ArrayList<HornClause>();
        for (IsoClauseWrapper icw : set){
            retVal.add(new HornClause(icw.getOriginalClause()));
        }
        return retVal;
    }

    private List<HornClause> removeContainingForbidden(List<HornClause> candidates, List<HornClause> forbidden){
        List<HornClause> retVal = new ArrayList<HornClause>();
        outerLoop: for (HornClause candidate : candidates){
            for (HornClause fb : forbidden){
                if (HornClause.subsumption(fb, candidate)){
                    continue outerLoop;
                }
            }
            retVal.add(candidate);
        }
        return retVal;
    }

    private List<HornClause> refinements(HornClause hc, Pair<String,Integer> predicate, Set<Literal> badRefinements){
        long m1 = System.currentTimeMillis();
        Map<IsoClauseWrapper,Literal> refinements = new HashMap<IsoClauseWrapper,Literal>();
        Set<Variable> variables = hc.variables();
        Set<Variable> freshVariables = LogicUtils.freshVariables(variables, predicate.s);
        Literal freshLiteral = LogicUtils.newLiteral(predicate.r, predicate.s, freshVariables).negation();
        Clause originalClause = hc.toClause();
        Clause init = new Clause(Sugar.union(originalClause.literals(), freshLiteral));
        refinements.put(new IsoClauseWrapper(init), freshLiteral);

        for (int i = 0; i < predicate.s; i++){
            Map<IsoClauseWrapper,Literal> newRefinements = new HashMap<IsoClauseWrapper, Literal>();
            for (Map.Entry<IsoClauseWrapper,Literal> entry : refinements.entrySet()){
                Variable x = (Variable)entry.getValue().get(i);
                for (Variable v : entry.getKey().getOriginalClause().variables()){
                    if (v != x){
                        Clause substituted = LogicUtils.substitute(entry.getKey().getOriginalClause(), x, v);
                        Literal newLiteral = LogicUtils.substitute(entry.getValue(), x, v);
                        if (substituted.countLiterals() > originalClause.countLiterals() && !badRefinements.contains(newLiteral) &&
                                !substituted.containsLiteral(newLiteral.negation())) {
                            HornClause candidate = new HornClause(substituted);
                            if (dataset.numPositiveMatchedExistentially(candidate, this.k, minSupport) >= minSupport) {
                                Clause candClause = candidate.toClause();
                                newRefinements.put(new IsoClauseWrapper(candClause), newLiteral);
                            } else {
                                badRefinements.add(newLiteral);
                            }
                        } else {
                            //System.out.println("bad: "+newLiteral+" for "+hc);
                        }
                    }
                }
            }
            refinements.putAll(newRefinements);
        }
        Set<IsoClauseWrapper> refinementSet;
        if (this.saturator != null){
            Set<IsoClauseWrapper> saturatedRefinements = new HashSet<IsoClauseWrapper>();
            for (IsoClauseWrapper icw : refinements.keySet()){
                saturatedRefinements.add(new IsoClauseWrapper(saturator.saturate(icw.getOriginalClause())));
            }
            refinementSet = saturatedRefinements;
        } else {
            refinementSet = refinements.keySet();
        }
        List<HornClause> retVal = new ArrayList<HornClause>();
        for (IsoClauseWrapper icw : refinementSet){
            if ((!this.connectedOnly || icw.getOriginalClause().connectedComponents().size() == 1) && icw.getOriginalClause().variables().size() <= this.k) {
                retVal.add(new HornClause(icw.getOriginalClause()));
            }
        }
        long m2 = System.currentTimeMillis();
        //System.out.println((m2-m1)+"ms");
        return retVal;
    }

    private List<HornClause> refinementsDown(HornClause hc){
        Set<IsoClauseWrapper> set = new HashSet<IsoClauseWrapper>();
        Clause c = hc.toClause();
        for (Literal l : c.literals()){
            if (l.isNegated()) {
                Clause cand = new Clause(Sugar.collectionDifference(c.literals(), l));
                if (cand.connectedComponents().size() == 1) {
                    set.add(new IsoClauseWrapper(cand));
                }
            }
        }
        List<HornClause> retVal = new ArrayList<HornClause>();
        for (IsoClauseWrapper icw : set){
            retVal.add(new HornClause(icw.getOriginalClause()));
        }
        return retVal;
    }

    public void setLanguageBias(Set<Pair<String,Integer>> predicates){
        this.allAllowedPredicates = predicates;
    }

    public void setSaturator(Saturator saturator){
        this.saturator = saturator;
    }


    public void setMinSupport(int minSupport) {
        this.minSupport = minSupport;
    }
}
