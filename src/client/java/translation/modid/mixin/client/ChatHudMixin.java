package translation.modid.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import translation.modid.SimpleTranslation;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.*;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    
    @Shadow
    public abstract void addMessage(Component message, MessageSignature signature, GuiMessageTag tag);
    
    @Unique
    private static final Set<String> translatingMessages = Collections.synchronizedSet(new HashSet<>());
    
    @Unique
    private static final Set<String> translatedMessages = Collections.synchronizedSet(new HashSet<>());
    
    @Unique
    private static boolean isProcessingTranslation = false;
    
    // 拦截所有三参数的addMessage方法
    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessageWithSignature(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (!isProcessingTranslation) {
            Component modified = tryAddTranslation(message);
            if (modified != null) {
                ci.cancel();
                isProcessingTranslation = true;
                this.addMessage(modified, signature, tag);
                isProcessingTranslation = false;
            }
        }
    }
    
    // 拦截单参数的addMessage方法
    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessageSimple(Component message, CallbackInfo ci) {
        if (!isProcessingTranslation) {
            Component modified = tryAddTranslation(message);
            if (modified != null) {
                ci.cancel();
                isProcessingTranslation = true;
                this.addMessage(modified, null, null);
                isProcessingTranslation = false;
            }
        }
    }
    
    @Unique
    private static final Map<String, String> translationCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Unique
    private static final Map<String, Component> pendingMessages = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Unique
    private Component tryAddTranslation(Component message) {
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (!config.enabled || !config.autoTranslate) {
            return null;
        }
        
        String originalText = message.getString();
        
        if (originalText == null || originalText.trim().length() < 2) {
            return null;
        }
        
        // 跳过已包含翻译的消息
        if (originalText.contains("§a") || originalText.contains("[译]") || originalText.contains("§8→")) {
            return null;
        }
        
        // 跳过已经是中文的消息（包含中文字符）
        if (containsChinese(originalText)) {
            return null;
        }
        
        // 跳过系统UI消息
        if (isSystemUIMessage(originalText)) {
            return null;
        }
        
        // 检查是否有缓存的翻译
        String cached = translationCache.get(originalText);
        if (cached != null) {
            // 在原消息后追加翻译（同一行，紧贴）
            MutableComponent modified = message.copy();
            modified.append(Component.literal(" §a" + cached));
            return modified;
        }
        
        // 启动异步翻译，并记录这条消息等待后续追加
        translateMessage(originalText, message);
        
        return null; // 先显示原文
    }
    
    @Unique
    private void translateMessage(String originalText, Component originalMessage) {
        if (translatingMessages.contains(originalText)) {
            return;
        }
        translatingMessages.add(originalText);
        pendingMessages.put(originalText, originalMessage);
        
        SimpleTranslation.LOGGER.info("准备翻译消息: {}", originalText);
        
        TranslationManager.getInstance().translate(originalText)
            .thenAccept(translatedText -> {
                try {
                    if (translatedText != null && !translatedText.equals(originalText)) {
                        SimpleTranslation.LOGGER.info("翻译成功: {} -> {}", originalText, translatedText);
                        
                        // 缓存翻译结果
                        translationCache.put(originalText, translatedText);
                        translatedMessages.add(originalText);
                        translatedMessages.add(translatedText);
                        
                        // 立即在聊天栏追加翻译（紧贴在原消息后面）
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.gui != null && mc.gui.getChat() != null) {
                            Component msgToDisplay = pendingMessages.get(originalText);
                            if (msgToDisplay != null) {
                                // 在主线程中添加包含翻译的完整消息
                                mc.execute(() -> {
                                    MutableComponent fullMessage = msgToDisplay.copy();
                                    fullMessage.append(Component.literal(" §a" + translatedText));
                                    
                                    isProcessingTranslation = true;
                                    this.addMessage(fullMessage, null, null);
                                    isProcessingTranslation = false;
                                });
                            }
                        }
                    }
                } finally {
                    translatingMessages.remove(originalText);
                    pendingMessages.remove(originalText);
                }
            })
            .exceptionally(e -> {
                SimpleTranslation.LOGGER.error("翻译出错: " + originalText, e);
                translatingMessages.remove(originalText);
                pendingMessages.remove(originalText);
                return null;
            });
    }
    
    @Unique
    private boolean isSystemUIMessage(String text) {
        String trimmed = text.trim();
        
        // 跳过纯符号消息
        if (trimmed.matches("[\\s\\-=━─│┃┌┐└┘├┤┬┴┼╋╔╗╚╝╠╣╦╩╬]+")) {
            return true;
        }
        
        // 跳过纯空格
        if (trimmed.isEmpty() || trimmed.length() < 2) {
            return true;
        }
        
        return false;
    }
    
    @Unique
    private boolean containsChinese(String text) {
        // 检查是否包含中文字符
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }
    
}

