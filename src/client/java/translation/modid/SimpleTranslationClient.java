package translation.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import translation.modid.cache.TranslationCacheManager;
import translation.modid.config.TranslationConfig;
import translation.modid.keybinding.ModKeyBindings;
import translation.modid.screen.ConfigScreen;
import translation.modid.sign.SignTranslationManager;
import translation.modid.textdisplay.TextDisplayRefreshManager;
import translation.modid.tooltip.TooltipTranslationHandler;
import translation.modid.translator.TranslationManager;

public class SimpleTranslationClient implements ClientModInitializer {
    private int tickCounter = 0;
    private boolean wasSneaking = false; // 跟踪上一次的潜行状态
    private boolean lastTranslateTextDisplayState = false; // 跟踪上一次的文字显示实体翻译状态
    
	@Override
	public void onInitializeClient() {
        // 注册按键绑定
        ModKeyBindings.register();
        
        // 注册物品提示框翻译
        TooltipTranslationHandler.register();
        
        // 注册按键事件处理
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 打开配置界面
            while (ModKeyBindings.openConfig.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ConfigScreen(null));
                }
            }
            
            // 清除告示牌翻译缓存
            while (ModKeyBindings.clearSignCache.consumeClick()) {
                SignTranslationManager.getInstance().clearAll();
                if (client.player != null) {
                    client.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§a[翻译] 已清除告示牌翻译缓存")
                    );
                }
            }
            
            if (client.player != null && client.level != null) {
                TranslationConfig config = TranslationConfig.getInstance();
                
                // 检查文字显示实体翻译配置是否发生变化
                boolean currentTranslateTextDisplayState = config.enabled && config.autoTranslate && config.translateTextDisplay;
                if (lastTranslateTextDisplayState != currentTranslateTextDisplayState) {
                    if (lastTranslateTextDisplayState && !currentTranslateTextDisplayState) {
                        // 从开启变为关闭，恢复所有实体的原始文本
                        restoreAllTextDisplayOriginalTexts();
                    } else if (!lastTranslateTextDisplayState && currentTranslateTextDisplayState) {
                        // 从关闭变为开启，强制刷新所有实体以触发重新翻译
                        forceRefreshAllTextDisplays();
                    }
                }
                lastTranslateTextDisplayState = currentTranslateTextDisplayState;
                
                // 强制刷新所有待更新的 TextDisplay
                if (currentTranslateTextDisplayState) {
                    TextDisplayRefreshManager.getInstance().forceRefreshPendingTextDisplays();
                }
                
                if (config.translateSignOnSneak) {
                    // 潜行触发模式：检测玩家从不潜行到潜行的变化
                    boolean isSneaking = client.player.isCrouching();
                    
                    if (isSneaking && !wasSneaking) {
                        // 玩家刚开始潜行，触发翻译
                        SignTranslationManager.getInstance().translateNearbySigns();
                    }
                    
                    wasSneaking = isSneaking;
                } else {
                    // 定时模式：每40个tick（2秒）自动翻译
                    tickCounter++;
                    if (tickCounter >= 40) {
                        tickCounter = 0;
                        SignTranslationManager.getInstance().translateNearbySigns();
                    }
                }
            }
        });
        
        // 初始化翻译管理器
        TranslationManager.getInstance();
        
        // 注册世界加入事件，切换缓存
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TranslationConfig config = TranslationConfig.getInstance();
            if (config.persistentCache && config.perWorldCache) {
                String worldName = getWorldName(client);
                TranslationCacheManager.getInstance().switchWorld(worldName);
                SimpleTranslation.LOGGER.info("已进入世界: {}", worldName);
            }
        });
        
        // 注册世界离开事件，保存缓存
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TranslationConfig config = TranslationConfig.getInstance();
            if (config.persistentCache) {
                TranslationCacheManager.getInstance().saveCache();
                SimpleTranslation.LOGGER.info("已保存当前世界的翻译缓存");
            }
        });
        
        // 注册客户端停止事件，保存缓存
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            TranslationConfig config = TranslationConfig.getInstance();
            if (config.persistentCache) {
                TranslationCacheManager.getInstance().saveCache();
                SimpleTranslation.LOGGER.info("已保存翻译缓存");
            }
        });
        
        SimpleTranslation.LOGGER.info("翻译客户端已初始化");
	}
	
	/**
	 * 强制刷新所有 TextDisplay 实体以触发重新翻译
	 * 通过反射调用 Mixin 中的静态方法
	 */
	private void forceRefreshAllTextDisplays() {
	    try {
	        // 通过反射访问 Display.TextDisplay 类中的 Mixin 方法
	        java.lang.reflect.Method refreshMethod = null;
	        
	        // 查找 forceRefreshAllTextDisplays 方法
	        for (java.lang.reflect.Method method : net.minecraft.world.entity.Display.TextDisplay.class.getDeclaredMethods()) {
	            if (method.getName().equals("forceRefreshAllTextDisplays") && 
	                method.getParameterCount() == 0 &&
	                java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
	                refreshMethod = method;
	                break;
	            }
	        }
	        
	        if (refreshMethod != null) {
	            refreshMethod.setAccessible(true);
	            refreshMethod.invoke(null);
	            SimpleTranslation.LOGGER.info("[TextDisplay] 已调用强制刷新所有文字显示实体方法");
	        } else {
	            SimpleTranslation.LOGGER.warn("[TextDisplay] 无法找到 forceRefreshAllTextDisplays 方法");
	        }
	    } catch (Exception e) {
	        SimpleTranslation.LOGGER.error("[TextDisplay] 强制刷新所有文字显示实体时出错: {}", e.getMessage(), e);
	    }
	}
	
	/**
	 * 恢复所有 TextDisplay 实体的原始文本
	 * 通过反射调用 Mixin 中的静态方法
	 */
	private void restoreAllTextDisplayOriginalTexts() {
	    try {
	        // 通过反射访问 Display.TextDisplay 类中的 Mixin 方法
	        java.lang.reflect.Method restoreMethod = null;
	        
	        // 查找 restoreAllOriginalTexts 方法
	        for (java.lang.reflect.Method method : net.minecraft.world.entity.Display.TextDisplay.class.getDeclaredMethods()) {
	            if (method.getName().equals("restoreAllOriginalTexts") && 
	                method.getParameterCount() == 0 &&
	                java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
	                restoreMethod = method;
	                break;
	            }
	        }
	        
	        if (restoreMethod != null) {
	            restoreMethod.setAccessible(true);
	            restoreMethod.invoke(null);
	            SimpleTranslation.LOGGER.info("[TextDisplay] 已调用恢复原始文本方法");
	        } else {
	            SimpleTranslation.LOGGER.warn("[TextDisplay] 无法找到 restoreAllOriginalTexts 方法");
	        }
	    } catch (Exception e) {
	        SimpleTranslation.LOGGER.error("[TextDisplay] 恢复原始文本时出错: {}", e.getMessage(), e);
	    }
	}
	
	/**
	 * 获取当前世界的名称
	 */
	private String getWorldName(net.minecraft.client.Minecraft client) {
	    if (client.getConnection() != null && client.getConnection().getConnection() != null) {
	        // 多人游戏：使用服务器地址
	        net.minecraft.client.multiplayer.ServerData serverData = client.getCurrentServer();
	        if (serverData != null) {
	            return "server_" + serverData.ip.replaceAll("[<>:\"/\\\\|?*]", "_");
	        }
	    }
	    
	    // 单人游戏：使用世界名称
	    if (client.level != null) {
	        net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
	        if (server != null) {
	            String worldName = server.getWorldData().getLevelName();
	            if (worldName != null && !worldName.isEmpty()) {
	                return "world_" + worldName.replaceAll("[<>:\"/\\\\|?*]", "_");
	            }
	        }
	    }
	    
	    // 默认
	    return "global";
	}
}