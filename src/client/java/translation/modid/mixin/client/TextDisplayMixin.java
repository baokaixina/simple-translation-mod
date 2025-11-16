package translation.modid.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;
import translation.modid.SimpleTranslation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文字显示实体 Mixin - 翻译 text_display 实体显示的文本
 */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin {
    
    @Unique
    private static final ConcurrentHashMap<String, String> textDisplayCache = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingTextDisplayTranslations = new ConcurrentHashMap<>();
    
    @Unique
    private static final Set<String> textsToUpdate = ConcurrentHashMap.newKeySet();
    
    @Unique
    private static java.lang.reflect.Field textFieldCache = null;
    
    @Unique
    private static final java.util.Map<String, java.util.Set<Display.TextDisplay>> textToDisplays = new ConcurrentHashMap<>();
    
    @Unique
    private static final java.util.Map<Display.TextDisplay, Component> displayToOriginalText = new ConcurrentHashMap<>();
    
    @Unique
    private static boolean hasLoggedFieldError = false;
    
    @Unique
    private static long lastCheckTime = 0;
    
    @Unique
    private static final long CHECK_INTERVAL = 50; // 每50ms检查一次待更新的翻译
    
    @Unique
    private static long lastForceRefreshTime = 0;
    
    @Unique
    private static final long FORCE_REFRESH_INTERVAL = 0; // 每个tick都刷新，确保及时更新
    
    /**
     * 拦截获取文本的方法，返回翻译后的文本
     */
    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void translateText(CallbackInfoReturnable<Component> cir) {
        TranslationConfig config = TranslationConfig.getInstance();
        Display.TextDisplay textDisplay = (Display.TextDisplay)(Object)this;
        Component currentText = cir.getReturnValue();
        
        // 如果翻译已关闭，检查是否有保存的原始文本，如果有则恢复
        if (!config.enabled || !config.autoTranslate || !config.translateTextDisplay) {
            Component originalText = displayToOriginalText.get(textDisplay);
            if (originalText != null) {
                // 始终返回原始文本，确保显示的是原始文本而不是翻译后的文本
                // 即使当前文本已经是原始文本，也返回原始文本以确保一致性
                cir.setReturnValue(originalText);
                return;
            }
            
            // 如果没有保存的原始文本，尝试从缓存中查找原文
            if (currentText != null) {
                String currentTextString = currentText.getString();
                if (currentTextString != null && !currentTextString.trim().isEmpty()) {
                    // 首先尝试从内存缓存中查找
                    String originalTextString = null;
                    for (java.util.Map.Entry<String, String> cacheEntry : textDisplayCache.entrySet()) {
                        if (currentTextString.equals(cacheEntry.getValue())) {
                            originalTextString = cacheEntry.getKey();
                            break;
                        }
                    }
                    
                    // 如果内存缓存中没有，从持久化缓存中查找
                    if (originalTextString == null) {
                        translation.modid.cache.TranslationCacheManager cacheManager = 
                            translation.modid.cache.TranslationCacheManager.getInstance();
                        originalTextString = cacheManager.findOriginalText(
                            translation.modid.cache.TranslationCacheManager.CacheType.OTHER, 
                            currentTextString
                        );
                    }
                    
                    // 如果找到了原文，返回原文
                    if (originalTextString != null && !originalTextString.equals(currentTextString)) {
                        MutableComponent foundOriginalText = Component.literal(originalTextString);
                        foundOriginalText.setStyle(currentText.getStyle());
                        // 保存原始文本到映射中，以便后续使用
                        displayToOriginalText.put(textDisplay, foundOriginalText);
                        cir.setReturnValue(foundOriginalText);
                        SimpleTranslation.LOGGER.debug("[TextDisplay] 从缓存恢复原始文本: '{}' -> '{}'", 
                            currentTextString, originalTextString);
                        return;
                    }
                }
            }
            
            // 如果没有找到原文，返回当前文本（可能是原始文本）
            return;
        }
        
        Component originalText = currentText;
        if (originalText == null) {
            return;
        }
        
        String text = originalText.getString();
        if (text == null || text.trim().isEmpty() || containsChinese(text)) {
            return;
        }
        
        // 记录这个 TextDisplay 实例和文本的关联
        textToDisplays.computeIfAbsent(text, k -> ConcurrentHashMap.newKeySet()).add(textDisplay);
        
        // 保存原始文本（如果还没有保存）
        if (!displayToOriginalText.containsKey(textDisplay)) {
            displayToOriginalText.put(textDisplay, originalText);
        }
        
        // 定期检查并应用待更新的翻译
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime >= CHECK_INTERVAL) {
            lastCheckTime = currentTime;
            checkAndApplyPendingTranslations();
        }
        
        // 检查缓存 - 优先检查缓存，确保翻译后的文本能够被返回
        String cachedTranslation = textDisplayCache.get(text);
        if (cachedTranslation != null) {
            MutableComponent translated = Component.literal(cachedTranslation);
            translated.setStyle(originalText.getStyle());
            cir.setReturnValue(translated);
            
            // 检查是否有待更新的翻译（说明翻译刚刚完成）
            boolean isPendingUpdate = textsToUpdate.contains(text);
            
            // 如果有待更新的翻译，强制触发更新
            if (isPendingUpdate) {
                // 在下一帧触发更新，确保渲染器重新调用 getText()
                Display.TextDisplay self = (Display.TextDisplay) (Object) this;
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        try {
                            triggerDisplayUpdate(self);
                        } catch (Exception e) {
                            // 忽略
                        }
                    });
                }
            }
            
            textsToUpdate.remove(text); // 已应用翻译，移除标记
            SimpleTranslation.LOGGER.debug("[TextDisplay] getText() 返回翻译后的文本: '{}' -> '{}'", text, cachedTranslation);
            return;
        }
        
        // 检查是否有待更新的翻译
        if (textsToUpdate.contains(text)) {
            // 有新的翻译可用，重新检查缓存
            cachedTranslation = textDisplayCache.get(text);
            if (cachedTranslation != null) {
                MutableComponent translated = Component.literal(cachedTranslation);
                translated.setStyle(originalText.getStyle());
                cir.setReturnValue(translated);
                textsToUpdate.remove(text);
                return;
            }
        }
        
        // 检查是否正在翻译
        Long pendingTime = pendingTextDisplayTranslations.get(text);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            // 正在翻译中，返回原文
            return;
        }
        
        // 开始异步翻译
        pendingTextDisplayTranslations.put(text, System.currentTimeMillis());
        TranslationManager.getInstance().translate(text)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                        textDisplayCache.put(text, translated);
                        SimpleTranslation.LOGGER.info("[TextDisplay] 翻译完成: '{}' -> '{}'", text, translated);
                        // 在主线程中标记需要更新，并尝试直接更新所有相关的 TextDisplay 实例
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null) {
                            mc.execute(() -> {
                                textsToUpdate.add(text);
                                
                                // 获取所有相关的 TextDisplay 实例（已经在 getText() 调用时记录）
                                // 当 TextDisplay 被渲染时，getText() 会被调用，我们会在那时记录实体
                                java.util.Set<Display.TextDisplay> displays = textToDisplays.get(text);
                                SimpleTranslation.LOGGER.info("[TextDisplay] 标记文本待更新: '{}'，相关TextDisplay数量: {}", 
                                    text, displays != null ? displays.size() : 0);
                                
                                // 直接修改所有相关的 TextDisplay 实例的文本（不删除实体）
                                if (displays != null && !displays.isEmpty()) {
                                    MutableComponent translatedComponent = Component.literal(translated);
                                    translatedComponent.setStyle(originalText.getStyle());
                                    int updatedCount = 0;
                                    
                                    for (Display.TextDisplay display : displays) {
                                        try {
                                            net.minecraft.world.level.Level level = display.level();
                                            
                                            if (level == null || level.isClientSide == false) {
                                                // 只在客户端处理
                                                continue;
                                            }
                                            
                                            // 检查实体是否仍然存在
                                            if (display.isRemoved() || !display.isAlive()) {
                                                SimpleTranslation.LOGGER.debug("[TextDisplay] 实体已被移除，跳过: {}", text);
                                                continue;
                                            }
                                            
                                            // 通过反射直接修改实体的文本字段
                                            boolean success = false;
                                            try {
                                                // 查找 DisplayData 字段并修改其中的文本字段
                                                java.lang.reflect.Field[] fields = Display.class.getDeclaredFields();
                                                for (java.lang.reflect.Field field : fields) {
                                                    String typeName = field.getType().getName();
                                                    if (typeName.contains("TextDisplayData")) {
                                                        field.setAccessible(true);
                                                        Object displayData = field.get(display);
                                                        if (displayData != null) {
                                                            // 在 DisplayData 中查找文本字段
                                                            java.lang.reflect.Field[] dataFields = displayData.getClass().getDeclaredFields();
                                                            for (java.lang.reflect.Field dataField : dataFields) {
                                                                if (Component.class.isAssignableFrom(dataField.getType())) {
                                                                    try {
                                                                        dataField.setAccessible(true);
                                                                        // 尝试移除 final 修饰符
                                                                        try {
                                                                            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                                                                            modifiersField.setAccessible(true);
                                                                            modifiersField.setInt(dataField, dataField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                                                                        } catch (Exception eMod) {
                                                                            // 在某些 Java 版本中，modifiers 字段可能不存在或无法访问
                                                                            SimpleTranslation.LOGGER.debug("[TextDisplay] 无法移除 final 修饰符: {}", eMod.getMessage());
                                                                        }
                                                                        
                                                                        // 保存原始文本（如果还没有保存）
                                                                        if (!displayToOriginalText.containsKey(display)) {
                                                                            try {
                                                                                Component dataText = (Component) dataField.get(displayData);
                                                                                if (dataText != null) {
                                                                                    displayToOriginalText.put(display, dataText);
                                                                                } else {
                                                                                    displayToOriginalText.put(display, originalText);
                                                                                }
                                                                            } catch (Exception eSave) {
                                                                                displayToOriginalText.put(display, originalText);
                                                                            }
                                                                        }
                                                                        
                                                                        // 设置新的文本
                                                                        dataField.set(displayData, translatedComponent);
                                                                        success = true;
                                                                        SimpleTranslation.LOGGER.debug("[TextDisplay] 通过反射成功设置文本: '{}' -> '{}'", text, translated);
                                                                        break;
                                                                    } catch (Exception e2) {
                                                                        SimpleTranslation.LOGGER.debug("[TextDisplay] 无法通过反射设置文本字段: {}", e2.getMessage());
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        break;
                                                    }
                                                }
                                            } catch (Exception e) {
                                                SimpleTranslation.LOGGER.warn("[TextDisplay] 反射设置文本失败: {}", e.getMessage());
                                            }
                                            
                                            // 如果反射失败，尝试通过 NBT 方式更新
                                            if (!success) {
                                                try {
                                                    // 保存当前 NBT
                                                    net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                                                    display.saveWithoutId(nbt);
                                                    
                                                    // 保存原始文本（如果还没有保存）
                                                    if (!displayToOriginalText.containsKey(display)) {
                                                        try {
                                                            // 从 NBT 中读取原始文本
                                                            if (nbt.contains("text")) {
                                                                String textJson = nbt.getString("text");
                                                                Component originalFromNbt = net.minecraft.network.chat.Component.Serializer.fromJson(textJson);
                                                                if (originalFromNbt != null) {
                                                                    displayToOriginalText.put(display, originalFromNbt);
                                                                } else {
                                                                    displayToOriginalText.put(display, originalText);
                                                                }
                                                            } else {
                                                                displayToOriginalText.put(display, originalText);
                                                            }
                                                        } catch (Exception eSave) {
                                                            displayToOriginalText.put(display, originalText);
                                                        }
                                                    }
                                                    
                                                    // 更新文本
                                                    try {
                                                        com.google.gson.JsonElement jsonElement = net.minecraft.network.chat.Component.Serializer.toJsonTree(translatedComponent);
                                                        com.google.gson.Gson gson = new com.google.gson.Gson();
                                                        String jsonString = gson.toJson(jsonElement);
                                                        if (nbt.contains("text")) {
                                                            nbt.remove("text");
                                                        }
                                                        nbt.putString("text", jsonString);
                                                        
                                                        // 重新加载 NBT
                                                        display.load(nbt);
                                                        success = true;
                                                        SimpleTranslation.LOGGER.debug("[TextDisplay] 通过 NBT 成功更新文本: '{}' -> '{}'", text, translated);
                                                    } catch (Exception e3) {
                                                        SimpleTranslation.LOGGER.warn("[TextDisplay] 通过 NBT 更新文本失败: {}", e3.getMessage());
                                                    }
                                                } catch (Exception e) {
                                                    SimpleTranslation.LOGGER.warn("[TextDisplay] 保存/加载 NBT 失败: {}", e.getMessage());
                                                }
                                            }
                                            
                                            // 验证文本是否已更新
                                            if (success) {
                                                try {
                                                    java.lang.reflect.Method getTextMethod = Display.TextDisplay.class.getDeclaredMethod("getText");
                                                    getTextMethod.setAccessible(true);
                                                    Component result = (Component) getTextMethod.invoke(display);
                                                    if (result != null) {
                                                        SimpleTranslation.LOGGER.debug("[TextDisplay] 验证: getText() 返回: '{}'", result.getString());
                                                    }
                                                } catch (Exception e) {
                                                    // 忽略验证错误
                                                }
                                                
                                                updatedCount++;
                                                net.minecraft.world.phys.Vec3 pos = display.position();
                                                SimpleTranslation.LOGGER.info("[TextDisplay] 已更新实体文本: '{}' -> '{}' (位置: {}, {}, {})", 
                                                    text, translated, pos.x, pos.y, pos.z);
                                            } else {
                                                SimpleTranslation.LOGGER.warn("[TextDisplay] 无法更新实体文本: {}", text);
                                            }
                                        } catch (Exception e) {
                                            SimpleTranslation.LOGGER.error("[TextDisplay] 更新实体文本时出错: {}", e.getMessage(), e);
                                        }
                                    }
                                    
                                    SimpleTranslation.LOGGER.info("[TextDisplay] 已更新 {} 个TextDisplay实体的文本", updatedCount);
                                    
                                    // 移除待更新标记，因为已经更新了实体文本
                                    textsToUpdate.remove(text);
                                } else {
                                    SimpleTranslation.LOGGER.warn("[TextDisplay] 没有找到相关的TextDisplay实例来更新文本: '{}'。提示：实体可能还没有被渲染，getText() 还没有被调用。", text);
                                }
                            });
                        }
                    }
                    pendingTextDisplayTranslations.remove(text);
                });
        
        // 返回原文，等待翻译完成
        cir.setReturnValue(originalText);
    }
    
    /**
     * 检查并应用待更新的翻译
     */
    @Unique
    private static void checkAndApplyPendingTranslations() {
        if (textsToUpdate.isEmpty()) {
            return;
        }
        
        // 遍历所有待更新的文本，尝试更新对应的 TextDisplay 实例
        java.util.Iterator<String> iterator = textsToUpdate.iterator();
        while (iterator.hasNext()) {
            String text = iterator.next();
            String cachedTranslation = textDisplayCache.get(text);
            if (cachedTranslation != null) {
                java.util.Set<Display.TextDisplay> displays = textToDisplays.get(text);
                if (displays != null) {
                    // 创建副本以避免并发修改异常
                    java.util.List<Display.TextDisplay> displayList = new java.util.ArrayList<>(displays);
                    for (Display.TextDisplay display : displayList) {
                        // 尝试更新字段以触发重新渲染
                        try {
                            // 直接使用缓存的翻译创建Component，避免调用getText()导致递归
                            MutableComponent translatedComponent = Component.literal(cachedTranslation);
                            updateTextDisplayField(display, translatedComponent);
                            triggerDisplayUpdate(display);
                        } catch (Exception e) {
                            // 忽略异常
                        }
                    }
                }
            }
        }
    }
    
    @Unique
    private static java.lang.reflect.Field displayDataFieldCache = null;
    
    @Unique
    private static boolean updateTextDisplayField(Display.TextDisplay textDisplay, MutableComponent translatedComponent) {
        // 尝试使用反射更新 TextDisplay 的文本字段
        // TextDisplay 的文本可能存储在 DisplayData 中，而不是直接在 TextDisplay 类中
        try {
            // 首先尝试更新 DisplayData 中的文本字段
            if (updateDisplayDataText(textDisplay, translatedComponent)) {
                return true;
            }
            
            // 如果已经找到字段，直接使用
            if (textFieldCache != null) {
                try {
                    textFieldCache.set(textDisplay, translatedComponent);
                    if (!hasLoggedFieldError) {
                        SimpleTranslation.LOGGER.info("[TextDisplay] 成功更新文本字段");
                    }
                    return true;
                } catch (Exception e) {
                    // 字段可能失效，重新查找
                    textFieldCache = null;
                    if (!hasLoggedFieldError) {
                        SimpleTranslation.LOGGER.warn("[TextDisplay] 字段更新失败，重新查找: {}", e.getMessage());
                    }
                }
            }
            
            // 尝试查找文本字段
            String[] possibleFieldNames = {"text", "textComponent", "message", "component", "content", "f_", "field_"};
            for (String fieldName : possibleFieldNames) {
                try {
                    java.lang.reflect.Field field = Display.TextDisplay.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    if (Component.class.isAssignableFrom(field.getType())) {
                        field.set(textDisplay, translatedComponent);
                        textFieldCache = field; // 缓存字段
                        if (!hasLoggedFieldError) {
                            SimpleTranslation.LOGGER.info("[TextDisplay] 成功找到文本字段: {}", fieldName);
                            hasLoggedFieldError = true;
                        }
                        return true;
                    }
                } catch (NoSuchFieldException e) {
                    // 继续尝试下一个字段名
                } catch (IllegalAccessException e) {
                    // 字段可能是final的，无法修改
                    if (!hasLoggedFieldError) {
                        SimpleTranslation.LOGGER.warn("[TextDisplay] 字段 {} 可能是final的，无法修改: {}", fieldName, e.getMessage());
                    }
                } catch (Exception e) {
                    // 忽略其他异常
                }
            }
            
            // 如果常见字段名都失败，遍历所有字段查找 Component 类型
            try {
                java.lang.reflect.Field[] fields = Display.TextDisplay.class.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    if (Component.class.isAssignableFrom(field.getType())) {
                        try {
                            field.setAccessible(true);
                            field.set(textDisplay, translatedComponent);
                            textFieldCache = field; // 缓存字段
                            if (!hasLoggedFieldError) {
                                SimpleTranslation.LOGGER.info("[TextDisplay] 通过遍历找到文本字段: {}", field.getName());
                                hasLoggedFieldError = true;
                            }
                            return true;
                        } catch (IllegalAccessException e) {
                            // 字段可能是final的，无法修改
                            if (!hasLoggedFieldError) {
                                SimpleTranslation.LOGGER.warn("[TextDisplay] 字段 {} 可能是final的，无法修改: {}", field.getName(), e.getMessage());
                            }
                        } catch (Exception e) {
                            // 继续尝试下一个字段
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略异常
            }
            
            if (!hasLoggedFieldError) {
                SimpleTranslation.LOGGER.warn("[TextDisplay] 无法找到或修改文本字段，将依赖getText()方法返回翻译");
                hasLoggedFieldError = true;
            }
        } catch (Exception e) {
            if (!hasLoggedFieldError) {
                SimpleTranslation.LOGGER.error("[TextDisplay] 更新文本字段时出错: {}", e.getMessage());
                hasLoggedFieldError = true;
            }
        }
        return false;
    }
    
    /**
     * 尝试更新 DisplayData 中的文本字段
     * TextDisplay 的文本通常存储在 Display.TextDisplay.TextDisplayData 中
     */
    @Unique
    private static boolean updateDisplayDataText(Display.TextDisplay textDisplay, MutableComponent translatedComponent) {
        try {
            // 首先找到 Display 的 data 字段
            if (displayDataFieldCache == null) {
                java.lang.reflect.Field[] fields = Display.class.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    String fieldName = field.getName();
                    String typeName = field.getType().getName();
                    // 查找 DisplayData 类型的字段
                    if (typeName.contains("DisplayData") || 
                        fieldName.equals("data") || 
                        fieldName.equals("displayData") ||
                        fieldName.equals("f_") || 
                        fieldName.startsWith("field_")) {
                        try {
                            field.setAccessible(true);
                            Object data = field.get(textDisplay);
                            if (data != null && typeName.contains("TextDisplayData")) {
                                displayDataFieldCache = field;
                                break;
                            }
                        } catch (Exception e) {
                            // 继续尝试
                        }
                    }
                }
            }
            
            if (displayDataFieldCache != null) {
                try {
                    Object displayData = displayDataFieldCache.get(textDisplay);
                    if (displayData != null) {
                        // 在 DisplayData 中查找文本字段
                        java.lang.reflect.Field[] dataFields = displayData.getClass().getDeclaredFields();
                        for (java.lang.reflect.Field field : dataFields) {
                            if (Component.class.isAssignableFrom(field.getType())) {
                                try {
                                    field.setAccessible(true);
                                    field.set(displayData, translatedComponent);
                                    if (!hasLoggedFieldError) {
                                        SimpleTranslation.LOGGER.info("[TextDisplay] 成功更新 DisplayData 中的文本字段: {}", field.getName());
                                        hasLoggedFieldError = true;
                                    }
                                    return true;
                                } catch (IllegalAccessException e) {
                                    // 字段可能是final的，尝试使用 Unsafe 或其他方法
                                } catch (Exception e) {
                                    // 继续尝试
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略异常
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return false;
    }
    
    @Unique
    private static void triggerDisplayUpdate(Display.TextDisplay textDisplay) {
        // 尝试通过修改Display的数据来触发更新
        // 由于 TextDisplay 是实体，我们需要强制触发重新渲染
        try {
            // 方法1: 通过修改实体的tickCount来强制更新（最可靠的方法）
            // 这会强制实体在下一帧重新渲染，从而重新调用 getText()
            try {
                java.lang.reflect.Field tickCountField = net.minecraft.world.entity.Entity.class.getDeclaredField("tickCount");
                tickCountField.setAccessible(true);
                int currentTick = tickCountField.getInt(textDisplay);
                // 修改tickCount来触发更新
                tickCountField.setInt(textDisplay, currentTick - 1);
                tickCountField.setInt(textDisplay, currentTick);
            } catch (Exception e) {
                // 如果tickCount修改失败，尝试其他方法
            }
            
            // 方法2: 通过微调实体的位置来强制重新渲染
            // 使用稍大的偏移量以确保触发更新（虽然会短暂移动，但立即恢复）
            try {
                net.minecraft.world.phys.Vec3 pos = textDisplay.position();
                // 使用较大的偏移量（0.01格），确保触发更新
                // 这个偏移量足够大以触发更新，但足够小以至于玩家几乎看不到
                textDisplay.setPos(pos.x + 0.01, pos.y, pos.z);
                // 在下一帧恢复位置（通过延迟执行）
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        try {
                            textDisplay.setPos(pos.x, pos.y, pos.z);
                        } catch (Exception e) {
                            // 忽略
                        }
                    });
                } else {
                    // 如果无法获取Minecraft实例，立即恢复
                    textDisplay.setPos(pos.x, pos.y, pos.z);
                }
            } catch (Exception e) {
                // 如果位置修改失败，尝试旋转角度
                try {
                    float yRot = textDisplay.getYRot();
                    // 使用稍大的旋转角度（0.1度），确保触发更新
                    textDisplay.setYRot(yRot + 0.1f);
                    // 在下一帧恢复
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null) {
                        mc.execute(() -> {
                            try {
                                textDisplay.setYRot(yRot);
                            } catch (Exception e2) {
                                // 忽略
                            }
                        });
                    } else {
                        textDisplay.setYRot(yRot);
                    }
                } catch (Exception e2) {
                    // 忽略
                }
            }
            
            // 方法3: 尝试通过Entity的setChanged方法来触发更新
            try {
                java.lang.reflect.Method setChanged = net.minecraft.world.entity.Entity.class.getDeclaredMethod("setChanged");
                setChanged.setAccessible(true);
                setChanged.invoke(textDisplay);
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法4: 尝试调用setChanged()或类似的方法来触发数据同步
            java.lang.reflect.Method[] methods = Display.TextDisplay.class.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                if (methodName.equals("setChanged") || methodName.equals("markDirty") || 
                    methodName.equals("setDirty") || methodName.contains("sync") ||
                    methodName.equals("method_5777") || methodName.equals("method_5778")) {
                    try {
                        method.setAccessible(true);
                        if (method.getParameterCount() == 0) {
                            method.invoke(textDisplay);
                        } else if (method.getParameterCount() == 1 && 
                                   method.getParameterTypes()[0] == boolean.class) {
                            method.invoke(textDisplay, true);
                        }
                    } catch (Exception e) {
                        // 继续尝试
                    }
                }
            }
            
            // 方法5: 尝试通过修改Display的data来触发更新
            try {
                if (displayDataFieldCache != null) {
                    Object data = displayDataFieldCache.get(textDisplay);
                    if (data != null) {
                        // 尝试触发数据更新
                        java.lang.reflect.Method[] dataMethods = data.getClass().getDeclaredMethods();
                        for (java.lang.reflect.Method method : dataMethods) {
                            String methodName = method.getName();
                            if (methodName.equals("setChanged") || 
                                methodName.equals("markDirty") ||
                                methodName.contains("sync") ||
                                methodName.contains("update")) {
                                try {
                                    method.setAccessible(true);
                                    if (method.getParameterCount() == 0) {
                                        method.invoke(data);
                                    } else if (method.getParameterCount() == 1 && 
                                               method.getParameterTypes()[0] == boolean.class) {
                                        method.invoke(data, true);
                                    }
                                } catch (Exception e) {
                                    // 继续尝试
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法5: 尝试通过修改实体的 tickCount 来触发更新
            try {
                java.lang.reflect.Field tickCountField = net.minecraft.world.entity.Entity.class.getDeclaredField("tickCount");
                tickCountField.setAccessible(true);
                int tickCount = tickCountField.getInt(textDisplay);
                tickCountField.setInt(textDisplay, tickCount + 1);
                tickCountField.setInt(textDisplay, tickCount); // 恢复
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法6: 尝试通过修改实体的 ID 来触发更新（这会强制重新同步）
            // 注意：这个方法可能会影响实体ID，所以我们要小心
            try {
                // 先尝试修改 entityData 来触发更新
                java.lang.reflect.Field entityDataField = net.minecraft.world.entity.Entity.class.getDeclaredField("entityData");
                entityDataField.setAccessible(true);
                Object entityData = entityDataField.get(textDisplay);
                if (entityData != null) {
                    // 尝试触发数据更新
                    java.lang.reflect.Method setMethod = entityData.getClass().getDeclaredMethod("set", 
                        net.minecraft.network.syncher.EntityDataAccessor.class, Object.class);
                    if (setMethod != null) {
                        // 查找文本相关的数据访问器
                        java.lang.reflect.Field[] fields = Display.TextDisplay.class.getDeclaredFields();
                        for (java.lang.reflect.Field field : fields) {
                            if (field.getType().getName().contains("EntityDataAccessor")) {
                                try {
                                    field.setAccessible(true);
                                    Object accessor = field.get(null);
                                    if (accessor != null) {
                                        // 获取当前值
                                        java.lang.reflect.Method getMethod = entityData.getClass().getDeclaredMethod("get", 
                                            net.minecraft.network.syncher.EntityDataAccessor.class);
                                        if (getMethod != null) {
                                            Object currentValue = getMethod.invoke(entityData, accessor);
                                            // 重新设置相同的值来触发更新
                                            setMethod.invoke(entityData, accessor, currentValue);
                                        }
                                    }
                                } catch (Exception e) {
                                    // 继续尝试
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    /**
     * 强制刷新所有待更新的 TextDisplay
     * 这个方法应该在客户端 tick 事件中定期调用
     * 注意：必须是私有的，因为 Mixin 不允许非私有的静态方法
     */
    @Unique
    private static void forceRefreshPendingTextDisplays() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastForceRefreshTime < FORCE_REFRESH_INTERVAL) {
            return;
        }
        lastForceRefreshTime = currentTime;
        
        if (textsToUpdate.isEmpty()) {
            return;
        }
        
        // 遍历所有待更新的文本，强制刷新对应的 TextDisplay 实例
        java.util.Iterator<String> iterator = textsToUpdate.iterator();
        while (iterator.hasNext()) {
            String text = iterator.next();
            String cachedTranslation = textDisplayCache.get(text);
            if (cachedTranslation != null) {
                java.util.Set<Display.TextDisplay> displays = textToDisplays.get(text);
                if (displays != null && !displays.isEmpty()) {
                    // 创建副本以避免并发修改异常
                    java.util.List<Display.TextDisplay> displayList = new java.util.ArrayList<>(displays);
                    for (Display.TextDisplay display : displayList) {
                        try {
                            // 尝试通过反射调用 getText() 来触发重新渲染
                            // 这会触发我们的 mixin，返回翻译后的文本
                            try {
                                java.lang.reflect.Method getTextMethod = Display.TextDisplay.class.getDeclaredMethod("getText");
                                getTextMethod.setAccessible(true);
                                getTextMethod.invoke(display);
                            } catch (Exception e) {
                                // 如果反射失败，继续尝试其他方法
                            }
                            
                            // 同时尝试更新字段和触发更新
                            MutableComponent translatedComponent = Component.literal(cachedTranslation);
                            updateTextDisplayField(display, translatedComponent);
                            triggerDisplayUpdate(display);
                        } catch (Exception e) {
                            // 忽略异常
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 恢复所有 TextDisplay 实体的原始文本
     * 当关闭翻译功能时调用
     */
    @Unique
    private static void restoreAllOriginalTexts() {
        int restoredCount = 0;
        int restoredFromCache = 0;
        
        // 获取缓存管理器
        translation.modid.cache.TranslationCacheManager cacheManager = 
            translation.modid.cache.TranslationCacheManager.getInstance();
        
        // 第一步：恢复有保存原始文本的实体
        java.util.Iterator<java.util.Map.Entry<Display.TextDisplay, Component>> iterator = 
            displayToOriginalText.entrySet().iterator();
        
        while (iterator.hasNext()) {
            java.util.Map.Entry<Display.TextDisplay, Component> entry = iterator.next();
            Display.TextDisplay display = entry.getKey();
            Component originalText = entry.getValue();
            
            try {
                // 检查实体是否仍然存在
                if (display.isRemoved() || !display.isAlive()) {
                    displayToOriginalText.remove(display);
                    continue;
                }
                
                net.minecraft.world.level.Level level = display.level();
                if (level == null || level.isClientSide == false) {
                    continue;
                }
                
                // 恢复原始文本
                boolean success = restoreTextDisplayText(display, originalText);
                if (success) {
                    restoredCount++;
                }
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("[TextDisplay] 恢复原始文本时出错: {}", e.getMessage(), e);
            }
        }
        
        // 第二步：对于没有保存原始文本的实体，尝试从缓存中查找原文
        // 遍历所有文本到实体的映射
        for (java.util.Map.Entry<String, java.util.Set<Display.TextDisplay>> entry : textToDisplays.entrySet()) {
            java.util.Set<Display.TextDisplay> displays = entry.getValue();
            
            if (displays == null || displays.isEmpty()) {
                continue;
            }
            
            // 创建副本以避免并发修改异常
            java.util.List<Display.TextDisplay> displayList = new java.util.ArrayList<>(displays);
            
            for (Display.TextDisplay display : displayList) {
                try {
                    // 检查实体是否仍然存在
                    if (display.isRemoved() || !display.isAlive()) {
                        continue;
                    }
                    
                    net.minecraft.world.level.Level level = display.level();
                    if (level == null || level.isClientSide == false) {
                        continue;
                    }
                    
                    // 如果已经有保存的原始文本，跳过（已在第一步处理）
                    if (displayToOriginalText.containsKey(display)) {
                        continue;
                    }
                    
                    // 获取当前显示的文本（可能是译文）
                    Component currentText = null;
                    try {
                        java.lang.reflect.Method getTextMethod = Display.TextDisplay.class.getDeclaredMethod("getText");
                        getTextMethod.setAccessible(true);
                        currentText = (Component) getTextMethod.invoke(display);
                    } catch (Exception e) {
                        // 如果反射失败，尝试直接获取字段
                        try {
                            java.lang.reflect.Field[] fields = Display.class.getDeclaredFields();
                            for (java.lang.reflect.Field field : fields) {
                                String typeName = field.getType().getName();
                                if (typeName.contains("TextDisplayData")) {
                                    field.setAccessible(true);
                                    Object displayData = field.get(display);
                                    if (displayData != null) {
                                        java.lang.reflect.Field[] dataFields = displayData.getClass().getDeclaredFields();
                                        for (java.lang.reflect.Field dataField : dataFields) {
                                            if (Component.class.isAssignableFrom(dataField.getType())) {
                                                dataField.setAccessible(true);
                                                currentText = (Component) dataField.get(displayData);
                                                break;
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        } catch (Exception e2) {
                            // 忽略
                        }
                    }
                    
                    if (currentText == null) {
                        continue;
                    }
                    
                    String currentTextString = currentText.getString();
                    if (currentTextString == null || currentTextString.trim().isEmpty()) {
                        continue;
                    }
                    
                    // 从缓存中查找原文
                    // 首先尝试从 textDisplayCache 中查找（内存缓存）
                    String originalTextString = null;
                    for (java.util.Map.Entry<String, String> cacheEntry : textDisplayCache.entrySet()) {
                        if (currentTextString.equals(cacheEntry.getValue())) {
                            originalTextString = cacheEntry.getKey();
                            break;
                        }
                    }
                    
                    // 如果内存缓存中没有，从持久化缓存中查找
                    if (originalTextString == null) {
                        originalTextString = cacheManager.findOriginalText(
                            translation.modid.cache.TranslationCacheManager.CacheType.OTHER, 
                            currentTextString
                        );
                    }
                    
                    // 如果找到了原文，恢复它
                    if (originalTextString != null && !originalTextString.equals(currentTextString)) {
                        MutableComponent originalText = Component.literal(originalTextString);
                        originalText.setStyle(currentText.getStyle());
                        
                        // 保存原始文本到映射中
                        displayToOriginalText.put(display, originalText);
                        
                        // 恢复原始文本
                        boolean success = restoreTextDisplayText(display, originalText);
                        if (success) {
                            restoredCount++;
                            restoredFromCache++;
                            SimpleTranslation.LOGGER.debug("[TextDisplay] 从缓存恢复原始文本: '{}' -> '{}'", 
                                currentTextString, originalTextString);
                        }
                    }
                } catch (Exception e) {
                    SimpleTranslation.LOGGER.warn("[TextDisplay] 从缓存恢复原始文本时出错: {}", e.getMessage());
                }
            }
        }
        
        if (restoredCount > 0) {
            SimpleTranslation.LOGGER.info("[TextDisplay] 已恢复 {} 个TextDisplay实体的原始文本（其中 {} 个从缓存中恢复）", 
                restoredCount, restoredFromCache);
        }
        
        // 不要清除映射，保留原始文本以便后续比较和恢复
        // 只有在实体被移除时才清除对应的映射
    }
    
    /**
     * 恢复单个 TextDisplay 实体的文本
     * @param display TextDisplay 实体
     * @param originalText 要恢复的原始文本
     * @return 是否成功恢复
     */
    @Unique
    private static boolean restoreTextDisplayText(Display.TextDisplay display, Component originalText) {
        try {
            net.minecraft.world.level.Level level = display.level();
            if (level == null || level.isClientSide == false) {
                return false;
            }
            
            // 通过反射恢复原始文本
            boolean success = false;
            try {
                // 查找 DisplayData 字段并恢复其中的文本字段
                java.lang.reflect.Field[] fields = Display.class.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    String typeName = field.getType().getName();
                    if (typeName.contains("TextDisplayData")) {
                        field.setAccessible(true);
                        Object displayData = field.get(display);
                        if (displayData != null) {
                            // 在 DisplayData 中查找文本字段
                            java.lang.reflect.Field[] dataFields = displayData.getClass().getDeclaredFields();
                            for (java.lang.reflect.Field dataField : dataFields) {
                                if (Component.class.isAssignableFrom(dataField.getType())) {
                                    try {
                                        dataField.setAccessible(true);
                                        // 尝试移除 final 修饰符
                                        try {
                                            java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                                            modifiersField.setAccessible(true);
                                            modifiersField.setInt(dataField, dataField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                                        } catch (Exception eMod) {
                                            // 忽略
                                        }
                                        
                                        // 恢复原始文本
                                        dataField.set(displayData, originalText);
                                        success = true;
                                        SimpleTranslation.LOGGER.debug("[TextDisplay] 通过反射成功恢复原始文本: '{}'", originalText.getString());
                                        break;
                                    } catch (Exception e2) {
                                        // 继续尝试
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                SimpleTranslation.LOGGER.warn("[TextDisplay] 反射恢复文本失败: {}", e.getMessage());
            }
            
            // 如果反射失败，尝试通过 NBT 方式恢复
            if (!success) {
                try {
                    // 保存当前 NBT
                    net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                    display.saveWithoutId(nbt);
                    
                    // 恢复原始文本
                    try {
                        com.google.gson.JsonElement jsonElement = net.minecraft.network.chat.Component.Serializer.toJsonTree(originalText);
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String jsonString = gson.toJson(jsonElement);
                        if (nbt.contains("text")) {
                            nbt.remove("text");
                        }
                        nbt.putString("text", jsonString);
                        
                        // 重新加载 NBT
                        display.load(nbt);
                        success = true;
                        SimpleTranslation.LOGGER.debug("[TextDisplay] 通过 NBT 成功恢复原始文本: '{}'", originalText.getString());
                    } catch (Exception e3) {
                        SimpleTranslation.LOGGER.warn("[TextDisplay] 通过 NBT 恢复文本失败: {}", e3.getMessage());
                    }
                } catch (Exception e) {
                    SimpleTranslation.LOGGER.warn("[TextDisplay] 保存/加载 NBT 失败: {}", e.getMessage());
                }
            }
            
            if (success) {
                // 触发更新，强制重新渲染
                triggerDisplayUpdate(display);
                
                // 额外强制触发：通过调用 getText() 来确保翻译逻辑被执行
                try {
                    java.lang.reflect.Method getTextMethod = Display.TextDisplay.class.getDeclaredMethod("getText");
                    getTextMethod.setAccessible(true);
                    getTextMethod.invoke(display);
                } catch (Exception e) {
                    // 忽略
                }
            }
            
            return success;
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("[TextDisplay] 恢复原始文本时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Unique
    private boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
    
    /**
     * 强制刷新所有 TextDisplay 实体，触发重新翻译
     * 当翻译功能从关闭变为开启时调用
     */
    @Unique
    private static void forceRefreshAllTextDisplays() {
        int refreshedCount = 0;
        java.util.Set<Display.TextDisplay> processedDisplays = new java.util.HashSet<>();
        
        try {
            // 获取当前世界的所有实体
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return;
            }
            
            // 遍历所有已注册的文字显示实体
            for (java.util.Map.Entry<String, java.util.Set<Display.TextDisplay>> entry : textToDisplays.entrySet()) {
                java.util.Set<Display.TextDisplay> displays = entry.getValue();
                if (displays == null || displays.isEmpty()) {
                    continue;
                }
                
                // 创建副本以避免并发修改异常
                java.util.List<Display.TextDisplay> displayList = new java.util.ArrayList<>(displays);
                
                for (Display.TextDisplay display : displayList) {
                    try {
                        // 检查实体是否仍然存在
                        if (display.isRemoved() || !display.isAlive()) {
                            continue;
                        }
                        
                        net.minecraft.world.level.Level level = display.level();
                        if (level == null || level.isClientSide == false) {
                            continue;
                        }
                        
                        // 避免重复处理
                        if (processedDisplays.contains(display)) {
                            continue;
                        }
                        processedDisplays.add(display);
                        
                        // 直接调用 getText() 方法，这会触发 mixin 并重新翻译
                        try {
                            java.lang.reflect.Method getTextMethod = Display.TextDisplay.class.getDeclaredMethod("getText");
                            getTextMethod.setAccessible(true);
                            getTextMethod.invoke(display);
                        } catch (Exception e) {
                            // 如果反射失败，使用 triggerDisplayUpdate
                            triggerDisplayUpdate(display);
                        }
                        
                        refreshedCount++;
                    } catch (Exception e) {
                        // 忽略单个实体的错误
                    }
                }
            }
            
            // 同时遍历所有保存了原始文本的实体
            for (java.util.Map.Entry<Display.TextDisplay, Component> entry : displayToOriginalText.entrySet()) {
                Display.TextDisplay display = entry.getKey();
                
                try {
                    // 检查实体是否仍然存在
                    if (display.isRemoved() || !display.isAlive()) {
                        continue;
                    }
                    
                    net.minecraft.world.level.Level level = display.level();
                    if (level == null || level.isClientSide == false) {
                        continue;
                    }
                    
                    // 避免重复处理
                    if (processedDisplays.contains(display)) {
                        continue;
                    }
                    processedDisplays.add(display);
                    
                    // 直接调用 getText() 方法，这会触发 mixin 并重新翻译
                    try {
                        java.lang.reflect.Method getTextMethod = Display.TextDisplay.class.getDeclaredMethod("getText");
                        getTextMethod.setAccessible(true);
                        getTextMethod.invoke(display);
                    } catch (Exception e) {
                        // 如果反射失败，使用 triggerDisplayUpdate
                        triggerDisplayUpdate(display);
                    }
                } catch (Exception e) {
                    // 忽略单个实体的错误
                }
            }
            
            // 最后，强制触发所有实体的显示更新
            for (Display.TextDisplay display : processedDisplays) {
                try {
                    triggerDisplayUpdate(display);
                } catch (Exception e) {
                    // 忽略
                }
            }
            
            SimpleTranslation.LOGGER.info("[TextDisplay] 强制刷新了 {} 个文字显示实体以触发重新翻译", refreshedCount);
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("[TextDisplay] 强制刷新所有文字显示实体时出错: {}", e.getMessage(), e);
        }
    }
}

