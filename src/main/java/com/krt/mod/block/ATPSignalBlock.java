package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.CBTCSystem;

public class ATPSignalBlock extends Block implements BlockEntityProvider {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final BooleanProperty IS_ACTIVE = BooleanProperty.of("is_active");
    public static final BooleanProperty HAS_DCS = BooleanProperty.of("has_dcs"); // 是否接入调度集中系统
    public static final BooleanProperty HAS_TRACK_SENSOR = BooleanProperty.of("has_track_sensor"); // 是否连接轨道传感器
    public static final EnumProperty<SignalBlock.SignalState> SIGNAL_STATE = EnumProperty.of("signal_state", SignalBlock.SignalState.class);
    public static final EnumProperty<SignalBlock.SignalType> SIGNAL_TYPE = EnumProperty.of("signal_type", SignalBlock.SignalType.class);
    public static final EnumProperty<ATPSystemType> ATP_SYSTEM_TYPE = EnumProperty.of("atp_system_type", ATPSystemType.class);

    public ATPSignalBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false)
                .with(IS_ACTIVE, true)
                .with(HAS_DCS, false)
                .with(HAS_TRACK_SENSOR, false)
                .with(SIGNAL_STATE, SignalBlock.SignalState.RED)
                .with(SIGNAL_TYPE, SignalBlock.SignalType.MAIN)
                .with(ATP_SYSTEM_TYPE, ATPSystemType.CBTC));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, IS_ACTIVE, HAS_DCS, HAS_TRACK_SENSOR, 
                   SIGNAL_STATE, SIGNAL_TYPE, ATP_SYSTEM_TYPE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing());
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ATPSignalBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 检查是否为调试模式或管理员
            boolean isDebugMode = false; // 可以根据游戏设置判断
            
            if (isDebugMode) {
                // 调试模式下允许手动切换信号状态
                SignalBlock.SignalState currentState = state.get(SIGNAL_STATE);
                SignalBlock.SignalState nextState;
                
                // 四显示循环切换
                switch (currentState) {
                    case GREEN:
                        nextState = SignalBlock.SignalState.YELLOW_GREEN;
                        break;
                    case YELLOW_GREEN:
                        nextState = SignalBlock.SignalState.YELLOW;
                        break;
                    case YELLOW:
                        nextState = SignalBlock.SignalState.RED;
                        break;
                    default:
                        nextState = SignalBlock.SignalState.GREEN;
                        break;
                }
                
                world.setBlockState(pos, state.with(SIGNAL_STATE, nextState));
                player.sendMessage(Text.literal("ATP信号机状态切换为: " + nextState.getDisplayName()), false);
                LogSystem.systemLog("玩家 " + player.getEntityName() + " 手动切换ATP信号机状态: " + pos + " -> " + nextState.name());
            } else {
                // 普通模式下显示ATP信号机信息
                String signalInfo = getATPSignalInfo(state, pos, world);
                player.sendMessage(Text.literal(signalInfo), false);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) {
            return;
        }
        
        boolean wasPowered = state.get(POWERED);
        boolean isPowered = world.isReceivingRedstonePower(pos);
        
        if (wasPowered != isPowered) {
            world.setBlockState(pos, state.with(POWERED, isPowered));
            
            // 根据供电状态更新信号机激活状态
            if (!isPowered && state.get(IS_ACTIVE)) {
                // 断电时停用信号机并设置为红灯
                setSignalActive(world, pos, false);
                LogSystem.warningLog("ATP信号机 " + pos + " 断电，已自动停用");
            } else if (isPowered && !state.get(IS_ACTIVE)) {
                // 来电时自动启用信号机
                setSignalActive(world, pos, true);
                LogSystem.systemLog("ATP信号机 " + pos + " 供电恢复，已自动启用");
            }
            
            // 更新方块实体的状态
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ATPSignalBlockEntity) {
                ATPSignalBlockEntity atpEntity = (ATPSignalBlockEntity) blockEntity;
                if (isPowered) {
                    LogSystem.systemLog("ATP信号方块已激活: " + pos);
                } else {
                    LogSystem.systemLog("ATP信号方块已关闭: " + pos);
                }
            }
        }
    }

    // 获取ATP信号数据
    public static ATPSignalBlockEntity.ATPData getATPData(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ATPSignalBlockEntity) {
            return ((ATPSignalBlockEntity) blockEntity).getATPData();
        }
        // 如果没有方块实体，返回默认数据
        return new ATPSignalBlockEntity.ATPData();
    }
    
    // 获取ATP信号机信息
    private String getATPSignalInfo(BlockState state, BlockPos pos, World world) {
        SignalBlock.SignalState signalState = state.get(SIGNAL_STATE);
        SignalBlock.SignalType signalType = state.get(SIGNAL_TYPE);
        ATPSystemType atpType = state.get(ATP_SYSTEM_TYPE);
        boolean isActive = state.get(IS_ACTIVE);
        boolean hasDCS = state.get(HAS_DCS);
        boolean hasSensor = state.get(HAS_TRACK_SENSOR);
        boolean powered = state.get(POWERED);
        
        StringBuilder info = new StringBuilder();
        info.append("ATP信号机信息:\n");
        info.append(String.format("位置: %s\n", pos.toString()));
        info.append(String.format("类型: %s\n", signalType.getDisplayName()));
        info.append(String.format("状态: %s\n", signalState.getDisplayName()));
        info.append(String.format("ATP系统: %s\n", atpType.getDisplayName()));
        info.append(String.format("供电状态: %s\n", powered ? "正常" : "断电"));
        info.append(String.format("激活状态: %s\n", isActive ? "是" : "否"));
        info.append(String.format("DCS接入: %s\n", hasDCS ? "是" : "否"));
        info.append(String.format("轨道传感器: %s\n", hasSensor ? "已连接" : "未连接"));
        
        // 添加ATP数据信息
        ATPSignalBlockEntity.ATPData atpData = getATPData(world, pos);
        if (atpData != null) {
            info.append("ATP数据:\n");
            info.append(String.format("  最大允许速度: %.1f km/h\n", atpData.getMaxSpeed()));
            info.append(String.format("  紧急制动: %s\n", atpData.isEmergencyBrake() ? "是" : "否"));
            info.append(String.format("  前方障碍物距离: %.2f 米\n", atpData.getObstacleDistance()));
            info.append(String.format("  目标信号状态: %s\n", atpData.getSignalState().getDisplayName()));
        }
        
        return info.toString();
    }
    
    // 设置信号状态
    public static void updateSignalState(World world, BlockPos pos, SignalBlock.SignalState newState) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ATPSignalBlock && state.get(IS_ACTIVE)) {
            world.setBlockState(pos, state.with(SIGNAL_STATE, newState));
            
            // 更新BlockEntity中的状态
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof ATPSignalBlockEntity) {
                ATPSignalBlockEntity atpEntity = (ATPSignalBlockEntity) entity;
                atpEntity.updateSignalState(newState);
            }
            
            // 如果接入DCS系统，通知CBTC系统
            if (state.get(HAS_DCS)) {
                CBTCSystem.getInstance(world).onSignalStateChanged(pos, newState);
            }
        }
    }
    
    // 设置信号机类型
    public static void setSignalType(World world, BlockPos pos, SignalBlock.SignalType type) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ATPSignalBlock) {
            world.setBlockState(pos, state.with(SIGNAL_TYPE, type));
        }
    }
    
    // 设置信号机激活状态
    public static void setSignalActive(World world, BlockPos pos, boolean active) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ATPSignalBlock) {
            world.setBlockState(pos, state.with(IS_ACTIVE, active));
            
            // 如果停用，设置为红灯
            if (!active) {
                world.setBlockState(pos, state.with(SIGNAL_STATE, SignalBlock.SignalState.RED));
                
                // 更新BlockEntity
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof ATPSignalBlockEntity) {
                    ATPSignalBlockEntity atpEntity = (ATPSignalBlockEntity) entity;
                    atpEntity.updateSignalState(SignalBlock.SignalState.RED);
                    atpEntity.setEmergencyBrake(true); // 停用信号机时触发紧急制动
                }
            }
        }
    }
    
    // 设置DCS接入状态
    public static void setDCSConnection(World world, BlockPos pos, boolean connected) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ATPSignalBlock) {
            world.setBlockState(pos, state.with(HAS_DCS, connected));
            LogSystem.systemLog("ATP信号机 " + pos + " DCS连接状态: " + (connected ? "已连接" : "已断开"));
        }
    }
    
    // 设置轨道传感器连接状态
    public static void setTrackSensorConnection(World world, BlockPos pos, boolean connected) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ATPSignalBlock) {
            world.setBlockState(pos, state.with(HAS_TRACK_SENSOR, connected));
            LogSystem.systemLog("ATP信号机 " + pos + " 轨道传感器连接状态: " + (connected ? "已连接" : "已断开"));
        }
    }
    
    // 设置ATP系统类型
    public static void setATPSystemType(World world, BlockPos pos, ATPSystemType type) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ATPSignalBlock) {
            world.setBlockState(pos, state.with(ATP_SYSTEM_TYPE, type));
            LogSystem.systemLog("ATP信号机 " + pos + " ATP系统类型: " + type.name());
        }
    }
    
    // ATP系统类型枚举
    public enum ATPSystemType implements net.minecraft.util.StringIdentifiable {
        CBTC("cbtc", "基于通信的列车控制系统"),
        ETCS_L1("etcs_l1", "欧洲列车控制系统 L1级"),
        ETCS_L2("etcs_l2", "欧洲列车控制系统 L2级"),
        ATC("atc", "列车自动控制系统"),
        ATO("ato", "列车自动驾驶系统");
        
        private final String name;
        private final String displayName;
        
        ATPSystemType(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }
        
        @Override
        public String asString() {
            return this.name;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}