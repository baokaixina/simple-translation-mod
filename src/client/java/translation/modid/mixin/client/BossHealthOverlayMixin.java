package translation.modid.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import translation.modid.SimpleTranslation;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    
    @Shadow
    private Map<UUID, BossEvent> events;
    
    @Unique
    private static final ConcurrentHashMap<String, String> bossBarCache = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingBossBarTranslations = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<UUID, Component> translatedBossBars = new ConcurrentHashMap<>();
    
    @Unique
    private static boolean hasLoggedFieldError = false;
    
    /**
     * 拦截boss血条的渲染，在渲染前翻译boss名称
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(net.minecraft.client.gui.GuiGraphics graphics, CallbackInfo ci) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateBossBar) {
            translatedBossBars.clear();
            return;
        }
        
        // 获取boss事件列表
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        
        // 尝试使用@Shadow字段，如果失败则使用反射
        Map<UUID, BossEvent> eventsMap = null;
        try {
            eventsMap = this.events;
        } catch (Exception e) {
            // @Shadow字段可能不存在，使用反射
            if (!hasLoggedFieldError) {
                SimpleTranslation.LOGGER.warn("[BossBar] @Shadow字段访问失败，尝试使用反射: {}", e.getMessage());
                hasLoggedFieldError = true;
            }
        }
        
        // 如果@Shadow字段为空，尝试通过反射获取
        if (eventsMap == null) {
            try {
                BossHealthOverlay overlay = (BossHealthOverlay)(Object)this;
                java.lang.reflect.Field eventsField = null;
                
                // 尝试多个可能的字段名
                String[] possibleFieldNames = {"events", "bossEvents", "f_93798_", "f_93799_"};
                for (String fieldName : possibleFieldNames) {
                    try {
                        eventsField = BossHealthOverlay.class.getDeclaredField(fieldName);
                        eventsField.setAccessible(true);
                        Object value = eventsField.get(overlay);
                        if (value instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<UUID, BossEvent> events = (Map<UUID, BossEvent>) value;
                            eventsMap = events;
                            if (!hasLoggedFieldError) {
                                SimpleTranslation.LOGGER.info("[BossBar] 成功通过反射找到字段: {}", fieldName);
                                hasLoggedFieldError = true;
                            }
                            break;
                        }
                    } catch (NoSuchFieldException e) {
                        // 继续尝试下一个字段名
                    }
                }
                
                if (eventsMap == null && !hasLoggedFieldError) {
                    SimpleTranslation.LOGGER.warn("[BossBar] 无法找到events字段，boss血条翻译可能无法工作");
                    hasLoggedFieldError = true;
                }
            } catch (Exception e) {
                if (!hasLoggedFieldError) {
                    SimpleTranslation.LOGGER.error("[BossBar] 反射获取events字段时出错: {}", e.getMessage());
                    hasLoggedFieldError = true;
                }
            }
        }
        
        if (eventsMap != null) {
            translateBossEvents(eventsMap);
        }
    }
    
    /**
     * 拦截render方法中drawString调用的Component参数
     * 这样可以更直接地修改渲染的文本
     */
    @ModifyArg(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
        ),
        index = 1,
        require = 0
    )
    private Component translateBossBarComponent(Component original) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateBossBar) {
            return original;
        }
        
        if (original == null) {
            return original;
        }
        
        String text = original.getString();
        if (text == null || text.trim().isEmpty() || containsChinese(text)) {
            return original;
        }
        
        // 检查缓存
        String cachedTranslation = bossBarCache.get(text);
        if (cachedTranslation != null) {
            MutableComponent translated = Component.literal(cachedTranslation);
            translated.setStyle(original.getStyle());
            return translated;
        }
        
        // 检查是否正在翻译
        Long pendingTime = pendingBossBarTranslations.get(text);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            return original;
        }
        
        // 开始异步翻译
        pendingBossBarTranslations.put(text, System.currentTimeMillis());
        TranslationManager.getInstance().translate(text)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                        bossBarCache.put(text, translated);
                    }
                    pendingBossBarTranslations.remove(text);
                });
        
        return original;
    }
    
    @Unique
    private void translateBossEvents(Map<UUID, BossEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        for (Map.Entry<UUID, BossEvent> entry : events.entrySet()) {
            BossEvent bossEvent = entry.getValue();
            if (bossEvent == null) {
                continue;
            }
            
            Component name = bossEvent.getName();
            if (name == null) {
                continue;
            }
            
            String nameText = name.getString();
            if (nameText == null || nameText.trim().isEmpty() || containsChinese(nameText)) {
                continue;
            }
            
            // 检查缓存
            String cachedTranslation = bossBarCache.get(nameText);
            if (cachedTranslation != null) {
                // 使用缓存的翻译
                MutableComponent translatedName = Component.literal(cachedTranslation);
                translatedName.setStyle(name.getStyle());
                translatedBossBars.put(entry.getKey(), translatedName);
                // 通过反射设置翻译后的名称
                setBossEventName(bossEvent, translatedName);
                continue;
            }
            
            // 检查是否正在翻译
            Long pendingTime = pendingBossBarTranslations.get(nameText);
            if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
                // 正在翻译中，保持原文
                continue;
            }
            
            // 开始异步翻译
            pendingBossBarTranslations.put(nameText, System.currentTimeMillis());
            final UUID bossId = entry.getKey();
            final BossEvent event = bossEvent;
            final Component originalName = name;
            TranslationManager.getInstance().translate(nameText)
                    .thenAccept(translated -> {
                        if (translated != null && !translated.isEmpty() && !translated.equals(nameText)) {
                            bossBarCache.put(nameText, translated);
                            // 在主线程中更新boss名称
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null) {
                                mc.execute(() -> {
                                    try {
                                        MutableComponent translatedName = Component.literal(translated);
                                        translatedName.setStyle(originalName.getStyle());
                                        translatedBossBars.put(bossId, translatedName);
                                        setBossEventName(event, translatedName);
                                    } catch (Exception e) {
                                        SimpleTranslation.LOGGER.error("[BossBar] 更新boss名称时出错: {}", e.getMessage());
                                    }
                                });
                            }
                        }
                        pendingBossBarTranslations.remove(nameText);
                    });
        }
    }
    
    @Unique
    private static java.lang.reflect.Field nameFieldCache = null;
    
    @Unique
    private static boolean hasLoggedNameFieldError = false;
    
    @Unique
    private static void setBossEventName(BossEvent bossEvent, Component translatedName) {
        try {
            // 如果已经找到字段，直接使用
            if (nameFieldCache != null) {
                try {
                    nameFieldCache.set(bossEvent, translatedName);
                    return;
                } catch (Exception e) {
                    // 字段可能失效，重新查找
                    nameFieldCache = null;
                }
            }
            
            // 尝试通过反射设置boss事件名称
            String[] possibleFieldNames = {"name", "f_93699_", "f_93700_"};
            for (String fieldName : possibleFieldNames) {
                try {
                    java.lang.reflect.Field nameField = BossEvent.class.getDeclaredField(fieldName);
                    nameField.setAccessible(true);
                    if (Component.class.isAssignableFrom(nameField.getType())) {
                        nameField.set(bossEvent, translatedName);
                        nameFieldCache = nameField; // 缓存字段
                        if (!hasLoggedNameFieldError) {
                            SimpleTranslation.LOGGER.info("[BossBar] 成功找到name字段: {}", fieldName);
                            hasLoggedNameFieldError = true;
                        }
                        return;
                    }
                } catch (NoSuchFieldException e) {
                    // 继续尝试下一个字段名
                } catch (IllegalAccessException e) {
                    // 字段可能是final的，无法修改
                    if (!hasLoggedNameFieldError) {
                        SimpleTranslation.LOGGER.warn("[BossBar] 字段 {} 可能是final的，无法修改: {}", fieldName, e.getMessage());
                    }
                }
            }
            
            // 如果常见字段名都失败，遍历所有字段查找 Component 类型
            java.lang.reflect.Field[] fields = BossEvent.class.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (Component.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        field.set(bossEvent, translatedName);
                        nameFieldCache = field; // 缓存字段
                        if (!hasLoggedNameFieldError) {
                            SimpleTranslation.LOGGER.info("[BossBar] 通过遍历找到name字段: {}", field.getName());
                            hasLoggedNameFieldError = true;
                        }
                        return;
                    } catch (IllegalAccessException e) {
                        // 字段可能是final的，无法修改
                        if (!hasLoggedNameFieldError) {
                            SimpleTranslation.LOGGER.warn("[BossBar] 字段 {} 可能是final的，无法修改: {}", field.getName(), e.getMessage());
                        }
                    } catch (Exception e) {
                        // 继续尝试下一个字段
                    }
                }
            }
            
            if (!hasLoggedNameFieldError) {
                SimpleTranslation.LOGGER.warn("[BossBar] 无法找到或修改BossEvent的name字段，将依赖@ModifyArg方法");
                hasLoggedNameFieldError = true;
            }
        } catch (Exception e) {
            if (!hasLoggedNameFieldError) {
                SimpleTranslation.LOGGER.error("[BossBar] 设置boss名称时出错: {}", e.getMessage());
                hasLoggedNameFieldError = true;
            }
        }
    }
    
    @Unique
    private boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}

