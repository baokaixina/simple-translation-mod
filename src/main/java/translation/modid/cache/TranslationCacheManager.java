package translation.modid.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import translation.modid.SimpleTranslation;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译缓存管理器 - 支持持久化存储和分区管理
 */
public class TranslationCacheManager {
    /**
     * 缓存类型枚举
     */
    public enum CacheType {
        SIGN("告示牌翻译"),
        SCOREBOARD("计分板翻译"),
        ITEM("物品翻译"),
        ENTITY("实体翻译"),
        CHAT("聊天翻译"),
        BOOK("书本翻译"),
        OTHER("其他翻译");
        
        private final String displayName;
        
        CacheType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private static TranslationCacheManager instance;
    // 分区缓存：类型 -> (原文 -> 译文)
    private final Map<CacheType, Map<String, String>> categorizedCache = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private String currentWorldName = "global"; // 当前世界名称
    private Path cacheFilePath;
    
    private TranslationCacheManager() {
        // 初始化所有分区
        for (CacheType type : CacheType.values()) {
            categorizedCache.put(type, new ConcurrentHashMap<>());
        }
        
        // 初始化时使用全局缓存
        updateCacheFilePath("global");
        
        // 迁移旧的缓存文件
        migrateOldCacheFiles();
        
        loadCache();
    }
    
    /**
     * 更新缓存文件路径（根据世界名称）
     */
    private void updateCacheFilePath(String worldName) {
        // 使用 config/simple-translation/cache/ 子文件夹
        String configDir = System.getProperty("user.dir") + File.separator + "config" 
                + File.separator + "simple-translation" + File.separator + "cache";
        
        if (worldName == null || worldName.isEmpty()) {
            worldName = "global";
        }
        // 清理世界名称，移除不允许的文件名字符
        worldName = worldName.replaceAll("[<>:\"/\\\\|?*]", "_");
        
        // 简化文件名：直接使用世界名.json
        cacheFilePath = Paths.get(configDir, worldName + ".json");
    }
    
    public static TranslationCacheManager getInstance() {
        if (instance == null) {
            instance = new TranslationCacheManager();
        }
        return instance;
    }
    
    /**
     * 从文件加载缓存
     */
    private void loadCache() {
        try {
            if (Files.exists(cacheFilePath)) {
                try (Reader reader = new FileReader(cacheFilePath.toFile())) {
                    // 尝试加载新格式（分区结构）
                    Type newType = new TypeToken<HashMap<String, HashMap<String, String>>>(){}.getType();
                    Map<String, Map<String, String>> loadedCache = gson.fromJson(reader, newType);
                    
                    if (loadedCache != null && !loadedCache.isEmpty()) {
                        // 检查是否是新格式（包含分区键）
                        boolean isNewFormat = false;
                        for (String key : loadedCache.keySet()) {
                            // 检查键是否是 CacheType 名称
                            try {
                                CacheType.valueOf(key);
                                isNewFormat = true;
                                break;
                            } catch (IllegalArgumentException e) {
                                // 不是 CacheType，说明是旧格式
                                break;
                            }
                        }
                        
                        int totalCount = 0;
                        if (isNewFormat) {
                            // 新格式：直接加载分区数据
                            for (Map.Entry<String, Map<String, String>> entry : loadedCache.entrySet()) {
                                try {
                                    CacheType cacheType = CacheType.valueOf(entry.getKey());
                                    categorizedCache.get(cacheType).putAll(entry.getValue());
                                    totalCount += entry.getValue().size();
                                } catch (IllegalArgumentException e) {
                                    SimpleTranslation.LOGGER.warn("未知的缓存类型: {}", entry.getKey());
                                }
                            }
                        } else {
                            // 旧格式：迁移到 OTHER 分区
                            SimpleTranslation.LOGGER.info("检测到旧格式缓存，正在迁移到分区结构...");
                            for (Map.Entry<String, Map<String, String>> entry : loadedCache.entrySet()) {
                                categorizedCache.get(CacheType.OTHER).put(entry.getKey(), 
                                    entry.getValue().values().iterator().next());
                                totalCount++;
                            }
                        }
                        
                        SimpleTranslation.LOGGER.info("已加载 {} 条翻译缓存", totalCount);
                    }
                }
            }
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("加载翻译缓存失败", e);
        }
    }
    
