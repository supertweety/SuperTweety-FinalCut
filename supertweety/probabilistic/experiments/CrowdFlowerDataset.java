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

import ida.utils.VectorUtils;
import ida.utils.tuples.Pair;
import supertweety.BinaryDataset;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created by kuzelkao_cardiff on 09/03/16.
 */
public class CrowdFlowerDataset {

    private static Map<String,String> abbrevs = new HashMap<String,String>();

    private static Random random = new Random(2016);

    static {
        abbrevs.put("tx", "Texas");
        abbrevs.put("ca", "California");
        abbrevs.put("mt", "Montana");
        abbrevs.put("nm", "New Mexico");
    }

    public static void main(String[] args) throws Exception {
        Reader reader = new FileReader("/Users/kuzelkao_cardiff/Dropbox/Experiments/UAI16/plants/plants.csv");
        PrintWriter writer = new PrintWriter(new File("/Users/kuzelkao_cardiff/Dropbox/Experiments/UAI16/plants/plants_crowd_flower.csv"));
        BinaryDataset dataset = BinaryDataset.readCSV(reader);
        Pair<BinaryDataset,BinaryDataset> trainTestSplit = dataset.randomSplit(0.5, new Random(2016));
        String[] states = new String[]{"tx", "ca", "mt", "nm"/*, "az", "nv", "co", "or", "wy", "mi"*/};

        BinaryDataset testSet = trainTestSplit.s.project(states);
        int evidenceSize = 2;
        int i = 0;
        for (boolean[] row : testSet.examples()){
            boolean[] mask = randomMask(row.length, evidenceSize);
            boolean positiveLiteralInEvidence = false;
            for (int j = 0; j < row.length; j++){
                if (mask[j] && row[j]){
                    positiveLiteralInEvidence = true;
                    break;
                }
            }
            if (!positiveLiteralInEvidence){
                continue;
            }
            String line = (i++)+","+makeText(row, states, mask)+","+groundTruthString(row, states);
            System.out.println(line);
            writer.println(line);
        }
        writer.close();
    }

    private static String makeText(boolean[] row, String[] states, boolean[] mask){
        StringBuilder sb = new StringBuilder();
        sb.append("You know the following facts about plant X: ");
        for (int i = 0; i < row.length; i++){
            if (mask[i]) {
                if (row[i]) {
                    sb.append("X is found in ").append(abbrevs.get(states[i])).append(". ");
                } else {
                    sb.append("X is not found in ").append(abbrevs.get(states[i])).append(". ");
                }
            }
        }
        sb.append("In which of the following states can X be likely found?");
        return sb.toString();
    }

    private static String groundTruthString(boolean[] row, String[] states){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++){
            if (!row[i]) {
                sb.append("!");
            }
            sb.append(abbrevs.get(states[i]));
            if (i < row.length-1){
                sb.append("&");
            }
        }
        return sb.toString();
    }

    private static boolean[] randomMask(int length, int trues){
        return VectorUtils.randomBooleanVector(length, trues, random);
    }
}
