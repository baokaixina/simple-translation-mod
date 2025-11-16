package translation.modid.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
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

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
    
    @Unique
    private Button translateButton;
    
    @Unique
    private int lastTranslatedPage = -1;
    
    @Unique
    private boolean hasLoggedButton = false;
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        SimpleTranslation.LOGGER.info("===== BookEditScreen.init 被调用（书与笔） =====");
        
        TranslationConfig config = TranslationConfig.getInstance();
        
        if (config.enabled && config.translateBook) {
            BookEditScreen screen = (BookEditScreen)(Object)this;
            
            // 重置书本翻译系统，自动开启翻译显示
            BookTranslationOverlay.getInstance().resetBook(System.identityHashCode(this));
            
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
                    translateCurrentPage(screen);
                    button.setMessage(Component.literal("§a已翻译"));
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
    
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TranslationConfig config = TranslationConfig.getInstance();
        if (config.enabled && config.translateBook) {
            try {
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
                
                // 检查页码是否变化
                BookEditScreen screen = (BookEditScreen)(Object)this;
                int currentPage = getCurrentPageNumber(screen);
                
                if (currentPage != lastTranslatedPage && translateButton != null) {
                    // 页码变化，重置按钮
                    translateButton.setMessage(Component.literal("翻译本页"));
                }
                
                // 渲染翻译叠加层
                int screenWidth = screen.width;
                int left = (screenWidth - 192) / 2;
                int top = 2;
                
                BookTranslationOverlay.getInstance().renderTranslation(
                    graphics, currentPage, left + 36, top + 16, 114, 128
                );
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("渲染翻译按钮时出错", e);
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
    private void translateCurrentPage(BookEditScreen screen) {
        try {
            String[] pages = getPages(screen);
            
            if (pages == null || pages.length == 0) {
                SimpleTranslation.LOGGER.warn("无法获取页面内容");
                return;
            }
            
            SimpleTranslation.LOGGER.info("开始翻译整本书，共{}页", pages.length);
            
            // 显示翻译
            BookTranslationOverlay.getInstance().setShowTranslation(true);
            
            // 收集所有非空页面内容
            StringBuilder fullText = new StringBuilder();
            java.util.Map<Integer, String> pageContents = new java.util.HashMap<>();
            
            for (int i = 0; i < pages.length; i++) {
                String pageContent = pages[i];
                if (pageContent != null && !pageContent.trim().isEmpty()) {
                    pageContents.put(i, pageContent);
                    if (fullText.length() > 0) {
                        fullText.append("\n\n===第").append(i + 1).append("页===\n");
                    }
                    fullText.append(pageContent);
                    SimpleTranslation.LOGGER.info("第{}页内容: {}", i, pageContent.substring(0, Math.min(50, pageContent.length())));
                }
            }
            
            if (fullText.length() == 0) {
                SimpleTranslation.LOGGER.warn("所有页面内容为空");
                return;
            }
            
            // 翻译整本书的内容
            BookTranslationOverlay.getInstance().translateWholeBook(pageContents, pages.length);
            
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("翻译书与笔时出错", e);
        }
    }
    
    @Unique
    private int getCurrentPageNumber(BookEditScreen screen) {
        try {
            // 尝试多个可能的字段名
            String[] possibleNames = {"currentPage", "field_3926", "f_98882_"};
            
            for (String fieldName : possibleNames) {
                try {
                    Field field = screen.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.getInt(screen);
                } catch (NoSuchFieldException ignored) {
                }
            }
            
            // 如果都失败了，尝试遍历所有int字段
            for (Field field : screen.getClass().getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    int value = field.getInt(screen);
                    // 假设页码在0-100之间
                    if (value >= 0 && value < 100) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            // 静默处理
        }
        return 0;
    }
    
    @Unique
    private String[] getPages(BookEditScreen screen) {
        try {
            // 尝试多个可能的字段名（1.20.1使用List<String>）
            String[] possibleNames = {"pages", "field_3925", "f_98881_"};
            
            for (String fieldName : possibleNames) {
                try {
                    Field field = screen.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(screen);
                    
                    SimpleTranslation.LOGGER.info("找到字段 '{}', 类型: {}", fieldName, value.getClass().getName());
                    
                    // 尝试String[]类型
                    if (value instanceof String[]) {
                        String[] pages = (String[]) value;
                        SimpleTranslation.LOGGER.info("成功获取String[]类型，共{}页", pages.length);
                        return pages;
                    }
                    
                    // 尝试List<String>类型（1.20.1使用这个）
                    if (value instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) value;
                        String[] pages = new String[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            pages[i] = list.get(i).toString();
                        }
                        SimpleTranslation.LOGGER.info("成功获取List<String>类型，共{}页", pages.length);
                        return pages;
                    }
                } catch (NoSuchFieldException e) {
                    SimpleTranslation.LOGGER.debug("字段 '{}' 不存在", fieldName);
                }
            }
            
            // 如果上面都失败，尝试遍历所有字段找List类型
            SimpleTranslation.LOGGER.info("尝试遍历所有字段查找List<String>...");
            for (Field field : screen.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) value;
                    if (!list.isEmpty() && list.get(0) instanceof String) {
                        String[] pages = new String[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            pages[i] = list.get(i).toString();
                        }
                        SimpleTranslation.LOGGER.info("通过遍历找到List<String>字段 '{}', 共{}页", field.getName(), pages.length);
                        return pages;
                    }
                }
            }
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("获取pages字段失败", e);
        }
        
        SimpleTranslation.LOGGER.warn("所有尝试都失败，无法获取页面内容");
        return null;
    }
    
    @Unique
    @SuppressWarnings("unchecked")
    private void addWidgetToList(BookEditScreen screen, String fieldName, Button widget) {
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
