/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.logic;

import ida.ilp.logic.Clause;
import ida.ilp.logic.Constant;
import ida.ilp.logic.Literal;
import ida.ilp.logic.LogicUtils;
import ida.ilp.logic.special.IsoClauseWrapper;
import ida.ilp.logic.subsumption.Matching;
import ida.ilp.logic.subsumption.SpecialBinaryPredicates;
import ida.ilp.logic.subsumption.SpecialVarargPredicates;
import ida.utils.Cache;
import ida.utils.Sugar;
import ida.utils.VectorUtils;
import ida.utils.tuples.Pair;
import ida.utils.tuples.Triple;
import supertweety.logic.utils.CryptoMiniSatExecutor;

import java.util.*;

/**
 * Created by kuzelkao_cardiff on 05/12/16.
 */
public class ApproximateModelCounter {

    private final static double log2 = Math.log(2);

    private double epsilon = 0.1, delta = 0.1;

    private int matchingMode = Matching.THETA_SUBSUMPTION;

    //may overload delta
    private int numTries = -1;

    private Collection<Clause> theory;

    private List<Literal> allGroundAtoms;

    private Set<Constant> universe;

    private Literal constantIntroduction;

    private Random random = new Random(getClass().getName().hashCode());

    private final static Object cacheLock = new Object();

    private static Cache<Triple<Set<IsoClauseWrapper>,Set<Constant>,Set<Literal>>,Double> cache = new Cache<Triple<Set<IsoClauseWrapper>,Set<Constant>,Set<Literal>>,Double>();


    public ApproximateModelCounter(Collection<Clause> theory, Set<Constant> universe, Set<Literal> allGroundAtoms){
        this.theory = theory;
        Set<Pair<String,Integer>> predicates = LogicUtils.predicates(theory);
        Set<Pair<String,Integer>> toBeRemoved = new HashSet<Pair<String,Integer>>();
        for (Pair<String,Integer> p : predicates){
            if (SpecialVarargPredicates.SPECIAL_PREDICATES.contains(p.r) || SpecialBinaryPredicates.SPECIAL_PREDICATES.contains(p.r)){
                toBeRemoved.add(p);
            }
        }
        predicates.removeAll(toBeRemoved);
        this.universe = universe;
        this.allGroundAtoms = Sugar.listFromCollections(allGroundAtoms);//Sugar.listFromCollections(LogicUtils.allGroundAtoms(predicates, universe));
        Literal l = new Literal("", universe.size());
        int i = 0;
        for (Constant c : universe){
            l.set(c, i++);
        }
        this.constantIntroduction = l;
    }


    public double logApproxCount(){
        Triple<Set<IsoClauseWrapper>,Set<Constant>,Set<Literal>> representative = new Triple<Set<IsoClauseWrapper>,Set<Constant>,Set<Literal>>();
        synchronized (cacheLock){
            representative.r = new HashSet<IsoClauseWrapper>();
            for (Clause c : this.theory){
                representative.r.add(new IsoClauseWrapper(c));
            }
            representative.s = this.universe;
            representative.t = Sugar.setFromCollections(this.allGroundAtoms);
            Double value;
            if ((value = cache.get(representative)) != null){
                return value;
            }
        }
        double retVal;
        int maxExact = 10000;
        int count1 = count(maxExact);
        if (count1 >= maxExact) {
            retVal = approxMC2();
        } else {
            retVal = Math.log(count1)/log2;
        }
        synchronized (cacheLock){
            cache.put(representative, retVal);
        }
        return retVal;
    }

    public int count(int maxCount){
        TheorySolver ts = constructTheorySolver();
        return ts.solveAll(theory, Sugar.<Literal>set(), Sugar.<Literal>set(constantIntroduction), Sugar.setFromCollections(allGroundAtoms), maxCount).size();
    }

    private int count(int maxCount, boolean[][] left, boolean[] right){
        List<Clause> extendedTheory = new ArrayList<Clause>();
        extendedTheory.addAll(this.theory);
        if (left != null && right != null) {
            for (int i = 0; i < left.length; i++) {
                Literal xorLit = new Literal("@xor", !right[i], VectorUtils.occurrences(left[i],true));
                int k = 0;
                for (int j = 0; j < left[i].length; j++) {
                    if (left[i][j]) {
                        xorLit.set(this.allGroundAtoms.get(j).toFunction(), k++);
                    }
                }
                extendedTheory.add(new Clause(xorLit));
            }
        }
        TheorySolver ts = constructTheorySolver();
        int retVal = ts.solveAll(extendedTheory, Sugar.<Literal>set(), Sugar.<Literal>set(constantIntroduction), Sugar.setFromCollections(allGroundAtoms), maxCount).size();
        return retVal;
    }


