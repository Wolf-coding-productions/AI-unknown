package com.rjwolf.horrormod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HorrorMod.MOD_ID)
public class HorrorMod {
    public static final String MOD_ID = "horrormod";
    private static final Logger LOGGER = LogManager.getLogger();
    
    private final AIFearLearner fearLearner;

    public HorrorMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
        
        fearLearner = new AIFearLearner();
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing Horror Mod AI systems...");
        fearLearner.initialize();
    }
}