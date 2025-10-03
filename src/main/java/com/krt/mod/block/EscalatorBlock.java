package com.krt.mod.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
// import com.krt.mod.blockentity.EscalatorBlockEntity; // 暂时注释，该类不存在

import java.util.Random;

/**
 * 自动扶梯方块类
 * 实现车站内的自动扶梯功能
 */
public class EscalatorBlock extends BlockWithEntity {
    // 道方向属性
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    // 运行状态属性
    public static final BooleanProperty POWERED = Properties.POWERED;
    // 运行方向属性（上或下）
    public static final EnumProperty<EscalatorDirection> DIRECTION = EnumProperty.of("escalator_direction", EscalatorDirection.class);
    
    // 扶梯的尺寸和碰撞箱
    private static final VoxelShape BASE_SHAPE = Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape RAILING_SHAPE = Block.createCuboidShape(0.0D, 2.0D, 0.0D, 16.0D, 14.0D, 16.0D);
    private static final VoxelShape COLLISION_SHAPE = VoxelShapes.union(BASE_SHAPE, RAILING_SHAPE);
    
    // 扶梯移动速度
    private static final double MOVEMENT_SPEED = 0.1D; // 每刻移动的距离
    
    public EscalatorBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
            .with(FACING, Direction.NORTH)
            .with(POWERED, false)
            .with(DIRECTION, EscalatorDirection.UP));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, DIRECTION);
    }
    
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPE;
    }
    
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPE;
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 检测玩家朝向，设置扶梯方向
        Direction facing = ctx.getPlayerFacing();
        
        // 检测上下方向，根据放置位置自动确定扶梯运行方向
        EscalatorDirection escalatorDirection = EscalatorDirection.UP;
        BlockPos pos = ctx.getBlockPos();
        World world = ctx.getWorld();
        
        // 检查上方是否有方块，如果有，尝试设置为向下
        if (world.getBlockState(pos.up()).getMaterial().isSolid()) {
            escalatorDirection = EscalatorDirection.DOWN;
        }
        
        return this.getDefaultState()
            .with(FACING, facing)
            .with(POWERED, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()))
            .with(DIRECTION, escalatorDirection);
    }
    
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        boolean isPowered = world.isReceivingRedstonePower(pos);
        if (isPowered != state.get(POWERED)) {
            world.setBlockState(pos, state.with(POWERED, isPowered));
        }
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // 允许玩家手动切换扶梯运行方向
        if (!world.isClient) {
            EscalatorDirection newDirection = state.get(DIRECTION) == EscalatorDirection.UP ? 
                EscalatorDirection.DOWN : EscalatorDirection.UP;
            world.setBlockState(pos, state.with(DIRECTION, newDirection));
            KRTMod.LOGGER.info("Escalator direction changed to {}", newDirection);
            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }
    
    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        // 只有在扶梯运行时才移动实体
        if (state.get(POWERED)) {
            moveEntityOnEscalator(state, entity, pos);
        }
    }
    
    // 移动扶梯上的实体
    private void moveEntityOnEscalator(BlockState state, Entity entity, BlockPos pos) {
        // 检查实体是否在扶梯的有效区域内
        if (entity.getY() - pos.getY() < 0.1 || entity.getY() - pos.getY() > 1.3) {
            return;
        }
        
        Direction facing = state.get(FACING);
        EscalatorDirection escalatorDirection = state.get(DIRECTION);
        
        // 计算移动向量
        Vec3d motion = entity.getVelocity();
        double x = motion.x;
        double y = motion.y;
        double z = motion.z;
        
        // 根据扶梯方向调整移动速度
        double horizontalSpeed = MOVEMENT_SPEED;
        double verticalSpeed = escalatorDirection == EscalatorDirection.UP ? 0.05 : -0.05;
        
        // 应用水平移动
        switch (facing) {
            case NORTH:
                z -= horizontalSpeed;
                break;
            case SOUTH:
                z += horizontalSpeed;
                break;
            case WEST:
                x -= horizontalSpeed;
                break;
            case EAST:
                x += horizontalSpeed;
                break;
        }
        
        // 应用垂直移动
        y += verticalSpeed;
        
        // 设置实体速度
        entity.setVelocity(x, y, z);
        
        // 如果是玩家，取消其跳跃能力
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            player.setJumping(false);
        }
        
        // 确保实体不会从扶梯上滑落
        double posX = Math.floor(entity.getX()) + 0.5;
        double posZ = Math.floor(entity.getZ()) + 0.5;
        entity.updatePosition(posX, entity.getY(), posZ);
    }
    
    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }
    
    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EscalatorBlockEntity(pos, state);
    }
    
    @Override
    public boolean hasRandomTicks(BlockState state) {
        return true; // 启用随机刻更新，用于动画效果
    }
    
    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        // 这里可以添加随机更新逻辑，比如扶梯的状态检测
        if (random.nextFloat() < 0.01f) { // 1%的概率
            checkEscalatorStatus(state, world, pos);
        }
    }
    
    // 检查扶梯状态
    private void checkEscalatorStatus(BlockState state, World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof EscalatorBlockEntity) {
            EscalatorBlockEntity escalatorEntity = (EscalatorBlockEntity) blockEntity;
            
            // 检查扶梯是否有故障
            if (escalatorEntity.hasFault()) {
                // 如果有故障，停止扶梯
                world.setBlockState(pos, state.with(POWERED, false));
                KRTMod.LOGGER.warn("Escalator at {} has fault, stopped", pos);
            }
        }
    }
    
    // 扶梯运行方向枚举
    public enum EscalatorDirection {
        UP,    // 向上运行
        DOWN   // 向下运行
    }
}