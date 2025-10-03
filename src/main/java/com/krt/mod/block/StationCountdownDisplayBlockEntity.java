package com.krt.mod.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class StationCountdownDisplayBlockEntity extends BlockEntity {
    private int countdownTime = 60; // 秒
    private String nextTrainLine = "1号线";
    private String nextStation = "下一站";
    private int displayId = 0;
    private int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 10; // 每10tick更新一次

    public StationCountdownDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(KRTBlockEntities.STATION_COUNTDOWN_DISPLAY, pos, state);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        nbt.putInt("countdownTime", countdownTime);
        nbt.putString("nextTrainLine", nextTrainLine);
        nbt.putString("nextStation", nextStation);
        nbt.putInt("displayId", displayId);
        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("countdownTime")) {
            countdownTime = nbt.getInt("countdownTime");
        }
        if (nbt.contains("nextTrainLine")) {
            nextTrainLine = nbt.getString("nextTrainLine");
        }
        if (nbt.contains("nextStation")) {
            nextStation = nbt.getString("nextStation");
        }
        if (nbt.contains("displayId")) {
            displayId = nbt.getInt("displayId");
        }
    }

    public void tick() {
        // 优化更新频率，每10tick才检查一次
        if (tickCounter % UPDATE_INTERVAL != 0) {
            tickCounter++;
            return;
        }
        
        tickCounter++;
        if (tickCounter >= 20 * UPDATE_INTERVAL) { // 每秒更新一次，但只在特定tick执行
            tickCounter = 0;
            if (countdownTime > 0) {
                countdownTime--;
            } else {
                // 倒计时结束，可以设置新的倒计时时间
                countdownTime = 120; // 重置为120秒
            }
            markDirty();
        }
    }

    public int getCountdownTime() {
        return countdownTime;
    }

    public String getNextTrainLine() {
        return nextTrainLine;
    }

    public String getNextStation() {
        return nextStation;
    }

    public void updateDisplayData(int countdownTime, String nextTrainLine, String nextStation) {
        this.countdownTime = countdownTime;
        this.nextTrainLine = nextTrainLine;
        this.nextStation = nextStation;
        markDirty();
    }

    public Text getDisplayText() {
        int minutes = countdownTime / 60;
        int seconds = countdownTime % 60;
        String timeString = minutes > 0 ? String.format("%d分%d秒", minutes, seconds) : String.format("%d秒", seconds);
        return Text.of(String.format("%s - %s\n到达时间: %s", nextTrainLine, nextStation, timeString));
    }
}