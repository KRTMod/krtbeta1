package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class PlatformLightBlock extends Block implements BlockEntityProvider {
    public static final PlatformLightBlock INSTANCE = new PlatformLightBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(1.0f).requiresTool());
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty LIT = BooleanProperty.of("lit");
    public static final String ID = "platform_light";
    
    public PlatformLightBlock(Settings settings) {
        super(settings.luminance(state -> state.get(LIT) ? 15 : 0));
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(LIT, false));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new com.krt.mod.blockentity.PlatformLightBlockEntity(pos, state);
    }
    
    // 设置照明状态
    public static void setLightState(World world, BlockPos pos, boolean lit) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PlatformLightBlock) {
            world.setBlockState(pos, state.with(LIT, lit));
        }
    }
    
    // 切换照明状态
    public static void toggleLightState(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PlatformLightBlock) {
            boolean currentState = state.get(LIT);
            world.setBlockState(pos, state.with(LIT, !currentState));
        }
    }
}