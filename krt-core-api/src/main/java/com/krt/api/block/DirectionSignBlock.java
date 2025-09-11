package com.krt.api.block;

import com.krt.api.data.Line;
import com.krt.api.data.Station;
import com.krt.api.KRTModFabric;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
 * 导向牌方块类
 * 用于显示车站和线路信息
 */
public class DirectionSignBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;
    
    // 导向牌数据
    private String targetStationId = null;
    private String targetLineId = null;
    private String displayText = "";
    private String signType = "station";
    
    public DirectionSignBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        
        // 显示导向牌信息
        if (displayText.isEmpty()) {
            updateDisplayText();
        }
        
        player.sendMessage(Text.of(displayText), false);
        
        // 如果是管理员或有权限的玩家，可以编辑导向牌
        if (player.isCreative()) {
            player.sendMessage(Text.of("右键点击编辑导向牌内容"), false);
            // 这里可以实现编辑导向牌的逻辑
        }
        
        return ActionResult.CONSUME;
    }
    
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        boolean powered = world.isReceivingRedstonePower(pos);
        
        if (powered != state.get(POWERED)) {
            world.setBlockState(pos, state.with(POWERED, powered), NOTIFY_LISTENERS);
            
            // 通电时可能触发特殊效果，例如改变显示内容或灯光效果
            if (powered) {
                updateDisplayText();
            }
        }
    }
    
    /**
     * 更新导向牌显示文本
     */
    private void updateDisplayText() {
        StringBuilder textBuilder = new StringBuilder();
        
        if (signType.equals("station")) {
            if (targetStationId != null) {
                Station station = KRTModFabric.getApiInstance().getStation(targetStationId);
                if (station != null) {
                    textBuilder.append(station.getName()).append("站\n");
                    
                    // 添加线路信息
                    if (targetLineId != null) {
                        Line line = KRTModFabric.getApiInstance().getLine(targetLineId);
                        if (line != null) {
                            textBuilder.append("线路：").append(line.getName()).append(" (").append(line.getColor()).append(")");
                        }
                    }
                }
            }
        } else if (signType.equals("direction")) {
            // 方向导向牌
            if (targetStationId != null) {
                Station station = KRTModFabric.getApiInstance().getStation(targetStationId);
                if (station != null) {
                    textBuilder.append("前方：").append(station.getName()).append("站");
                }
            }
        } else if (signType.equals("transfer")) {
            // 换乘导向牌
            textBuilder.append("换乘信息\n");
            // 这里应该添加换乘线路和方向信息
        }
        
        displayText = textBuilder.toString();
    }
    
    /**
     * 设置目标车站
     */
    public void setTargetStation(String stationId) {
        this.targetStationId = stationId;
        updateDisplayText();
    }
    
    /**
     * 设置目标线路
     */
    public void setTargetLine(String lineId) {
        this.targetLineId = lineId;
        updateDisplayText();
    }
    
    /**
     * 设置导向牌类型
     */
    public void setSignType(String type) {
        this.signType = type;
        updateDisplayText();
    }
    
    /**
     * 获取显示文本
     */
    public String getDisplayText() {
        return displayText;
    }
}