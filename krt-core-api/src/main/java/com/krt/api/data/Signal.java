package com.krt.api.data;

/**
 * 信号机数据模型
 * 表示地铁系统中的信号机信息
 */
public class Signal {
    private String id;
    private int x, y, z; // 信号机位置坐标
    private String type; // 信号机类型
    private String state; // 信号状态：绿色、黄色、红色
    private String lineId; // 所属线路ID
    private String trackSwitchId; // 关联的道岔ID
    private int distanceToNextSignal; // 到下一个信号机的距离

    // 信号状态常量
    public static final String STATE_GREEN = "绿色"; // 正常通行
    public static final String STATE_YELLOW = "黄色"; // 减速通行
    public static final String STATE_RED = "红色"; // 停止等待

    // 信号机类型常量
    public static final String TYPE_MAIN = "主信号机";
    public static final String TYPE_PREVIEW = "预告信号机";
    public static final String TYPE_BLOCK = "闭塞信号机";
    public static final String TYPE_SWITCH = "道岔信号机";

    // 距离阈值常量
    public static final int RED_DISTANCE_THRESHOLD = 100; // 红色信号距离阈值（格）
    public static final int YELLOW_DISTANCE_THRESHOLD = 200; // 黄色信号距离阈值（格）

    // 构造函数
    public Signal(String id, int x, int y, int z, String type, String lineId) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.lineId = lineId;
        this.state = STATE_GREEN; // 默认状态为绿色
        this.trackSwitchId = null;
        this.distanceToNextSignal = 500;
    }

    // 更新信号状态（基于道岔状态）
    public void updateStateByTrackSwitch(String trackSwitchState, int distance) {
        // 如果道岔状态不为正常，根据距离设置信号状态
        if (!trackSwitchState.equals("正常")) {
            if (distance <= RED_DISTANCE_THRESHOLD) {
                this.state = STATE_RED;
            } else if (distance <= YELLOW_DISTANCE_THRESHOLD) {
                this.state = STATE_YELLOW;
            } else {
                this.state = STATE_GREEN;
            }
        } else {
            this.state = STATE_GREEN;
        }
    }

    // 更新信号状态（基于前方列车距离）
    public void updateStateByTrainDistance(int distance) {
        if (distance <= 50) {
            this.state = STATE_RED; // 前方50格内有列车，显示红色
        } else if (distance <= 150) {
            this.state = STATE_YELLOW; // 前方150格内有列车，显示黄色
        } else {
            this.state = STATE_GREEN; // 前方无列车或距离足够远，显示绿色
        }
    }

    // 计算信号机允许的最大速度
    public int getAllowedSpeed() {
        switch (state) {
            case STATE_GREEN:
                return 80; // 绿色信号，允许最高速度80km/h
            case STATE_YELLOW:
                return 40; // 黄色信号，允许最高速度40km/h
            case STATE_RED:
                return 0; // 红色信号，禁止通行
            default:
                return 0;
        }
    }

    // 检查信号机是否正常工作
    public boolean isWorking() {
        // 实际应用中可能需要更复杂的检查逻辑
        return state != null;
    }

    // Getter和Setter方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public String getTrackSwitchId() {
        return trackSwitchId;
    }

    public void setTrackSwitchId(String trackSwitchId) {
        this.trackSwitchId = trackSwitchId;
    }

    public int getDistanceToNextSignal() {
        return distanceToNextSignal;
    }

    public void setDistanceToNextSignal(int distanceToNextSignal) {
        this.distanceToNextSignal = distanceToNextSignal;
    }

    // 计算两个坐标点之间的距离
    public static int calculateDistance(int x1, int y1, int z1, int x2, int y2, int z2) {
        return (int) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
    }
}