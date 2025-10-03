package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Material;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.system.LogSystem;


public class StationCountdownDisplayBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public StationCountdownDisplayBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }
    
    public StationCountdownDisplayBlock() {
        super(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool().nonOpaque().luminance(state -> 15));
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerLookDirection().getOpposite());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StationCountdownDisplayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return (clientWorld, pos, clientState, blockEntity) -> {
                if (blockEntity instanceof StationCountdownDisplayBlockEntity) {
                    ((StationCountdownDisplayBlockEntity) blockEntity).tick();
                }
            };
        }
        return null;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 获取方块实体
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof StationCountdownDisplayBlockEntity) {
                StationCountdownDisplayBlockEntity displayEntity = (StationCountdownDisplayBlockEntity) blockEntity;
                
                // 显示当前倒计时信息给玩家
                Text displayText = displayEntity.getDisplayText();
                player.sendMessage(displayText, false);
                
                LogSystem.systemLog("玩家 " + player.getEntityName() + " 查看了车站倒计时信息: " + pos);
            } else {
                // 如果没有方块实体，显示基本信息
                player.sendMessage(Text.literal("车站倒计时显示器"), false);
            }
        }
        return ActionResult.SUCCESS;
    }
}