package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TrackBlock extends Block {
    public TrackBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        // 处理列车在轨道上行驶的逻辑
        if (entity instanceof AbstractMinecartEntity) {
            // 这里可以添加列车在轨道上行驶的特殊逻辑
            // 例如：播放轨道行驶声音，调整列车速度等
        }
    }
}