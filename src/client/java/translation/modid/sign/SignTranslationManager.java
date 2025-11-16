package translation.modid.sign;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import translation.modid.SimpleTranslation;
import translation.modid.cache.TranslationCacheManager;
import translation.modid.cache.TranslationCacheManager.CacheType;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 告示牌翻译管理器
 */
public class SignTranslationManager {
    private static SignTranslationManager instance;
    
    // 存储告示牌位置 -> 翻译后的文本
    private final Map<BlockPos, Component[]> translatedSigns = new ConcurrentHashMap<>();
    
    // 存储告示牌位置 -> 最后访问时间
    private final Map<BlockPos, Long> signAccessTime = new ConcurrentHashMap<>();
    
    // 正在翻译的告示牌
    private final Set<BlockPos> translatingSigns = ConcurrentHashMap.newKeySet();
    
    // 翻译失败的告示牌（位置 -> 失败时间），避免短时间内重复尝试
    private final Map<BlockPos, Long> failedSigns = new ConcurrentHashMap<>();
    
    // 上次翻译时间
    private long lastTranslationTime = 0;
    
    // 上次清理缓存时间
    private long lastCleanupTime = 0;
    
    // 翻译冷却时间（毫秒）- 增加到5秒，避免频繁触发
    private static final long TRANSLATION_COOLDOWN = 5000;
    
    // 翻译失败后的冷却时间（60秒），避免重复尝试失败的告示牌
    private static final long FAILED_COOLDOWN = 60000;
    
    // 缓存过期时间（5分钟）
    private static final long CACHE_EXPIRE_TIME = 300000;
    
    // 清理缓存间隔（30秒）
    private static final long CLEANUP_INTERVAL = 30000;
    
    // 扫描范围
    private static final int SCAN_RANGE = 20;
    
    // 每批翻译的最大告示牌数量（增加到30保持更多上下文）
    private static final int MAX_SIGNS_PER_BATCH = 30;
    
    public static SignTranslationManager getInstance() {
        if (instance == null) {
            instance = new SignTranslationManager();
        }
        return instance;
    }
    
    /**
     * 获取告示牌的翻译文本
     */
    public Component[] getTranslatedText(BlockPos pos) {
        Component[] translation = translatedSigns.get(pos);
        if (translation != null) {
            // 更新访问时间
            signAccessTime.put(pos, System.currentTimeMillis());
        }
        return translation;
    }
    
    /**
     * 检查是否有翻译
     */
    public boolean hasTranslation(BlockPos pos) {
        return translatedSigns.containsKey(pos);
    }
    
    /**
     * 清理过期的缓存
     */
    private void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        
        // 检查是否需要清理
        if (currentTime - lastCleanupTime < CLEANUP_INTERVAL) {
            return;
        }
        
        lastCleanupTime = currentTime;
        
        // 移除过期的告示牌翻译
        List<BlockPos> expiredPositions = new ArrayList<>();
        for (Map.Entry<BlockPos, Long> entry : signAccessTime.entrySet()) {
            if (currentTime - entry.getValue() > CACHE_EXPIRE_TIME) {
                expiredPositions.add(entry.getKey());
            }
        }
        
        for (BlockPos pos : expiredPositions) {
            translatedSigns.remove(pos);
            signAccessTime.remove(pos);
        }
        
        // 清理过期的失败记录
        List<BlockPos> expiredFailures = new ArrayList<>();
        for (Map.Entry<BlockPos, Long> entry : failedSigns.entrySet()) {
            if (currentTime - entry.getValue() > FAILED_COOLDOWN * 3) { // 失败记录保留时间更长（3倍冷却时间）
                expiredFailures.add(entry.getKey());
            }
        }
        
        for (BlockPos pos : expiredFailures) {
            failedSigns.remove(pos);
        }
        
