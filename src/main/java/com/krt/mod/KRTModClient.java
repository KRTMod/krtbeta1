package com.krt.mod;

import com.krt.mod.block.DepartureTimerBlockEntity;
import com.krt.mod.block.renderer.DepartureTimerBlockRenderer;
import com.krt.mod.block.KRTBlockEntities;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.renderer.TrainEntityRenderer;
import com.krt.mod.gui.OperationManualScreen;
import com.krt.mod.system.ModDebugger;
import com.krt.mod.util.TextureReferenceFixer;
// import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
// import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
// import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_I;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_T;

@Environment(EnvType.CLIENT)
public class KRTModClient implements ClientModInitializer {
    private static final Map<Identifier, Identifier> FIXED_TEXTURE_REFERENCES = new ConcurrentHashMap<>();
    
    // 声明按键绑定对象
    private static KeyBinding testKeyBinding;
    private static KeyBinding debugModeKeyBinding;
    private static KeyBinding debugTrackKeyBinding;
    private static KeyBinding debugTrainKeyBinding;
    private static KeyBinding debugSignalKeyBinding;
    private static KeyBinding debugInfoKeyBinding;
    
    @Override
    public void onInitializeClient() {
        // 注册操作手册的Screen和ScreenHandler映射关系
        ScreenRegistry.register(Init.OPERATION_MANUAL_SCREEN_HANDLER, OperationManualScreen::new);
        
        // 初始化纹理引用修复器
        TextureReferenceFixer.init();
        
        // 预加载并修复所有纹理引用
        preloadAndFixTextures();
        
        // 注册按键绑定
        registerKeyBindings();
        
        // 注册列车实体渲染器
        registerEntityRenderers();
        
        // 注册渲染事件
        registerRenderEvents();
        
        KRTMod.LOGGER.info("KRT 昆明轨道交通模组客户端初始化完成!");
    }
    
    /**
     * 注册实体渲染器
     */
    private void registerEntityRenderers() {
        // 注释掉使用不存在的渲染注册表相关类的代码
        /*
        EntityRendererRegistry.INSTANCE.register(Init.TRAIN, 
                (EntityRendererRegistry.Context context) -> new TrainEntityRenderer(context));
        
        // 注册端门计时器方块实体渲染器
        BlockEntityRendererRegistry.INSTANCE.register(KRTBlockEntities.DEPARTURE_TIMER, 
                (BlockEntityRendererFactory.Context context) -> new DepartureTimerBlockRenderer(context));
        
        KRTMod.LOGGER.info("列车实体渲染器和端门计时器方块实体渲染器注册完成");
        */
    }
    
    /**
     * 注册按键绑定
     */
    private void registerKeyBindings() {
        // 创建并注册按键绑定
        testKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.krt_mod.test", // 按键绑定的翻译键名
            InputUtil.Type.KEYSYM, // 按键类型，KEYSYM表示键盘，MOUSE表示鼠标
            GLFW_KEY_R, // 默认绑定的按键（R键）
            "category.krt_mod.general" // 按键绑定的类别
        ));
        
        // 调试模式按键
        debugModeKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.krt_mod.debug_mode",
            InputUtil.Type.KEYSYM,
            GLFW_KEY_F3, // 使用F3键作为调试模式主键
            "category.krt_mod.debug"
        ));
        
        // 轨道调试按键
        debugTrackKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.krt_mod.debug_track",
            InputUtil.Type.KEYSYM,
            GLFW_KEY_T,
            "category.krt_mod.debug"
        ));
        
        // 列车调试按键
        debugTrainKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.krt_mod.debug_train",
            InputUtil.Type.KEYSYM,
            GLFW_KEY_R,
            "category.krt_mod.debug"
        ));
        
        // 信号调试按键
        debugSignalKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.krt_mod.debug_signal",
            InputUtil.Type.KEYSYM,
            GLFW_KEY_S,
            "category.krt_mod.debug"
        ));
        
        // 信息调试按键
        debugInfoKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.krt_mod.debug_info",
            InputUtil.Type.KEYSYM,
            GLFW_KEY_I,
            "category.krt_mod.debug"
        ));
        
        // 注册按键事件监听
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 当测试按键被按下时执行的逻辑
            while (testKeyBinding.wasPressed()) {
                if (client.player != null) {
                    client.player.sendMessage(Text.of("昆明轨道交通模组测试键被按下！"), false);
                }
            }
            
            // 处理调试模式按键
            if (client.player != null) {
                ModDebugger debugger = ModDebugger.getInstance();
                
                if (debugModeKeyBinding.wasPressed()) {
                    debugger.toggleDebugMode(client.player);
                }
                
                if (debugger.isDebugModeEnabled()) {
                    if (debugTrackKeyBinding.wasPressed()) {
                        debugger.toggleTrackSegments(client.player);
                    }
                    
                    if (debugTrainKeyBinding.wasPressed()) {
                        debugger.toggleTrainPaths(client.player);
                    }
                    
                    if (debugSignalKeyBinding.wasPressed()) {
                        debugger.toggleSignalStates(client.player);
                    }
                    
                    if (debugInfoKeyBinding.wasPressed()) {
                        debugger.toggleTrainInfo(client.player);
                    }
                }
            }
        });
        
        KRTMod.LOGGER.info("按键绑定注册完成");
    }
    
    /**
     * 注册渲染事件
     */
    private void registerRenderEvents() {
        // 注册世界渲染事件，用于渲染调试信息
        WorldRenderEvents.LAST.register(context -> {
            ModDebugger debugger = ModDebugger.getInstance();
            if (debugger.isDebugModeEnabled()) {
                MatrixStack matrices = context.matrixStack();
                VertexConsumerProvider vertexConsumers = context.consumers();
                float tickDelta = context.tickDelta();
                
                debugger.renderDebugInfo(matrices, vertexConsumers, tickDelta);
            }
        });
        
        KRTMod.LOGGER.info("渲染事件注册完成");
    }
    
    /**
     * 预加载并修复所有纹理引用
     */
    private void preloadAndFixTextures() {
        KRTMod.LOGGER.info("开始预加载并修复纹理引用...");
        
        // 预加载一些常用的纹理
        String[] commonTextures = {
            "block/track",
            "block/switch_track",
            "block/signal_block",
            "block/platform_door",
            "block/line_setting_panel",
            "block/train_control_panel",
            "item/track",
            "item/door_key",
            "item/driver_key",
            "item/line_setting_panel"
        };
        
        for (String texturePath : commonTextures) {
            Identifier originalId = new Identifier(KRTMod.MOD_ID, texturePath + ".png");
            Identifier fixedId = TextureReferenceFixer.fixTextureReference(originalId);
            FIXED_TEXTURE_REFERENCES.put(originalId, fixedId);
        }
        
        KRTMod.LOGGER.info("纹理引用预加载完成，共修复了 " + FIXED_TEXTURE_REFERENCES.size() + " 个纹理引用");
    }
    
    /**
     * 获取修复后的纹理标识符
     */
    public static Identifier getFixedTextureId(Identifier originalId) {
        return FIXED_TEXTURE_REFERENCES.getOrDefault(originalId, originalId);
    }
}