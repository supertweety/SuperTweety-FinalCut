# SuperTweety-FinalCut
Codes from our papers (Kuzelka, Davis, Schockaert UAI'15/IJCAI'16/ECAI'16/IJCAI'17).

**Below are several tips on how to use the libraries.**

**1. Libraries**

The following libraries are needed: Sat4j (https://gitlab.ow2.org/sat4j/sat4j/), JOptimizer (http://www.joptimizer.com), Colt (https://dst.lbl.gov/ACSSoftware/colt/).

**2. Basics**

A possibilistic logic theory can be created from a set of weighted formulas as follows:

```java
import ida.ilp.logic.Clause;
import ida.ilp.logic.Literal;
import ida.utils.tuples.Pair;
import supertweety.possibilistic.PossibilisticLogicTheory;
import java.util.Set;
...

PossibilisticLogicTheory plt = new PossibilisticLogicTheory();
        
//the exclamation mark denotes negation
Clause rule1 = Clause.parse("!penguin(X), bird(X)");
plt.addRule(rule1, 1.0);

Clause rule2 = Clause.parse("!penguin(X), !flies(X)");
plt.addRule(rule2, 1.0);

Clause rule3 = Clause.parse("!bird(X), flies(X)");
plt.addRule(rule3, 0.5);

```

To find a most plausible world given the evidence that there is a penguin called *tweety*, we can use the *solve(...)* method 

```java
Set<Literal> evidence = new HashSet<Literal>();
Literal tweety = Literal.parseLiteral("penguin(tweety)");
evidence.add(tweety);

Pair<Set<Literal>,Double> solution = plt.solve(evidence);

System.out.println("A most plausible world: "+solution.r+", consistency level: "+solution.s);

```

When we run the above code, we get the output: **"A most plausible world: [penguin(tweety), bird(tweety)], consistency level: 1.0."**
Here the solution contains only the ground atoms (literals) that are true in the returned possible world, the ground atoms that are false in the possible world are those that are not contained in the returned set (this is useful in sparse domains).

To check if the literal *!flies(tweety)* is entailed (by possibilistic entailment relation \vdash_{poss}), we can use the following piece of code:

```java
boolean implied = plt.implies(evidence, Literal.parseLiteral("!flies(tweety)"));

System.out.println("is !flies(tweety) implied by the theory? "+implied);
```

**3. Learning Possibilistic Logic Theories Using Density Estimation Trees (ECAI'16)**

Propositional possibilistic logic theories can be learned using density estimation trees as follows:

First we create a binary dataset (binary datasets can also be read from CSV files using the static method *readCSV(Reader reader).*

```java
import supertweety.BinaryDataset;
import supertweety.probabilistic.DensityEstimationTree;
...
String[] attributeNames = {"bird", "penguin", "flies"};
boolean[][] data = {
                {true, false, true},
                {true, false, true},
                {true, true, false},
                {true, false, true},
                {true, false, true},
                {true, true, false},
                {true, false, true},
                {true, false, true}};

BinaryDataset binaryDataset = new BinaryDataset(data, attributeNames);
```

Then we can construct the density estimation tree.
```java
DensityEstimationTree densityEstimationTree = DensityEstimationTree.learn(binaryDataset, 1);
System.out.println(densityEstimationTree);
```

What we will then see is the following output (the numbers shown are "densities", i.e. probabilities of the individual worlds in the respective leaf).

```
bird = TRUE: 0.25
 flies = TRUE: 0.375
  penguin = TRUE: 0.0
  penguin = FALSE: 0.75
 flies = FALSE: 0.125
bird = FALSE: 0.0
```

To convert the density estimation tree to a possibilistic logic theory we can use the following code.

```java
PossibilisticLogicTheory detTheory = densityEstimationTree.toPossibilisticLogic(false);

System.out.println(detTheory);
```

We obtain the following output (the internal representation subtracts 1 from the actual weights of the rules for numerical reasons):

```
---------------------
Level -0.0
!penguin(), !flies(), !bird()
bird()
---------------------
Level -0.125
flies(), !bird()
---------------------
Level -0.75
penguin(), !flies(), !bird()
```

The theory can clearly be simplified; we can simplify it as follows:

```java
detTheory = PossibilisticUtils.simplify(detTheory);

System.out.println(detTheory);
```

This gives us:

```
---------------------
Level -0.0
bird()
!penguin(), !flies()
---------------------
Level -0.125
flies()
```

This is different from the theory we have before but it is actually not a too suprising result. In our simple dataset, "everything" was a bird, which is the reason why we ended up with the rule bird() with the maximum weight. 


**4. Maximum-likelihood Estimation (ECAI'16)**

It is also possible to optimize weights of a given possibilistic logic theory using maximum-likelihood principle (to give them probabilistic meaning and to allow marginal queries).
This can be done as follows:

```java
boolean[][] data = new boolean[][]{
        {true,true,true},{true,true,true},{true,true,false},{true,true,false},
        {true,false,true},{true,false,true},{true,false,true},
        {false,true,true},{false,true,true},
        {false,false,true}
};
String[] attributes = {"a", "b", "c"};

//this is now our binary dataset
BinaryDataset dataset = new BinaryDataset(data, attributes);

//a model counter is needed for maximum likelihood estimation
ModelCounter mc = Globals.modelCounterFactory.newInstance();

//this is the possibilistic logic theory that we will play with
PossibilisticLogicTheory plt = new PossibilisticLogicTheory();

plt.addRule(Clause.parse("!a(),b()"), 0.85);
plt.addRule(Clause.parse("a(),!b()"), 0.9);
plt.addRule(Clause.parse("a(),b()"), 0.95);

//c() is not contained in any of the formulas but it is an attribute so we have to "tell" the possibilistic logic theory about c()
plt.addAdditionalGroundAtom(Literal.parseLiteral("c()"));

//next we obtain the maximum likelihood estimate of the weights
PossibilisticLogicTheory mlplt = plt.maximumLikelihoodWeightEstimation(dataset, Globals.modelCounterFactory.newInstance());

//we print the learned theory
System.out.println(mlplt);

//this will be our marginal query, we will be asking about probability of "b()"
Collection<Clause> query = Sugar.<Clause>list(
        Clause.parse("b()")
);

System.out.println("Probability of b() is "+mlplt.probability(query, mc));
```

The output is then:

```
---------------------
Level -0.04999947380860192
a(), b()
---------------------
Level -0.0999995836979177
a(), !b()
---------------------
Level -0.14999954396355156
!a(), b()

Probability of b() is 0.6000012862051816
```

**5. Learning from Default Rules (IJCAI'16)**

Possibilistic logic theories can also be learnt from default rules. For this setting, there is also a command line interface in *supertweety.possibilistic.learningFromDefaultRules.Main*.

The heuristic learned can also be called directly from the code as follows:

```java
DefaultRule dr1 = new DefaultRule("bird()", "flies()");
DefaultRule dr2 = new DefaultRule("penguin()", "!flies()");
DefaultRule dr3 = new DefaultRule("penguin()", "bird()");

List<DefaultRule> positiveExamples = new ArrayList<DefaultRule>();
positiveExamples.add(dr1);
positiveExamples.add(dr2);
positiveExamples.add(dr3);

DefaultRule dr4 = new DefaultRule("", "bird()");
DefaultRule dr5 = new DefaultRule("", "!bird()");
DefaultRule dr6 = new DefaultRule("", "!flies()");
DefaultRule dr7 = new DefaultRule("", "flies()");
List<DefaultRule> negativeExamples = new ArrayList<DefaultRule>();
negativeExamples.add(dr4);
negativeExamples.add(dr5);
negativeExamples.add(dr6);
negativeExamples.add(dr7);

HeuristicLearningFromDefaults learner = new HeuristicLearningFromDefaults(positiveExamples, negativeExamples);

PossibilisticLogicTheory learnedPLT = learner.greedyIncrementalLearner(100);

System.out.println(learnedPLT);
```

The output in this case is a possibilistic logic theory:

```
---------------------
Level 0.6666666666666666
!penguin(), !flies()
!penguin(), bird()
---------------------
Level 0.3333333333333333
!bird(), flies()
```

Note that the heuristic learner needs sufficient number of examples to start doing anything interesting. In the simple case when the given set of default rules is consistent, it is also possible to obtain a similar possibilistic logic theory as follows using rational closure (Z-ranking):

```java
PossibilisticLogicTheory rationalClosure = GroundRationalClosure.transform(positiveExamples);

System.out.println(rationalClosure);
```

This again gives us:

```
---------------------
Level 0.6666666666666666
!penguin(), bird()
!penguin(), !flies()
---------------------
Level 0.3333333333333333
!bird(), flies()
```

The datasets used for evaluation of the heuristic learner are available from https://github.com/supertweety/datasets.

Btw. if you are interested in "lifted" version of rational, lexicographic and max-entropy closures (from our ILP'15 paper), check also our repository https://github.com/supertweety/mln2poss.

**6. Learning Relational Possibilistic Logic Theories (IJCAI'17)**

The codes for learning relational possibilistic logic theories (as in our IJCAI'17 paper) are in the package *supertweety.shorty*. The datasets are available from https://github.com/supertweety/SuperTweety-FinalCut/tree/master/datasets/ijcai17. 

More details will likely be added here in the future.

**7. Transforming Markov Logic Networks to Possibilistic Logic Theories (UAI'15)**

We start by creating a Markov logic network (MLN):

```java
import supertweety.probabilistic.mln.MarkovLogicNetwork;
...
//rules that will be used in the MLN
Clause r1 = Clause.parse("!bird(X), flies(X)");
Clause r2 = Clause.parse("!antarctic(X),!flies(X)");
Clause r3 = Clause.parse("!heavy(X), !flies(X)");
Clause r4 = Clause.parse("!hasJetPack(X),flies(X)");
Clause r5 = Clause.parse("exists(tweety)");
MarkovLogicNetwork mln = new MarkovLogicNetwork(Sugar.<Pair<Clause, BigInteger>>list(
        new Pair<Clause, BigInteger>(r1, BigInteger.valueOf(10)), /* a rule and its weight */
        new Pair<Clause, BigInteger>(r2, BigInteger.valueOf(1)), /* a rule and its weight */
        new Pair<Clause, BigInteger>(r3, BigInteger.valueOf(10)), /* a rule and its weight */ 
        new Pair<Clause, BigInteger>(r4, BigInteger.valueOf(100)), /* a rule and its weight */
        new Pair<Clause, BigInteger>(r5, null) /*rules with weight "null" are hard rules, i.e. rules with infinite weight */
));
```

First, we can play with MAP-inference a bit:

```java
//set evidence
mln.addEvidence(Literal.parseLiteral("penguin(tweety)"));
mln.addEvidence(Literal.parseLiteral("bird(donald)"));

//run MAP-inference withno bound on number of steps
mln.runMAPInference(Integer.MAX_VALUE);

//print the MAP-state that was found
System.out.println("state: " + mln.state());
System.out.println("penalty: " + mln.penalty());
```

The result we get is:

```
state: [bird(donald), exists(tweety), flies(donald), antarctic(tweety)]
penalty: 0
```

Next we remove the addEvidence commands (the transformer expects the evidence to be empty) and run the algorithm to trasnform the MLN to a possibilistic logic theory:

```java
ExhaustiveConvertor exhaustiveConvertor = new ExhaustiveConvertor(mln, Sugar.<Term>set(Constant.construct("tweety")));
exhaustiveConvertor.setDoNotRemoveEntailedByLonger(true);
exhaustiveConvertor.convert(10000);

PossibilisticLogicTheory p = exhaustiveConvertor.possibilisticLogic();

System.out.println(p);
```

And the result is:

```
---------------------
Level Infinity
exists(tweety)
---------------------
Level 100.0
!bird(tweety), flies(tweety), !hasJetPack(tweety)
---------------------
Level 11.0
flies(tweety), !hasJetPack(tweety), bird(tweety)
---------------------
Level 10.0
!antarctic(tweety), !heavy(tweety), !flies(tweety)
---------------------
Level 1.0
!bird(tweety), flies(tweety), hasJetPack(tweety)
!heavy(tweety), antarctic(tweety), !flies(tweety)
!bird(tweety), !heavy(tweety)
---------------------
Level 0.0
!antarctic(tweety), !flies(tweety)
!bird(tweety), flies(tweety)
!heavy(tweety), !flies(tweety)
flies(tweety), !hasJetPack(tweety)
```

**8. Others**

There are plenty of other things implemented in the codes here but I will not list them here all.
