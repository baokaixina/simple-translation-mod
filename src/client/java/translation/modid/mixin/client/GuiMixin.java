package translation.modid.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(Gui.class)
public abstract class GuiMixin {
    
    @Shadow
    private Component title;
    
    @Shadow
    private Component subtitle;
    
    @Unique
    private static final ConcurrentHashMap<String, String> titleCommandCache = new ConcurrentHashMap<>();
    
    @Unique
    private Component lastTranslatedTitle;
    
    @Unique
    private Component lastTranslatedSubtitle;
    
    @Unique
    private Component lastTranslatedOverlayMessage;
    
    @Unique
    private String lastTitleText;
    
    @Unique
    private String lastSubtitleText;
    
    @Unique
    private String lastOverlayMessageText;
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingTitleTranslations = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingSubtitleTranslations = new ConcurrentHashMap<>();
    
    @Unique
    private static final ConcurrentHashMap<String, Long> pendingActionbarTranslations = new ConcurrentHashMap<>();
    
    /**
     * 拦截渲染方法，在渲染时替换title和subtitle
     * 这样可以确保每次渲染时都检查并应用翻译
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {
        translateTitleComponent();
        translateSubtitleComponent();
        translateOverlayMessageComponent();
    }
    
    @Unique
    private void translateTitleComponent() {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateTitleCommand) {
            lastTranslatedTitle = null;
            lastTitleText = null;
            return;
        }
        
        if (this.title == null) {
            lastTranslatedTitle = null;
            lastTitleText = null;
            return;
        }
        
        String titleText = this.title.getString();
        if (titleText == null || titleText.trim().isEmpty() || containsChinese(titleText)) {
            lastTranslatedTitle = null;
            lastTitleText = null;
            return;
        }
        
        // 如果文本没有变化，且已有翻译，直接使用
        if (titleText.equals(lastTitleText) && lastTranslatedTitle != null) {
            this.title = lastTranslatedTitle;
            return;
        }
        
        // 检查缓存
        String cachedTranslation = titleCommandCache.get(titleText);
        if (cachedTranslation != null) {
            MutableComponent translatedTitle = Component.literal(cachedTranslation);
            translatedTitle.setStyle(this.title.getStyle());
            this.title = translatedTitle;
            lastTranslatedTitle = translatedTitle;
            lastTitleText = titleText;
            return;
        }
        
        // 检查是否正在翻译
        Long pendingTime = pendingTitleTranslations.get(titleText);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            // 正在翻译中，保持原文
            return;
        }
        
        // 开始异步翻译
        pendingTitleTranslations.put(titleText, System.currentTimeMillis());
        TranslationManager.getInstance().translate(titleText)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(titleText)) {
                        titleCommandCache.put(titleText, translated);
                        // 在主线程中更新title
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null) {
                            mc.execute(() -> {
                                if (this.title != null && this.title.getString().equals(titleText)) {
                                    MutableComponent translatedTitle = Component.literal(translated);
                                    translatedTitle.setStyle(this.title.getStyle());
                                    this.title = translatedTitle;
                                    lastTranslatedTitle = translatedTitle;
                                    lastTitleText = titleText;
                                }
                            });
                        }
                    }
                    pendingTitleTranslations.remove(titleText);
                });
    }
    
    @Unique
    private void translateSubtitleComponent() {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateTitleCommand) {
            lastTranslatedSubtitle = null;
            lastSubtitleText = null;
            return;
        }
        
        if (this.subtitle == null) {
            lastTranslatedSubtitle = null;
            lastSubtitleText = null;
            return;
        }
        
        String subtitleText = this.subtitle.getString();
        if (subtitleText == null || subtitleText.trim().isEmpty() || containsChinese(subtitleText)) {
            lastTranslatedSubtitle = null;
            lastSubtitleText = null;
            return;
        }
        
        // 如果文本没有变化，且已有翻译，直接使用
        if (subtitleText.equals(lastSubtitleText) && lastTranslatedSubtitle != null) {
            this.subtitle = lastTranslatedSubtitle;
            return;
        }
        
        // 检查缓存
        String cachedTranslation = titleCommandCache.get(subtitleText);
        if (cachedTranslation != null) {
            MutableComponent translatedSubtitle = Component.literal(cachedTranslation);
            translatedSubtitle.setStyle(this.subtitle.getStyle());
            this.subtitle = translatedSubtitle;
            lastTranslatedSubtitle = translatedSubtitle;
            lastSubtitleText = subtitleText;
            return;
        }
        
        // 检查是否正在翻译
        Long pendingTime = pendingSubtitleTranslations.get(subtitleText);
        if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
            // 正在翻译中，保持原文
            return;
        }
        
        // 开始异步翻译
        pendingSubtitleTranslations.put(subtitleText, System.currentTimeMillis());
        TranslationManager.getInstance().translate(subtitleText)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(subtitleText)) {
                        titleCommandCache.put(subtitleText, translated);
                        // 在主线程中更新subtitle
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null) {
                            mc.execute(() -> {
                                if (this.subtitle != null && this.subtitle.getString().equals(subtitleText)) {
                                    MutableComponent translatedSubtitle = Component.literal(translated);
                                    translatedSubtitle.setStyle(this.subtitle.getStyle());
                                    this.subtitle = translatedSubtitle;
                                    lastTranslatedSubtitle = translatedSubtitle;
                                    lastSubtitleText = subtitleText;
                                }
                            });
                        }
                    }
                    pendingSubtitleTranslations.remove(subtitleText);
                });
    }
    
    @Unique
    private void translateOverlayMessageComponent() {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate || !config.translateTitleCommand) {
            lastTranslatedOverlayMessage = null;
            lastOverlayMessageText = null;
            return;
        }
        
        // 尝试通过反射访问 overlayMessage 字段
        // 在 Minecraft 1.20.1 中，字段名可能不同，所以使用反射来尝试多个可能的字段名
        try {
            Gui gui = (Gui)(Object)this;
            Component overlayMessage = null;
            
            // 尝试多个可能的字段名
            String[] possibleFieldNames = {"overlayMessage", "overlay", "actionbarMessage", "actionbar"};
            java.lang.reflect.Field field = null;
            
            for (String fieldName : possibleFieldNames) {
                try {
                    field = Gui.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(gui);
                    if (value instanceof Component) {
                        overlayMessage = (Component) value;
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // 继续尝试下一个字段名
                }
            }
            
            if (overlayMessage == null || field == null) {
                lastTranslatedOverlayMessage = null;
                lastOverlayMessageText = null;
                return;
            }
            
            String overlayText = overlayMessage.getString();
            if (overlayText == null || overlayText.trim().isEmpty() || containsChinese(overlayText)) {
                lastTranslatedOverlayMessage = null;
                lastOverlayMessageText = null;
                return;
            }
            
            // 如果文本没有变化，且已有翻译，直接使用
            if (overlayText.equals(lastOverlayMessageText) && lastTranslatedOverlayMessage != null) {
                field.set(gui, lastTranslatedOverlayMessage);
                return;
            }
            
            // 检查缓存
            String cachedTranslation = titleCommandCache.get(overlayText);
            if (cachedTranslation != null) {
                MutableComponent translatedOverlay = Component.literal(cachedTranslation);
                translatedOverlay.setStyle(overlayMessage.getStyle());
                field.set(gui, translatedOverlay);
                lastTranslatedOverlayMessage = translatedOverlay;
                lastOverlayMessageText = overlayText;
                return;
            }
            
            // 检查是否正在翻译
            Long pendingTime = pendingActionbarTranslations.get(overlayText);
            if (pendingTime != null && System.currentTimeMillis() - pendingTime < 5000) {
                // 正在翻译中，保持原文
                return;
            }
            
            // 开始异步翻译
            final java.lang.reflect.Field finalField = field;
            pendingActionbarTranslations.put(overlayText, System.currentTimeMillis());
            TranslationManager.getInstance().translate(overlayText)
                    .thenAccept(translated -> {
                        if (translated != null && !translated.isEmpty() && !translated.equals(overlayText)) {
                            titleCommandCache.put(overlayText, translated);
                            // 在主线程中更新 overlayMessage
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null) {
                                mc.execute(() -> {
                                    try {
                                        Gui guiInstance = (Gui)(Object)GuiMixin.this;
                                        Component currentOverlay = (Component) finalField.get(guiInstance);
                                        if (currentOverlay != null && currentOverlay.getString().equals(overlayText)) {
                                            MutableComponent translatedOverlay = Component.literal(translated);
                                            translatedOverlay.setStyle(currentOverlay.getStyle());
                                            finalField.set(guiInstance, translatedOverlay);
                                            lastTranslatedOverlayMessage = translatedOverlay;
                                            lastOverlayMessageText = overlayText;
                                        }
                                    } catch (Exception e) {
                                        // 忽略字段访问异常
                                    }
                                });
                            }
                        }
                        pendingActionbarTranslations.remove(overlayText);
                    });
        } catch (Exception e) {
            // 如果无法访问 overlayMessage 字段，忽略错误
            // 这在 Minecraft 1.20.1 中可能是正常的，因为字段名可能不同或不存在
        }
    }
    
    
    @Unique
    private boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}

