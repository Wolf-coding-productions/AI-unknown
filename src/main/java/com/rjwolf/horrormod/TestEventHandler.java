package com.rjwolf.horrormod;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = HorrorMod.MOD_ID)
public class TestEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static PlayerBehaviorData playerData = new PlayerBehaviorData();
    private static AIFearLearner fearLearner = new AIFearLearner();
    private static int tickCounter = 0;

    static {
        fearLearner.initialize();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level.isClientSide) {
            tickCounter++;
            
            // Update behavior data every 20 ticks (1 second)
            if (tickCounter % 20 == 0) {
                playerData.updatePlayerBehavior(event.player);
                
                // Test the AI system every 100 ticks (5 seconds)
                if (tickCounter % 100 == 0) {
                    LOGGER.info("Testing AI Fear Learning System...");
                    
                    // Create some test fear data
                    double[] testFearLabel = new double[10];
                    testFearLabel[FearProfile.FearType.DARKNESS.ordinal()] = 0.8;
                    testFearLabel[FearProfile.FearType.MONSTERS.ordinal()] = 0.6;
                    playerData.setFearLabel(testFearLabel);
                    
                    // Test learning
                    fearLearner.learnFromPlayerBehavior(playerData);
                    
                    // Test prediction
                    FearProfile fearProfile = fearLearner.predictPlayerFears(playerData);
                    LOGGER.info("Dominant Fear Predicted: " + fearProfile.getDominantFear());
                    LOGGER.info("Fear Levels: " + fearProfile.getAllFearLevels());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            LOGGER.info("Player logged in - Initializing Horror Mod AI tracking...");
        }
    }
}