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
import ida.ilp.logic.Literal;
import ida.ilp.logic.LogicUtils;
import ida.utils.Sugar;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kuzelkao_cardiff on 02/02/17.
 */
public class IMDB {

    public static Clause imdb(){
        List<Literal> literals = new ArrayList<Literal>();
        try {
            for (String line : Sugar.readLines(new FileReader("/Users/kuzelkao_cardiff/Dropbox/Experiments/IJCAI17/uwcse/uw-cse/imdb.1.db"))) {
                line = line.trim();
                if (line.length() > 0) {
                    Literal l = Literal.parseLiteral(line);
                    literals.add(Sugar.chooseOne(LogicUtils.constantizeClause(new Clause(l)).literals()));

                }
            }
        } catch (Exception e){
            throw new RuntimeException(e);
        }
        return new Clause(literals);
    }

}
