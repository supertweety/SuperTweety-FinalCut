/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.logic.utils;


import ida.ilp.logic.Clause;
import ida.ilp.logic.Literal;
import supertweety.logic.ModelCounter;
import supertweety.logic.TheorySolver;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by kuzelkao_cardiff on 08/12/17.
 */
public class NaiveModelCounterFactory implements ModelCounterFactory {

    @Override
    public ModelCounter newInstance() {
        return new ModelCounter() {
            @Override
            public BigInteger modelCount(Collection<Clause> satProblem) {
                TheorySolver ts = new TheorySolver();
                return BigInteger.valueOf(ts.solveAll(satProblem, new HashSet<Literal>(), new HashSet<Literal>(), Integer.MAX_VALUE).size());
            }
        };
    }
}
