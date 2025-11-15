package com.rjwolf.horrormod;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public class AIFearLearner {
    private MultiLayerNetwork network;
    private static final int INPUT_FEATURES = PlayerBehaviorData.FEATURE_COUNT; // Number of behavioral features we track
    private static final int FEAR_CATEGORIES = 10; // Different types of fears we can identify

    public void initialize() {
        // Create a neural network configuration
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(123)
            .updater(new Adam(0.001))
            .list()
            .layer(0, new DenseLayer.Builder()
                .nIn(INPUT_FEATURES)
                .nOut(100)
                .activation(Activation.RELU)
                .build())
            .layer(1, new DenseLayer.Builder()
                .nOut(50)
                .activation(Activation.RELU)
                .build())
            .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                .nOut(FEAR_CATEGORIES)
                .activation(Activation.SOFTMAX)
                .build())
            .build();

        network = new MultiLayerNetwork(conf);
        network.init();
    }

    public void learnFromPlayerBehavior(PlayerBehaviorData data) {
        INDArray features = convertBehaviorToFeatures(data);
        INDArray labels;
        if (data.getFearLabel() != null) {
            double[] raw = data.getFearLabel();
            // Ensure correct length
            if (raw.length != FEAR_CATEGORIES) {
                double[] resized = new double[FEAR_CATEGORIES];
                System.arraycopy(raw, 0, resized, 0, Math.min(raw.length, FEAR_CATEGORIES));
                raw = resized;
            }
            labels = Nd4j.create(raw).reshape(1, FEAR_CATEGORIES);
        } else {
            labels = Nd4j.zeros(1, FEAR_CATEGORIES);
        }

        org.nd4j.linalg.dataset.DataSet ds = new org.nd4j.linalg.dataset.DataSet(features, labels);
        network.fit(ds);
    }

    public FearProfile predictPlayerFears(PlayerBehaviorData data) {
        INDArray features = convertBehaviorToFeatures(data);
        INDArray output = network.output(features);
        return new FearProfile(output.getRow(0));
    }

    private INDArray convertBehaviorToFeatures(PlayerBehaviorData data) {
        // Delegate conversion to PlayerBehaviorData so the feature mapping is centralized
        return data.toINDArray();
    }
}