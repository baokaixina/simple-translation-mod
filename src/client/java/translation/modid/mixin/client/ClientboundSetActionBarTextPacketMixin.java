package translation.modid.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(ClientboundSetActionBarTextPacket.class)
public class ClientboundSetActionBarTextPacketMixin {
    
    @Unique
    private static final ConcurrentHashMap<String, String> actionbarCache = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingActionbarTranslations = new ConcurrentHashMap<>();
    
    /**
     * 拦截数据包处理，在设置 actionbar 消息时进行翻译
     * 在方法执行前获取数据包内容，在方法执行后修改 Gui 的 overlayMessage 字段
     */
    @Inject(
        method = "handle",
        at = @At("HEAD")
    )
    private void onHandleBefore(net.minecraft.network.protocol.game.ClientGamePacketListener listener, CallbackInfo ci) {
        // 在方法执行前获取数据包内容
        ClientboundSetActionBarTextPacket packet = (ClientboundSetActionBarTextPacket)(Object)this;
        Component message = getText(packet);
        
        if (message != null) {
            String overlayText = message.getString();
            System.out.println("[Actionbar Mixin] [HEAD] 获取到 actionbar 文本: " + overlayText);
            
            TranslationConfig config = TranslationConfig.getInstance();
            if (config.enabled && config.autoTranslate && config.translateTitleCommand) {
                if (overlayText != null && !overlayText.trim().isEmpty() && !containsChinese(overlayText)) {
                    // 检查缓存
                    String cachedTranslation = actionbarCache.get(overlayText);
                    if (cachedTranslation != null) {
                        // 有缓存，直接修改数据包内容
                        System.out.println("[Actionbar Mixin] [HEAD] 使用缓存翻译并修改数据包: " + cachedTranslation);
                        setPacketText(packet, Component.literal(cachedTranslation));
                    }
                }
            }
        }
    }
    
    /**
     * 拦截数据包处理，在设置 actionbar 消息时进行翻译
     * 在方法执行后修改 Gui 的 overlayMessage 字段
     */
    @Inject(
        method = "handle",
        at = @At("RETURN")
    )
    private void onHandleAfter(net.minecraft.network.protocol.game.ClientGamePacketListener listener, CallbackInfo ci) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        System.out.println("[Actionbar Mixin] 拦截到 actionbar 数据包");
        
        if (!config.enabled || !config.autoTranslate || !config.translateTitleCommand) {
            System.out.println("[Actionbar Mixin] 翻译未启用: enabled=" + config.enabled + ", autoTranslate=" + config.autoTranslate + ", translateTitleCommand=" + config.translateTitleCommand);
            return;
        }
        
        // 获取数据包中的消息
        ClientboundSetActionBarTextPacket packet = (ClientboundSetActionBarTextPacket)(Object)this;
        Component message = getText(packet);
        
        if (message == null) {
            System.out.println("[Actionbar Mixin] 无法获取数据包中的消息");
            return;
        }
        
        String overlayText = message.getString();
        System.out.println("[Actionbar Mixin] 获取到 actionbar 文本: " + overlayText);
        
        if (overlayText == null || overlayText.trim().isEmpty() || containsChinese(overlayText)) {
            System.out.println("[Actionbar Mixin] 跳过翻译: null=" + (overlayText == null) + ", empty=" + (overlayText != null && overlayText.trim().isEmpty()) + ", chinese=" + (overlayText != null && containsChinese(overlayText)));
            return;
        }
        
        // 检查缓存
        String cachedTranslation = actionbarCache.get(overlayText);
        if (cachedTranslation != null) {
            System.out.println("[Actionbar Mixin] 使用缓存翻译: " + cachedTranslation);
            // 有缓存，直接设置到 Gui
            MutableComponent translatedOverlay = Component.literal(cachedTranslation);
            translatedOverlay.setStyle(message.getStyle());
            setGuiOverlayMessage(translatedOverlay, overlayText);
            return;
        }
        
