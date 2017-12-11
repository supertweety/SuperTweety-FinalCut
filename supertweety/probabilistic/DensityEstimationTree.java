/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.probabilistic;

import ida.ilp.logic.Clause;
import ida.ilp.logic.Literal;
import ida.utils.Sugar;
import ida.utils.tuples.Pair;
import supertweety.BinaryDataset;
import supertweety.Globals;
import supertweety.logic.ModelCounter;
import supertweety.possibilistic.*;

import java.math.BigInteger;
import java.util.*;

/**
 * Created by kuzelkao_cardiff on 20/01/16.
 */
public class DensityEstimationTree implements DiscreteProbabilityDistribution {

    //primary

    public static double fractionUsedForStructureLearning = 0.33;

    private static Random random = new Random(2016);

    private Node root;

    private String[] attributes;

    public final static int MIN_IN_LEAF_COUNT = 1, MIN_IN_LEAF_WEIGHTED_SUM = 2;

    //derived:

    private Map<String,Integer> attributeIndices = new HashMap<String,Integer>();

    private DensityEstimationTree(){}

    private DensityEstimationTree(Node root, String[] attributes){
        this.set(root, attributes);
    }

    private void set(Node root, String[] attributes){
        this.root = root;
        this.attributes = attributes;
        for (int i = 0; i < this.attributes.length; i++){
            this.attributeIndices.put(this.attributes[i], i);
        }
    }

    public static DensityEstimationTree learn(BinaryDataset dataset, int minInLeaf){
        return learn(dataset, minInLeaf, MIN_IN_LEAF_COUNT);
    }

    public static DensityEstimationTree learn(BinaryDataset dataset, int minInLeaf, int minInLeafMode){
        Pair<BinaryDataset,BinaryDataset> datasetSplit = dataset.randomSplit(fractionUsedForStructureLearning, random);
        BinaryDataset datasetA = datasetSplit.r;
        Pair<BinaryDataset,BinaryDataset> datasetSplit2 = datasetSplit.s.randomSplit(0.75, random);
        BinaryDataset weightLearningDataset = datasetSplit2.r;
        BinaryDataset pruningDataset = datasetSplit2.s;
        DensityEstimationTree structure = new DensityEstimationTree();
        structure.set(learnStructure(datasetA, Sugar.set(dataset.attributes()), minInLeaf, datasetA.sumOfWeights(), structure, minInLeafMode), datasetA.attributes());
        structure.estimateParameters(weightLearningDataset, pruningDataset, true);
        pruneZeros(structure.root);
        return structure;
    }

    private void estimateParameters(BinaryDataset dataset, BinaryDataset pruningValidationDataset, boolean prune){
        estimateParameters(this.root, dataset, pruningValidationDataset, dataset.sumOfWeights(), 0, prune);
    }

    private void estimateParameters(Node node, BinaryDataset subdataset, BinaryDataset pruningValidationSubDataset, double N, double depth, boolean prune){
        if (subdataset.sumOfWeights() == 0){
            node.v = 0;
            node.attribute = null;
            node.yes = null;
            node.no = null;
        } else {
            double volume = Math.pow(2, subdataset.attributes().length - depth);
            node.v = subdataset.sumOfWeights() / (volume * N);
            if (!node.isLeaf()){
                Pair<BinaryDataset,BinaryDataset> split = subdataset.splitOnAttribute(node.attribute);
                Pair<BinaryDataset,BinaryDataset> validationSplit = pruningValidationSubDataset.splitOnAttribute(node.attribute);
                estimateParameters(node.yes(), split.r, validationSplit.r, N, depth+1, prune);
                estimateParameters(node.no(), split.s, validationSplit.s, N, depth+1, prune);

                if (prune) {
                    double logLik = logLikelihood(node, pruningValidationSubDataset);
                    double flatLogLik = logLikelihoodFromConstantDensity(node, pruningValidationSubDataset);
                    if (logLik < flatLogLik) {
                        //System.out.println("Pruning");
                        node.attribute = null;
                        node.yes = null;
                        node.no = null;
                    }
                }
            }
        }
    }

    private double logLikelihood(Node node, BinaryDataset subdataset){
        if (node.isLeaf()){
            double logEpsilon = - subdataset.attributes().length;
            return subdataset.sumOfWeights() * Math.max(logEpsilon, Math.log(node.density()));
        } else {
            Pair<BinaryDataset,BinaryDataset> split = subdataset.splitOnAttribute(node.attribute);
            return logLikelihood(node.yes(), split.r) + logLikelihood(node.no(), split.s);
        }
    }

