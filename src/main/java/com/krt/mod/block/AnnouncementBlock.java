package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.gui.AnnouncementScreen;
import com.krt.mod.KRTMod;

public class AnnouncementBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    public AnnouncementBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 打开报站设置界面
            player.openHandledScreen(new AnnouncementScreenHandler(pos));
        }
        return ActionResult.SUCCESS;
    }

    // 播放列车进站广播
    public static void playTrainArrivingAnnouncement(World world, BlockPos pos, String destination) {
        if (world.isClient) return;
        String message = "乘客们，列车即将进站，本次列车终点站" + destination + "站，请乘客们按照地面指示标志排队候车，列车到站时，请先下后上，注意站台间隙";
        // 播放音频
        world.playSound(null, pos, KRTMod.ANNOUNCEMENT_ARRIVING_SOUND, SoundCategory.AMBIENT, 1.0F, 1.0F);
        // 发送消息给附近的玩家
        sendMessageToNearbyPlayers(world, pos, message);
    }

    // 播放列车报站广播
    public static void playTrainStationAnnouncement(World world, BlockPos pos, String destination, String nextStation) {
        if (world.isClient) return;
        String message = "乘客们，本次列车终点站" + destination + "站，下一站" + nextStation + "站，上车的乘客请往里走，请勿在车厢内乞讨卖艺散发小广告";
        // 播放音频
        world.playSound(null, pos, KRTMod.ANNOUNCEMENT_STATION_SOUND, SoundCategory.AMBIENT, 1.0F, 1.0F);
        // 发送消息给附近的玩家
        sendMessageToNearbyPlayers(world, pos, message);
    }

    // 播放列车到站广播
    public static void playTrainArrivedAnnouncement(World world, BlockPos pos, String stationName, String exitDirection) {
        if (world.isClient) return;
        String message = "乘客们，" + stationName + "站，到了，请带好你的随身物品，从列车前进方向的" + exitDirection + "方向下车，开门请当心";
        // 播放音频
        world.playSound(null, pos, KRTMod.ANNOUNCEMENT_ARRIVED_SOUND, SoundCategory.AMBIENT, 1.0F, 1.0F);
        // 发送消息给附近的玩家
        sendMessageToNearbyPlayers(world, pos, message);
    }

    // 发送消息给附近的玩家
    private static void sendMessageToNearbyPlayers(World world, BlockPos pos, String message) {
        for (PlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 256.0D) {
                player.sendMessage(Text.literal(message), false);
            }
        }
    }
}