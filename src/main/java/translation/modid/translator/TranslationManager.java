package translation.modid.translator;

import translation.modid.SimpleTranslation;
import translation.modid.cache.TranslationCacheManager;
import translation.modid.cache.TranslationCacheManager.CacheType;
import translation.modid.config.TranslationConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TranslationManager {
    private static TranslationManager instance;
    private BaiduTranslator baiduTranslator;
    private BaiduLLMTranslator baiduLLMTranslator;
    private FreeTranslator freeTranslator;
    private YoudaoFreeTranslator youdaoFreeTranslator;
    private LLMTranslator llmTranslator;
    private final Map<String, String> translationCache = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingTranslations = new ConcurrentHashMap<>();
    
    private TranslationManager() {
        reload();
    }
    
    public static TranslationManager getInstance() {
        if (instance == null) {
            instance = new TranslationManager();
        }
        return instance;
    }
    
    public void reload() {
        TranslationConfig config = TranslationConfig.getInstance();
        if ("baidu".equals(config.apiType)) {
            baiduTranslator = new BaiduTranslator(config.baiduAppId, config.baiduSecretKey);
        } else if ("baidu_llm".equals(config.apiType)) {
            baiduLLMTranslator = new BaiduLLMTranslator(
                config.baiduLLMApiKey,
                config.baiduLLMSecretKey,
                config.baiduLLMModel
            );
        } else if ("free".equals(config.apiType)) {
            freeTranslator = new FreeTranslator();
            youdaoFreeTranslator = new YoudaoFreeTranslator(); // 作为备用
        } else if ("llm".equals(config.apiType)) {
            llmTranslator = new LLMTranslator(
                config.llmApiKey,
                config.llmApiUrl,
                config.llmModel,
                config.llmSystemPrompt
            );
        }
    }
    
    /**
     * 翻译文本（默认类型：OTHER）
     * @param text 原文
     * @return 翻译结果的CompletableFuture
     */
    public CompletableFuture<String> translate(String text) {
        return translate(text, CacheType.OTHER);
    }
    
    /**
     * 翻译文本（指定缓存类型）
     * @param text 原文
     * @param cacheType 缓存分区类型
     * @return 翻译结果的CompletableFuture
     */
    public CompletableFuture<String> translate(String text, CacheType cacheType) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled) {
            return CompletableFuture.completedFuture(null);
        }
        
        // 先检查内存缓存
        if (translationCache.containsKey(text)) {
            return CompletableFuture.completedFuture(translationCache.get(text));
        }
        
        // 检查持久化缓存（如果启用）
        if (config.persistentCache) {
            TranslationCacheManager cacheManager = TranslationCacheManager.getInstance();
            if (cacheManager.contains(cacheType, text)) {
                String cached = cacheManager.get(cacheType, text);
                // 同时放入内存缓存以加快后续访问
                translationCache.put(text, cached);
                return CompletableFuture.completedFuture(cached);
            }
        }
        
        // 检查是否正在翻译
        Long pendingTime = pendingTranslations.get(text);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            return CompletableFuture.completedFuture(null);
        }
        
        // 标记为正在翻译
        pendingTranslations.put(text, System.currentTimeMillis());
        
        // 开始翻译
        return translateWithApi(text, config.sourceLang, config.targetLang)
                .thenApply(result -> {
                    if (result != null) {
                        // 保存到内存缓存
                        translationCache.put(text, result);
                        
                        // 如果启用了持久化缓存，也保存到文件（指定类型）
                        if (config.persistentCache) {
                            TranslationCacheManager cacheManager = TranslationCacheManager.getInstance();
                            cacheManager.put(cacheType, text, result);
                            // 定期保存到文件（每100条保存一次）
                            if (cacheManager.size() % 100 == 0) {
                                cacheManager.saveCache();
                            }
                        }
                        
                        if (SimpleTranslation.LOGGER.isDebugEnabled()) {
                            SimpleTranslation.LOGGER.debug("[{}] 翻译完成: {} -> {}", cacheType.getDisplayName(), text, result);
                        }
                    }
                    pendingTranslations.remove(text);
                    return result;
                })
                .exceptionally(e -> {
                    SimpleTranslation.LOGGER.warn("翻译失败: {}", e.getMessage());
                    pendingTranslations.remove(text);
                    return null;
                });
    }
    
    private CompletableFuture<String> translateWithApi(String text, String from, String to) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        switch (config.apiType) {
            case "llm":
                if (llmTranslator == null) {
                    llmTranslator = new LLMTranslator(
                        config.llmApiKey,
                        config.llmApiUrl,
                        config.llmModel,
                        config.llmSystemPrompt
                    );
                }
                // LLM翻译不需要源语言参数，只需要目标语言
                return llmTranslator.translateAsync(text, to);
                
            case "free":
                if (freeTranslator == null) {
                    freeTranslator = new FreeTranslator();
                }
                if (youdaoFreeTranslator == null) {
                    youdaoFreeTranslator = new YoudaoFreeTranslator();
                }
                
                // 先尝试Google翻译，失败则使用有道翻译作为备用
                return freeTranslator.translateAsync(text, from, to)
                    .thenCompose(result -> {
                        if (result != null && !result.equals(text)) {
                            return CompletableFuture.completedFuture(result);
                        }
                        // Google翻译失败，尝试有道翻译
                        SimpleTranslation.LOGGER.info("Google翻译无结果，尝试使用有道翻译备用方案");
                        return youdaoFreeTranslator.translateAsync(text, from, to);
                    })
                    .exceptionally(e -> {
                        // Google翻译出错，尝试有道翻译
                        SimpleTranslation.LOGGER.warn("Google翻译失败，尝试使用有道翻译备用方案");
                        try {
                            return youdaoFreeTranslator.translateAsync(text, from, to).join();
                        } catch (Exception ex) {
                            SimpleTranslation.LOGGER.error("所有免费翻译方案均失败", ex);
                            return null;
                        }
                    });
                    
            case "baidu":
                if (baiduTranslator != null) {
                    return baiduTranslator.translateAsync(text, from, to);
                } else {
                    SimpleTranslation.LOGGER.warn("百度翻译未配置，请设置API密钥");
                }
                break;
                
            case "baidu_llm":
                if (baiduLLMTranslator == null) {
                    baiduLLMTranslator = new BaiduLLMTranslator(
                        config.baiduLLMApiKey,
                        config.baiduLLMSecretKey,
                        config.baiduLLMModel
                    );
                }
                // 百度千帆大模型翻译
                return baiduLLMTranslator.translateAsync(text, from, to);
                
            // 可以在这里添加其他翻译API
            default:
                SimpleTranslation.LOGGER.warn("不支持的翻译API类型: " + config.apiType);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 清除翻译缓存
     */
    public void clearCache() {
        translationCache.clear();
        SimpleTranslation.LOGGER.info("翻译缓存已清除");
    }
    
    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return translationCache.size();
    }
    
    /**
     * 批量翻译文本（仅支持LLM）
     * @param texts 要翻译的文本列表
     * @return 翻译结果的CompletableFuture，返回Map<原文, 译文>
     */
    public CompletableFuture<Map<String, String>> translateBatch(List<String> texts) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        
        // 过滤已缓存的文本
        Map<String, String> cachedResults = new HashMap<>();
        List<String> toTranslate = new ArrayList<>();
        
        for (String text : texts) {
            if (translationCache.containsKey(text)) {
                cachedResults.put(text, translationCache.get(text));
            } else {
                toTranslate.add(text);
            }
        }
        
        if (toTranslate.isEmpty()) {
            return CompletableFuture.completedFuture(cachedResults);
        }
        
        // 只支持LLM批量翻译
        if (!"llm".equals(config.apiType) || llmTranslator == null) {
            // 如果不是LLM，回退到单个翻译
            List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
            for (String text : toTranslate) {
                CompletableFuture<Map.Entry<String, String>> future = translate(text)
                        .thenApply(result -> new AbstractMap.SimpleEntry<>(text, result));
                futures.add(future);
            }
            
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        Map<String, String> results = new HashMap<>(cachedResults);
                        for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                            Map.Entry<String, String> entry = future.join();
                            if (entry.getValue() != null) {
                                results.put(entry.getKey(), entry.getValue());
                            }
                        }
                        return results;
                    });
        }
        
        // 使用LLM批量翻译
        return llmTranslator.translateBatchAsync(toTranslate, config.targetLang)
                .thenApply(batchResults -> {
                    // 将批量翻译结果加入缓存
                    for (Map.Entry<String, String> entry : batchResults.entrySet()) {
                        if (entry.getValue() != null) {
                            translationCache.put(entry.getKey(), entry.getValue());
                        }
                    }
                    // 合并缓存结果和批量翻译结果
                    Map<String, String> allResults = new HashMap<>(cachedResults);
                    allResults.putAll(batchResults);
                    return allResults;
                });
    }
}