    private double logLikelihoodFromConstantDensity(Node node, BinaryDataset subdataset){
        double logEpsilon = - subdataset.attributes().length;
        return subdataset.sumOfWeights() * Math.max(logEpsilon, Math.log(node.density()));
    }

    private static Node learnStructure(BinaryDataset subdataset, Set<String> unusedAttributes, int minInLeaf, double N, DensityEstimationTree det, int minInLeafMode){
        double volume = Math.pow(2,unusedAttributes.size());
        double t;
        if (minInLeafMode == MIN_IN_LEAF_COUNT){
            t = subdataset.numExamples();
        } else if (minInLeafMode == MIN_IN_LEAF_WEIGHTED_SUM){
            t = subdataset.sumOfWeights();
        } else {
            throw new IllegalArgumentException("Illegal argument for 'minInLeafMode' paramete");
        }
        if (t <= minInLeaf || unusedAttributes.isEmpty()){
            if (subdataset.sumOfWeights() == 0){
                return det.new Node(0);
            } else {
                double density = subdataset.sumOfWeights() / (volume * N);
                return det.new Node(density);
            }
        } else {
            double bestScore = Double.NEGATIVE_INFINITY;
            String bestAttribute = null;
            for (String attribute : unusedAttributes) {
                double s = splitScore(subdataset, attribute, unusedAttributes.size()-1, N);
                if (s > bestScore){
                    bestScore = s;
                    bestAttribute = attribute;
                }
            }
            Pair<BinaryDataset,BinaryDataset> split = subdataset.splitOnAttribute(bestAttribute);
            Set<String> newUnusedAttributes = new HashSet<String>(unusedAttributes);
            newUnusedAttributes.remove(bestAttribute);

            double density = subdataset.sumOfWeights() / (volume * N);
            return det.new Node(
                    learnStructure(split.r, newUnusedAttributes, minInLeaf, N, det, minInLeafMode),
                    learnStructure(split.s, newUnusedAttributes, minInLeaf, N, det, minInLeafMode),
                    bestAttribute,
                    density);
        }
    }

