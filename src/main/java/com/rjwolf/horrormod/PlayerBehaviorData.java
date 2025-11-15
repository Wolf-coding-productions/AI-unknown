package com.rjwolf.horrormod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.util.HashMap;
import java.util.Map;

public class PlayerBehaviorData {
    // Feature vector layout constants
    private static final int SCALAR_METRICS_COUNT = 5;
    private static final int ENTITY_REACTIONS_COUNT = 10;
    private static final int BIOME_PREFERENCES_COUNT = 10;
    private static final int BLOCK_AVOIDANCE_COUNT = 5;

    private static final int SCALAR_METRICS_START = 0;
    private static final int ENTITY_REACTIONS_START = SCALAR_METRICS_START + SCALAR_METRICS_COUNT;
    private static final int BIOME_PREFERENCES_START = ENTITY_REACTIONS_START + ENTITY_REACTIONS_COUNT;
    private static final int BLOCK_AVOIDANCE_START = BIOME_PREFERENCES_START + BIOME_PREFERENCES_COUNT;
    public static final int FEATURE_COUNT = BLOCK_AVOIDANCE_START + BLOCK_AVOIDANCE_COUNT;


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
    private Vec3 lastMovementDirection = Vec3.ZERO;

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
            
            // Update movement direction
            Vec3 movement = new Vec3(currentPos.getX() - lastPosition.getX(), 0, currentPos.getZ() - lastPosition.getZ());
            if (movement.lengthSqr() > 0.1) { // Only update if there's significant horizontal movement
                lastMovementDirection = movement.normalize();
            }
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
        
        // Record biome presence
        player.level.getBiome(currentPos).unwrapKey().ifPresent(key -> {
            recordBiomePresence(key.location().toString(), 1);
        });

        lastPosition = currentPos;
        lastUpdateTime = currentTime;
    }

    public boolean isFleeing(Player player, Vec3 threatPosition) {
        if (lastMovementDirection.equals(Vec3.ZERO)) {
            return false; // Not moving
        }

        Vec3 playerPos = player.position();
        Vec3 directionFromThreat = new Vec3(playerPos.x - threatPosition.x, 0, playerPos.z - threatPosition.z).normalize();

        // If the player's movement direction is similar to the direction away from the threat, they are fleeing
        // A dot product > 0.7 indicates the angle between the vectors is less than ~45 degrees
        return lastMovementDirection.dot(directionFromThreat) > 0.7;
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
        features[SCALAR_METRICS_START] = averageMovementSpeed * 1000.0; // convert to blocks/sec-ish
        features[SCALAR_METRICS_START + 1] = jumpFrequency;
        features[SCALAR_METRICS_START + 2] = sneakDuration;
        features[SCALAR_METRICS_START + 3] = combatEngagements;
        features[SCALAR_METRICS_START + 4] = fleeingInstances;

        // Map-based metrics: dump entity reaction counts
        int idx = ENTITY_REACTIONS_START;
        for (Map.Entry<String, Integer> e : entityReactions.entrySet()) {
            if (idx >= BIOME_PREFERENCES_START) break;
            features[idx++] = e.getValue();
        }
        // fill remaining entity slots with 0
        while (idx < BIOME_PREFERENCES_START) features[idx++] = 0.0;

        // Biome preferences
        idx = BIOME_PREFERENCES_START;
        for (Map.Entry<String, Integer> e : biomePreferences.entrySet()) {
            if (idx >= BLOCK_AVOIDANCE_START) break;
            features[idx++] = e.getValue();
        }
        while (idx < BLOCK_AVOIDANCE_START) features[idx++] = 0.0;

        // Block avoidance counts
        idx = BLOCK_AVOIDANCE_START;
        for (Map.Entry<Block, Integer> e : blockAvoidance.entrySet()) {
            if (idx >= FEATURE_COUNT) break;
            features[idx++] = e.getValue();
        }
        while (idx < FEATURE_COUNT) features[idx++] = 0.0;

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
