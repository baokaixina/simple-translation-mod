package translation.modid.book;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import translation.modid.SimpleTranslation;
import translation.modid.config.TranslationConfig;
import translation.modid.translator.TranslationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 书本翻译叠加层
 */
public class BookTranslationOverlay {
    private static BookTranslationOverlay instance;
    private final Map<Integer, String> translatedPages = new HashMap<>();
    private final Map<Integer, Boolean> translatingPages = new HashMap<>();
    private final Map<Integer, List<String>> formattedTranslations = new HashMap<>();
    
    private int currentBookHash = 0;
    private boolean showTranslation = false;
    
    public static BookTranslationOverlay getInstance() {
        if (instance == null) {
            instance = new BookTranslationOverlay();
        }
        return instance;
    }
    
    /**
     * 重置书本（打开新书本时调用）
     */
    public void resetBook(int bookHash) {
        if (this.currentBookHash != bookHash) {
            translatedPages.clear();
            translatingPages.clear();
            formattedTranslations.clear();
            this.currentBookHash = bookHash;
            this.showTranslation = true; // 始终开启翻译显示
        }
    }
    
    /**
     * 设置是否显示翻译
     */
    public void setShowTranslation(boolean show) {
        this.showTranslation = true; // 始终为true，强制显示
    }
    
