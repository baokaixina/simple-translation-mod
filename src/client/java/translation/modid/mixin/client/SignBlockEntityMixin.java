package translation.modid.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import translation.modid.config.TranslationConfig;
import translation.modid.sign.SignTranslationManager;

/**
 * 告示牌方块实体 Mixin - 在获取文本时返回翻译后的文本
 */
@Mixin(SignBlockEntity.class)
public class SignBlockEntityMixin {
    
    /**
     * 拦截获取正面文本的方法，返回翻译后的文本
     */
    @Inject(method = "getFrontText", at = @At("RETURN"), cancellable = true)
    private void onGetFrontText(CallbackInfoReturnable<SignText> cir) {
        TranslationConfig config = TranslationConfig.getInstance();
        if (!config.enabled || !config.translateSign) {
            return;
        }
        
        // 直接使用 this 来获取 SignBlockEntity 实例
        SignBlockEntity signEntity = (SignBlockEntity)(Object)this;
        BlockPos pos = signEntity.getBlockPos();
        
        Component[] translatedLines = SignTranslationManager.getInstance().getTranslatedText(pos);
        
        if (translatedLines != null) {
            SignText original = cir.getReturnValue();
            
            // 创建一个新的 SignText，包含翻译后的文本
            SignText translated = new SignText(
                translatedLines,
                translatedLines, // filtered messages 也使用翻译文本
                original.getColor(),
                original.hasGlowingText()
            );
            
            cir.setReturnValue(translated);
        }
    }
}

