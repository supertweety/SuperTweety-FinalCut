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
import ida.utils.Sugar;
import ida.utils.collections.MultiMap;
import supertweety.BinaryDataset;

import java.io.*;

/**
 * Created by kuzelkao_cardiff on 16/03/16.
 */
public class AnimalsDatasetConvertor {

    public static void convert(Reader reader, Writer writer) throws IOException {
        MultiMap<String,String> features = new MultiMap<String,String>();
        for (String line : Sugar.readLines(reader)){
            line = line.trim();
            if (line.length() > 0){
                Literal l = Sugar.chooseOne(Clause.parse(line).literals());
                features.put(l.get(0).name(), l.get(1).name());
            }
        }
        BinaryDataset dataset = BinaryDataset.fromFeatures(features);
        dataset.writeCSV(writer);
    }

    public static void main(String[] args) throws Exception {
        Reader reader = new FileReader("/Users/kuzelkao_cardiff/Dropbox/Experiments/ECAI16/animals.alchemy");
        Writer writer = new FileWriter("/Users/kuzelkao_cardiff/Dropbox/Experiments/ECAI16/animals.csv");
        convert(reader, writer);
        reader.close();
        writer.close();
    }

}
