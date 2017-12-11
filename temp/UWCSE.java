/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package temp;

import ida.ilp.logic.Clause;
import ida.ilp.logic.Constant;
import ida.ilp.logic.Literal;
import ida.ilp.logic.LogicUtils;
import ida.utils.Combinatorics;
import ida.utils.Sugar;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by kuzelkao_cardiff on 19/01/17.
 */
public class UWCSE {

    public static Clause uwcse(){
        return uwcse("ai","language","theory");
    }

    public static Clause uwcse(String ...dbs){
        List<Literal> literals = new ArrayList<Literal>();
        for (String db : dbs) {
            try {
                for (String line : Sugar.readLines(new FileReader("/Users/kuzelkao_cardiff/Dropbox/Experiments/IJCAI17/uwcse/uw-cse/" + db + ".db"))) {
                    line = line.trim();
                    if (line.length() > 0) {
                        Literal l = Literal.parseLiteral(line);
                        if (l.predicate().equals("taughtBy") || l.predicate().equals("projectMember") || l.predicate().equals("advisedBy") ||
                                l.predicate().equals("tempAdvisedBy") || l.predicate().equals("ta") || l.predicate().equals("professor") ||
                                l.predicate().equals("student") || l.predicate().equals("publication")) {
                            literals.add(Sugar.chooseOne(LogicUtils.constantizeClause(new Clause(l)).literals()));
                        } else if (l.predicate().equals("hasPosition") || l.predicate().equals("inPhase") /*|| l.predicate().equals("yearsInProgram")*/) {
                            literals.add(new Literal(l.get(1).name(), Constant.construct(l.get(0).name().toLowerCase())));
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return new Clause(literals);
    }

    public static void writeEvidenceForQueries(File folder) throws Exception {
        Random random = new Random(1);
        if (!folder.exists()){
            folder.mkdirs();
        }
        List<Literal> literals = Sugar.listFromCollections(uwcse("systems", "graphics").literals());
        for (int i = 1; i < literals.size()-1; i++){
            PrintWriter pw = new PrintWriter(new FileWriter(folder.getAbsoluteFile()+"/queries"+i+".db"));
            for (Literal l : Combinatorics.randomCombination(literals, i, random).toList()){
                pw.println(LogicUtils.variabilizeClause(new Clause(l)));
            }
            pw.close();
        }
    }

    public static void main(String[] args) throws Exception {
//        for (Literal l : LogicUtils.variabilizeClause(uwcse(/*"ai", "language", "theory"*/"systems", "graphics")).literals()){
//            System.out.println(l);
//        }
        System.out.println(LogicUtils.variabilizeClause(uwcse(/*"ai", "language", "theory"*/"systems", "graphics")).predicates());
//        writeEvidenceForQueries(new File("/Users/kuzelkao_cardiff/Dropbox/Experiments/IJCAI17/uwcse/uw-cse/queries/"));
    }
}
