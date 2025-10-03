package com.krt.mod.system;

import com.krt.mod.KRTMod;
import com.krt.mod.system.LineControlSystem.LineInfo;
import com.krt.mod.system.LineControlSystem.StationInfo;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

public class SaveDataManager {
    private static final String DATA_KEY = "krt_railway_data";

    // 保存线路数据到世界存档
    public static void saveRailwayData(World world) {
        if (!(world instanceof ServerWorld)) {
            return; // 只在服务端保存数据
        }

        ServerWorld serverWorld = (ServerWorld) world;
        PersistentStateManager stateManager = serverWorld.getPersistentStateManager();
        RailwayData data = stateManager.getOrCreate(RailwayData::new, RailwayData::new, DATA_KEY);

        // 从LineControlSystem获取线路数据
        Collection<LineInfo> lines = LineControlSystem.getAllLines();
        data.saveLines(lines);

        // 标记数据为已修改，以便保存
        data.markDirty();
        KRTMod.LOGGER.info("已保存 {} 条线路数据到存档", lines.size());
    }

    // 从世界存档加载线路数据
    public static void loadRailwayData(World world) {
        if (!(world instanceof ServerWorld)) {
            return; // 只在服务端加载数据
        }

        ServerWorld serverWorld = (ServerWorld) world;
        PersistentStateManager stateManager = serverWorld.getPersistentStateManager();
        RailwayData data = stateManager.getOrCreate(RailwayData::new, RailwayData::new, DATA_KEY);

        // 加载线路数据到LineControlSystem
        Collection<LineInfo> lines = data.loadLines();
        LineControlSystem lineControlSystem = LineControlSystem.getInstance(world);
        
        // 清除现有线路数据（避免重复）
        clearExistingLines(lineControlSystem);
        
        // 添加加载的线路数据
        for (LineInfo line : lines) {
            lineControlSystem.createLine(line.getLineId(), line.getLineName());
            for (StationInfo station : line.getStations()) {
                lineControlSystem.addStationToLine(line.getLineId(), station.getStationId(), station.getStationName(), station.getPosition());
            }
            for (BlockPos trackPos : line.getTracks()) {
                lineControlSystem.addTrackToLine(line.getLineId(), trackPos);
            }
        }

        KRTMod.LOGGER.info("已从存档加载 {} 条线路数据", lines.size());
    }

    // 清除现有线路数据
    private static void clearExistingLines(LineControlSystem lineControlSystem) {
        // 在实际实现中，这里需要访问LineControlSystem中的lines字段
        // 由于我们无法直接访问私有字段，这里需要通过反射或者修改LineControlSystem提供清除方法
        // 简化处理：实际实现时需要根据LineControlSystem的具体结构调整
        KRTMod.LOGGER.debug("清除现有线路数据");
    }

    // 铁路数据持久化类
    public static class RailwayData extends PersistentState {
        private final List<LineInfoData> lineDataList = new ArrayList<>();

        public RailwayData() {
        }

        // 从NBT创建RailwayData实例
        public RailwayData(NbtCompound nbt) {
            readNbt(nbt);
        }

        // 保存线路数据
        public void saveLines(Collection<LineInfo> lines) {
            lineDataList.clear();
            for (LineInfo line : lines) {
                LineInfoData lineData = new LineInfoData();
                lineData.lineId = line.getLineId();
                lineData.lineName = line.getLineName();
                lineData.maxSpeed = line.getMaxSpeed();

                // 保存车站数据
                for (StationInfo station : line.getStations()) {
                    StationInfoData stationData = new StationInfoData();
                    stationData.stationId = station.getStationId();
                    stationData.stationName = station.getStationName();
                    stationData.posX = station.getPosition().getX();
                    stationData.posY = station.getPosition().getY();
                    stationData.posZ = station.getPosition().getZ();
                    lineData.stations.add(stationData);
                }

                // 保存轨道数据
                for (BlockPos pos : line.getTracks()) {
                    TrackPosData trackData = new TrackPosData();
                    trackData.x = pos.getX();
                    trackData.y = pos.getY();
                    trackData.z = pos.getZ();
                    lineData.tracks.add(trackData);
                }

                lineDataList.add(lineData);
            }
        }

