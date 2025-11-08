package com.rjwolf.horrormod;

import org.nd4j.linalg.api.ndarray.INDArray;
import java.util.HashMap;
import java.util.Map;

public class FearProfile {
    private final Map<FearType, Double> fearLevels;

    public FearProfile(INDArray networkOutput) {
        fearLevels = new HashMap<>();
        
        // Convert network output to fear levels
        double[] fearArray = networkOutput.toDoubleVector();
        for (int i = 0; i < fearArray.length; i++) {
            fearLevels.put(FearType.values()[i], fearArray[i]);
        }
    }

    public double getFearLevel(FearType fearType) {
        return fearLevels.getOrDefault(fearType, 0.0);
    }

    public FearType getDominantFear() {
        return fearLevels.entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(FearType.UNKNOWN);
    }

    public enum FearType {
        DARKNESS,
        HEIGHTS,
        ENCLOSED_SPACES,
        MONSTERS,
        WATER,
        FIRE,
        ISOLATION,
        UNDERGROUND,
        UNKNOWN,
        CUSTOM
    }

    public Map<FearType, Double> getAllFearLevels() {
        return new HashMap<>(fearLevels);
    }
}