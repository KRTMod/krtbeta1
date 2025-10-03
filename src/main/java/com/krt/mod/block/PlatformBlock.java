package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Material;

public class PlatformBlock extends Block {
    public PlatformBlock(Settings settings) {
        super(settings);
    }
    
    public PlatformBlock() {
        super(FabricBlockSettings.of(Material.STONE).strength(1.5f).requiresTool());
    }
}