    /**
     * 保存缓存到文件（分区结构）
     */
    public void saveCache() {
        try {
            // 确保目录存在
            Files.createDirectories(cacheFilePath.getParent());
            
            // 构建分区结构的数据
            Map<String, Map<String, String>> saveData = new HashMap<>();
            int totalCount = 0;
            
            for (Map.Entry<CacheType, Map<String, String>> entry : categorizedCache.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    saveData.put(entry.getKey().name(), new HashMap<>(entry.getValue()));
                    totalCount += entry.getValue().size();
                }
            }
            
            // 写入文件
            try (Writer writer = new FileWriter(cacheFilePath.toFile())) {
                gson.toJson(saveData, writer);
                SimpleTranslation.LOGGER.info("已保存 {} 条翻译缓存到: {}", totalCount, cacheFilePath);
            }
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("保存翻译缓存失败", e);
        }
    }
    
    /**
     * 获取翻译（指定类型）
     */
    public String get(CacheType type, String text) {
        Map<String, String> typeCache = categorizedCache.get(type);
        return typeCache != null ? typeCache.get(text) : null;
    }
    
    /**
     * 获取翻译（默认类型：OTHER）
     */
    public String get(String text) {
        // 向后兼容：先查找所有分区
        for (Map<String, String> typeCache : categorizedCache.values()) {
            String result = typeCache.get(text);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
    
    /**
     * 添加翻译（指定类型）
     */
    public void put(CacheType type, String text, String translation) {
        Map<String, String> typeCache = categorizedCache.get(type);
        if (typeCache != null) {
            typeCache.put(text, translation);
        }
    }
    
    /**
     * 添加翻译（默认类型：OTHER）
     */
    public void put(String text, String translation) {
        put(CacheType.OTHER, text, translation);
    }
    
    /**
     * 检查是否包含翻译（指定类型）
     */
    public boolean contains(CacheType type, String text) {
        Map<String, String> typeCache = categorizedCache.get(type);
        return typeCache != null && typeCache.containsKey(text);
    }
    
    /**
     * 检查是否包含翻译（查找所有分区）
     */
    public boolean contains(String text) {
        for (Map<String, String> typeCache : categorizedCache.values()) {
            if (typeCache.containsKey(text)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 反向查找：通过译文查找原文（指定类型）
     * @param translatedText 译文
     * @param type 缓存类型
     * @return 原文，如果未找到则返回 null
     */
    public String findOriginalText(CacheType type, String translatedText) {
        if (translatedText == null || translatedText.isEmpty()) {
            return null;
        }
        
        Map<String, String> typeCache = categorizedCache.get(type);
        if (typeCache == null) {
            return null;
        }
        
        // 遍历缓存，查找值匹配的键
        for (Map.Entry<String, String> entry : typeCache.entrySet()) {
            if (translatedText.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        
        return null;
    }
    
    /**
     * 反向查找：通过译文查找原文（查找所有分区）
     * @param translatedText 译文
     * @return 原文，如果未找到则返回 null
     */
    public String findOriginalText(String translatedText) {
        if (translatedText == null || translatedText.isEmpty()) {
            return null;
        }
        
        // 遍历所有分区，查找值匹配的键
        for (Map<String, String> typeCache : categorizedCache.values()) {
            for (Map.Entry<String, String> entry : typeCache.entrySet()) {
                if (translatedText.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        
        return null;
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAll() {
        for (Map<String, String> typeCache : categorizedCache.values()) {
            typeCache.clear();
        }
        // 删除缓存文件
        try {
            if (Files.exists(cacheFilePath)) {
                Files.delete(cacheFilePath);
                SimpleTranslation.LOGGER.info("已清空翻译缓存并删除缓存文件");
            }
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("删除缓存文件失败", e);
        }
    }
    
    /**
     * 清空指定类型的缓存
     */
    public void clear(CacheType type) {
        Map<String, String> typeCache = categorizedCache.get(type);
        if (typeCache != null) {
            int count = typeCache.size();
            typeCache.clear();
            SimpleTranslation.LOGGER.info("已清空 {} 条 {} 缓存", count, type.getDisplayName());
        }
    }
    
    /**
     * 获取缓存大小（所有分区总和）
     */
    public int size() {
        int total = 0;
        for (Map<String, String> typeCache : categorizedCache.values()) {
            total += typeCache.size();
        }
        return total;
    }
    
    /**
     * 获取指定类型的缓存大小
     */
    public int size(CacheType type) {
        Map<String, String> typeCache = categorizedCache.get(type);
        return typeCache != null ? typeCache.size() : 0;
    }
    
    /**
     * 获取内存缓存（用于临时存储）- 向后兼容
     */
    @Deprecated
    public Map<String, String> getMemoryCache() {
        // 返回 OTHER 类型的缓存
        return categorizedCache.get(CacheType.OTHER);
    }
    
    /**
     * 获取指定类型的缓存
     */
    public Map<String, String> getCacheByType(CacheType type) {
        return categorizedCache.get(type);
    }
    
    /**
     * 获取所有分区的缓存统计信息
     */
    public Map<CacheType, Integer> getCacheStats() {
        Map<CacheType, Integer> stats = new HashMap<>();
        for (Map.Entry<CacheType, Map<String, String>> entry : categorizedCache.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
    
    /**
     * 切换到指定世界的缓存
     */
    public void switchWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            worldName = "global";
        }
        
        // 如果世界名称没变，不需要切换
        if (worldName.equals(currentWorldName)) {
            return;
        }
        
        // 保存当前世界的缓存
        saveCache();
        
        // 清空内存缓存（所有分区）
        for (Map<String, String> typeCache : categorizedCache.values()) {
            typeCache.clear();
        }
        
        // 更新当前世界名称和文件路径
        currentWorldName = worldName;
        updateCacheFilePath(worldName);
        
        // 加载新世界的缓存
        loadCache();
        
        SimpleTranslation.LOGGER.info("已切换到世界 [{}] 的翻译缓存，缓存文件: {}", worldName, cacheFilePath);
    }
    
    /**
     * 获取当前世界名称
     */
    public String getCurrentWorldName() {
        return currentWorldName;
    }
    
    /**
     * 迁移旧的缓存文件到新位置
     */
    private void migrateOldCacheFiles() {
        try {
            String configDir = System.getProperty("user.dir") + File.separator + "config";
            File oldConfigDir = new File(configDir);
            
            if (!oldConfigDir.exists()) {
                return;
            }
            
            // 查找所有旧的缓存文件（simple-translation-cache-*.json）
            File[] oldCacheFiles = oldConfigDir.listFiles((dir, name) -> 
                name.startsWith("simple-translation-cache-") && name.endsWith(".json")
            );
            
            if (oldCacheFiles == null || oldCacheFiles.length == 0) {
                return;
            }
            
            // 确保新的缓存目录存在
            String newCacheDir = configDir + File.separator + "simple-translation" + File.separator + "cache";
            Files.createDirectories(Paths.get(newCacheDir));
            
            int migratedCount = 0;
            for (File oldFile : oldCacheFiles) {
                try {
                    // 提取世界名称：simple-translation-cache-xxx.json -> xxx.json
                    String oldFileName = oldFile.getName();
                    String worldName = oldFileName.substring("simple-translation-cache-".length(), 
                                                            oldFileName.length() - ".json".length());
                    
                    // 新文件路径
                    Path newFilePath = Paths.get(newCacheDir, worldName + ".json");
                    
                    // 如果新文件不存在，则迁移
                    if (!Files.exists(newFilePath)) {
                        Files.move(oldFile.toPath(), newFilePath);
                        migratedCount++;
                        SimpleTranslation.LOGGER.info("已迁移缓存文件: {} -> {}", oldFile.getName(), newFilePath);
                    } else {
                        // 新文件已存在，删除旧文件
                        Files.delete(oldFile.toPath());
                        SimpleTranslation.LOGGER.info("已删除旧缓存文件: {}", oldFile.getName());
                    }
                } catch (Exception e) {
                    SimpleTranslation.LOGGER.error("迁移缓存文件失败: " + oldFile.getName(), e);
                }
            }
            
            if (migratedCount > 0) {
                SimpleTranslation.LOGGER.info("已迁移 {} 个缓存文件到新位置: {}", migratedCount, newCacheDir);
            }
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("迁移缓存文件时出错", e);
        }
    }
}