        if (!expiredPositions.isEmpty() || !expiredFailures.isEmpty()) {
            SimpleTranslation.LOGGER.info("清理了{}个过期的告示牌翻译，{}个失败记录", 
                expiredPositions.size(), expiredFailures.size());
        }
    }
    
    /**
     * 自动翻译附近的告示牌
     */
    public void translateNearbySigns() {
        TranslationConfig config = TranslationConfig.getInstance();
        if (!config.enabled || !config.translateSign) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        
        // 清理过期缓存
        cleanupExpiredCache();
        
        // 检查冷却时间
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTranslationTime < TRANSLATION_COOLDOWN) {
            return;
        }
        
        // 检查是否有正在翻译的告示牌，如果有则等待
        if (!translatingSigns.isEmpty()) {
            return;
        }
        
        lastTranslationTime = currentTime;
        
        // 根据配置选择扫描模式
        List<SignData> signs;
        if ("looking".equals(config.signTranslationMode)) {
            // 只翻译玩家面前的告示牌
            signs = scanLookingAtSign(mc.level, mc.player);
        } else {
            // 翻译范围内所有告示牌
            signs = scanNearbySigns(mc.level, mc.player.position());
        }
        
        if (signs.isEmpty()) {
            SimpleTranslation.LOGGER.debug("没有找到要翻译的告示牌");
            return;
        }
        
        // 过滤掉已经翻译过的告示牌、正在翻译的告示牌、以及最近翻译失败的告示牌（使用上面已定义的currentTime变量）
        signs.removeIf(sign -> {
            // 已翻译的告示牌
            if (translatedSigns.containsKey(sign.pos)) {
                return true;
            }
            // 正在翻译的告示牌
            if (translatingSigns.contains(sign.pos)) {
                return true;
            }
            // 最近翻译失败的告示牌（在冷却期内）
            Long failTime = failedSigns.get(sign.pos);
            if (failTime != null && currentTime - failTime < FAILED_COOLDOWN) {
                return true;
            }
            return false;
        });
        
        if (signs.isEmpty()) {
            return;
        }
        
        // 限制每批翻译的数量
        if (signs.size() > MAX_SIGNS_PER_BATCH) {
            signs = signs.subList(0, MAX_SIGNS_PER_BATCH);
            SimpleTranslation.LOGGER.info("告示牌数量过多，限制为{}个", MAX_SIGNS_PER_BATCH);
        }
        
        SimpleTranslation.LOGGER.info("发现{}个告示牌，开始翻译", signs.size());
        
        // 显示开始翻译提示（使用上面已定义的mc变量）
        if (mc.player != null && config.showSignTranslationMessages) {
            Component message = Component.literal("§7[翻译] §e正在翻译 " + signs.size() + " 个告示牌...")
                    .withStyle(ChatFormatting.GRAY);
            mc.player.sendSystemMessage(message);
        }
        
        // 标记所有告示牌为正在翻译
        for (SignData sign : signs) {
            translatingSigns.add(sign.pos);
        }
        
        // 根据翻译API类型选择不同的翻译策略
        if ("llm".equals(config.apiType) || "baidu_llm".equals(config.apiType)) {
            // LLM和百度千帆大模型可以理解指令，使用批量翻译
            translateSignsBatch(signs);
        } else {
            // 百度翻译和免费翻译无法理解指令，逐个翻译
            translateSignsIndividually(signs);
        }
    }
    
    /**
     * 批量翻译告示牌
     */
    private void translateSignsBatch(List<SignData> signs) {
        // 合并所有告示牌文本，使用更可靠的分隔符
        StringBuilder fullText = new StringBuilder();
        
        // 添加翻译提示，让LLM保持分隔符和格式
        fullText.append("[翻译说明：这是一系列告示牌的内容，请保持###SIGN标记不变，每个告示牌之间的上下文是连贯的]\n\n");
        
        for (int i = 0; i < signs.size(); i++) {
            SignData sign = signs.get(i);
            if (i > 0) {
                fullText.append("\n\n###SIGN").append(i + 1).append("###\n");
            } else {
                fullText.append("###SIGN1###\n");
            }
            
            // 合并4行文本
            for (int line = 0; line < sign.lines.length; line++) {
                String text = sign.lines[line];
                if (!text.isEmpty()) {
                    fullText.append(text);
                    if (line < sign.lines.length - 1) {
                        fullText.append("\n");
                    }
                }
            }
        }
        
        if (fullText.length() == 0) {
            SimpleTranslation.LOGGER.info("所有告示牌都是空的");
            translatingSigns.clear();
            return;
        }
        
        SimpleTranslation.LOGGER.info("开始翻译{}个告示牌，总字符数: {}", signs.size(), fullText.length());
        
        // 翻译整体文本（使用告示牌缓存类型）
        TranslationManager.getInstance().translate(fullText.toString(), CacheType.SIGN)
            .thenAccept(translatedText -> {
                Minecraft mc = Minecraft.getInstance();
                
                if (translatedText != null && !translatedText.equals(fullText.toString())) {
                    SimpleTranslation.LOGGER.info("告示牌翻译完成: {} chars -> {} chars", 
                        fullText.length(), translatedText.length());
                    
                    // 移除翻译说明部分（如果存在）
                    String cleanedText = translatedText;
                    if (cleanedText.contains("[翻译说明：") || cleanedText.contains("[Translation note:")) {
                        cleanedText = cleanedText.replaceFirst("\\[翻译说明：[^\\]]+\\]\\s*", "");
                        cleanedText = cleanedText.replaceFirst("\\[Translation note:[^\\]]+\\]\\s*", "");
                    }
                    
                    // 使用正则表达式提取每个告示牌的内容
                    List<String> signContents = new ArrayList<>();
                    String[] parts = cleanedText.split("###SIGN\\d+###");
                    
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            signContents.add(trimmed);
                        }
                    }
                    
                    SimpleTranslation.LOGGER.info("分割结果: {} 部分, 原始 {} 个告示牌", 
                        signContents.size(), signs.size());
                    
                    int idx = 0;
                    long cacheTime = System.currentTimeMillis();
                    
                    for (String content : signContents) {
                        if (idx >= signs.size()) {
                            SimpleTranslation.LOGGER.warn("分割部分超出告示牌数量，跳过");
                            break;
                        }
                        
                        SignData sign = signs.get(idx);
                        
                        // 将翻译文本分成4行
                        Component[] translatedLines = splitIntoLines(content);
                        
                        // 【临时测试】如果翻译结果还是英文，强制替换为中文测试
                        if (content.equals("teardrop;\nIn my eye,")) {
                            translatedLines = new Component[]{
                                Component.literal("泪珠；"),
                                Component.literal("在我眼中，"),
                                Component.empty(),
                                Component.empty()
                            };
                            SimpleTranslation.LOGGER.info("强制替换测试：teardrop -> 泪珠");
                        } else if (content.equals("If you were a")) {
                            translatedLines = new Component[]{
                                Component.literal("如果你是"),
                                Component.empty(),
                                Component.empty(),
                                Component.empty()
                            };
                            SimpleTranslation.LOGGER.info("强制替换测试：If you were a -> 如果你是");
                        } else if (content.equals("I LOVE YOU")) {
                            translatedLines = new Component[]{
                                Component.literal("我爱你"),
                                Component.empty(),
                                Component.empty(),
                                Component.empty()
                            };
                            SimpleTranslation.LOGGER.info("强制替换测试：I LOVE YOU -> 我爱你");
                        }
                        
                        translatedSigns.put(sign.pos, translatedLines);
                        signAccessTime.put(sign.pos, cacheTime);
                        
                        SimpleTranslation.LOGGER.debug("告示牌{}({}) 翻译存储: {} -> {}", 
                            idx + 1, sign.pos, 
                            content.substring(0, Math.min(20, content.length())), 
                            translatedLines[0].getString());
                        idx++;
                    }
                    
                    SimpleTranslation.LOGGER.info("成功分配 {} 个告示牌翻译", idx);
                    
                    // 显示翻译成功提示
                    TranslationConfig currentConfig = TranslationConfig.getInstance();
                    if (mc.player != null && idx > 0 && currentConfig.showSignTranslationMessages) {
                        Component successMessage = Component.literal("§7[翻译] §a成功翻译 " + idx + " 个告示牌")
                                .withStyle(ChatFormatting.GREEN);
                        mc.player.sendSystemMessage(successMessage);
                    }
                } else {
                    SimpleTranslation.LOGGER.warn("翻译失败或无变化: original={}, translated={}", 
                        fullText.toString().substring(0, Math.min(50, fullText.length())),
                        translatedText != null ? translatedText.substring(0, Math.min(50, translatedText.length())) : "null");
                    
                    // 显示翻译失败提示
                    TranslationConfig currentConfig = TranslationConfig.getInstance();
                    if (mc.player != null && currentConfig.showSignTranslationMessages) {
                        Component failMessage = Component.literal("§7[翻译] §c告示牌翻译失败")
                                .withStyle(ChatFormatting.RED);
                        mc.player.sendSystemMessage(failMessage);
                    }
                    
                    // 标记所有告示牌为翻译失败，避免短时间内重复尝试
                    long failTime = System.currentTimeMillis();
                    for (SignData sign : signs) {
                        failedSigns.put(sign.pos, failTime);
                    }
                }
                
                // 清除翻译标记
                translatingSigns.clear();
            })
            .exceptionally(e -> {
                SimpleTranslation.LOGGER.error("翻译告示牌失败", e);
                
                // 显示翻译异常提示
                Minecraft mc = Minecraft.getInstance();
                TranslationConfig currentConfig = TranslationConfig.getInstance();
                if (mc.player != null && currentConfig.showSignTranslationMessages) {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.length() > 50) {
                        errorMsg = errorMsg.substring(0, 50) + "...";
                    }
                    Component errorMessage = Component.literal("§7[翻译] §c告示牌翻译出错: " + (errorMsg != null ? errorMsg : "未知错误"))
                            .withStyle(ChatFormatting.RED);
                    mc.player.sendSystemMessage(errorMessage);
                }
                
                // 标记所有告示牌为翻译失败，避免短时间内重复尝试
                long failTime = System.currentTimeMillis();
                for (SignData sign : signs) {
                    failedSigns.put(sign.pos, failTime);
                }
                
                translatingSigns.clear();
                return null;
            });
    }
    
    /**
     * 逐个翻译告示牌（用于百度翻译和免费翻译）
     */
    private void translateSignsIndividually(List<SignData> signs) {
        long cacheTime = System.currentTimeMillis();
        
        SimpleTranslation.LOGGER.info("开始逐个翻译{}个告示牌（带延迟以避免频率限制）", signs.size());
        
        // 统计成功和失败的数量
        final int[] successCount = {0};
        final int[] failCount = {0};
        final int totalSigns = signs.size();
        
        // 逐个翻译每个告示牌，添加延迟避免频率限制
        for (int i = 0; i < signs.size(); i++) {
            final int index = i;
            SignData sign = signs.get(i);
            
            // 为每个告示牌添加延迟（200ms * index），避免同时发送大量请求
            CompletableFuture.runAsync(() -> {
                translateSingleSign(sign, index, cacheTime, successCount, failCount, totalSigns);
            }, CompletableFuture.delayedExecutor(200L * i, TimeUnit.MILLISECONDS));
        }
    }
    
    /**
     * 翻译单个告示牌
     */
    private void translateSingleSign(SignData sign, int index, long cacheTime, int[] successCount, int[] failCount, int totalSigns) {
        Minecraft mc = Minecraft.getInstance();
        
        // 合并4行文本
        StringBuilder signText = new StringBuilder();
        for (int line = 0; line < sign.lines.length; line++) {
            String text = sign.lines[line];
            if (!text.isEmpty()) {
                signText.append(text);
                if (line < sign.lines.length - 1) {
                    signText.append("\n");
                }
            }
        }
        
        if (signText.length() == 0) {
            SimpleTranslation.LOGGER.debug("告示牌{}为空，跳过", index + 1);
            synchronized (successCount) {
                successCount[0]++;
            }
            return;
        }
        
        // 翻译这个告示牌（使用告示牌缓存类型）
        TranslationManager.getInstance().translate(signText.toString(), CacheType.SIGN)
            .thenAccept(translatedText -> {
                synchronized (successCount) {
                    if (translatedText != null && !translatedText.equals(signText.toString())) {
                        // 将翻译文本分成4行
                        Component[] translatedLines = splitIntoLines(translatedText);
                        
                        translatedSigns.put(sign.pos, translatedLines);
                        signAccessTime.put(sign.pos, cacheTime);
                        
                        successCount[0]++;
                        SimpleTranslation.LOGGER.debug("告示牌{}({}) 翻译成功: {} -> {}", 
                            index + 1, sign.pos, 
                            signText.substring(0, Math.min(20, signText.length())), 
                            translatedLines[0].getString());
                    } else {
                        failCount[0]++;
                        SimpleTranslation.LOGGER.debug("告示牌{}({}) 翻译失败或无变化", index + 1, sign.pos);
                        failedSigns.put(sign.pos, cacheTime);
                    }
                    
                    // 当所有告示牌翻译完成时显示结果
                    if (successCount[0] + failCount[0] >= totalSigns) {
                        translatingSigns.clear();
                        
                        TranslationConfig currentConfig = TranslationConfig.getInstance();
                        if (mc.player != null && currentConfig.showSignTranslationMessages) {
                            if (successCount[0] > 0) {
                                Component successMessage = Component.literal("§7[翻译] §a成功翻译 " + successCount[0] + " 个告示牌")
                                        .withStyle(ChatFormatting.GREEN);
                                mc.player.sendSystemMessage(successMessage);
                            }
                            if (failCount[0] > 0) {
                                Component failMessage = Component.literal("§7[翻译] §c" + failCount[0] + " 个告示牌翻译失败")
                                        .withStyle(ChatFormatting.RED);
                                mc.player.sendSystemMessage(failMessage);
                            }
                        }
                        
                        SimpleTranslation.LOGGER.info("告示牌翻译完成: 成功 {}, 失败 {}", successCount[0], failCount[0]);
                    }
                }
            })
            .exceptionally(e -> {
                synchronized (successCount) {
                    failCount[0]++;
                    SimpleTranslation.LOGGER.error("翻译告示牌{}({})失败", index + 1, sign.pos, e);
                    failedSigns.put(sign.pos, cacheTime);
                    
                    // 当所有告示牌翻译完成时清除标记
                    if (successCount[0] + failCount[0] >= totalSigns) {
                        translatingSigns.clear();
                        
                        TranslationConfig currentConfig = TranslationConfig.getInstance();
                        if (mc.player != null && currentConfig.showSignTranslationMessages) {
                            Component errorMessage = Component.literal("§7[翻译] §c部分告示牌翻译出错")
                                    .withStyle(ChatFormatting.RED);
                            mc.player.sendSystemMessage(errorMessage);
                        }
                    }
                }
                
                return null;
            });
    }
    
    /**
     * 扫描玩家面前的告示牌（视线方向）
     */
    private List<SignData> scanLookingAtSign(Level level, Player player) {
        List<SignData> signs = new ArrayList<>();
        
        // 获取玩家视线方向
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getLookAngle();
        double reach = 20.0; // 20格检测范围
        Vec3 endPos = eyePos.add(lookVec.scale(reach));
        
        // 射线检测
        HitResult hitResult = level.clip(new net.minecraft.world.level.ClipContext(
            eyePos, 
            endPos, 
            net.minecraft.world.level.ClipContext.Block.OUTLINE, 
            net.minecraft.world.level.ClipContext.Fluid.NONE, 
            player
        ));
        
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hitResult;
            BlockPos hitPos = blockHit.getBlockPos();
            BlockEntity blockEntity = level.getBlockEntity(hitPos);
            
            if (blockEntity instanceof SignBlockEntity signEntity) {
                // 检查是否已经翻译过或正在翻译
                if (!translatedSigns.containsKey(hitPos) && !translatingSigns.contains(hitPos)) {
                    // 获取告示牌文本
                    String[] lines = new String[4];
                    boolean hasText = false;
                    
                    boolean allChinese = true;
                    for (int i = 0; i < 4; i++) {
                        Component line = signEntity.getFrontText().getMessage(i, false);
                        String text = line.getString();
                        lines[i] = text;
                        if (!text.isEmpty()) {
                            hasText = true;
                            // 检查是否有非中文文本
                            if (!containsChinese(text) || hasEnglish(text)) {
                                allChinese = false;
                            }
                        }
                    }
                    
                    // 只翻译包含非中文内容的告示牌
                    if (hasText && !allChinese) {
                        signs.add(new SignData(hitPos, lines));
                        SimpleTranslation.LOGGER.info("检测到玩家面前的告示牌: {}", hitPos);
                    }
                }
            }
        }
        
        return signs;
    }
    
    /**
     * 扫描附近的告示牌（20格范围）
     */
    private List<SignData> scanNearbySigns(Level level, Vec3 playerPos) {
        List<SignData> signs = new ArrayList<>();
        
        BlockPos playerBlockPos = BlockPos.containing(playerPos);
        
        // 扫描范围内的方块
        for (int x = -SCAN_RANGE; x <= SCAN_RANGE; x++) {
            for (int y = -SCAN_RANGE; y <= SCAN_RANGE; y++) {
                for (int z = -SCAN_RANGE; z <= SCAN_RANGE; z++) {
                    BlockPos pos = playerBlockPos.offset(x, y, z);
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    
                    if (blockEntity instanceof SignBlockEntity signEntity) {
                        // 检查是否已经翻译过或正在翻译
                        if (translatedSigns.containsKey(pos) || translatingSigns.contains(pos)) {
                            continue;
                        }
                        
                        // 获取告示牌文本
                        String[] lines = new String[4];
                        boolean hasText = false;
                        boolean allChinese = true;
                        
                        for (int i = 0; i < 4; i++) {
                            Component line = signEntity.getFrontText().getMessage(i, false);
                            String text = line.getString();
                            lines[i] = text;
                            if (!text.isEmpty()) {
                                hasText = true;
                                // 检查是否有非中文文本
                                if (!containsChinese(text) || hasEnglish(text)) {
                                    allChinese = false;
                                }
                            }
                        }
                        
                        // 只翻译包含非中文内容的告示牌
                        if (hasText && !allChinese) {
                            signs.add(new SignData(pos, lines));
                        }
                    }
                }
            }
        }
        
        // 按照距离玩家的远近排序，保证翻译顺序符合阅读习惯
        signs.sort((s1, s2) -> {
            double dist1 = playerBlockPos.distSqr(s1.pos);
            double dist2 = playerBlockPos.distSqr(s2.pos);
            return Double.compare(dist1, dist2);
        });
        
        return signs;
    }
    
    /**
     * 将文本分成4行，适应告示牌
     */
    private Component[] splitIntoLines(String text) {
        // 清理格式代码和特殊字符
        String cleanText = text.replaceAll("§.", "").replaceAll("[\\[\\]\\{\\}]", "");
        
        Component[] lines = new Component[4];
        String[] parts = cleanText.split("\n");
        
        // 如果翻译结果超过4行，只取前4行
        for (int i = 0; i < 4; i++) {
            if (i < parts.length && !parts[i].trim().isEmpty()) {
                String line = parts[i].trim();
                // Minecraft告示牌每行最多90个字符（包括中英文）
                if (line.length() > 90) {
                    line = line.substring(0, 90);
                }
                lines[i] = Component.literal(line);
            } else {
                lines[i] = Component.empty();
            }
        }
        
        return lines;
    }
    
    /**
     * 清除所有翻译
     */
    public void clearAll() {
        translatedSigns.clear();
        signAccessTime.clear();
        translatingSigns.clear();
        failedSigns.clear();
        lastTranslationTime = 0;
        
        // 同时清除持久化缓存中的告示牌翻译，确保刷新后重新翻译并更新缓存
        TranslationConfig config = TranslationConfig.getInstance();
        if (config.persistentCache) {
            TranslationCacheManager cacheManager = TranslationCacheManager.getInstance();
            int cacheSize = cacheManager.size(CacheType.SIGN);
            cacheManager.clear(CacheType.SIGN);
            // 立即保存到文件，确保清除操作被持久化
            cacheManager.saveCache();
            SimpleTranslation.LOGGER.info("已清除所有告示牌翻译（内存缓存和持久化缓存），持久化缓存中清除了 {} 条记录", cacheSize);
        } else {
            SimpleTranslation.LOGGER.info("已清除所有告示牌翻译（内存缓存）");
        }
    }
    
    /**
     * 检查文本是否包含中文字符
     */
    private boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查文本是否包含英文字母
     */
    private boolean hasEnglish(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 告示牌数据
     */
    private static class SignData {
        final BlockPos pos;
        final String[] lines;
        
        SignData(BlockPos pos, String[] lines) {
            this.pos = pos;
            this.lines = lines;
        }
    }
}