        // 检查是否正在翻译
        Long pendingTime = pendingActionbarTranslations.get(overlayText);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            // 正在翻译中，先显示原文
            return;
        }
        
        // 开始异步翻译
        System.out.println("[Actionbar Mixin] 开始翻译: " + overlayText);
        final Component originalMessage = message;
        pendingActionbarTranslations.put(overlayText, System.currentTimeMillis());
        TranslationManager.getInstance().translate(overlayText)
                .thenAccept(translated -> {
                    System.out.println("[Actionbar Mixin] 翻译完成: " + translated);
                    if (translated != null && !translated.isEmpty() && !translated.equals(overlayText)) {
                        actionbarCache.put(overlayText, translated);
                        // 在主线程中更新 actionbar
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.gui != null) {
                            mc.execute(() -> {
                                System.out.println("[Actionbar Mixin] 在主线程中设置翻译后的 actionbar: " + translated);
                                MutableComponent translatedOverlay = Component.literal(translated);
                                translatedOverlay.setStyle(originalMessage.getStyle());
                                setGuiOverlayMessage(translatedOverlay, overlayText);
                            });
                        } else {
                            System.out.println("[Actionbar Mixin] Minecraft 或 Gui 为 null");
                        }
                    } else {
                        System.out.println("[Actionbar Mixin] 翻译结果无效: " + translated);
                    }
                    pendingActionbarTranslations.remove(overlayText);
                });
        
        // 先显示原文，翻译完成后再更新
    }
    
    @Unique
    private static java.lang.reflect.Field textFieldCache = null;
    
    @Unique
    private void setPacketText(ClientboundSetActionBarTextPacket packet, Component text) {
        // 如果已经找到字段，直接使用
        if (textFieldCache != null) {
            try {
                textFieldCache.set(packet, text);
                System.out.println("[Actionbar Mixin] 使用缓存的字段修改数据包文本成功");
                return;
            } catch (Exception e) {
                System.out.println("[Actionbar Mixin] 使用缓存的字段修改数据包文本失败: " + e.getMessage());
                // 字段可能失效，重新查找
                textFieldCache = null;
            }
        }
        
        // 尝试使用反射修改字段
        String[] possibleFieldNames = {"text", "message", "component", "content", "f_168742_", "f_168743_"};
        for (String fieldName : possibleFieldNames) {
            try {
                java.lang.reflect.Field field = ClientboundSetActionBarTextPacket.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                if (Component.class.isAssignableFrom(field.getType())) {
                    field.set(packet, text);
                    textFieldCache = field; // 缓存字段
                    System.out.println("[Actionbar Mixin] 通过字段名修改数据包文本: " + fieldName);
                    return;
                }
            } catch (NoSuchFieldException e) {
                // 继续尝试下一个字段名
            } catch (Exception e) {
                System.out.println("[Actionbar Mixin] 修改字段 " + fieldName + " 时出错: " + e.getMessage());
            }
        }
        
        // 如果常见字段名都失败，遍历所有字段查找 Component 类型
        try {
            java.lang.reflect.Field[] fields = ClientboundSetActionBarTextPacket.class.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (Component.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        field.set(packet, text);
                        textFieldCache = field; // 缓存字段
                        System.out.println("[Actionbar Mixin] 通过遍历修改数据包文本: " + field.getName());
                        return;
                    } catch (Exception e) {
                        // 继续尝试下一个字段
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Actionbar Mixin] 遍历字段修改时出错: " + e.getMessage());
        }
        
        System.out.println("[Actionbar Mixin] 无法修改数据包中的文本字段");
    }
    
    @Unique
    private Component getText(ClientboundSetActionBarTextPacket packet) {
        // 如果已经找到字段，直接使用
        if (textFieldCache != null) {
            try {
                Object value = textFieldCache.get(packet);
                if (value instanceof Component) {
                    return (Component) value;
                }
            } catch (Exception e) {
                // 字段可能失效，重新查找
                textFieldCache = null;
            }
        }
        
        // 尝试使用数据包的 getText() 方法
        try {
            java.lang.reflect.Method getTextMethod = ClientboundSetActionBarTextPacket.class.getDeclaredMethod("getText");
            getTextMethod.setAccessible(true);
            Object result = getTextMethod.invoke(packet);
            if (result instanceof Component) {
                return (Component) result;
            }
        } catch (Exception e) {
            // 方法不存在，继续尝试字段
        }
        
        // 尝试使用反射访问字段 - 先尝试常见的字段名
        String[] possibleFieldNames = {"text", "message", "component", "content", "f_168742_", "f_168743_"};
        for (String fieldName : possibleFieldNames) {
            try {
                java.lang.reflect.Field field = ClientboundSetActionBarTextPacket.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(packet);
                if (value instanceof Component) {
                    textFieldCache = field; // 缓存字段
                    System.out.println("[Actionbar Mixin] 找到文本字段: " + fieldName);
                    return (Component) value;
                }
            } catch (NoSuchFieldException e) {
                // 继续尝试下一个字段名
            } catch (Exception e) {
                System.out.println("[Actionbar Mixin] 访问字段 " + fieldName + " 时出错: " + e.getMessage());
            }
        }
        
        // 如果常见字段名都失败，遍历所有字段查找 Component 类型
        try {
            java.lang.reflect.Field[] fields = ClientboundSetActionBarTextPacket.class.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (Component.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(packet);
                        if (value instanceof Component && value != null) {
                            textFieldCache = field; // 缓存字段
                            System.out.println("[Actionbar Mixin] 通过遍历找到文本字段: " + field.getName());
                            return (Component) value;
                        }
                    } catch (Exception e) {
                        // 继续尝试下一个字段
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Actionbar Mixin] 遍历字段时出错: " + e.getMessage());
        }
        
        System.out.println("[Actionbar Mixin] 无法找到数据包中的文本字段");
        return null;
    }
    
    @Unique
    private static java.lang.reflect.Field overlayMessageField = null;
    
    @Unique
    private void setGuiOverlayMessage(Component text, String originalText) {
        // 直接设置 Gui 的 overlayMessage 字段
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            System.out.println("[Actionbar Mixin] setGuiOverlayMessage: Minecraft 或 Gui 为 null");
            return;
        }
        
        try {
            net.minecraft.client.gui.Gui gui = mc.gui;
            
            // 如果已经找到字段，直接使用
            if (overlayMessageField != null) {
                try {
                    overlayMessageField.set(gui, text);
                    System.out.println("[Actionbar Mixin] 使用缓存的字段设置 overlayMessage 成功");
                    return;
                } catch (Exception e) {
                    System.out.println("[Actionbar Mixin] 使用缓存的字段失败: " + e.getMessage());
                    // 字段可能失效，重新查找
                    overlayMessageField = null;
                }
            }
            
            // 尝试多个可能的字段名
            String[] possibleFieldNames = {"overlayMessage", "overlay", "actionbarMessage", "actionbar", "f_168742_"};
            
            for (String fieldName : possibleFieldNames) {
                try {
                    java.lang.reflect.Field field = net.minecraft.client.gui.Gui.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(gui, text);
                    overlayMessageField = field; // 缓存字段
                    System.out.println("[Actionbar Mixin] 通过字段名找到并设置 overlayMessage: " + fieldName);
                    return; // 成功设置，返回
                } catch (NoSuchFieldException e) {
                    // 继续尝试下一个字段名
                } catch (Exception e) {
                    System.out.println("[Actionbar Mixin] 设置字段 " + fieldName + " 时出错: " + e.getMessage());
                    // 其他异常，继续尝试
                }
            }
            
            // 如果所有字段名都失败，尝试遍历所有字段
            // 查找 Component 类型且不是 title 和 subtitle 的字段
            // 并且其值与原始文本匹配（说明这就是 overlayMessage）
            java.lang.reflect.Field[] fields = net.minecraft.client.gui.Gui.class.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (Component.class.isAssignableFrom(field.getType())) {
                    String fieldName = field.getName();
                    // 跳过 title 和 subtitle 字段
                    if (fieldName.contains("title") || fieldName.contains("subtitle")) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object value = field.get(gui);
                        // 如果这个字段的值与原始文本匹配，说明这就是 overlayMessage
                        if (value != null && value instanceof Component) {
                            Component comp = (Component) value;
                            String compText = comp.getString();
                            if (originalText != null && originalText.equals(compText)) {
                                field.set(gui, text);
                                overlayMessageField = field; // 缓存字段
                                System.out.println("[Actionbar Mixin] 通过文本匹配找到并设置 overlayMessage: " + fieldName + ", 原始文本: " + compText);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        // 继续尝试下一个字段
                    }
                }
            }
            
            // 如果还是找不到，尝试设置所有非 title/subtitle 的 Component 字段
            // 这是最后的备选方案
            for (java.lang.reflect.Field field : fields) {
                if (Component.class.isAssignableFrom(field.getType())) {
                    String fieldName = field.getName();
                    if (fieldName.contains("title") || fieldName.contains("subtitle")) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        field.set(gui, text);
                        overlayMessageField = field; // 缓存字段
                        System.out.println("[Actionbar Mixin] 通过备选方案设置 overlayMessage: " + fieldName);
                        return; // 设置第一个找到的字段
                    } catch (Exception e) {
                        System.out.println("[Actionbar Mixin] 设置字段 " + fieldName + " 时出错: " + e.getMessage());
                        // 继续尝试下一个字段
                    }
                }
            }
            System.out.println("[Actionbar Mixin] 无法找到 overlayMessage 字段");
        } catch (Exception e) {
            System.out.println("[Actionbar Mixin] setGuiOverlayMessage 异常: " + e.getMessage());
            e.printStackTrace();
            // 如果无法设置，忽略错误
        }
    }
    
    @Unique
    private boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}