    private static double logVolume(Set<Literal> fixedLiterals, Collection<Clause> hardRules, Set<String> attributes){
        Set<Clause> clauses = new HashSet<Clause>();
        clauses.addAll(hardRules);
        for (Literal l : fixedLiterals){
            clauses.add(new Clause(l));
        }
        Set<String> usedPredicates = new HashSet<String>();
        for (Clause c : clauses){
            usedPredicates.addAll(c.predicates());
        }
        try {
            BigInteger modelCount = Globals.modelCounterFactory.newInstance().modelCount(clauses);
            return Sugar.logBigInteger(modelCount) + Sugar.setDifference(attributes, usedPredicates).size()*Math.log(2);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    private static double splitScore(BinaryDataset dataset, String attribute, int freeAttributes, double N){
        Pair<BinaryDataset,BinaryDataset> split = dataset.splitOnAttribute(attribute);
        double yesScore = score(split.r, freeAttributes, N);
        double noScore = score(split.s, freeAttributes, N);
        return yesScore+noScore;
    }

    private static double score(BinaryDataset dataset, int freeAttributes, double N){
        double t = dataset.sumOfWeights();
        double volume = Math.pow(2, freeAttributes);
        return t*t/(N*N*volume);
    }

    public double probability(boolean[] world){
        return this.root.probability(world);
    }

    public String[] attributes(){
        return this.attributes;
    }

    public String toString(){
        return this.root.toString();
    }

    public int numberOfNodes(){
        return this.root.numberOfNodes();
    }

    public int numberOfLeaves(){
        return this.root.numberOfLeaves();
    }

    private static int numLeaves(Node node){
        if (node.isLeaf()){
            return 1;
        } else {
            return numLeaves(node.yes)+ numLeaves(node.no);
        }
    }


    public Node root(){
        return this.root;
    }

    public PossibilisticLogicTheory toPossibilisticLogic(){
        return toPossibilisticLogic(true);
    }

    public PossibilisticLogicTheory toPossibilisticLogic(boolean simplify){
        if (this.root == null || this.root.isLeaf()){
            return new PossibilisticLogicTheory();
        }
        PossibilisticLogicTheory plt = new PossibilisticLogicTheory();
        addRulesToPossibilisticLogicTheory(this.root.yes(), plt);
        addRulesToPossibilisticLogicTheory(this.root.no(), plt);
        if (simplify) {
            plt = PossibilisticUtils.removeDrownedLevels(plt);
            plt = PossibilisticUtils.simplifyByUnitPropagation(plt);
            plt = PossibilisticUtils.simplifyByResolution(plt);
            plt = PossibilisticUtils.simplifyBySAT(plt);
        }
        for (String attribute : this.attributes){
            //in case it is not explicitly in the theory, we add it here
            plt.addAdditionalGroundAtom(new Literal(attribute));
        }
        return plt;
    }

    private void addRulesToPossibilisticLogicTheory(Node node, PossibilisticLogicTheory plt){
        if (node.isLeaf()) {
            plt.addRule(new Clause(getLiterals(node)), -node.density());
        }
        if (node.yes != null) {
            addRulesToPossibilisticLogicTheory(node.yes(), plt);
        }
        if (node.no != null){
            addRulesToPossibilisticLogicTheory(node.no(), plt);
        }
    }

    private List<Literal> getLiterals(Node node){
        List<Literal> lits = new ArrayList<Literal>();
        getLiterals(node, lits);
        return lits;
    }

    private void getLiterals(Node node, List<Literal> literals){
        if (node.parent != null){
            boolean isNegative = node.parent.no == node;
            //Node sibling = isNegative ? node.parent.yes : node.parent.no;
            literals.add(new Literal(node.parent.attribute, !isNegative));
            getLiterals(node.parent, literals);
        }
    }

    private static void pruneZeros(Node node){
        if (node.density() == 0){
            node.attribute = null;
            node.yes = null;
            node.no = null;
        } else if (node.attribute != null){
            pruneZeros(node.yes);
            pruneZeros(node.no);
        }
    }

    protected List<BinaryDataset> cluster(BinaryDataset dataset, int components){
        List<BinaryDataset> retVal = new ArrayList<BinaryDataset>();
        double avgSize = (double)dataset.numExamples()/components;
        while (dataset.numExamples() >= 2.0*avgSize){
            List<BinaryDataset> list = new ArrayList<BinaryDataset>();
            clusterDown(this.root, dataset, components, (int)dataset.numExamples(), list);
            List<boolean[]> fromTooSmall = new ArrayList<boolean[]>();
            for (BinaryDataset bd : list){
                if (bd.numExamples() >= Math.ceil(avgSize)){
                    retVal.add(bd);
                } else {
                    for (boolean[] example : bd.examples()){
                        fromTooSmall.add(example);
                    }
                }
            }
            dataset = BinaryDataset.fromRows(fromTooSmall, dataset.attributes());
        }
        return retVal;
    }

    private void clusterDown(Node node, BinaryDataset dataset, int components, int totalNumExamples, List<BinaryDataset> out){
        if (dataset.numExamples() <= 1 || dataset.numExamples() <= totalNumExamples/(double)components || node.isLeaf()){
            out.add(dataset);
        } else {
            Pair<BinaryDataset,BinaryDataset> split = dataset.splitOnAttribute(node.attribute);
            if (split.r.numExamples() >= totalNumExamples/(double)components){
                clusterDown(node.yes(), split.r, components, totalNumExamples, out);
            }
            if (split.s.numExamples() >= totalNumExamples/(double)components){
                clusterDown(node.no(), split.s, components, totalNumExamples, out);
            }
        }
    }

    @Override
    public double probability(Collection<Clause> query, ModelCounter modelCounter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double density(Collection<Literal> possibleWorld) {
        boolean[] world = new boolean[this.attributes.length];
        for (Literal l : possibleWorld){
            if (this.attributeIndices.containsKey(l.predicate())) {
                if (l.isNegated()) {
                    world[this.attributeIndices.get(l.predicate())] = false;
                } else {
                    world[this.attributeIndices.get(l.predicate())] = true;
                }
            }
        }
        return this.probability(world);
    }

    @Override
    public Set<Literal> mostProbableWorld(Collection<Literal> evidence) {
        Set<String> positiveEvidence = new HashSet<String>();
        Set<String> negativeEvidence = new HashSet<String>();
        for (Literal l : evidence){
            if (l.isNegated()){
                negativeEvidence.add(l.predicate());
            } else {
                positiveEvidence.add(l.predicate());
            }
        }
        Node mostProbableLeaf = this.root.mostProbableLeaf(positiveEvidence, negativeEvidence);
        Set<Literal> retVal = new HashSet<Literal>();
        for (Literal l : getLiterals(mostProbableLeaf)){
            retVal.add(l.negation());
        }
        retVal.addAll(evidence);
        return retVal;
    }

    public List<Clause> probableClauses(double minCertainty){
        List<Node> improbableNodes = new ArrayList<Node>();
        improbNodes(1 - minCertainty, this.root, improbableNodes);
        List<Clause> retVal = new ArrayList<Clause>();
        for (Node node : improbableNodes){
            retVal.add(new Clause(getLiterals(node)));
        }
        return retVal;
    }

    private void improbNodes(double threshold, Node node, List<Node> out){
        if (node.probability() < threshold){
            out.add(node);
        } else if (!node.isLeaf()){
            improbNodes(threshold, node.yes(), out);
            improbNodes(threshold, node.no(), out);
        }
    }

    public class Node {

        private String attribute;

        private Node yes, no, parent;

        private double v;

        private int depth;

        public Node(){}

        public Node(Node yes, Node no, String attribute, double v){
            this.yes = yes;
            this.no = no;
            this.no.parent = this;
            this.yes.parent = this;
            this.yes.depth = this.depth+1;
            this.no.depth = this.depth+1;
            this.attribute = attribute;
            this.v = v;
        }

        public Node(double v){
            this.v = v;
        }


        public Node yes(){
            return yes;
        }

        public Node no(){
            return no;
        }


        public boolean isLeaf(){
            return this.attribute == null;
        }

        public double density(){
            return v;
        }

        public String toString(){
            StringBuilder sb = new StringBuilder();
            this.toString(sb, 0);
            return sb.toString();
        }

        private Node mostProbableLeaf(Set<String> positiveEvidence, Set<String> negativeEvidence){
            if (this.isLeaf()) {
                return this;
            } else {
                if (positiveEvidence.contains(this.attribute)){
                    return this.yes.mostProbableLeaf(positiveEvidence, negativeEvidence);
                } else if (negativeEvidence.contains(this.attribute)){
                    return this.no.mostProbableLeaf(positiveEvidence, negativeEvidence);
                } else {
                    Node y = this.yes.mostProbableLeaf(positiveEvidence, negativeEvidence);
                    Node n = this.no.mostProbableLeaf(positiveEvidence, negativeEvidence);
                    //negatively biased - implicitly everything is false by default
                    if (y.density() > n.density()){
                        return y;
                    } else {
                        return n;
                    }
                }
            }
        }

        private void toString(StringBuilder sb, int indents){
            if (this.attribute != null){
                for (int i = 0; i < indents; i++){
                    sb.append(' ');
                }
                sb.append(this.attribute).append(" = TRUE: ");
                sb.append(this.yes.density()).append('\n');
                this.yes.toString(sb, indents+1);
                for (int i = 0; i < indents; i++){
                    sb.append(' ');
                }
                sb.append(this.attribute).append(" = FALSE: ");
                sb.append(this.no.density()).append('\n');
                this.no.toString(sb, indents+1);
            }
        }

        protected double sanityCheck(int depth, int numAttributesInDataset){
            if (this.attribute == null){
                return this.v * Math.pow(2, numAttributesInDataset-depth);
            } else {
                return yes.sanityCheck(depth+1, numAttributesInDataset)+no.sanityCheck(depth+1, numAttributesInDataset);
            }
        }

        public int numberOfNodes(){
            if (this.isLeaf()){
                return 1;
            } else {
                return 1 + this.yes.numberOfNodes() + this.no.numberOfNodes();
            }
        }

        public int numberOfLeaves(){
            if (this.isLeaf()){
                return 1;
            } else {
                return this.yes.numberOfLeaves() + this.no.numberOfLeaves();
            }
        }

        public double probability(boolean[] world){
            if (isLeaf()){
                return this.density();
            } else {
                int attributeIndex = attributeIndices.get(this.attribute);
                if (world[attributeIndex]){
                    return yes.probability(world);
                } else {
                    return no.probability(world);
                }
            }
        }

        private double probability(){
            if (this.isLeaf()){
                return Math.exp(Math.log(this.density())+Math.log(2)*(attributes.length-depth));
            } else {
                return this.yes().probability()+this.no().probability();
            }
        }

    }
}
