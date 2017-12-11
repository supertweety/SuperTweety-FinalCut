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
import ida.ilp.logic.Literal;
import supertweety.possibilistic.DefaultRule;
import supertweety.possibilistic.PossibilisticLogicTheory;

/**
 * Created by kuzelkao_cardiff on 13/04/16.
 */
public class CreditDefaultsExperiment {

    private static boolean clausesAsRules = true;

    public static String latexClause(Clause clause){
        return latexIt(clause, "\\vee");
    }

    public static String latexRule(DefaultRule rule){
        return latexIt(rule.antecedent(), "\\wedge")+" \\snake "+latexIt(rule.consequent(), "\\vee");
    }

    private static String latexIt(Clause clause, String logicConnective){
        StringBuilder sb = new StringBuilder();
        for (Literal literal : clause.literals()){
            if (!literal.predicate().startsWith("type:")) {
                if (literal.predicate().startsWith("@")){
                    Literal newLit = new Literal(literal.predicate().substring(1), literal.arity());
                    for (int i = 0; i < literal.arity(); i++){
                        newLit.set(literal.get(i), i);
                    }
                }
                if (literal.isNegated()) {
                    sb.append(" \\neg ");
                }
                sb.append("\\textit{" + literal.predicate() + "}(");
                for (int i = 0; i < literal.arity(); i++) {

                    sb.append("\\textit{" + literal.get(i) + "}");
                    if (i < literal.arity() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append(") "+logicConnective+" ");
            }
        }
        if (sb.toString().contains(" "+logicConnective+" ")){
            sb.delete(sb.length()-(" "+logicConnective+" ").length(), sb.length());
        }
        return sb.toString().replaceAll("_", "\\\\_");
    }

    public static void toLatex(PossibilisticLogicTheory pl){
        System.out.println("\\begin{align*}");
        int i = 0;
        for (double level : pl.weights()){
            for (Clause rule : pl.level(level)) {
                System.out.println("("+latexClause(rule)+", \\lambda_{"+(i)+"}) \\\\");
            }
            i++;
        }
        System.out.println("\\end{align*}");
    }


//    public static void main(String[] args) throws Exception {
//        Reader reader = new FileReader("../../../Experiments/ECAI16/default/default-yes.data.txt");
//        BinaryDataset dataset = BinaryDataset.readCSV(reader);
//        Pair<BinaryDataset,BinaryDataset> split = dataset.randomSplit(0.5, new Random(2016));
//
//        DensityEstimationTree et = DensityEstimationTree.train(split.r, 10);
//
//        PossibilisticLogicTheory plt = et.toPossibilisticLogic();
//
//        Set<Clause> hardRules = Sugar.<Clause>set(
//                Clause.parse("gradSchool(x),university(x),highSchool(x),otherSchool(x)"),
//                Clause.parse("!gradSchool(x),!university(x)"),
//                Clause.parse("!gradSchool(x),!highSchool(x)"),
//                Clause.parse("!gradSchool(x),!otherSchool(x)"),
//                Clause.parse("!university(x),!highSchool(x)"),
//                Clause.parse("!university(x),!otherSchool(x)"),
//                Clause.parse("!highSchool(x),!otherSchool(x)"),
//                Clause.parse("married(x), single(x), otherMaritalStatus(x)"),
//                Clause.parse("!married(x),!single(x)"),
//                Clause.parse("!married(x),!otherMaritalStatus(x)"),
//                Clause.parse("!single(x),!otherMaritalStatus(x)")
//        );
//        plt.addAllHardRules(hardRules);
//
//
//        plt = PossibilisticUtils.simplifyBySAT(PossibilisticUtils.removeImpliedRules(PossibilisticUtils.removeDrownedLevels(plt)));
//
//        System.out.println(plt);
//
//        ExpectationMaximizationDET emd = new ExpectationMaximizationDET();
//        emd.setMinInLeaf(10);
//
//        DensityEstimationTreeMixture mixture = emd.train(dataset, 5, 100);
//
//        System.out.println("Mixture probs: "+ VectorUtils.doubleArrayToString(mixture.mixtureProbabilities()));
//
//        for (DensityEstimationTree tree : mixture.trees()) {
//
//            PossibilisticLogicTheory comp = tree.toPossibilisticLogic();
//            comp.addAllHardRules(hardRules);
//
//            comp = PossibilisticUtils.collapse(comp, 100);
//            comp = PossibilisticUtils.simplifyBySAT(PossibilisticUtils.removeImpliedRules(PossibilisticUtils.removeDrownedLevels(comp)));
//
//            System.out.println(comp);
//        }
////
//////        for (DefaultRule rule : DefaultsExtractor.extractSystemPDefaults(plt, hardRules, 1)){
//////            System.out.println(rule);
//////            //System.out.println(latexRule(rule));
//////        }
////
////        toLatex(plt);
////
////        System.out.println("COLLAPSED: ");
////
////        plt = PossibilisticUtils.collapse(plt, 5);
////
////
////
////        System.out.println(plt);
////
////        plt = PossibilisticUtils.simplifyBySAT(PossibilisticUtils.removeImpliedRules(PossibilisticUtils.removeDrownedLevels(plt)));
////
////        System.out.println(plt);
////
////        toLatex(plt);
//    }

}
