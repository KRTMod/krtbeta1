package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.gui.AnnouncementScreenHandler;
import com.krt.mod.sound.ModSounds;
import com.krt.mod.system.TrainControlSystem; // 假设这是控制列车的系统类

public class AnnouncementBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final EnumProperty<ConnectionMode> CONNECTION_MODE = EnumProperty.of("connection_mode", ConnectionMode.class);
    public static final BooleanProperty HAS_POWER_SOURCE = BooleanProperty.of("has_power_source");

    public enum ConnectionMode implements StringIdentifiable {
        AUTO("auto"),
        MANUAL("manual"),
        ADVANCED("advanced");

        private final String name;

        ConnectionMode(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return this.name;
        }
    }

    public AnnouncementBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false)
                .with(CONNECTION_MODE, ConnectionMode.AUTO)
                .with(HAS_POWER_SOURCE, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, CONNECTION_MODE, HAS_POWER_SOURCE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 尝试检测相邻轨道并自动对齐
        Direction trackDirection = detectAdjacentTrack(ctx.getWorld(), ctx.getBlockPos());
        if (trackDirection != null) {
            return this.getDefaultState()
                    .with(FACING, trackDirection)
                    .with(POWERED, false)
                    .with(CONNECTION_MODE, ConnectionMode.AUTO)
                    .with(HAS_POWER_SOURCE, hasNearbyPowerSource(ctx.getWorld(), ctx.getBlockPos()));
        }
        // 如果没有相邻轨道，则使用玩家朝向的相反方向
        return this.getDefaultState()
                .with(FACING, ctx.getPlayerFacing().getOpposite())
                .with(POWERED, false)
                .with(CONNECTION_MODE, ConnectionMode.AUTO)
                .with(HAS_POWER_SOURCE, hasNearbyPowerSource(ctx.getWorld(), ctx.getBlockPos()));
    }

    /**
     * 检测相邻的轨道方块并返回轨道方向
     */
    private Direction detectAdjacentTrack(World world, BlockPos pos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            
            // 检查是否是轨道方块
            if (neighborState.getBlock() instanceof TrackBlock || neighborState.getBlock() instanceof SwitchTrackBlock) {
                // 对于轨道方块，返回朝向轨道的方向
                return dir;
            }
        }
        return null;
    }

    /**
     * 检测附近是否有电源
     */
    private boolean hasNearbyPowerSource(World world, BlockPos pos) {
        // 检查周围6个方向是否有电源方块
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            if (world.isReceivingRedstonePower(neighborPos)) {
                return true;
            }
        }
        return false;
    }

    // 默认音量配置
    private static final float DEFAULT_ANNOUNCEMENT_VOLUME = 1.0F;
    private static final float BUTTON_SOUND_VOLUME = 0.5F;
    private static final float POWER_SOUND_VOLUME = 0.5F;
    private static final float ALIGN_SOUND_VOLUME = 0.3F;
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking()) {
            // 切换连接模式 - 需要权限校验
            if (!world.isClient && hasPermission(player)) {
                ConnectionMode currentMode = state.get(CONNECTION_MODE);
                ConnectionMode nextMode;
                switch (currentMode) {
                    case AUTO:
                        nextMode = ConnectionMode.MANUAL;
                        break;
                    case MANUAL:
                        nextMode = ConnectionMode.ADVANCED;
                        break;
                    case ADVANCED:
                    default:
                        nextMode = ConnectionMode.AUTO;
                        break;
                }
                world.setBlockState(pos, state.with(CONNECTION_MODE, nextMode));
                // 使用多语言
                player.sendMessage(Text.translatable("announcement.mode_switched", Text.translatable(getModeTranslationKey(nextMode))), true);
                
                // 播放切换音效
                world.playSound(null, pos, ModSounds.BUTTON_CLICK_SOUND, SoundCategory.BLOCKS, BUTTON_SOUND_VOLUME, 1.0F);
            } else if (!hasPermission(player)) {
                player.sendMessage(Text.translatable("announcement.no_permission"), true);
            }
        } else {
            // 打开报站设置界面 - 需要权限校验
            if (!world.isClient && hasPermission(player)) {
                player.openHandledScreen(new AnnouncementScreenHandler(pos));
            } else if (!hasPermission(player)) {
                player.sendMessage(Text.translatable("announcement.no_permission"), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * 检查玩家是否有权限操作此方块
     */
    private boolean hasPermission(PlayerEntity player) {
        // 仅允许管理员或创造模式玩家操作
        return player.hasPermissionLevel(2) || player.isCreative();
    }
    
    /**
     * 获取连接模式的翻译键
     */
    private String getModeTranslationKey(ConnectionMode mode) {
        switch (mode) {
            case AUTO:
                return "announcement.mode.auto";
            case MANUAL:
                return "announcement.mode.manual";
            case ADVANCED:
                return "announcement.mode.advanced";
            default:
                return "announcement.mode.unknown";
        }
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        // 检测周围轨道或电源的变化
        boolean hasPower = world.isReceivingRedstonePower(pos) || world.getBlockPowered(pos);
        boolean hadPower = state.get(POWERED);
        boolean hasSource = hasNearbyPowerSource(world, pos);
        boolean hadSource = state.get(HAS_POWER_SOURCE);
        
        // 更新电源状态
        if (hasPower != hadPower) {
            world.setBlockState(pos, state.with(POWERED, hasPower));
            
            // 添加状态变化的视觉或音效反馈
            if (!world.isClient) {
                if (hasPower) {
                    world.playSound(null, pos, ModSounds.POWER_ON_SOUND, SoundCategory.BLOCKS, POWER_SOUND_VOLUME, 1.0F);
                } else {
                    world.playSound(null, pos, ModSounds.POWER_OFF_SOUND, SoundCategory.BLOCKS, POWER_SOUND_VOLUME, 0.8F);
                }
            }
        }
        
        // 更新电源源状态
        if (hasSource != hadSource) {
            world.setBlockState(pos, state.with(HAS_POWER_SOURCE, hasSource));
        }
        
        // 检测轨道变化，如果需要可以重新对齐方向
        if (sourceBlock instanceof TrackBlock || sourceBlock instanceof SwitchTrackBlock) {
            if (state.get(CONNECTION_MODE) == ConnectionMode.AUTO) {
                Direction trackDirection = detectAdjacentTrack(world, pos);
                if (trackDirection != null && trackDirection != state.get(FACING)) {
                    world.setBlockState(pos, state.with(FACING, trackDirection));
                    if (!world.isClient) {
                        world.playSound(null, pos, ModSounds.ALIGN_SOUND, SoundCategory.BLOCKS, ALIGN_SOUND_VOLUME, 1.0F);
                    }
                }
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            // 方块被移除时的清理逻辑
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    // 播放列车进站广播
    public static void playTrainArrivingAnnouncement(World world, BlockPos pos, String destination) {
        playTrainArrivingAnnouncement(world, pos, destination, DEFAULT_ANNOUNCEMENT_VOLUME);
    }
    
    // 重载版本，支持自定义音量
    public static void playTrainArrivingAnnouncement(World world, BlockPos pos, String destination, float volume) {
        if (world.isClient) return;
        // 使用多语言
        Text message = Text.translatable("announcement.arriving", destination);
        // 播放音频
        world.playSound(null, pos, ModSounds.ANNOUNCEMENT_ARRIVING_SOUND, SoundCategory.AMBIENT, volume, 1.0F);
        // 发送消息给附近的玩家
        sendMessageToNearbyPlayers(world, pos, message.getString());
    }

    // 播放列车报站广播
    public static void playTrainStationAnnouncement(World world, BlockPos pos, String destination, String nextStation) {
        playTrainStationAnnouncement(world, pos, destination, nextStation, DEFAULT_ANNOUNCEMENT_VOLUME);
    }
    
    // 重载版本，支持自定义音量
    public static void playTrainStationAnnouncement(World world, BlockPos pos, String destination, String nextStation, float volume) {
        if (world.isClient) return;
        // 使用多语言
        Text message = Text.translatable("announcement.station", destination, nextStation);
        // 播放音频
        world.playSound(null, pos, ModSounds.ANNOUNCEMENT_STATION_SOUND, SoundCategory.AMBIENT, volume, 1.0F);
        // 发送消息给附近的玩家
        sendMessageToNearbyPlayers(world, pos, message.getString());
    }

    // 播放列车到站广播
    public static void playTrainArrivedAnnouncement(World world, BlockPos pos, String stationName, String exitDirection) {
        playTrainArrivedAnnouncement(world, pos, stationName, exitDirection, DEFAULT_ANNOUNCEMENT_VOLUME);
    }
    
    // 重载版本，支持自定义音量
    public static void playTrainArrivedAnnouncement(World world, BlockPos pos, String stationName, String exitDirection, float volume) {
        if (world.isClient) return;
        // 使用多语言
        Text message = Text.translatable("announcement.arrived", stationName, exitDirection);
        // 播放音频
        world.playSound(null, pos, ModSounds.ANNOUNCEMENT_ARRIVED_SOUND, SoundCategory.AMBIENT, volume, 1.0F);
        // 发送消息给附近的玩家
        sendMessageToNearbyPlayers(world, pos, message.getString());
    }
    
    // 自动检测列车行驶方向并播放到站广播
    public static void playTrainArrivedAnnouncementWithAutoDirection(World world, BlockPos pos, String stationName) {
        // 尝试获取附近列车的行驶方向
        String exitDirection = getTrainExitDirection(world, pos);
        if (exitDirection == null) {
            // 如果无法获取方向，使用默认值
            exitDirection = "右侧";
        }
        playTrainArrivedAnnouncement(world, pos, stationName, exitDirection);
    }
    
    /**
     * 根据列车实际行驶方向自动判断下车方向
     */
    private static String getTrainExitDirection(World world, BlockPos pos) {
        try {
            // 这里假设TrainControlSystem可以获取到附近列车的信息
            // 实际实现需要根据模组的具体逻辑来调整
            TrainControlSystem trainSystem = TrainControlSystem.getInstance(world);
            if (trainSystem != null) {
                // 获取附近最近的列车方向
                Direction trainDirection = trainSystem.getNearestTrainDirection(pos);
                if (trainDirection != null) {
                    // 根据列车行驶方向判断下车方向
                    // 这里使用一个简单的逻辑：假设右侧总是站台方向
                    // 实际实现可能需要更复杂的站台布局判断
                    switch (trainDirection) {
                        case NORTH:
                            return "右侧";
                        case EAST:
                            return "右侧";
                        case SOUTH:
                            return "右侧";
                        case WEST:
                            return "右侧";
                        default:
                            return "右侧";
                    }
                }
            }
        } catch (Exception e) {
            // 如果获取方向失败，返回默认值
        }
        return null;
    }

    // 发送消息给附近的玩家
    private static void sendMessageToNearbyPlayers(World world, BlockPos pos, String message) {
        for (PlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 256.0D) {
                // 这里保持原样，因为消息内容已经是通过Text.translatable处理过的
                player.sendMessage(Text.literal(message), false);
            }
        }
    }
}