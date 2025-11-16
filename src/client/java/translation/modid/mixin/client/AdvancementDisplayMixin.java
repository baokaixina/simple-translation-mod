package translation.modid.mixin.client;

import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(DisplayInfo.class)
public abstract class AdvancementDisplayMixin {
    
    @Unique
    private static final ConcurrentHashMap<String, String> advancementCache = new ConcurrentHashMap<>();
    
    @Unique
    private Component cachedTitle;
    @Unique
    private Component cachedDescription;
    @Unique
    private boolean titleDirty;
    @Unique
    private boolean descriptionDirty;
    @Unique
    private static long gameStartTime = System.currentTimeMillis();
    @Unique
    private static final long INIT_DELAY = 5000; // 游戏启动后5秒内不翻译成就

    @Unique
    private boolean shouldTranslate() {
        // 只在渲染线程中翻译，并且游戏已启动一段时间
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return false;
        }
        // 检查是否在渲染线程
        if (!mc.isSameThread()) {
            return false;
        }
        // 游戏启动后等待一段时间再开始翻译
        if (System.currentTimeMillis() - gameStartTime < INIT_DELAY) {
            return false;
        }
        return true;
    }

    @Inject(method = "getTitle", at = @At("RETURN"), cancellable = true)
    private void translateTitle(CallbackInfoReturnable<Component> cir) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateAdvancements) {
            cachedTitle = null;
            titleDirty = false;
            return;
        }

        // 只在应该翻译的时候才翻译
        if (!shouldTranslate()) {
            return;
        }

        Component originalTitle = cir.getReturnValue();
        if (originalTitle == null) {
            return;
        }

        String titleText = originalTitle.getString();
        if (titleText == null || titleText.trim().isEmpty() || containsChinese(titleText)) {
            return;
        }

        String cachedTranslation = advancementCache.get(titleText);
        if (cachedTranslation != null) {
            MutableComponent translatedTitle = Component.literal(cachedTranslation);
            translatedTitle.setStyle(originalTitle.getStyle());
            cir.setReturnValue(translatedTitle);
            cachedTitle = translatedTitle;
            return;
        }

        if (titleDirty) {
            titleDirty = false;
            String cached = advancementCache.get(titleText);
            if (cached != null) {
                MutableComponent translatedTitle = Component.literal(cached);
                translatedTitle.setStyle(originalTitle.getStyle());
                cir.setReturnValue(translatedTitle);
                cachedTitle = translatedTitle;
            } else {
                cir.setReturnValue(originalTitle);
            }
        } else if (cachedTitle == null) {
            titleDirty = true;
            TranslationManager.getInstance().translate(titleText)
                    .thenAccept(translated -> {
                        if (translated != null && !translated.isEmpty() && !translated.equals(titleText)) {
                            advancementCache.put(titleText, translated);
                            titleDirty = false;
                        }
                    });
            cir.setReturnValue(originalTitle);
        } else {
            cir.setReturnValue(cachedTitle);
        }
    }

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true)
    private void translateDescription(CallbackInfoReturnable<Component> cir) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateAdvancements) {
            cachedDescription = null;
            descriptionDirty = false;
            return;
        }

        // 只在应该翻译的时候才翻译
        if (!shouldTranslate()) {
            return;
        }

        Component originalDescription = cir.getReturnValue();
        if (originalDescription == null) {
            return;
        }

        String descriptionText = originalDescription.getString();
        if (descriptionText == null || descriptionText.trim().isEmpty() || containsChinese(descriptionText)) {
            return;
        }

        String cachedTranslation = advancementCache.get(descriptionText);
        if (cachedTranslation != null) {
            MutableComponent translatedDescription = Component.literal(cachedTranslation);
            translatedDescription.setStyle(originalDescription.getStyle());
            cir.setReturnValue(translatedDescription);
            cachedDescription = translatedDescription;
            return;
        }

        if (descriptionDirty) {
            descriptionDirty = false;
            String cached = advancementCache.get(descriptionText);
            if (cached != null) {
                MutableComponent translatedDescription = Component.literal(cached);
                translatedDescription.setStyle(originalDescription.getStyle());
                cir.setReturnValue(translatedDescription);
                cachedDescription = translatedDescription;
            } else {
                cir.setReturnValue(originalDescription);
            }
        } else if (cachedDescription == null) {
            descriptionDirty = true;
            TranslationManager.getInstance().translate(descriptionText)
                    .thenAccept(translated -> {
                        if (translated != null && !translated.isEmpty() && !translated.equals(descriptionText)) {
                            advancementCache.put(descriptionText, translated);
                            descriptionDirty = false;
                        }
                    });
            cir.setReturnValue(originalDescription);
        } else {
            cir.setReturnValue(cachedDescription);
        }
    }

    private boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}

