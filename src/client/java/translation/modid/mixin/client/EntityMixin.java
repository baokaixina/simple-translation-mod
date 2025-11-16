package translation.modid.mixin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(Entity.class)
public abstract class EntityMixin {
    
    @Unique
    private static final ConcurrentHashMap<String, String> entityNameCache = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingTranslations = new ConcurrentHashMap<>();

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void translateDisplayName(CallbackInfoReturnable<Component> cir) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateEntityName) {
            return;
        }

        Component originalName = cir.getReturnValue();
        if (originalName == null) {
            return;
        }

        String nameText = originalName.getString();
        if (nameText == null || nameText.trim().isEmpty() || containsChinese(nameText)) {
            return;
        }

        // 跳过玩家名称（通常不需要翻译，玩家名称通常包含格式代码）
        if (nameText.contains("§") && nameText.length() < 20) {
            // 可能是格式化的玩家名称，跳过
            return;
        }

        // 检查缓存
        String cachedTranslation = entityNameCache.get(nameText);
        if (cachedTranslation != null) {
            MutableComponent translatedName = Component.literal(cachedTranslation);
            translatedName.setStyle(originalName.getStyle());
            cir.setReturnValue(translatedName);
            return;
        }

        // 检查是否正在翻译
        Long pendingTime = pendingTranslations.get(nameText);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            // 正在翻译中，返回原文
            return;
        }

        // 开始异步翻译
        pendingTranslations.put(nameText, System.currentTimeMillis());
        TranslationManager.getInstance().translate(nameText)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(nameText)) {
                        entityNameCache.put(nameText, translated);
                    }
                    pendingTranslations.remove(nameText);
                });
        
        // 返回原文，等待翻译完成
        cir.setReturnValue(originalName);
    }

    @Unique
    private boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}