    /**
     * 翻译整本书（保持上下文连贯）
     */
    public void translateWholeBook(Map<Integer, String> pageContents, int totalPages) {
        TranslationConfig config = TranslationConfig.getInstance();
        if (!config.enabled || !config.translateBook) {
            return;
        }
        
        // 清空之前的翻译
        translatedPages.clear();
        formattedTranslations.clear();
        translatingPages.clear();
        
        // 标记所有页为正在翻译
        for (int i = 0; i < totalPages; i++) {
            translatingPages.put(i, true);
        }
        
        // 构建完整文本，每页用分隔符标记
        StringBuilder fullText = new StringBuilder();
        java.util.List<Integer> pageIndices = new java.util.ArrayList<>();
        
        for (int i = 0; i < totalPages; i++) {
            String content = pageContents.get(i);
            if (content != null && !content.trim().isEmpty()) {
                if (fullText.length() > 0) {
                    fullText.append("\n\n===第").append(i + 1).append("页===\n");
                }
                fullText.append(content);
                pageIndices.add(i);
            }
        }
        
        if (fullText.length() == 0) {
            return;
        }
        
        SimpleTranslation.LOGGER.info("开始翻译整本书，总字符数: {}", fullText.length());
        
        // 翻译整本书
        TranslationManager.getInstance().translate(fullText.toString())
            .thenAccept(translatedText -> {
                if (translatedText != null && !translatedText.equals(fullText.toString())) {
                    SimpleTranslation.LOGGER.info("整本书翻译完成，开始分页");
                    SimpleTranslation.LOGGER.info("翻译结果前200字符: {}", translatedText.substring(0, Math.min(200, translatedText.length())));
                    
                    // 按页分割翻译结果 - 使用更精确的正则表达式匹配分隔符
                    // 分隔符格式: \n\n===第数字页===\n
                    String[] translatedParts = translatedText.split("\\n\\n===第\\d+页===\\n");
                    
                    SimpleTranslation.LOGGER.info("分割后得到{}部分，期望{}页", translatedParts.length, pageIndices.size());
                    
                    // 如果分割失败（可能翻译API改变了格式），尝试其他方法
                    if (translatedParts.length != pageIndices.size() && translatedParts.length > 0) {
                        // 尝试更宽松的分割方式
                        translatedParts = translatedText.split("===第\\d+页===");
                        SimpleTranslation.LOGGER.info("使用宽松分割后得到{}部分", translatedParts.length);
                    }
                    
                    // 分配翻译结果到各页
                    if (translatedParts.length > 0 && pageIndices.size() > 0) {
                        int idx = 0;
                        String lastTrimmed = null;
                        for (String part : translatedParts) {
                            String trimmed = part.trim();
                            if (trimmed.isEmpty()) continue;
                            if (idx >= pageIndices.size()) break;
                            
                            int pageNum = pageIndices.get(idx);
                            translatedPages.put(pageNum, trimmed);
                            formatTranslationForPage(pageNum, trimmed);
                            SimpleTranslation.LOGGER.info("第{}页翻译已分配，内容长度: {}", pageNum + 1, trimmed.length());
                            lastTrimmed = trimmed;
                            idx++;
                            
                            // 确保翻译完成后屏幕能刷新显示
                            Minecraft.getInstance().execute(() -> {
                                // 在主线程中触发屏幕重绘
                                if (Minecraft.getInstance().screen != null) {
                                    // 屏幕会在下一帧自动重绘
                                }
                            });
                        }
                        
                        // 如果还有未分配的页面，可能是分割失败，尝试按原始顺序分配
                        if (idx < pageIndices.size() && translatedParts.length == 1 && lastTrimmed != null) {
                            // 如果只有一个部分，可能是翻译API没有保留分隔符，尝试按原始长度比例分割
                            SimpleTranslation.LOGGER.warn("翻译结果未正确分割，尝试按比例分配");
                            // 这种情况下，我们无法准确分割，只能显示第一页
                            if (pageIndices.size() > 0) {
                                int firstPage = pageIndices.get(0);
                                translatedPages.put(firstPage, lastTrimmed);
                                formatTranslationForPage(firstPage, lastTrimmed);
                            }
                        }
                    }
                } else {
                    SimpleTranslation.LOGGER.warn("翻译结果为空或与原文相同");
                    // 显示翻译失败提示
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        String errorMsg = "翻译结果为空或与原文相同";
                        net.minecraft.network.chat.Component errorMessage = net.minecraft.network.chat.Component.literal("§7[翻译] §c书本翻译失败: " + errorMsg)
                                .withStyle(net.minecraft.ChatFormatting.RED);
                        mc.player.sendSystemMessage(errorMessage);
                    }
                }
                
                // 清除翻译标记
                translatingPages.clear();
            })
            .exceptionally(e -> {
                SimpleTranslation.LOGGER.error("翻译整本书失败", e);
                
                // 显示翻译失败提示给用户
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null) {
                        // 提取关键错误信息
                        if (errorMsg.contains("超时")) {
                            errorMsg = "请求超时，请检查网络连接或稍后重试";
                        } else if (errorMsg.contains("无法连接")) {
                            errorMsg = "无法连接到翻译服务器，请检查网络";
                        } else if (errorMsg.length() > 50) {
                            errorMsg = errorMsg.substring(0, 50) + "...";
                        }
                    } else {
                        errorMsg = "未知错误";
                    }
                    
                    net.minecraft.network.chat.Component errorMessage = net.minecraft.network.chat.Component.literal("§7[翻译] §c书本翻译失败: " + errorMsg)
                            .withStyle(net.minecraft.ChatFormatting.RED);
                    mc.player.sendSystemMessage(errorMessage);
                }
                
