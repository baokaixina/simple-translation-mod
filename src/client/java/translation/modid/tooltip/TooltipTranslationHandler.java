package translation.modid.tooltip;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TooltipTranslationHandler {
    private static final ConcurrentHashMap<String, String> tooltipCache = new ConcurrentHashMap<>();

    public static void register() {
        ItemTooltipCallback.EVENT.register((item, context, lines) -> {
            TranslationConfig config = TranslationConfig.getInstance();

            if (!config.enabled || !config.autoTranslate || !config.translateTooltip) {
                return;
            }

            if (lines == null || lines.isEmpty()) {
                return;
            }

            List<Integer> toTranslateIndices = new ArrayList<>();
            List<Component> toTranslateComponents = new ArrayList<>();
            StringBuilder combinedText = new StringBuilder();

            for (int i = 0; i < lines.size(); i++) {
                Component component = lines.get(i);
                try {
                    String text = component.getString();
                    if (text != null && !text.trim().isEmpty() && !containsChinese(text)) {
                        toTranslateIndices.add(i);
                        toTranslateComponents.add(component);
                        if (combinedText.length() > 0) {
                            combinedText.append("\n");
                        }
                        combinedText.append(text);
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (combinedText.length() == 0) {
                return;
            }

            String combinedString = combinedText.toString();
            String cached = tooltipCache.get(combinedString);

            if (cached != null) {
                String[] translatedLines = cached.split("\n");
                for (int i = 0; i < translatedLines.length && i < toTranslateIndices.size(); i++) {
                    int lineIndex = toTranslateIndices.get(i);
                    try {
                        Component originalComponent = toTranslateComponents.get(i);
                        MutableComponent translatedComponent = Component.literal(translatedLines[i]);
                        translatedComponent.setStyle(originalComponent.getStyle());
                        lines.set(lineIndex, translatedComponent);
                    } catch (Exception e) {
                        continue;
                    }
                }
            } else {
                translateAsync(combinedString);
            }
        });
    }

    private static void translateAsync(String text) {
        TranslationManager.getInstance().translate(text)
                .thenAccept(translated -> {
                    if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                        tooltipCache.put(text, translated);
                    }
                });
    }

    private static boolean containsChinese(String text) {
        if (text == null) return false;
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }
}
