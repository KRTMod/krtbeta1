package com.krt.api.block;

import com.krt.api.audio.KAudioManager;
import com.krt.api.data.Station;
import com.krt.api.KRTModFabric;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Random;
import java.util.UUID;

/**
 * 报站方块类
 * 支持列车进站广播、列车报站和到站等功能
 */
public class AnnouncementBlock extends Block {
    public static final BooleanProperty POWERED = Properties.POWERED;
    
    // 默认报站音频ID
    private String arrivalAudioId = "announcement.arrival";
    private String departureAudioId = "announcement.departure";
    private String stationAudioId = "announcement.station";
    
    // 当前报站方块所属车站
    private String stationId = null;
    
    // 自定义报站内容
    private String arrivalAnnouncement = "乘客们，列车即将进站，本次列车终点站{terminal}站，请乘客们按照地面指示标志排队候车，列车到站时，请先下后上，注意站台间隙";
    private String departureAnnouncement = "乘客们，本次列车终点站{terminal}站，下一站{next}站，上车的乘客请往里走，请勿在车厢内乞讨卖艺散发小广告";
    private String stationAnnouncement = "乘客们，{station}站，到了，请带好你的随身物品，从列车前进方向的{direction}方向下车，开门请当心";
    
    public AnnouncementBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(POWERED, false));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        
        ItemStack stack = player.getStackInHand(hand);
        
        // 这里可以实现与报站方块交互的逻辑
        // 例如：打开设置界面、更换报站音频等
        player.sendMessage(Text.of("报站方块交互界面尚未实现"), false);
        
        return ActionResult.CONSUME;
    }
    
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        boolean powered = world.isReceivingRedstonePower(pos);
        
        if (powered != state.get(POWERED)) {
            world.setBlockState(pos, state.with(POWERED, powered), NOTIFY_LISTENERS);
            
            if (powered) {
                // 接收到红石信号，播放报站音频
                playRandomAnnouncement(world, pos);
            }
        }
    }
    
    /**
     * 播放随机报站音频
     */
    private void playRandomAnnouncement(World world, BlockPos pos) {
        Random random = new Random();
        int type = random.nextInt(3); // 0: 进站, 1: 报站, 2: 到站
        
        switch (type) {
            case 0:
                playArrivalAnnouncement(world, pos);
                break;
            case 1:
                playDepartureAnnouncement(world, pos);
                break;
            case 2:
                playStationAnnouncement(world, pos);
                break;
        }
    }
    
    /**
     * 播放列车进站广播
     */
    public void playArrivalAnnouncement(World world, BlockPos pos) {
        KAudioManager.getInstance().playAudio(arrivalAudioId, pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * 播放列车报站广播
     */
    public void playDepartureAnnouncement(World world, BlockPos pos) {
        KAudioManager.getInstance().playAudio(departureAudioId, pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * 播放列车到站广播
     */
    public void playStationAnnouncement(World world, BlockPos pos) {
        KAudioManager.getInstance().playAudio(stationAudioId, pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * 设置所属车站
     */
    public void setStationId(String stationId) {
        this.stationId = stationId;
    }
    
    /**
     * 设置进站广播内容
     */
    public void setArrivalAnnouncement(String announcement) {
        this.arrivalAnnouncement = announcement;
    }
    
    /**
     * 设置报站广播内容
     */
    public void setDepartureAnnouncement(String announcement) {
        this.departureAnnouncement = announcement;
    }
    
    /**
     * 设置到站广播内容
     */
    public void setStationAnnouncement(String announcement) {
        this.stationAnnouncement = announcement;
    }
    
    /**
     * 更换进站广播音频
     */
    public void setArrivalAudioId(String audioId) {
        this.arrivalAudioId = audioId;
    }
    
    /**
     * 更换报站广播音频
     */
    public void setDepartureAudioId(String audioId) {
        this.departureAudioId = audioId;
    }
    
    /**
     * 更换到站广播音频
     */
    public void setStationAudioId(String audioId) {
        this.stationAudioId = audioId;
    }
}