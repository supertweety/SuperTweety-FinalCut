/*
 * Copyright (c) 2015 Ondrej Kuzelka
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package supertweety.probabilistic;

import supertweety.BinaryDataset;
import supertweety.logic.ModelCounter;

/**
 * Created by kuzelkao_cardiff on 18/08/16.
 */
public class DensityEstimationTreeLearner implements DiscreteProbabilityDistributionEstimator {

    private int minInLeaf = 1;

    private int minInLeafMode = DensityEstimationTree.MIN_IN_LEAF_COUNT;

    public DensityEstimationTreeLearner(){}

    public DensityEstimationTreeLearner(int minInLeaf){
        this.minInLeaf = minInLeaf;
    }

    public DensityEstimationTree estimate(BinaryDataset dataset, ModelCounter modelCounter){
        return DensityEstimationTree.learn(dataset, minInLeaf);
    }

    public void setMinInLeaf(int minInLeaf){
        this.minInLeaf = minInLeaf;
    }

    public void setMinInLeafMode(int minInLeafMode){
        this.minInLeafMode = minInLeafMode;
    }

}
