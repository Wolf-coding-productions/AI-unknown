package com.rjwolf.horrormod;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = HorrorMod.MOD_ID)
public class TestEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, PlayerBehaviorData> playerDataMap = new ConcurrentHashMap<>();
    private static final Map<UUID, AIFearLearner> fearLearnerMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> tickCounterMap = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.player;
            UUID playerUUID = player.getUUID();

            PlayerBehaviorData playerData = playerDataMap.computeIfAbsent(playerUUID, k -> new PlayerBehaviorData());
            AIFearLearner fearLearner = fearLearnerMap.computeIfAbsent(playerUUID, k -> {
                AIFearLearner learner = new AIFearLearner();
                learner.initialize();
                return learner;
            });
            int tickCounter = tickCounterMap.getOrDefault(playerUUID, 0);

            // Update behavior data every 20 ticks (1 second)
            if (tickCounter % 20 == 0) {
                playerData.updatePlayerBehavior(player);

                // --- Environmental Fear Learning ---
                // 1. Fear of Darkness
                if (player.level.getLightEmission(player.blockPosition()) < 4) {
                    LOGGER.debug("Player {} is in darkness. Learning fear of DARKNESS.", player.getName().getString());
                    learnFear(player, FearProfile.FearType.DARKNESS, 0.1); // Weak, continuous signal
                }

                // 2. Fear of Enclosed Spaces
                if (isPlayerEnclosed(player)) {
                    LOGGER.debug("Player {} is in an enclosed space. Learning fear of ENCLOSED_SPACES.", player.getName().getString());
                    learnFear(player, FearProfile.FearType.ENCLOSED_SPACES, 0.2); // Slightly stronger signal
                }
                
                // 3. Fear of being Underground
                if (player.getY() < 50) {
                    learnFear(player, FearProfile.FearType.UNDERGROUND, 0.05); // Very weak, ambient signal
                }


                // Periodically predict fear profile and log it
                if (tickCounter % 200 == 0) { // Every 10 seconds
                    if (fearLearner != null) {
                        FearProfile fearProfile = fearLearner.predictPlayerFears(playerData);
                        LOGGER.info("Player {}'s dominant fear: {}", player.getName().getString(), fearProfile.getDominantFear());
                        LOGGER.debug("Full fear profile: {}", fearProfile.getAllFearLevels());
                    }
                }
            }

            tickCounterMap.put(playerUUID, tickCounter + 1);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player && event.getSource().getEntity() != null) {
            Player player = (Player) event.getEntity();
            UUID playerUUID = player.getUUID();
            PlayerBehaviorData playerData = playerDataMap.get(playerUUID);

            if (playerData != null) {
                String attackerName = event.getSource().getEntity().getName().getString();
                LOGGER.info("Player {} was hurt by {}. Learning fear of MONSTERS.", player.getName().getString(), attackerName);
                
                // Record the combat event
                playerData.recordCombatEngagement();
                
                // Check if the player is fleeing
                boolean isFleeing = playerData.isFleeing(player, event.getSource().getEntity().position());
                playerData.recordEntityReaction(attackerName, 0, isFleeing);

                learnFear(player, FearProfile.FearType.MONSTERS, 0.8); // Strong signal for monster fear
            }
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            float distance = event.getDistance();
            if (distance > 3.0f) { // Fall damage starts at 3 blocks
                LOGGER.info("Player {} fell {} blocks. Learning fear of HEIGHTS.", player.getName().getString(), distance);
                double fearSignal = Math.min(1.0, (distance - 3.0) / 20.0); // Normalize fear signal based on fall distance
                learnFear(player, FearProfile.FearType.HEIGHTS, fearSignal);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            LOGGER.info("Player {} died. Applying strong fear signals based on cause.", player.getName().getString());
            // Example: if killed by a monster, max out monster fear
            if (event.getSource().getEntity() != null) {
                learnFear(player, FearProfile.FearType.MONSTERS, 1.0);
            }
            // TODO: Add more death-related fear signals (drowning, fire, etc.)
        }
    }

    private static void learnFear(Player player, FearProfile.FearType fearType, double strength) {
        UUID playerUUID = player.getUUID();
        PlayerBehaviorData playerData = playerDataMap.get(playerUUID);
        AIFearLearner fearLearner = fearLearnerMap.get(playerUUID);

        if (playerData != null && fearLearner != null) {
            double[] fearLabel = new double[FearProfile.FearType.values().length];
            fearLabel[fearType.ordinal()] = strength;
            playerData.setFearLabel(fearLabel);
            fearLearner.learnFromPlayerBehavior(playerData);
        }
    }

    private static boolean isPlayerEnclosed(Player player) {
        Level world = player.level;
        BlockPos playerPos = player.blockPosition();
        int solidBlocks = 0;
        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-1, 0, -1), playerPos.offset(1, 2, 1))) {
            if (world.getBlockState(pos).getMaterial().isSolid()) {
                solidBlocks++;
            }
        }
        // If more than ~60% of the surrounding 3x3x3 area is solid, consider it enclosed
        return solidBlocks > 16;
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerUUID = player.getUUID();
            LOGGER.info("Player {} logged in. Initializing horror AI.", player.getName().getString());
            playerDataMap.put(playerUUID, new PlayerBehaviorData());
            AIFearLearner learner = new AIFearLearner();
            learner.initialize();
            fearLearnerMap.put(playerUUID, learner);
            tickCounterMap.put(playerUUID, 0);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerUUID = player.getUUID();
            LOGGER.info("Player {} logged out. Removing horror AI data.", player.getName().getString());
            playerDataMap.remove(playerUUID);
            fearLearnerMap.remove(playerUUID);
            tickCounterMap.remove(playerUUID);
        }
    }
}