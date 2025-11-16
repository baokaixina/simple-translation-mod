package translation.modid.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import translation.modid.SimpleTranslation;
import translation.modid.book.BookTranslationOverlay;
import translation.modid.config.TranslationConfig;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin {
    
    @Shadow
    private BookViewScreen.BookAccess bookAccess;
    
    @Shadow
    private int currentPage;
    
    @Unique
    private Button translateButton;
    
    @Unique
    private boolean hasLoggedButton = false;
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        SimpleTranslation.LOGGER.info("===== BookViewScreen.init 被调用（成书） =====");
        
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (config.enabled && config.translateBook) {
            BookViewScreen screen = (BookViewScreen)(Object)this;
            
            // 重置书本翻译系统，自动开启翻译显示
            BookTranslationOverlay.getInstance().resetBook(System.identityHashCode(bookAccess));
            
            // 添加翻译按钮 - 放在书本右侧空白处
            int screenWidth = screen.width;
            int bookLeft = (screenWidth - 192) / 2;
            int bookTop = 2;
            
            // 按钮放在书本右侧，避开所有原版按钮
            int buttonX = bookLeft + 192 + 5; // 书本右边缘外侧
            int buttonY = bookTop + 10; // 书本顶部偏下
            
            translateButton = Button.builder(
                Component.literal("翻译本页"),
                button -> {
                    SimpleTranslation.LOGGER.info("翻译按钮onPress回调被调用");
                    // 翻译当前可见的页面
                    translateCurrentVisiblePages();
                    button.setMessage(Component.literal("§a已翻译"));
                    button.active = false;
                })
                .bounds(buttonX, buttonY, 98, 20)
                .build();
            
            // 尝试将按钮添加到屏幕的widget列表
            try {
                // 尝试使用反射调用 addRenderableWidget
                java.lang.reflect.Method addMethod = screen.getClass().getMethod("addRenderableWidget", net.minecraft.client.gui.components.Renderable.class);
                addMethod.invoke(screen, translateButton);
                SimpleTranslation.LOGGER.info("翻译按钮已通过addRenderableWidget添加到屏幕");
            } catch (Exception e) {
                // 如果失败，尝试添加到children列表
                try {
                    addWidgetToList(screen, "children", translateButton);
                    SimpleTranslation.LOGGER.info("翻译按钮已通过children列表添加到屏幕");
                } catch (Exception e2) {
                    SimpleTranslation.LOGGER.warn("无法将翻译按钮添加到屏幕widget列表，将使用手动渲染: {}", e2.getMessage());
                }
            }
            
            SimpleTranslation.LOGGER.info("翻译按钮已创建 - 位置: ({}, {}), 尺寸: {}x{}", 
                buttonX, buttonY, 98, 20);
            SimpleTranslation.LOGGER.info("书本界面 - 宽度: {}, 左边距: {}, 顶部: {}", 
                screenWidth, bookLeft, bookTop);
        }
    }
    
    @Inject(method = "pageForward", at = @At("TAIL"))
    private void onPageForward(CallbackInfo ci) {
        resetTranslateButton();
    }
    
    @Inject(method = "pageBack", at = @At("TAIL"))
    private void onPageBack(CallbackInfo ci) {
        resetTranslateButton();
    }
    
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TranslationConfig config = TranslationConfig.getInstance();
        if (config.enabled && config.translateBook) {
            // 手动渲染翻译按钮
            if (translateButton != null) {
                // 确保按钮可见
                translateButton.visible = true;
                translateButton.active = true;
                translateButton.render(graphics, mouseX, mouseY, delta);
                
                // 添加调试信息（仅第一次渲染时）
                if (!hasLoggedButton) {
                    SimpleTranslation.LOGGER.info("按钮正在渲染 - X:{}, Y:{}, 宽:{}, 高:{}, 可见:{}", 
                        translateButton.getX(), translateButton.getY(), 
                        translateButton.getWidth(), translateButton.getHeight(),
                        translateButton.visible);
                    hasLoggedButton = true;
                }
            } else {
                if (!hasLoggedButton) {
                    SimpleTranslation.LOGGER.error("翻译按钮为null！");
                    hasLoggedButton = true;
                }
            }
            
            // 渲染翻译叠加层 - 只显示当前页
            BookViewScreen screen = (BookViewScreen)(Object)this;
            int screenWidth = screen.width;
            int left = (screenWidth - 192) / 2;
            int top = 2;
            
            // 只渲染当前查看的页面翻译
            // 成书是双页显示，但我们只翻译左页（偶数页）
            if (currentPage < bookAccess.getPageCount()) {
                // 渲染在左页上
                BookTranslationOverlay.getInstance().renderTranslation(
                    graphics, currentPage, left + 36, top + 16, 114, 128
                );
            }
        }
    }
    
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        TranslationConfig config = TranslationConfig.getInstance();
        if (config.enabled && config.translateBook && translateButton != null) {
            // 检查点击是否在按钮区域内
            boolean isButtonClick = mouseX >= translateButton.getX() && 
                                   mouseX <= translateButton.getX() + translateButton.getWidth() &&
                                   mouseY >= translateButton.getY() && 
                                   mouseY <= translateButton.getY() + translateButton.getHeight();
            
            if (isButtonClick) {
                SimpleTranslation.LOGGER.info("检测到翻译按钮点击 - 位置: ({}, {}), 按钮区域: ({}, {}) - ({}, {})", 
                    mouseX, mouseY, translateButton.getX(), translateButton.getY(),
                    translateButton.getX() + translateButton.getWidth(), 
                    translateButton.getY() + translateButton.getHeight());
                
                // 直接调用按钮的onPress回调，而不是通过mouseClicked
                if (translateButton.active && translateButton.visible) {
                    translateButton.onPress();
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
            
            // 也尝试标准的mouseClicked方法
            if (translateButton.mouseClicked(mouseX, mouseY, button)) {
                SimpleTranslation.LOGGER.info("翻译按钮mouseClicked返回true");
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
    
    @Unique
    private void translateCurrentVisiblePages() {
        try {
            int totalPages = bookAccess.getPageCount();
            SimpleTranslation.LOGGER.info("开始翻译整本书，共{}页", totalPages);
            
            // 显示翻译
            BookTranslationOverlay.getInstance().setShowTranslation(true);
            
            // 收集所有页面内容
            java.util.Map<Integer, String> pageContents = new java.util.HashMap<>();
            
            for (int i = 0; i < totalPages; i++) {
                FormattedText pageContent = bookAccess.getPage(i);
                if (pageContent != null) {
                    String text = pageContent.getString();
                    if (text != null && !text.trim().isEmpty()) {
                        pageContents.put(i, text);
                        SimpleTranslation.LOGGER.info("第{}页内容: {}", i, text.substring(0, Math.min(50, text.length())));
                    }
                }
            }
            
            if (pageContents.isEmpty()) {
                SimpleTranslation.LOGGER.warn("所有页面内容为空");
                return;
            }
            
            // 翻译整本书的内容
            BookTranslationOverlay.getInstance().translateWholeBook(pageContents, totalPages);
            
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("翻译页面时出错", e);
        }
    }
    
    @Unique
    private void translatePage(int pageNum) {
        try {
            if (pageNum < 0 || pageNum >= bookAccess.getPageCount()) {
                return;
            }
            
            // 获取页面内容
            FormattedText pageContent = bookAccess.getPage(pageNum);
            if (pageContent == null) {
                return;
            }
            
            String originalText = pageContent.getString();
            
            if (originalText != null && !originalText.trim().isEmpty()) {
                SimpleTranslation.LOGGER.info("提交翻译：第{}页", pageNum);
                BookTranslationOverlay.getInstance().translatePage(pageNum, originalText);
            }
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("获取书本第{}页内容时出错", pageNum, e);
        }
    }
    
    @Unique
    private void resetTranslateButton() {
        if (translateButton != null) {
            translateButton.setMessage(Component.literal("翻译本页"));
            translateButton.active = true;
        }
    }
    
    @Unique
    @SuppressWarnings("unchecked")
    private void addWidgetToList(BookViewScreen screen, String fieldName, Button widget) {
        try {
            Field field = screen.getClass().getSuperclass().getDeclaredField(fieldName);
            field.setAccessible(true);
            List<Object> list = (List<Object>) field.get(screen);
            list.add(widget);
        } catch (Exception e) {
            // 静默忽略，可能是字段名不对
        }
    }
}
