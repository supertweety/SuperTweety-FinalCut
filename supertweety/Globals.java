/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety;

import supertweety.logic.utils.ModelCounterFactory;
import supertweety.logic.utils.NaiveModelCounterFactory;
import supertweety.logic.utils.RelsatModelCounterFactory;

import java.io.File;

/**
 * Created by kuzelkao_cardiff on 12/04/16.
 */
public class Globals {


    public static ModelCounterFactory modelCounterFactory;


    static {
        String relsatPath = "../../../Experiments/ECAI16/relsat_2.02/";
        if (new File(relsatPath).exists()) {
            modelCounterFactory = new RelsatModelCounterFactory(relsatPath);
        } else {
            modelCounterFactory = new NaiveModelCounterFactory();
            System.err.println("Warning: Using NaiveModelCounterFactory because relsat couldn't be found.");
        }

    }


    public static void setRelsatPath(String relsatPath){
        Globals.modelCounterFactory = new RelsatModelCounterFactory(relsatPath);
    }
}