                translatingPages.clear();
                return null;
            });
    }
    
    /**
     * 翻译单页（保持向后兼容）
     */
    public void translatePage(int pageNum, String originalText) {
        TranslationConfig config = TranslationConfig.getInstance();
        if (!config.enabled || !config.translateBook) {
            return;
        }
        
        // 如果已翻译或正在翻译，跳过
        if (translatedPages.containsKey(pageNum) || translatingPages.getOrDefault(pageNum, false)) {
            return;
        }
        
        if (originalText == null || originalText.trim().isEmpty()) {
            return;
        }
        
        translatingPages.put(pageNum, true);
        SimpleTranslation.LOGGER.info("开始翻译书本第{}页", pageNum + 1);
        
        TranslationManager.getInstance().translate(originalText)
            .thenAccept(translatedText -> {
                if (translatedText != null && !translatedText.equals(originalText)) {
                    translatedPages.put(pageNum, translatedText);
                    // 格式化翻译文本以适应书本页面
                    formatTranslationForPage(pageNum, translatedText);
                    SimpleTranslation.LOGGER.info("书本第{}页翻译完成", pageNum + 1);
                    
                    // 确保翻译完成后屏幕能刷新显示
                    Minecraft.getInstance().execute(() -> {
                        // 在主线程中触发屏幕重绘
                        if (Minecraft.getInstance().screen != null) {
                            // 屏幕会在下一帧自动重绘
                        }
                    });
                }
                translatingPages.put(pageNum, false);
            })
            .exceptionally(e -> {
                SimpleTranslation.LOGGER.error("翻译书本第{}页失败", pageNum + 1, e);
                translatingPages.put(pageNum, false);
                return null;
            });
    }
    
    /**
     * 格式化翻译文本以适应页面
     */
    private void formatTranslationForPage(int pageNum, String translation) {
        Font font = Minecraft.getInstance().font;
        List<String> lines = new ArrayList<>();
        
        // 清理所有格式代码（§和后面的一个字符）
        String cleanText = translation.replaceAll("§.", "");
        // 移除所有特殊字符
        cleanText = cleanText.replaceAll("[\\[\\]\\{\\}]", "");
        
        int maxWidth = 110; // 书本页面宽度，稍微减小避免超出
        StringBuilder currentLine = new StringBuilder();
        
        // 逐字符处理，支持中文换行
        for (int i = 0; i < cleanText.length(); i++) {
            char c = cleanText.charAt(i);
            String testLine = currentLine.toString() + c;
            
            // 检查是否换行符
            if (c == '\n') {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                continue;
            }
            
            // 检查宽度
            if (font.width(testLine) <= maxWidth) {
                currentLine.append(c);
            } else {
                // 当前行已满，开始新行
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder();
                currentLine.append(c);
            }
        }
        
        // 添加最后一行
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        // 限制最大行数，避免超出书页
        int maxLines = 12; // 书页最多显示12行
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
            // 最后一行添加省略号
            String lastLine = lines.get(maxLines - 1);
            lines.set(maxLines - 1, lastLine + "...");
        }
        
        formattedTranslations.put(pageNum, lines);
        SimpleTranslation.LOGGER.info("第{}页格式化完成，共{}行", pageNum, lines.size());
    }
    
    /**
     * 渲染翻译文本到书本页面
     */
    public void renderTranslation(GuiGraphics graphics, int pageNum, int x, int y, int width, int height) {
        if (!showTranslation) {
            return;
        }
        
        List<String> lines = formattedTranslations.get(pageNum);
        if (lines == null || lines.isEmpty()) {
            // 显示翻译中提示
            if (translatingPages.getOrDefault(pageNum, false)) {
                Font font = Minecraft.getInstance().font;
                String text = "§7§o正在翻译中...";
                graphics.drawString(font, text, x, y, 0x666666, false);
            } else if (translatedPages.containsKey(pageNum)) {
                // 已有翻译但未格式化，重新格式化
                String translation = translatedPages.get(pageNum);
                if (translation != null) {
                    formatTranslationForPage(pageNum, translation);
                    lines = formattedTranslations.get(pageNum);
                }
            }
            
            // 如果还是没有内容，直接返回
            if (lines == null || lines.isEmpty()) {
                return;
            }
        }
        
        // 渲染翻译文本
        Font font = Minecraft.getInstance().font;
        int lineHeight = 9; // 书本字体行高，稍微小一点以容纳更多行
        int currentY = y;
        
        // 计算实际需要的高度
        int actualHeight = Math.min(lines.size() * lineHeight, height);
        
        // 半透明黑色背景 - 根据实际内容调整大小
        graphics.fill(x - 2, y - 2, x + width, y + actualHeight + 2, 0xCC000000);
        
        // 渲染每一行
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (currentY + lineHeight > y + height) {
                break; // 超出页面高度
            }
            // 使用白色文字渲染翻译，更清晰
            graphics.drawString(font, line, x, currentY, 0xFFFFFF, false);
            currentY += lineHeight;
        }
    }
    
    /**
     * 获取翻译文本
     */
    public String getTranslation(int pageNum) {
        return translatedPages.get(pageNum);
    }
    
    /**
     * 清除所有翻译
     */
    public void clearAll() {
        translatedPages.clear();
        translatingPages.clear();
        formattedTranslations.clear();
        showTranslation = false;
    }
}