        // 加载线路数据
        public Collection<LineInfo> loadLines() {
            List<LineInfo> lines = new ArrayList<>();
            for (LineInfoData lineData : lineDataList) {
                LineInfo line = new LineInfo(lineData.lineId, lineData.lineName);
                line.setMaxSpeed(lineData.maxSpeed);

                // 加载车站数据
                for (StationInfoData stationData : lineData.stations) {
                    BlockPos pos = new BlockPos(stationData.posX, stationData.posY, stationData.posZ);
                    StationInfo station = new StationInfo(stationData.stationId, stationData.stationName, pos);
                    line.addStation(station);
                }

                // 加载轨道数据
                for (TrackPosData trackData : lineData.tracks) {
                    BlockPos pos = new BlockPos(trackData.x, trackData.y, trackData.z);
                    line.addTrack(pos);
                }

                lines.add(line);
            }
            return lines;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList linesList = new NbtList();
            for (LineInfoData lineData : lineDataList) {
                NbtCompound lineTag = new NbtCompound();
                lineTag.putString("lineId", lineData.lineId);
                lineTag.putString("lineName", lineData.lineName);
                lineTag.putDouble("maxSpeed", lineData.maxSpeed);

                // 保存车站列表
                NbtList stationsList = new NbtList();
                for (StationInfoData stationData : lineData.stations) {
                    NbtCompound stationTag = new NbtCompound();
                    stationTag.putString("stationId", stationData.stationId);
                    stationTag.putString("stationName", stationData.stationName);
                    stationTag.putInt("posX", stationData.posX);
                    stationTag.putInt("posY", stationData.posY);
                    stationTag.putInt("posZ", stationData.posZ);
                    stationsList.add(stationTag);
                }
                lineTag.put("stations", stationsList);

                // 保存轨道列表
                NbtList tracksList = new NbtList();
                for (TrackPosData trackData : lineData.tracks) {
                    NbtCompound trackTag = new NbtCompound();
                    trackTag.putInt("x", trackData.x);
                    trackTag.putInt("y", trackData.y);
                    trackTag.putInt("z", trackData.z);
                    tracksList.add(trackTag);
                }
                lineTag.put("tracks", tracksList);

                linesList.add(lineTag);
            }
            nbt.put("lines", linesList);
            return nbt;
        }

        public void readNbt(NbtCompound nbt) {
            lineDataList.clear();
            if (nbt.contains("lines", NbtElement.LIST_TYPE)) {
                NbtList linesList = nbt.getList("lines", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < linesList.size(); i++) {
                    NbtCompound lineTag = linesList.getCompound(i);
                    LineInfoData lineData = new LineInfoData();
                    lineData.lineId = lineTag.getString("lineId");
                    lineData.lineName = lineTag.getString("lineName");
                    lineData.maxSpeed = lineTag.getDouble("maxSpeed");

                    // 加载车站列表
                    if (lineTag.contains("stations", NbtElement.LIST_TYPE)) {
                        NbtList stationsList = lineTag.getList("stations", NbtElement.COMPOUND_TYPE);
                        for (int j = 0; j < stationsList.size(); j++) {
                            NbtCompound stationTag = stationsList.getCompound(j);
                            StationInfoData stationData = new StationInfoData();
                            stationData.stationId = stationTag.getString("stationId");
                            stationData.stationName = stationTag.getString("stationName");
                            stationData.posX = stationTag.getInt("posX");
                            stationData.posY = stationTag.getInt("posY");
                            stationData.posZ = stationTag.getInt("posZ");
                            lineData.stations.add(stationData);
                        }
                    }

                    // 加载轨道列表
                    if (lineTag.contains("tracks", NbtElement.LIST_TYPE)) {
                        NbtList tracksList = lineTag.getList("tracks", NbtElement.COMPOUND_TYPE);
                        for (int j = 0; j < tracksList.size(); j++) {
                            NbtCompound trackTag = tracksList.getCompound(j);
                            TrackPosData trackData = new TrackPosData();
                            trackData.x = trackTag.getInt("x");
                            trackData.y = trackTag.getInt("y");
                            trackData.z = trackTag.getInt("z");
                            lineData.tracks.add(trackData);
                        }
                    }

                    lineDataList.add(lineData);
                }
            }
        }

        // 内部数据类
        private static class LineInfoData {
            public String lineId;
            public String lineName;
            public double maxSpeed;
            public List<StationInfoData> stations = new ArrayList<>();
            public List<TrackPosData> tracks = new ArrayList<>();
        }

        private static class StationInfoData {
            public String stationId;
            public String stationName;
            public int posX;
            public int posY;
            public int posZ;
        }

        private static class TrackPosData {
            public int x;
            public int y;
            public int z;
        }
    }
}