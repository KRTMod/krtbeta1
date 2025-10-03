package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
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

public class SignalBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final EnumProperty<SignalState> SIGNAL_STATE = EnumProperty.of("signal_state", SignalState.class);
    public static final EnumProperty<SignalType> SIGNAL_TYPE = EnumProperty.of("signal_type", SignalType.class);
    public static final BooleanProperty IS_ACTIVE = BooleanProperty.of("is_active");
    public static final BooleanProperty HAS_DCS = BooleanProperty.of("has_dcs"); // 是否接入调度集中系统

    public SignalBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(SIGNAL_STATE, SignalState.RED)
                .with(SIGNAL_TYPE, SignalType.MAIN)
                .with(IS_ACTIVE, true)
                .with(HAS_DCS, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIGNAL_STATE, SIGNAL_TYPE, IS_ACTIVE, HAS_DCS);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 检查是否为调试模式或管理员
            boolean isDebugMode = false; // 可以根据游戏设置判断
            
            if (isDebugMode) {
                // 调试模式下允许手动切换信号状态
                SignalState currentState = state.get(SIGNAL_STATE);
                SignalState nextState;
                
                // 四显示循环切换
                switch (currentState) {
                    case GREEN:
                        nextState = SignalState.YELLOW_GREEN;
                        break;
                    case YELLOW_GREEN:
                        nextState = SignalState.YELLOW;
                        break;
                    case YELLOW:
                        nextState = SignalState.RED;
                        break;
                    default:
                        nextState = SignalState.GREEN;
                        break;
                }
                
                world.setBlockState(pos, state.with(SIGNAL_STATE, nextState));
                player.sendMessage(Text.literal("信号机状态切换为: " + nextState.getDisplayName()), false);
                LogSystem.systemLog("玩家 " + player.getEntityName() + " 手动切换信号机状态: " + pos + " -> " + nextState.name());
            } else {
                // 普通模式下显示信号机信息
                String signalInfo = getSignalInfo(state, pos);
                player.sendMessage(Text.literal(signalInfo), false);
            }
        }
        return ActionResult.SUCCESS;
    }

    // 获取信号机信息
    private String getSignalInfo(BlockState state, BlockPos pos) {
        SignalState signalState = state.get(SIGNAL_STATE);
        SignalType signalType = state.get(SIGNAL_TYPE);
        boolean isActive = state.get(IS_ACTIVE);
        boolean hasDCS = state.get(HAS_DCS);
        
        return String.format("信号机信息:\n位置: %s\n类型: %s\n状态: %s\n激活: %s\nDCS接入: %s",
                pos.toString(),
                signalType.getDisplayName(),
                signalState.getDisplayName(),
                isActive ? "是" : "否",
                hasDCS ? "是" : "否");
    }

    // 更新信号状态
    public static void updateSignalState(World world, BlockPos pos, SignalState newState) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SignalBlock && state.get(IS_ACTIVE)) {
            world.setBlockState(pos, state.with(SIGNAL_STATE, newState));
            
            // 如果接入DCS系统，通知CBTC系统
            if (state.get(HAS_DCS)) {
                CBTCSystem.getInstance(world).onSignalStateChanged(pos, newState);
            }
        }
    }
    
    // 设置信号机类型
    public static void setSignalType(World world, BlockPos pos, SignalType type) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SignalBlock) {
            world.setBlockState(pos, state.with(SIGNAL_TYPE, type));
        }
    }
    
    // 设置信号机激活状态
    public static void setSignalActive(World world, BlockPos pos, boolean active) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SignalBlock) {
            world.setBlockState(pos, state.with(IS_ACTIVE, active));
            // 如果停用，设置为红灯
            if (!active) {
                world.setBlockState(pos, state.with(SIGNAL_STATE, SignalState.RED));
            }
        }
    }
    
    // 设置DCS接入状态
    public static void setDCSConnection(World world, BlockPos pos, boolean connected) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SignalBlock) {
            world.setBlockState(pos, state.with(HAS_DCS, connected));
            LogSystem.systemLog("信号机 " + pos + " DCS连接状态: " + (connected ? "已连接" : "已断开"));
        }
    }

    // 四显示自动闭塞信号状态枚举
    public enum SignalState implements net.minecraft.util.StringIdentifiable {
        RED("red", 0, "红灯 - 停车，禁止越过"),
        YELLOW("yellow", 1, "黄灯 - 注意减速，前方有一个闭塞分区被占用"),
        YELLOW_GREEN("yellow_green", 2, "绿黄灯 - 前方有两个闭塞分区空闲，准备减速"),
        GREEN("green", 3, "绿灯 - 前方至少有三个闭塞分区空闲，可以按规定速度运行");
        
        private final String name;
        private final int priority; // 优先级，用于状态转换
        private final String displayName;
        
        SignalState(String name, int priority, String displayName) {
            this.name = name;
            this.priority = priority;
            this.displayName = displayName;
        }
        
        @Override
        public String asString() {
            return this.name;
        }
        
        public int getPriority() {
            return priority;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        // 根据前方闭塞分区数量获取对应的信号状态
        public static SignalState fromBlockSections(int freeSections) {
            if (freeSections >= 3) {
                return GREEN;
            } else if (freeSections == 2) {
                return YELLOW_GREEN;
            } else if (freeSections == 1) {
                return YELLOW;
            } else {
                return RED;
            }
        }
    }
    
    // 信号机类型枚举
    public enum SignalType implements net.minecraft.util.StringIdentifiable {
        MAIN("main", "主信号机"),
        ENTRY("entry", "进站信号机"),
        EXIT("exit", "出站信号机"),
        SHUNTING("shunting", "调车信号机"),
        BLOCK("block", "闭塞信号机"),
        REPEATER("repeater", "复示信号机"),
        DISTANT("distant", "预告信号机");
        
        private final String name;
        private final String displayName;
        
        SignalType(String name, String displayName) {
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