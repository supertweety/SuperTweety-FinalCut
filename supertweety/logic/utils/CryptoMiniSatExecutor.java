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
import ida.utils.Sugar;
import ida.utils.collections.ValueToIndex;
import supertweety.logic.SatSolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Created by kuzelkao_cardiff on 11/01/17.
 */
public class CryptoMiniSatExecutor implements SatSolver {

    private String path = "/Users/kuzelkao_cardiff/Dropbox/Experiments/IJCAI17/cryptominisat/build/";

    private long id;

    private final static Random random = new Random(System.nanoTime());

    public CryptoMiniSatExecutor(){}

    public CryptoMiniSatExecutor(String path){
        this.path = path;
        this.id = random.nextLong();
    }

    public Set<Literal> solve(Collection<Clause> satProblem){
        List<Set<Literal>> sols = solveAll(satProblem, 1);
        if (sols.isEmpty()){
            return null;
        }
        return Sugar.chooseOne(sols);
    }

    public List<Set<Literal>> solveAll(Collection<Clause> satProblem, int maxCount) {
        return solveAll(satProblem, Sugar.<Literal>set(), maxCount);
    }

    @Override
    public List<Set<Literal>> solveAll(Collection<Clause> satProblem, Set<Literal> groundAtoms, int maxCount) {
        try {
            Set<Set<Literal>> retVal = new HashSet<Set<Literal>>();
            String filepath = path + "satproblem" + id + ".cnf";
            ValueToIndex<Literal> vti = new ValueToIndex<Literal>(1);
            for (Literal groundAtom : groundAtoms){
                vti.valueToIndex(groundAtom);
            }

            FileWriter writer = new FileWriter(filepath);
            DimacsIO.write(satProblem, writer, vti);
            writer.flush();
            writer.close();

//            StringWriter sw = new StringWriter();
//            DimacsIO.write(satProblem, sw, vti);
//            System.out.println(sw.getBuffer().toString());

            String[] cmd = {"/bin/sh", "-c", "cd " + path + "; ./cryptominisat5 --verb 0  --maxsol "+maxCount+" "+filepath};
            Process p = Runtime.getRuntime().exec(cmd);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            String s;
            String currentLine = null;
            while ((s = stdInput.readLine()) != null) {
                //System.out.println(s);
                if (s.startsWith("v ")) {
                    if (currentLine == null) {
                        currentLine = s;
                    } else {
                        currentLine += s.substring(2);
                    }

                } else if (s.startsWith("s ")){
                    if (currentLine != null) {
                        Set<Literal> solution = new HashSet<Literal>();
                        currentLine = currentLine.substring(1).trim();
                        for (String spl : currentLine.split(" ")) {
                            int i = Integer.parseInt(spl);
                            if (i != 0) {
                                int abs = (int) Math.abs(i);
                                Literal l = vti.indexToValue(abs);
                                if (i > 0) {
                                    solution.add(l);
                                }
                            }
                        }
                        //System.out.println(solution+" "+currentLine);
                        retVal.add(solution);
                        currentLine = null;
                    }
                } else {
                    currentLine += s;
                }
            }
            if (currentLine != null) {
                Set<Literal> solution = new HashSet<Literal>();
                currentLine = currentLine.substring(1).trim();
                for (String spl : currentLine.split(" ")) {
                    int i = Integer.parseInt(spl);
                    if (i != 0) {
                        int abs = (int) Math.abs(i);
                        Literal l = vti.indexToValue(abs);
                        if (i > 0) {
                            solution.add(l);
                        }
                    }
                }
                retVal.add(solution);
            }
            //System.out.println("NUM RESULTS FROM CMS: "+retVal.size()+", "+maxCount);
            while ((s = stdError.readLine()) != null) {
                System.err.println("Errors when executing Cryptominisat: " + s);
            }
            p.waitFor();
            new File(filepath).delete();
            return Sugar.listFromCollections(retVal);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }


    public int modelCount(Collection<Clause> satProblem, int maxCount) {
        return solveAll(satProblem, maxCount).size();
    }

    public static void main(String[] args) throws Exception {
        CryptoMiniSatExecutor re = new CryptoMiniSatExecutor();
        List<Set<Literal>> sols = re.solveAll(Sugar.<Clause>list(
                Clause.parse("!bird(x),flies(x)"),
                Clause.parse("bird(x),pig(x)"),
                Clause.parse("@xor(bird(x),pig(x))")
        ), 100);
        System.out.println(sols);
    }

}
