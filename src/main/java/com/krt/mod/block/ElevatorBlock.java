package com.krt.mod.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
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
import com.krt.mod.blockentity.ElevatorBlockEntity;
// import com.krt.mod.screen.ElevatorScreenHandler; // 暂时注释，该类不存在

import java.util.Random;
import java.util.UUID;

/**
 * 电梯方块类
 * 实现车站内的电梯功能
 */
public class ElevatorBlock extends BlockWithEntity {
    // 方向属性
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    // 运行状态属性
    public static final BooleanProperty DOORS_OPEN = BooleanProperty.of("doors_open");
    // 当前楼层属性
    public static final IntProperty CURRENT_FLOOR = Properties.INTEGER_1_20; // 使用Minecraft 1.19.2兼容的楼层属性
    public static final IntProperty TARGET_FLOOR = Properties.INTEGER_1_20;
    
    // 电梯的尺寸和碰撞箱
    private static final VoxelShape BASE_SHAPE = Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    private static final VoxelShape SHAFT_SHAPE = Block.createCuboidShape(1.0D, 2.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    private static final VoxelShape COLLISION_SHAPE = VoxelShapes.union(BASE_SHAPE, SHAFT_SHAPE);
    
    // 电梯移动速度
    private static final double MOVEMENT_SPEED = 0.15D; // 每刻移动的距离
    // 门开关速度
    private static final int DOOR_OPERATION_TIME = 20; // 门开关所需的刻数
    
    public ElevatorBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
            .with(FACING, Direction.NORTH)
            .with(DOORS_OPEN, true)
            .with(CURRENT_FLOOR, 1)
            .with(TARGET_FLOOR, 1));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOORS_OPEN, CURRENT_FLOOR, TARGET_FLOOR);
    }
    
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION_SHAPE;
    }
    
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // 门打开时，允许实体通过
        if (state.get(DOORS_OPEN)) {
            return BASE_SHAPE; // 只有底部碰撞箱
        }
        return COLLISION_SHAPE; // 门关闭时，完整碰撞箱
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 检测玩家朝向，设置电梯方向
        Direction facing = ctx.getPlayerFacing();
        
        // 检测当前楼层（根据Y坐标）
        int floor = calculateFloorFromY(ctx.getBlockPos().getY());
        
        return this.getDefaultState()
            .with(FACING, facing)
            .with(DOORS_OPEN, true)
            .with(CURRENT_FLOOR, floor)
            .with(TARGET_FLOOR, floor);
    }
    
    // 从Y坐标计算楼层
    private int calculateFloorFromY(int y) {
        // 假设每层高度为4方块
        int baseFloorHeight = 64; // 基岩层上方的起始楼层高度
        return Math.max(1, (y - baseFloorHeight) / 4 + 1);
    }
    
    // 从楼层计算Y坐标
    private int calculateYFromFloor(int floor) {
        int baseFloorHeight = 64;
        return baseFloorHeight + (floor - 1) * 4;
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 打开电梯UI界面
            NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
            if (screenHandlerFactory != null) {
                player.openHandledScreen(screenHandlerFactory);
            }
        }
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        // 只有电梯在运行且门关闭时才移动实体
        if (!state.get(DOORS_OPEN)) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ElevatorBlockEntity) {
                ElevatorBlockEntity elevatorEntity = (ElevatorBlockEntity) blockEntity;
                moveEntityWithElevator(elevatorEntity, entity, pos);
            }
        }
    }
    
    // 移动电梯内的实体
    private void moveEntityWithElevator(ElevatorBlockEntity elevatorEntity, Entity entity, BlockPos pos) {
        // 检查实体是否在电梯的有效区域内
        if (entity.getY() - pos.getY() < 0.1 || entity.getY() - pos.getY() > 1.8) {
            return;
        }
        
        // 获取电梯移动方向
        double elevatorMotion = elevatorEntity.getMotion();
        
        // 应用电梯移动到实体
        Vec3d entityMotion = entity.getVelocity();
        entity.setVelocity(entityMotion.x, elevatorMotion, entityMotion.z);
        
        // 确保实体随电梯一起移动
        entity.fallDistance = 0.0F; // 防止摔落伤害
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
        return new ElevatorBlockEntity(pos, state);
    }
    
    @Override
    public boolean hasRandomTicks(BlockState state) {
        return true; // 启用随机刻更新，用于电梯状态检测
    }
    
    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        // 这里可以添加随机更新逻辑，比如电梯的故障检测
        if (random.nextFloat() < 0.001f) { // 0.1%的概率
            checkElevatorStatus(state, world, pos);
        }
    }
    
    // 检查电梯状态
    private void checkElevatorStatus(BlockState state, World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ElevatorBlockEntity) {
            ElevatorBlockEntity elevatorEntity = (ElevatorBlockEntity) blockEntity;
            
            // 检查电梯是否有故障
            if (elevatorEntity.hasFault()) {
                // 如果有故障，停止电梯并打开门
                world.setBlockState(pos, state.with(DOORS_OPEN, true));
                elevatorEntity.setMotion(0.0);
                KRTMod.LOGGER.warn("Elevator at {} has fault, stopped with doors open", pos);
            }
        }
    }
    
    // 请求电梯到指定楼层
    public static boolean requestElevator(World world, BlockPos elevatorPos, int targetFloor, UUID requesterId) {
        BlockState state = world.getBlockState(elevatorPos);
        if (!(state.getBlock() instanceof ElevatorBlock)) {
            return false;
        }
        
        BlockEntity blockEntity = world.getBlockEntity(elevatorPos);
        if (!(blockEntity instanceof ElevatorBlockEntity)) {
            return false;
        }
        
        ElevatorBlockEntity elevatorEntity = (ElevatorBlockEntity) blockEntity;
        
        // 添加请求到队列
        elevatorEntity.addFloorRequest(targetFloor, requesterId);
        
        KRTMod.LOGGER.info("Elevator at {} requested to floor {} by {}", elevatorPos, targetFloor, requesterId);
        
        // 更新目标楼层状态
        world.setBlockState(elevatorPos, state.with(TARGET_FLOOR, targetFloor));
        
        return true;
    }
    
    // 更新电梯状态
    public static void updateElevatorState(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ElevatorBlock)) {
            return;
        }
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof ElevatorBlockEntity)) {
            return;
        }
        
        ElevatorBlockEntity elevatorEntity = (ElevatorBlockEntity) blockEntity;
        
        // 获取当前电梯状态
        int currentFloor = state.get(CURRENT_FLOOR);
        int targetFloor = state.get(TARGET_FLOOR);
        boolean doorsOpen = state.get(DOORS_OPEN);
        
        // 电梯控制逻辑
        if (doorsOpen) {
            // 门打开状态，检查是否需要关闭门
            if (currentFloor != targetFloor && elevatorEntity.getDoorTimer() <= 0) {
                // 关闭门
                elevatorEntity.setDoorTimer(DOOR_OPERATION_TIME);
                world.setBlockState(pos, state.with(DOORS_OPEN, false));
                KRTMod.LOGGER.info("Elevator at {} closing doors", pos);
            } else if (currentFloor == targetFloor) {
                // 在目标楼层，检查是否有新的请求
                Integer nextTarget = elevatorEntity.getNextFloorRequest();
                if (nextTarget != null) {
                    world.setBlockState(pos, state.with(TARGET_FLOOR, nextTarget));
                } else if (elevatorEntity.getDoorTimer() > 0) {
                    // 减少门开启计时器
                    elevatorEntity.setDoorTimer(elevatorEntity.getDoorTimer() - 1);
                }
            } else if (elevatorEntity.getDoorTimer() > 0) {
                // 减少门开启计时器
                elevatorEntity.setDoorTimer(elevatorEntity.getDoorTimer() - 1);
            }
        } else {
            // 门关闭状态
            if (elevatorEntity.getDoorTimer() > 0) {
                // 门正在关闭
                elevatorEntity.setDoorTimer(elevatorEntity.getDoorTimer() - 1);
                if (elevatorEntity.getDoorTimer() == 0) {
                    KRTMod.LOGGER.info("Elevator at {} doors closed", pos);
                }
            } else if (currentFloor != targetFloor) {
                // 移动电梯到目标楼层
                int targetY = calculateYFromFloor(targetFloor);
                int currentY = pos.getY();
                
                if (Math.abs(currentY - targetY) < 0.5) {
                    // 到达目标楼层
                    world.setBlockState(pos, state.with(CURRENT_FLOOR, targetFloor));
                    elevatorEntity.setMotion(0.0);
                    elevatorEntity.setDoorTimer(DOOR_OPERATION_TIME * 2); // 门保持开启时间
                    world.setBlockState(pos, state.with(CURRENT_FLOOR, targetFloor).with(DOORS_OPEN, true));
                    KRTMod.LOGGER.info("Elevator at {} arrived at floor {}", pos, targetFloor);
                } else {
                    // 继续移动
                    double direction = currentY < targetY ? 1.0 : -1.0;
                    elevatorEntity.setMotion(direction * MOVEMENT_SPEED);
                    
                    // 更新电梯位置
                    BlockPos newPos = new BlockPos(pos.getX(), pos.getY() + (int)Math.signum(direction), pos.getZ());
                    
                    // 检查新位置是否有效
                    if (world.isAir(newPos) || world.getBlockState(newPos).getMaterial().isReplaceable()) {
                        // 移动电梯方块
                        world.setBlockState(newPos, state);
                        world.removeBlock(pos, false);
                        
                        // 移动电梯实体
                        BlockEntity newEntity = world.getBlockEntity(newPos);
                        if (newEntity instanceof ElevatorBlockEntity && blockEntity instanceof ElevatorBlockEntity) {
                            ((ElevatorBlockEntity) newEntity).copyDataFrom((ElevatorBlockEntity) blockEntity);
                        }
                    }
                }
            } else {
                // 门关闭但已经在目标楼层，打开门
                elevatorEntity.setDoorTimer(DOOR_OPERATION_TIME);
                world.setBlockState(pos, state.with(DOORS_OPEN, true));
                KRTMod.LOGGER.info("Elevator at {} opening doors", pos);
            }
        }
    }
}