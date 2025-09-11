# KRT Core API - 轨道交通模组核心API

KRT Core API 是一个用于 Minecraft 的轨道交通系统模组，提供了完整的地铁/轻轨系统模拟功能。

## 功能特性

### 基础系统
- 列车驾驶系统（支持自动驾驶和手动驾驶）
- 站台屏蔽门系统（支持高架和地下两种类型）
- 报站广播系统
- 线路控制系统
- 车站导向牌系统

### 高级功能
- ATC/ATO 自动控制系统
- 车厂管理系统
- 轨道信号系统
- 多语言支持（简体中文、繁体中文、英文、日文、泰文）
- 扩展包API支持

## 核心方块和物品

### 方块
- **报站方块 (Announcement Block)**：播放列车进站、报站和到站广播
- **屏蔽门 (Platform Door)**：站台安全屏蔽门
- **高架屏蔽门 (Elevated Platform Door)**：高架车站专用屏蔽门
- **地下屏蔽门 (Underground Platform Door)**：地下车站专用屏蔽门
- **导向牌 (Direction Sign)**：显示车站和线路信息

### 物品
- **屏蔽门钥匙 (Platform Door Key)**：手动开关屏蔽门的钥匙

## 多语言支持
本模组支持以下语言：
- 简体中文（默认）
- 繁体中文
- 英文
- 日文
- 泰文

游戏会根据玩家的语言设置自动选择合适的语言。

## 音频文件自定义
模组支持自定义音频文件。将符合要求的OGG格式音频文件放入对应目录：
- `sounds/train/`：列车相关音频
- `sounds/door/`：屏蔽门相关音频
- `sounds/announcement/`：报站相关音频

详细要求请参考 `src/main/resources/assets/krt/sounds/README.txt` 文件。

## 扩展包API
KRT Core API 提供了扩展包制作支持，允许开发者创建自己的：
- 列车模型
- 车站类型
- 轨道材质
- 自定义线路

详细API文档请参考 `KRTAddonAPI.java` 文件。

## 安装说明
1. 确保安装了 Minecraft 1.19.2
2. 安装 Fabric Loader 0.14.19 或更高版本
3. 安装 Fabric API 0.76.0+1.19.2 或更高版本
4. 将本模组的JAR文件放入 Minecraft 的 mods 文件夹

## 开发环境搭建
1. 克隆本仓库
2. 运行 `gradlew genSources` 生成源代码
3. 使用 IntelliJ IDEA 或 Eclipse 导入项目
4. 运行 `gradlew build` 编译模组

## 配置文件
模组的配置文件位于：
- Windows: `%appdata%\.minecraft\config\krt.properties`
- Linux/Mac: `~/.minecraft/config/krt.properties`

配置项包括：
- 信号系统启用状态
- ATC功能开关
- AI驾驶建议
- 示例数据生成

## 命令列表
- `/krt help`：显示帮助信息
- `/krt line create <name>`：创建新线路
- `/krt station add <line> <name>`：为线路添加车站
- `/krt train spawn <line>`：生成新列车
- `/krt door toggle <x> <y> <z>`：切换屏蔽门状态
- `/krt announcement play <type> <station>`：播放广播

## 注意事项
- 本模组仍在开发中，可能存在一些bug
- 请定期备份你的存档
- 请勿将本模组用于商业用途
- 使用本模组时，请遵守服务器规则

## 版权信息
© 2023 KRT Team. 保留所有权利。

## 联系方式
如有问题或建议，请联系：
- Email: support@krtmod.com
- Discord: https://discord.gg/krtmod