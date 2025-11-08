package com.rjwolf.horrormod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import java.util.HashMap;
import java.util.Map;

public class PlayerBehaviorData {
    private final Map<Block, Integer> blockAvoidance = new HashMap<>();
    private final Map<String, Integer> entityReactions = new HashMap<>();
    private final Map<String, Integer> biomePreferences = new HashMap<>();
    
    private double averageMovementSpeed;
    private int jumpFrequency;
    private int sneakDuration;
    private int combatEngagements;
    private int fleeingInstances;
    
    private BlockPos lastPosition;
    private long lastUpdateTime;
    private double[] fearLabel;

    public void updatePlayerBehavior(Player player) {
        // Update position and movement metrics
        BlockPos currentPos = player.blockPosition();
        long currentTime = System.currentTimeMillis();
        
        if (lastPosition != null) {
            // Calculate movement speed
            double distance = getDistance(lastPosition, currentPos);
            long timeDiff = currentTime - lastUpdateTime;
            double speed = distance / timeDiff;
            updateMovementSpeed(speed);
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

    private double getDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(pos1.distSqr(pos2));
    }

    private void updateMovementSpeed(double speed) {
        averageMovementSpeed = (averageMovementSpeed * 0.9) + (speed * 0.1); // Exponential moving average
    }

    public double[] getFearLabel() {
        return fearLabel;
    }

    public void setFearLabel(double[] label) {
        this.fearLabel = label;
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