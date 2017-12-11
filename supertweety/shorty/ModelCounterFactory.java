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

import ida.ilp.logic.Clause;
import ida.ilp.logic.Constant;
import ida.ilp.logic.Literal;
import ida.ilp.logic.subsumption.Matching;
import supertweety.logic.ApproximateModelCounter;

import java.util.Collection;
import java.util.Set;

/**
 * Created by kuzelkao_cardiff on 06/02/17.
 */
public class ModelCounterFactory {

    private double epsilon = 0.2;

    private double delta = 0.1;

    public ApproximateModelCounter create(Collection<Clause> theory, Set<Constant> universe, Set<Literal> allGroundAtoms){
        ApproximateModelCounter amc = new ApproximateModelCounter(theory, universe, allGroundAtoms);
        amc.setSubsumptionMode(Matching.OI_SUBSUMPTION);
        amc.setNumTries(3);

        return amc;
    }


}
