package translation.modid.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    private static final ConcurrentHashMap<String, String> chatTooltipCache = new ConcurrentHashMap<>();

    @Inject(method = "getClickedComponentStyleAt", at = @At("RETURN"), cancellable = true)
    private void translateChatTooltip(double mouseX, double mouseY, CallbackInfoReturnable<Style> cir) {
        TranslationConfig config = TranslationConfig.getInstance();

        if (!config.enabled || !config.autoTranslate || !config.translateChatTooltip) {
            return;
        }

        Style originalStyle = cir.getReturnValue();
        if (originalStyle == null) {
            return;
        }

        HoverEvent hoverEvent = originalStyle.getHoverEvent();
        if (hoverEvent == null || hoverEvent.getAction() != HoverEvent.Action.SHOW_TEXT) {
            return;
        }

        Component hoverContent = hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT);
        if (hoverContent == null) {
            return;
        }

        String originalText = hoverContent.getString();
        if (originalText == null || originalText.trim().isEmpty() || containsChinese(originalText)) {
            return;
        }

        String cachedTranslation = chatTooltipCache.get(originalText);
        if (cachedTranslation != null) {
            MutableComponent translatedComponent = Component.literal(cachedTranslation);
            translatedComponent.setStyle(hoverContent.getStyle());
            HoverEvent translatedHoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, translatedComponent);
            Style newStyle = originalStyle.withHoverEvent(translatedHoverEvent);
            cir.setReturnValue(newStyle);
        } else {
            translateAsync(originalText);
        }
    }

    private void translateAsync(String text) {
        TranslationManager.getInstance().translate(text)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                        chatTooltipCache.put(text, translated);
                    }
                });
    }

    private boolean containsChinese(String text) {
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}