    private double approxMC2(){
        double threshold = 1+9.84*(1+epsilon/(1+epsilon))*Sugar.square(1 + 1 / epsilon);
        int count = count((int)threshold, null, null);
        if (count < (int)threshold){
            return Math.log(count)/log2;
        }
        double t;
        if (this.numTries == -1) {
            t = Math.ceil(17 * Math.log(2 / delta) / log2);
        } else {
            t = numTries;
        }
        int logNCells = 1;
        List<Double> logCounts = new ArrayList<Double>();
        for (int i = 0; i < t; i++){
            Pair<Double,Double> p = approxMC2Core((int)threshold, logNCells);
            if (p != null) {
                logCounts.add(p.r + Math.log(p.s) / log2);
                logNCells = p.r.intValue();
            }
        }
        System.out.println("logCounts: "+logCounts);
        if (logCounts.isEmpty()){
            return Double.NaN;
        }
        double[] logCountsArray = VectorUtils.toDoubleArray(logCounts);
        Arrays.sort(logCountsArray);
        System.out.println("retVal: "+logCountsArray[logCountsArray.length/2]);
        return logCountsArray[logCountsArray.length/2];
    }

    private Pair<Double,Double> approxMC2Core(int threshold, int logPrevNCells){
        int numRows = this.allGroundAtoms.size();
        boolean[][] left = new boolean[numRows][];
        boolean[] right = new boolean[numRows];
        for (int i = 0; i < left.length; i++){
            left[i] = VectorUtils.randomBooleanVector(this.allGroundAtoms.size(), this.random);
            right[i] = this.random.nextBoolean();
        }

        //MatrixUtils.gaussianElimination(left, right);

        int count = count(threshold, left,  right);
        if (count >= threshold){
            return null;
        }
        int m = logSatSearch(threshold, left, right, logPrevNCells);
        boolean[][] leftM = new boolean[m][];
        System.arraycopy(left, 0, leftM, 0, m);
        boolean[] rightM = new boolean[m];
        System.arraycopy(right, 0, rightM, 0, m);
        count = count(threshold, leftM, rightM);
        return new Pair<Double,Double>((double)m, (double)count);
    }

    public void setNumTries(int tries){
        this.numTries = tries;
    }

    public void setSubsumptionMode(int mode){
        this.matchingMode = mode;
    }

    private TheorySolver constructTheorySolver(){
        TheorySolver ts = new TheorySolver();
        ts.setSubsumptionMode(this.matchingMode);
        ts.setSatSolver(new CryptoMiniSatExecutor());
        ts.setMode(TheorySolver.GROUND_ALL);
        return ts;
    }

    private int logSatSearch(int threshold, boolean[][] left, boolean[] right, int mPrev) {
        //slower but kind-of tested
        for (int m = 1; m <= left.length; m++) {
            boolean[][] leftM = new boolean[m][];
            System.arraycopy(left, 0, leftM, 0, m);
            boolean[] rightM = new boolean[m];
            System.arraycopy(right, 0, rightM, 0, m);
            int count = count(threshold, leftM, rightM);
            System.out.println(rightM.length + " -> " + count);
            if (count < threshold) {
                return m;
            }
        }
        return left.length;

// faster, but not extensively tested

//        TheorySolver ts = new TheorySolver();
//
//
//        int loIndex = 0;
//        int hiIndex = right.length - 1;
//        int m = mPrev;
//        Boolean[] bigCell = new Boolean[right.length];
//        bigCell[0] = Boolean.TRUE;
//        bigCell[1] = Boolean.FALSE;
//        while (true){
//            boolean[][] leftM = new boolean[m][];
//            System.arraycopy(left, 0, leftM, 0, m);
//            boolean[] rightM = new boolean[m];
//            System.arraycopy(right, 0, rightM, 0, m);
//            int count = count(threshold, leftM,  rightM);
//            System.out.println(rightM.length+" -> "+count+", threshold: "+threshold);
//            if (count >= threshold){
//                if (Boolean.FALSE.equals(bigCell[m+1])){
//                    return m+1;
//                }
//                for (int i = 1; i <= m; i++){
//                    bigCell[i] = Boolean.TRUE;
//                }
//                loIndex = m;
//                if (Math.abs(m-mPrev) < 3){
//                    m = m+1;
//                } else if (2*m < hiIndex){
//                    m = 2*m;
//                } else {
//                    m = (m+hiIndex)/2;
//                }
//            } else {
//                if (Boolean.TRUE.equals(bigCell[m-1])){
//                    return m;
//                }
//                for (int i = m; i < bigCell.length; i++){
//                    bigCell[i] = Boolean.FALSE;
//                }
//                hiIndex = m;
//                if (Math.abs(m-mPrev) < 3){
//                    m = m-1;
//                } else {
//                    m = (m+loIndex)/2;
//                }
//            }
//        }
    }

}
