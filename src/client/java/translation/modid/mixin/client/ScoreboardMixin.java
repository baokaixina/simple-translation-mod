package translation.modid.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 计分板翻译Mixin
 * 通过拦截Gui类中所有drawString调用来翻译计分板文本
 */
@Mixin(Gui.class)
public abstract class ScoreboardMixin {
    
    @Unique
    private static final ConcurrentHashMap<String, String> scoreboardCache = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingTranslations = new ConcurrentHashMap<>();
    
    /**
     * 拦截Gui类中所有drawString调用中的Component参数
     * 通过检查调用栈来确定是否是计分板相关的渲染
     */
    @ModifyArg(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
        ),
        index = 1,
        require = 0
    )
    private Component translateScoreboardComponent(Component original) {
        // 检查调用栈，确定是否是计分板相关的渲染
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        boolean isScoreboard = false;
        
        // 检查调用栈中是否有计分板相关的方法
        for (int i = 0; i < Math.min(stackTrace.length, 15); i++) {
            StackTraceElement element = stackTrace[i];
            String methodName = element.getMethodName();
            String className = element.getClassName();
            
            // 检查方法名或类名是否包含scoreboard相关关键词
            if ((methodName != null && (
                methodName.toLowerCase().contains("scoreboard") ||
                methodName.toLowerCase().contains("score") ||
                methodName.equals("method_51718") || // 可能的混淆方法名
                methodName.equals("method_51719")
            )) || (className != null && className.toLowerCase().contains("score"))) {
                isScoreboard = true;
                break;
            }
        }
        
        if (!isScoreboard) {
            return original;
        }
        
        return translateComponent(original, false);
    }
    
    @Unique
    private Component translateComponent(Component original, boolean isTitle) {
        if (original == null) {
            return original;
        }
        
        TranslationConfig config = TranslationConfig.getInstance();
        if (!config.enabled || !config.autoTranslate || !config.translateScoreboard) {
            return original;
        }
        
        String text = original.getString();
        if (text == null || text.trim().isEmpty() || containsChinese(text)) {
            return original;
        }
        
        // 对于条目，跳过玩家名称和数字
        if (!isTitle) {
            // 跳过纯数字（分数）
            if (text.matches("^\\d+$")) {
                return original;
            }
            // 跳过玩家名称（通常长度较短且只包含字母数字下划线）
            if (text.length() <= 16 && text.matches("^[a-zA-Z0-9_]+$")) {
                return original;
            }
            // 跳过包含格式代码的短文本（可能是玩家名称）
            if (text.contains("§") && text.length() <= 20) {
                return original;
            }
        }
        
        // 检查缓存
        String cachedTranslation = scoreboardCache.get(text);
        if (cachedTranslation != null) {
            MutableComponent translated = Component.literal(cachedTranslation);
            translated.setStyle(original.getStyle());
            return translated;
        }
        
        // 检查是否正在翻译
        Long pendingTime = pendingTranslations.get(text);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            return original;
        }
        
        // 开始异步翻译
        pendingTranslations.put(text, System.currentTimeMillis());
        TranslationManager.getInstance().translate(text)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                        scoreboardCache.put(text, translated);
                    }
                    pendingTranslations.remove(text);
                });
        
        return original;
    }
    
    @Unique
    private static boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}

