package com.rjwolf.horrormod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.util.HashMap;
import java.util.Map;

public class PlayerBehaviorData {
    // Feature vector length used by the AI. Keep in sync with AIFearLearner.
    public static final int FEATURE_COUNT = 50;

    private final Map<Block, Integer> blockAvoidance = new HashMap<>();
    private final Map<String, Integer> entityReactions = new HashMap<>();
    private final Map<String, Integer> biomePreferences = new HashMap<>();

    private double averageMovementSpeed;
    private int jumpFrequency;
    private int sneakDuration; // in ticks
    private int combatEngagements;
    private int fleeingInstances;

    private BlockPos lastPosition;
    private long lastUpdateTime;
    private boolean lastOnGround = true;
    private double[] fearLabel;

    public void updatePlayerBehavior(Player player) {
        // Update position and movement metrics
        BlockPos currentPos = player.blockPosition();
        long currentTime = System.currentTimeMillis();

        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            lastPosition = currentPos;
            lastOnGround = player.isOnGround();
            return;
        }

        long timeDiff = currentTime - lastUpdateTime;
        if (timeDiff <= 0) timeDiff = 1; // avoid divide by zero

        if (lastPosition != null) {
            // Calculate movement speed (blocks per millisecond -> normalize later)
            double distance = getDistance(lastPosition, currentPos);
            double speed = distance / (double) timeDiff;
            updateMovementSpeed(speed);
        }

        // Jump detection: left ground and has upward motion
        boolean onGround = player.isOnGround();
        double yMotion = player.getDeltaMovement().y();
        if (lastOnGround && !onGround && yMotion > 0.1) {
            jumpFrequency++;
        }
        lastOnGround = onGround;

        // Sneak detection: accumulate ticks spent sneaking
        if (player.isCrouching()) {
            sneakDuration++;
        }

        lastPosition = currentPos;
        lastUpdateTime = currentTime;
    }

    public void recordEntityReaction(String entityType, double reactionTime, boolean fled) {
        entityReactions.merge(entityType, fled ? 1 : 0, Integer::sum);
        if (fled) {
            fleeingInstances++;
        }
    }

    public void recordBlockAvoidance(Block block) {
        blockAvoidance.merge(block, 1, Integer::sum);
    }

    public void recordBiomePresence(String biome, int duration) {
        biomePreferences.merge(biome, duration, Integer::sum);
    }

    public void recordCombatEngagement() {
        combatEngagements++;
    }

    private double getDistance(BlockPos pos1, BlockPos pos2) {
        int dx = pos2.getX() - pos1.getX();
        int dy = pos2.getY() - pos1.getY();
        int dz = pos2.getZ() - pos1.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void updateMovementSpeed(double speed) {
        averageMovementSpeed = (averageMovementSpeed * 0.9) + (speed * 0.1); // Exponential moving average
    }

    public double[] getFearLabel() {
        return fearLabel;
    }

    public INDArray getFearLabelINDArray(int categories) {
        double[] label = (fearLabel != null) ? fearLabel : new double[categories];
        if (label.length != categories) {
            double[] resized = new double[categories];
            System.arraycopy(label, 0, resized, 0, Math.min(label.length, categories));
            label = resized;
        }
        return Nd4j.create(label).reshape(1, categories);
    }

    public void setFearLabel(double[] label) {
        this.fearLabel = label;
    }

    // Convert all tracked metrics into a fixed-length feature array for the AI
    public double[] toFeatureArray() {
        double[] features = new double[FEATURE_COUNT];

        // Basic scalar metrics (with simple normalization)
        features[0] = averageMovementSpeed * 1000.0; // convert to blocks/sec-ish
        features[1] = jumpFrequency;
        features[2] = sneakDuration;
        features[3] = combatEngagements;
        features[4] = fleeingInstances;

        // Map-based metrics: dump up to 10 entity reaction counts
        int idx = 5;
        for (Map.Entry<String, Integer> e : entityReactions.entrySet()) {
            if (idx >= 15) break;
            features[idx++] = e.getValue();
        }
        // fill remaining entity slots with 0
        while (idx < 15) features[idx++] = 0.0;

        // Biome preferences (up to 10)
        idx = 15;
        for (Map.Entry<String, Integer> e : biomePreferences.entrySet()) {
            if (idx >= 25) break;
            features[idx++] = e.getValue();
        }
        while (idx < 25) features[idx++] = 0.0;

        // Block avoidance counts (up to 5)
        idx = 25;
        for (Map.Entry<Block, Integer> e : blockAvoidance.entrySet()) {
            if (idx >= 30) break;
            features[idx++] = e.getValue();
        }
        while (idx < 30) features[idx++] = 0.0;

        // Remaining features reserved for future signals (leave as 0)
        for (int i = 30; i < FEATURE_COUNT; i++) features[i] = 0.0;

        return features;
    }

    public INDArray toINDArray() {
        return Nd4j.create(toFeatureArray()).reshape(1, FEATURE_COUNT);
    }

    // Getters for all tracked metrics
    public Map<Block, Integer> getBlockAvoidance() {
        return blockAvoidance;
    }

    public Map<String, Integer> getEntityReactions() {
        return entityReactions;
    }

    public Map<String, Integer> getBiomePreferences() {
        return biomePreferences;
    }

    public double getAverageMovementSpeed() {
        return averageMovementSpeed;
    }

    public int getFleeingInstances() {
        return fleeingInstances;
    }

    public int getJumpFrequency() {
        return jumpFrequency;
    }

    public int getSneakDuration() {
        return sneakDuration;
    }

    public int getCombatEngagements() {
        return combatEngagements;
    }
}