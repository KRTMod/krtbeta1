package com.krt.api.data;

import java.util.HashMap;
import java.util.Map;

/**
 * 列车数据模型
 * 表示地铁系统中的列车信息
 */
public class Train {
    private String id;
    private String name;
    private String model;
    private String lineId;
    private String currentStationId;
    private int speed;
    private int maxSpeed;
    private int healthStatus; // 健康状态，0-100
    private String direction; // 方向：前进、后退
    private int gear; // 档位：0-停止，1-低速，2-中速，3-高速
    private boolean isRunning;
    private boolean doorsOpen;
    private int passengerCount;
    private int maxPassengerCount;
    private Map<String, String> audioMap = new HashMap<>(); // 音频ID与文件路径映射

    // 构造函数
    public Train(String id, String name, String model, String lineId, String startStationId) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.lineId = lineId;
        this.currentStationId = startStationId;
        this.speed = 0;
        this.maxSpeed = 80; // 默认最大速度80km/h
        this.healthStatus = 100; // 初始健康状态100%
        this.direction = "前进";
        this.gear = 0; // 初始档位为停止
        this.isRunning = false;
        this.doorsOpen = false;
        this.passengerCount = 0;
        this.maxPassengerCount = 1000;
    }

    // 列车自检系统
    public boolean selfCheck() {
        // 检查健康状态
        if (healthStatus < 50) {
            return false; // 健康状态低于50%，不能运营
        }
        
        // 检查车门状态
        if (doorsOpen) {
            return false; // 车门未关闭，不能运营
        }
        
        // 检查档位
        if (gear != 0) {
            return false; // 档位不为停止，不能启动
        }
        
        return true;
    }

    // 控制列车速度
    public boolean setSpeed(int newSpeed) {
        // 检查速度是否在允许范围内
        if (newSpeed >= 0 && newSpeed <= maxSpeed) {
            this.speed = newSpeed;
            // 根据速度自动调整档位
            if (newSpeed == 0) {
                this.gear = 0;
            } else if (newSpeed <= maxSpeed * 0.3) {
                this.gear = 1;
            } else if (newSpeed <= maxSpeed * 0.7) {
                this.gear = 2;
            } else {
                this.gear = 3;
            }
            return true;
        }
        return false;
    }

    // 切换档位
    public boolean changeGear(int newGear) {
        if (newGear >= 0 && newGear <= 3) {
            this.gear = newGear;
            // 根据档位调整速度
            switch (newGear) {
                case 0:
                    this.speed = 0;
                    break;
                case 1:
                    this.speed = (int) (maxSpeed * 0.3);
                    break;
                case 2:
                    this.speed = (int) (maxSpeed * 0.7);
                    break;
                case 3:
                    this.speed = maxSpeed;
                    break;
            }
            return true;
        }
        return false;
    }

    // 切换方向
    public boolean changeDirection(String newDirection) {
        if (speed == 0 && (newDirection.equals("前进") || newDirection.equals("后退"))) {
            this.direction = newDirection;
            return true;
        }
        return false; // 列车未停止，不能切换方向
    }

    // 启动列车
    public boolean start() {
        if (!isRunning && selfCheck()) {
            this.isRunning = true;
            return true;
        }
        return false;
    }

    // 停止列车
    public boolean stop() {
        if (isRunning) {
            this.speed = 0;
            this.gear = 0;
            this.isRunning = false;
            return true;
        }
        return false;
    }

    // 开关车门
    public boolean toggleDoors() {
        if (speed == 0) {
            this.doorsOpen = !this.doorsOpen;
            return true;
        }
        return false; // 列车未停止，不能开关车门
    }

    // 注册音频文件
    public void registerAudio(String audioId, String soundPath) {
        audioMap.put(audioId, soundPath);
    }

    // 获取音频文件路径
    public String getAudioPath(String audioId) {
        return audioMap.get(audioId);
    }

    // 检查列车运营状态
    public boolean isOperational() {
        return healthStatus >= 50 && !doorsOpen && selfCheck();
    }

    // Getter和Setter方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public String getCurrentStationId() {
        return currentStationId;
    }

    public void setCurrentStationId(String currentStationId) {
        this.currentStationId = currentStationId;
    }

    public int getSpeed() {
        return speed;
    }

    public int getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(int maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public int getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(int healthStatus) {
        this.healthStatus = Math.max(0, Math.min(100, healthStatus));
    }

    public String getDirection() {
        return direction;
    }

    public int getGear() {
        return gear;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isDoorsOpen() {
        return doorsOpen;
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(int passengerCount) {
        this.passengerCount = Math.max(0, Math.min(maxPassengerCount, passengerCount));
    }

    public int getMaxPassengerCount() {
        return maxPassengerCount;
    }

    public void setMaxPassengerCount(int maxPassengerCount) {
        this.maxPassengerCount = maxPassengerCount;
    }

    public Map<String, String> getAudioMap() {
        return audioMap;
    }

    public void setAudioMap(Map<String, String> audioMap) {
        this.audioMap = audioMap;
    }
}