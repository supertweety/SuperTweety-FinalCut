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
import ida.ilp.logic.Function;
import ida.ilp.logic.Literal;
import ida.utils.Sugar;
import ida.utils.collections.ValueToIndex;

import java.io.*;
import java.util.*;

/**
 * Created by kuzelkao_cardiff on 17/03/16.
 */
public class DimacsIO {

    public static void write(Collection<Clause> groundClauses, Writer writer, ValueToIndex<Literal> vti) throws IOException {
        PrintWriter pw = new PrintWriter(writer);

        List<Clause> clauses  = new ArrayList<Clause>();
        List<Clause> xorClauses  = new ArrayList<Clause>();

        for (Clause c : groundClauses) {
            if (c.predicates().contains("@xor")) {
                if (c.literals().size() > 1) {
                    throw new IllegalArgumentException("Multiple xors in one clause not allowed yet.");
                }
                Literal xl = Sugar.chooseOne(c.literals());
                if (xl.arity() > 0) {
                    ArrayList<Literal> lits = new ArrayList<Literal>();
                    for (int i = 0; i < xl.arity(); i++) {
                        lits.add(((Function) xl.get(i)).toLiteral());
                    }
                    if (xl.isNegated()) {
                        lits.set(0, lits.get(0).negation());
                    }
                    xorClauses.add(new Clause(lits));
                }
            } else {
                clauses.add(c);
            }
        }

        for (Clause c : clauses){
            for (Literal l : c.literals()){
                if (l.isNegated()){
                    l = l.negation();
                }
                vti.valueToIndex(l);
            }
        }
        for (Clause c : xorClauses){
            for (Literal l : c.literals()){
                if (l.isNegated()){
                    l = l.negation();
                }
                vti.valueToIndex(l);
            }
        }

        pw.println("p cnf "+vti.size()+" "+(clauses.size()+xorClauses.size()));
        for (Clause c : clauses){
            pw.println(clauseToString(c, vti));
        }
        for (Clause c : xorClauses){
            pw.println("x"+clauseToString(c, vti));
        }
        pw.flush();
    }

    private static String clauseToString(Clause clause, ValueToIndex<Literal> vti){
        StringBuilder sb = new StringBuilder();
        List<Integer> literals = new ArrayList<Integer>();
        for (Literal l : clause.literals()){
            if (l.isNegated()){
                literals.add(-vti.valueToIndex(l.negation()));
            } else {
                literals.add(vti.valueToIndex(l));
            }
        }
        Collections.sort(literals, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return Math.abs(o1)-Math.abs(o2);
            }
        });
        int j = 0;
        for (Integer i : literals){
            sb.append(i);
            sb.append(" ");
            j++;
        }
        sb.append("0");
        return sb.toString();
    }

//    public static Collection<Clause> read(Reader reader) throws IOException {
//        return null;
//    }

}
