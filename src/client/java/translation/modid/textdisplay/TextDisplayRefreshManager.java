package translation.modid.textdisplay;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Display;
import translation.modid.SimpleTranslation;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TextDisplay 刷新管理器
 * 负责定期刷新待更新的 TextDisplay 实体
 */
public class TextDisplayRefreshManager {
    private static final TextDisplayRefreshManager INSTANCE = new TextDisplayRefreshManager();
    
    private long lastForceRefreshTime = 0;
    private static final long FORCE_REFRESH_INTERVAL = 0; // 每个tick都刷新，确保及时更新
    
    // 缓存字段引用，避免每帧都重新查找
    private java.lang.reflect.Field cachedTextsToUpdateField = null;
    private java.lang.reflect.Field cachedTextDisplayCacheField = null;
    private java.lang.reflect.Field cachedTextToDisplaysField = null;
    private boolean fieldsInitialized = false;
    
    // 记录每个文本的刷新次数，避免无限刷新
    private final java.util.Map<String, Integer> refreshCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_REFRESH_ATTEMPTS = 10; // 最多尝试10次刷新
    
    private TextDisplayRefreshManager() {
    }
    
    public static TextDisplayRefreshManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 强制刷新所有待更新的 TextDisplay
     * 这个方法应该在客户端 tick 事件中定期调用
     * 通过反射访问 Mixin 注入到 Display.TextDisplay 类中的静态字段
     */
    @SuppressWarnings("unchecked")
    public void forceRefreshPendingTextDisplays() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastForceRefreshTime < FORCE_REFRESH_INTERVAL) {
            return;
        }
        lastForceRefreshTime = currentTime;
        
        // 如果字段还未初始化，先初始化字段缓存
        if (!fieldsInitialized) {
            initializeFields();
        }
        
        // 如果字段初始化失败，直接返回
        if (cachedTextsToUpdateField == null || cachedTextDisplayCacheField == null || cachedTextToDisplaysField == null) {
            return;
        }
        
        try {
            // 使用缓存的字段引用执行刷新逻辑
            Set<String> textsToUpdate = (Set<String>) cachedTextsToUpdateField.get(null);
            Map<String, String> textDisplayCache = (Map<String, String>) cachedTextDisplayCacheField.get(null);
            Map<String, Set<Display.TextDisplay>> textToDisplays = (Map<String, Set<Display.TextDisplay>>) cachedTextToDisplaysField.get(null);
            
            if (textsToUpdate != null && !textsToUpdate.isEmpty()) {
                SimpleTranslation.LOGGER.info("[TextDisplay] 发现 {} 个待更新的文本", textsToUpdate.size());
                int totalRefreshed = 0;
                // 遍历所有待更新的文本
                Iterator<String> iterator = textsToUpdate.iterator();
                while (iterator.hasNext()) {
                    String text = iterator.next();
                    
                    // 检查刷新次数，避免无限刷新
                    int refreshCount = refreshCounts.getOrDefault(text, 0);
                    if (refreshCount >= MAX_REFRESH_ATTEMPTS) {
                        // 超过最大刷新次数，移除标记并记录警告
                        iterator.remove();
                        refreshCounts.remove(text);
                        SimpleTranslation.LOGGER.warn("[TextDisplay] 文本 '{}' 已达到最大刷新次数，停止刷新", text);
                        continue;
                    }
                    
                    String cachedTranslation = textDisplayCache.get(text);
                    
                    if (cachedTranslation != null) {
                        Set<Display.TextDisplay> displays = textToDisplays.get(text);
                        if (displays != null && !displays.isEmpty()) {
                            if (refreshCount == 0) {
                                // 只在第一次刷新时记录日志
                                SimpleTranslation.LOGGER.info("[TextDisplay] 刷新文本 '{}' -> '{}'，关联 {} 个 TextDisplay", 
                                    text, cachedTranslation, displays.size());
                            }
                            // 创建副本以避免并发修改异常
                            List<Display.TextDisplay> displayList = new java.util.ArrayList<>(displays);
                            
                            for (Display.TextDisplay display : displayList) {
                                try {
                                    // 强制触发重新渲染
                                    forceRefreshTextDisplay(display, cachedTranslation);
                                    totalRefreshed++;
                                } catch (Exception e) {
                                    SimpleTranslation.LOGGER.warn("[TextDisplay] 刷新 TextDisplay 时出错: {}", e.getMessage());
                                }
                            }
                            
                            // 增加刷新计数
                            refreshCounts.put(text, refreshCount + 1);
                            
                            // 如果刷新次数达到一定值，移除标记（让 getText() 来处理后续更新）
                            if (refreshCount >= 3) {
                                iterator.remove();
                                refreshCounts.remove(text);
                                SimpleTranslation.LOGGER.debug("[TextDisplay] 文本 '{}' 已刷新 {} 次，移除待更新标记，依赖 getText() 返回翻译", text, refreshCount + 1);
                            }
                        } else {
                            // 没有关联的实例，移除标记
                            iterator.remove();
                            refreshCounts.remove(text);
                            SimpleTranslation.LOGGER.warn("[TextDisplay] 文本 '{}' 没有关联的 TextDisplay 实例", text);
                        }
                    } else {
                        // 没有缓存的翻译，移除标记
                        iterator.remove();
                        refreshCounts.remove(text);
                        SimpleTranslation.LOGGER.warn("[TextDisplay] 文本 '{}' 没有缓存的翻译", text);
                    }
                }
                
                if (totalRefreshed > 0 && refreshCounts.isEmpty()) {
                    SimpleTranslation.LOGGER.info("[TextDisplay] 已刷新 {} 个 TextDisplay 实体", totalRefreshed);
                }
            }
        } catch (Exception e) {
            // 记录异常，避免影响游戏运行
            SimpleTranslation.LOGGER.error("刷新 TextDisplay 时出错", e);
        }
    }
    
    /**
     * 初始化字段缓存
     */
    private void initializeFields() {
        try {
            // 通过反射访问 Mixin 注入到 Display.TextDisplay 类中的静态字段
            Class<?> textDisplayClass = Display.TextDisplay.class;
            
            // 遍历所有字段，查找 Mixin 注入的字段
            // 先收集所有候选字段，然后根据类型特征进行匹配
            java.util.List<java.lang.reflect.Field> candidateSetFields = new java.util.ArrayList<>();
            java.util.List<java.lang.reflect.Field> candidateMapFields = new java.util.ArrayList<>();
            
            for (java.lang.reflect.Field field : textDisplayClass.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                
                if (Set.class.isAssignableFrom(fieldType)) {
                    candidateSetFields.add(field);
                } else if (Map.class.isAssignableFrom(fieldType)) {
                    candidateMapFields.add(field);
                }
            }
            
            // 查找 textsToUpdate 字段（Set<String> 类型）
            for (java.lang.reflect.Field field : candidateSetFields) {
                if (cachedTextsToUpdateField == null) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(null);
                        if (value instanceof Set) {
                            Set<?> set = (Set<?>) value;
                            // 检查集合中的元素类型（如果集合不为空）
                            if (set.isEmpty() || set.iterator().next() instanceof String) {
                                cachedTextsToUpdateField = field;
                                SimpleTranslation.LOGGER.info("[TextDisplay] 找到 textsToUpdate 字段: {}", field.getName());
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // 继续查找
                    }
                }
            }
            
            // 查找 textDisplayCache 字段（Map<String, String> 类型）和 textToDisplays 字段（Map<String, Set<TextDisplay>> 类型）
            for (java.lang.reflect.Field field : candidateMapFields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) value;
                        
                        if (!map.isEmpty()) {
                            Object firstValue = map.values().iterator().next();
                            
                            // 如果值是 String，这是 textDisplayCache
                            if (firstValue instanceof String && cachedTextDisplayCacheField == null) {
                                cachedTextDisplayCacheField = field;
                                SimpleTranslation.LOGGER.info("[TextDisplay] 找到 textDisplayCache 字段: {}", field.getName());
                            }
                            // 如果值是 Set，这可能是 textToDisplays
                            else if (firstValue instanceof Set && cachedTextToDisplaysField == null) {
                                Set<?> set = (Set<?>) firstValue;
                                // 检查 Set 中的元素是否是 TextDisplay
                                if (!set.isEmpty() && set.iterator().next() instanceof Display.TextDisplay) {
                                    cachedTextToDisplaysField = field;
                                    SimpleTranslation.LOGGER.info("[TextDisplay] 找到 textToDisplays 字段: {}", field.getName());
                                }
                            }
                        } else {
                            // 空map，通过字段名判断
                            String fieldName = field.getName().toLowerCase();
                            // 排除包含 "pending" 的字段（如 pendingTextDisplayTranslations）
                            if (fieldName.contains("pending")) {
                                continue;
                            }
                            if (cachedTextDisplayCacheField == null && fieldName.contains("cache")) {
                                cachedTextDisplayCacheField = field;
                                SimpleTranslation.LOGGER.info("[TextDisplay] 通过字段名找到 textDisplayCache 字段（空）: {}", field.getName());
                            } else if (cachedTextToDisplaysField == null && fieldName.equals("texttodisplays")) {
                                // 优先匹配精确的字段名
                                cachedTextToDisplaysField = field;
                                SimpleTranslation.LOGGER.info("[TextDisplay] 通过字段名找到 textToDisplays 字段（空）: {}", field.getName());
                            }
                        }
                    }
                } catch (Exception e) {
                    // 继续查找
                }
            }
            
            // 如果还有字段没找到，尝试通过字段名匹配（作为后备方案）
            if (cachedTextDisplayCacheField == null || cachedTextToDisplaysField == null) {
                for (java.lang.reflect.Field field : candidateMapFields) {
                    String fieldName = field.getName().toLowerCase();
                    // 排除包含 "pending" 的字段
                    if (fieldName.contains("pending")) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        if (cachedTextDisplayCacheField == null && fieldName.contains("cache")) {
                            cachedTextDisplayCacheField = field;
                            SimpleTranslation.LOGGER.info("[TextDisplay] 通过字段名找到 textDisplayCache 字段: {}", field.getName());
                        } else if (cachedTextToDisplaysField == null && fieldName.equals("texttodisplays")) {
                            // 优先匹配精确的字段名
                            cachedTextToDisplaysField = field;
                            SimpleTranslation.LOGGER.info("[TextDisplay] 通过字段名找到 textToDisplays 字段: {}", field.getName());
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
            
            // 标记字段已初始化
            fieldsInitialized = true;
            
            // 如果找不到字段，记录警告
            if (cachedTextsToUpdateField == null || cachedTextDisplayCacheField == null || cachedTextToDisplaysField == null) {
                SimpleTranslation.LOGGER.warn("[TextDisplay] 无法找到 Mixin 注入的字段 - textsToUpdate: {}, textDisplayCache: {}, textToDisplays: {}", 
                    cachedTextsToUpdateField != null, cachedTextDisplayCacheField != null, cachedTextToDisplaysField != null);
            } else {
                SimpleTranslation.LOGGER.info("[TextDisplay] 字段初始化成功");
            }
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("[TextDisplay] 初始化字段时出错", e);
            fieldsInitialized = true; // 标记为已初始化，避免重复尝试
        }
    }
    
    /**
     * 强制刷新单个 TextDisplay 实体
     */
    private void forceRefreshTextDisplay(Display.TextDisplay textDisplay, String translatedText) {
        try {
            // 方法1: 尝试修改 DisplayData 的其他属性来触发更新
            // 修改背景颜色、阴影等属性可能会触发重新渲染
            try {
                java.lang.reflect.Field[] fields = Display.class.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    String typeName = field.getType().getName();
                    if (typeName.contains("TextDisplayData")) {
                        try {
                            field.setAccessible(true);
                            Object displayData = field.get(textDisplay);
                            if (displayData != null) {
                                // 尝试修改 DisplayData 中的其他属性来触发更新
                                java.lang.reflect.Field[] dataFields = displayData.getClass().getDeclaredFields();
                                for (java.lang.reflect.Field dataField : dataFields) {
                                    String fieldName = dataField.getName().toLowerCase();
                                    Class<?> fieldType = dataField.getType();
                                    
                                    // 尝试修改背景颜色（background）或阴影（shadow）属性
                                    if ((fieldName.contains("background") || fieldName.contains("shadow")) && 
                                        (fieldType == int.class || fieldType == Integer.class || fieldType == boolean.class || fieldType == Boolean.class)) {
                                        try {
                                            dataField.setAccessible(true);
                                            if (fieldType == int.class || fieldType == Integer.class) {
                                                int currentValue = dataField.getInt(displayData);
                                                // 切换背景颜色（在0和1之间切换）
                                                int newValue = (currentValue == 0) ? 1 : 0;
                                                dataField.setInt(displayData, newValue);
                                                // 立即恢复
                                                dataField.setInt(displayData, currentValue);
                                            } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                                                boolean currentValue = dataField.getBoolean(displayData);
                                                // 切换布尔值
                                                dataField.setBoolean(displayData, !currentValue);
                                                // 立即恢复
                                                dataField.setBoolean(displayData, currentValue);
                                            }
                                            // 如果成功修改了属性，可能已经触发了更新
                                        } catch (Exception e) {
                                            // 字段可能是final的，继续尝试
                                        }
                                    }
                                    
                                    // 尝试更新文本字段
                                    if (Component.class.isAssignableFrom(fieldType)) {
                                        try {
                                            dataField.setAccessible(true);
                                            MutableComponent translatedComponent = Component.literal(translatedText);
                                            dataField.set(displayData, translatedComponent);
                                        } catch (Exception e) {
                                            // 字段可能是final的，继续尝试
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 继续尝试
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法2: 通过修改实体的旋转角度来强制重新渲染
            // 旋转角度的变化通常能触发重新渲染
            try {
                float yRot = textDisplay.getYRot();
                float xRot = textDisplay.getXRot();
                // 使用很小的旋转角度变化（0.01度），几乎不可见
                textDisplay.setYRot(yRot + 0.01f);
                textDisplay.setXRot(xRot);
                // 立即恢复
                textDisplay.setYRot(yRot);
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法3: 通过微调实体位置来强制重新渲染
            // 使用很小的偏移量，几乎不可见
            try {
                net.minecraft.world.phys.Vec3 pos = textDisplay.position();
                // 使用很小的偏移量（0.001格），几乎不可见
                textDisplay.setPos(pos.x + 0.001, pos.y, pos.z);
                // 立即恢复
                textDisplay.setPos(pos.x, pos.y, pos.z);
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法4: 尝试调用 setChanged() 来触发更新
            try {
                java.lang.reflect.Method setChanged = net.minecraft.world.entity.Entity.class.getDeclaredMethod("setChanged");
                setChanged.setAccessible(true);
                setChanged.invoke(textDisplay);
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法5: 尝试通过修改实体的 tickCount 来触发更新
            try {
                java.lang.reflect.Field tickCountField = net.minecraft.world.entity.Entity.class.getDeclaredField("tickCount");
                tickCountField.setAccessible(true);
                int tickCount = tickCountField.getInt(textDisplay);
                tickCountField.setInt(textDisplay, tickCount - 1);
                tickCountField.setInt(textDisplay, tickCount); // 恢复
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法6: 尝试通过反射调用 getText() 来触发 Mixin，确保翻译被应用
            // 这不会直接更新显示，但可以确保翻译逻辑被执行
            try {
                java.lang.reflect.Method getTextMethod = Display.TextDisplay.class.getDeclaredMethod("getText");
                getTextMethod.setAccessible(true);
                getTextMethod.invoke(textDisplay);
            } catch (Exception e) {
                // 忽略
            }
            
        } catch (Exception e) {
            // 忽略异常
        }
    }
}

