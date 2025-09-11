package com.krt.api.data.json;

import com.krt.api.data.*;
import java.util.ArrayList;
import java.util.List;

/**
 * KRT模组JSON数据模型
 * 用于模组的数据导入导出功能
 */
public class KRTJsonData {
    private List<Station> stations = new ArrayList<>();
    private List<Depot> depots = new ArrayList<>();
    private List<Line> lines = new ArrayList<>();
    private List<Train> trains = new ArrayList<>();
    private List<Signal> signals = new ArrayList<>();
    private String version = "1.0.0";
    private String minecraftVersion = "1.20.1";
    private String modLoader = "forge";

    // 构造函数
    public KRTJsonData() {
    }

    // 检查数据完整性
    public boolean validateData() {
        // 检查必要数据是否存在
        if (stations.isEmpty() && lines.isEmpty() && depots.isEmpty()) {
            return false;
        }
        
        // 检查版本信息
        if (version == null || minecraftVersion == null || modLoader == null) {
            return false;
        }
        
        // 检查线路和车厂的有效性
        for (Line line : lines) {
            if (!line.isValid()) {
                return false;
            }
            
            // 检查线路是否关联有效的车厂
            boolean depotExists = false;
            for (Depot depot : depots) {
                if (depot.getId().equals(line.getDepotId())) {
                    depotExists = true;
                    break;
                }
            }
            if (!depotExists) {
                return false;
            }
        }
        
        // 检查车厂是否具备必要设施
        for (Depot depot : depots) {
            if (!depot.hasRequiredFacilities()) {
                return false;
            }
        }
        
        return true;
    }

    // Getter和Setter方法
    public List<Station> getStations() {
        return stations;
    }

    public void setStations(List<Station> stations) {
        this.stations = stations;
    }

    public List<Depot> getDepots() {
        return depots;
    }

    public void setDepots(List<Depot> depots) {
        this.depots = depots;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public List<Train> getTrains() {
        return trains;
    }

    public void setTrains(List<Train> trains) {
        this.trains = trains;
    }

    public List<Signal> getSignals() {
        return signals;
    }

    public void setSignals(List<Signal> signals) {
        this.signals = signals;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String getModLoader() {
        return modLoader;
    }

    public void setModLoader(String modLoader) {
        this.modLoader = modLoader;
    }
}