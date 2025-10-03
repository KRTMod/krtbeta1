package com.krt.mod.block;

import com.krt.mod.system.TrainDepartureTimerLogic;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 端门计时器方块类
 * 表示地铁端门内侧的发车时间计时器方块
 */
public class DepartureTimerBlock extends BlockWithEntity implements BlockEntityProvider {
    // 方块朝向属性
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    
    // 是否显示详细信息属性
    public static final BooleanProperty SHOW_DETAILS = BooleanProperty.of("show_details");
    
    // 方块是否通电属性
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    
    public DepartureTimerBlock(FabricBlockSettings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(SHOW_DETAILS, false).with(POWERED, false));
    }
    
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // 使用实体渲染类型
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        // 创建方块实体
        return new DepartureTimerBlockEntity(pos, state);
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // 在服务器端处理交互
        if (!world.isClient()) {
            // 切换显示详细信息模式
            boolean showDetails = !state.get(SHOW_DETAILS);
            world.setBlockState(pos, state.with(SHOW_DETAILS, showDetails));
            
            // 发送提示信息给玩家
            player.sendMessage(Text.literal("显示模式已切换为: " + (showDetails ? "详细模式" : "简洁模式")), true);
        }
        return ActionResult.SUCCESS;
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 根据玩家朝向设置方块方向
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing().getOpposite());
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        // 添加方块属性
        builder.add(FACING, SHOW_DETAILS, POWERED);
    }
    
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        // 检查方块是否通电
        boolean powered = world.isReceivingRedstonePower(pos);
        world.setBlockState(pos, state.with(POWERED, powered));
        
        // 如果方块实体存在，通知其更新
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof DepartureTimerBlockEntity) {
            ((DepartureTimerBlockEntity) blockEntity).onPowerStateChanged(powered);
        }
    }
}