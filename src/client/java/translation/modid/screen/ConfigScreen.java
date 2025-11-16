package translation.modid.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import translation.modid.config.TranslationConfig;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private EditBox appIdField;
    private EditBox secretKeyField;
    private EditBox sourceLangField;
    private EditBox targetLangField;
    private Button toggleButton;
    private Button apiTypeButton;
    
    // 百度千帆大模型字段
    private EditBox baiduLLMApiKeyField;
    private EditBox baiduLLMSecretKeyField;
    private EditBox baiduLLMModelField;
    
    // LLM相关字段
    private EditBox llmApiKeyField;
    private EditBox llmApiUrlField;
    private EditBox llmModelField;
    private EditBox llmSystemPromptField;
    
    // 滚动相关
    private int scrollOffset = 0;
    private static final int SCROLL_SPEED = 20;
    private static final int CONTENT_START_Y = 40;
    
    // 保存和取消按钮引用（用于提高点击优先级）
    private Button saveButton;
    private Button cancelButton;
    
    public ConfigScreen(Screen parent) {
        super(Component.literal("翻译配置"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        TranslationConfig config = TranslationConfig.getInstance();
        
        // 同步 actionbar 状态到 title 命令状态
        config.translateActionbar = config.translateTitleCommand;
        
        // 标题
        int centerX = this.width / 2;
        int startY = CONTENT_START_Y - scrollOffset;
        int scrollAreaTop = 35;  // 为顶部遮罩预留空间
        int scrollAreaBottom = this.height - 60; // 为底部遮罩和保存按钮预留空间
        int visibleHeight = scrollAreaBottom - scrollAreaTop;
        
        // API类型按钮
        this.apiTypeButton = Button.builder(
                Component.literal("翻译接口: §e" + getApiTypeName(config.apiType)),
                button -> {
                    // 循环切换API类型: free -> llm -> baidu -> baidu_llm -> free
                    if ("free".equals(config.apiType)) {
                        config.apiType = "llm";
                    } else if ("llm".equals(config.apiType)) {
                        config.apiType = "baidu";
                    } else if ("baidu".equals(config.apiType)) {
                        config.apiType = "baidu_llm";
                    } else {
                        config.apiType = "free";
                    }
                    button.setMessage(Component.literal("翻译接口: §e" + getApiTypeName(config.apiType)));
                    // 重新初始化界面以更新输入框可见性
                    this.clearWidgets();
                    this.init();
                })
                .bounds(centerX - 100, startY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7切换翻译接口类型\n§7• 免费接口：无需配置，直接使用\n§7• LLM翻译：支持DeepSeek、OpenAI等\n§7• 百度翻译：需要API密钥\n§7• 百度千帆：使用大模型翻译")
                ))
                .build();
        this.addRenderableWidget(this.apiTypeButton);
        
        // 根据API类型显示不同的配置
        int fieldY = startY + 30;
        
        if ("baidu".equals(config.apiType)) {
            this.appIdField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("APP ID"));
            this.appIdField.setMaxLength(256);
            this.appIdField.setValue(config.baiduAppId);
            this.addRenderableWidget(this.appIdField);
            fieldY += 30;
            
            this.secretKeyField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("Secret Key"));
            this.secretKeyField.setMaxLength(256);
            this.secretKeyField.setValue(config.baiduSecretKey);
            this.addRenderableWidget(this.secretKeyField);
            fieldY += 30;
            
        } else if ("baidu_llm".equals(config.apiType)) {
            // 百度千帆大模型配置
            this.baiduLLMApiKeyField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("API Key"));
            this.baiduLLMApiKeyField.setMaxLength(256);
            this.baiduLLMApiKeyField.setValue(config.baiduLLMApiKey);
            this.addRenderableWidget(this.baiduLLMApiKeyField);
            fieldY += 30;
            
            this.baiduLLMSecretKeyField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("Secret Key"));
            this.baiduLLMSecretKeyField.setMaxLength(256);
            this.baiduLLMSecretKeyField.setValue(config.baiduLLMSecretKey);
            this.addRenderableWidget(this.baiduLLMSecretKeyField);
            fieldY += 30;
            
            this.baiduLLMModelField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("模型名称（默认ernie-4.0-turbo-8k）"));
            this.baiduLLMModelField.setMaxLength(128);
            this.baiduLLMModelField.setValue(config.baiduLLMModel);
            this.addRenderableWidget(this.baiduLLMModelField);
            fieldY += 30;
            
        } else if ("llm".equals(config.apiType)) {
            // API密钥输入框
            this.llmApiKeyField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("API密钥（必填）"));
            this.llmApiKeyField.setMaxLength(256);
            this.llmApiKeyField.setValue(config.llmApiKey);
            this.llmApiKeyField.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7DeepSeek: sk-开头\n§7OpenAI: sk-开头\n§7豆包: 从火山引擎获取")
            ));
            this.addRenderableWidget(this.llmApiKeyField);
            fieldY += 30;
            
            // API地址输入框
            this.llmApiUrlField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("API地址（可选）"));
            this.llmApiUrlField.setMaxLength(256);
            this.llmApiUrlField.setValue(config.llmApiUrl);
            this.llmApiUrlField.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7DeepSeek: 留空自动使用官方地址\n§7OpenAI: 留空自动使用官方地址\n§7豆包: ark.cn-beijing.volces.com/api/v3\n§7其他: 填写完整API地址")
            ));
            this.addRenderableWidget(this.llmApiUrlField);
            fieldY += 30;
            
            // 模型名称输入框
            this.llmModelField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("模型名称（可选）"));
            this.llmModelField.setMaxLength(128);
            this.llmModelField.setValue(config.llmModel);
            this.llmModelField.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7DeepSeek: 留空使用deepseek-chat\n§7OpenAI: 留空使用gpt-3.5-turbo\n§7豆包: §c必填§7你的endpoint ID\n§7     如：doubao-seed-1-6-251015")
            ));
            this.addRenderableWidget(this.llmModelField);
            fieldY += 30;
            
            // 系统提示词输入框
            this.llmSystemPromptField = new EditBox(this.font, centerX - 100, fieldY, 200, 20, Component.literal("系统提示词（可选）"));
            this.llmSystemPromptField.setMaxLength(500);
            this.llmSystemPromptField.setValue(config.llmSystemPrompt);
            this.llmSystemPromptField.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("§7自定义翻译风格\n§7例如：翻译要幽默风趣\n§7例如：使用文言文风格\n§7留空使用默认提示\n§e推荐在地图外更改系统提示词")
            ));
            this.addRenderableWidget(this.llmSystemPromptField);
            fieldY += 30;
        }
        
        // 源语言输入框
        this.sourceLangField = new EditBox(this.font, centerX - 100, fieldY, 95, 20, Component.literal("源语言"));
        this.sourceLangField.setMaxLength(10);
        this.sourceLangField.setValue(config.sourceLang);
        this.addRenderableWidget(this.sourceLangField);
        
        // 目标语言输入框
        this.targetLangField = new EditBox(this.font, centerX + 5, fieldY, 95, 20, Component.literal("目标语言"));
        this.targetLangField.setMaxLength(10);
        this.targetLangField.setValue(config.targetLang);
        this.addRenderableWidget(this.targetLangField);
        fieldY += 30;
        
        // === 基本开关 ===
        fieldY += 10;
        
        // 翻译功能总开关
        this.toggleButton = Button.builder(
                Component.literal("§l总开关: " + (config.enabled ? "§a开启" : "§c关闭")),
                button -> {
                    config.enabled = !config.enabled;
                    button.setMessage(Component.literal("§l总开关: " + (config.enabled ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制翻译功能的全局开关\n§7关闭后，所有翻译功能将停止工作")
                ))
                .build();
        this.addRenderableWidget(this.toggleButton);
        fieldY += 30;
        
        // === 翻译内容（直接开关，不跳转）===
        
        // 聊天消息
        this.addRenderableWidget(Button.builder(
                Component.literal("聊天消息: " + (config.autoTranslate ? "§a开启" : "§c关闭")),
                button -> {
                    config.autoTranslate = !config.autoTranslate;
                    button.setMessage(Component.literal("聊天消息: " + (config.autoTranslate ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制聊天栏消息的自动翻译\n§7开启后，聊天栏中的消息将自动翻译")
                ))
                .build());
        fieldY += 25;
        
        // 聊天栏提示框
        this.addRenderableWidget(Button.builder(
                Component.literal("聊天栏提示框: " + (config.translateChatTooltip ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateChatTooltip = !config.translateChatTooltip;
                    button.setMessage(Component.literal("聊天栏提示框: " + (config.translateChatTooltip ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制聊天栏提示框（Tooltip）的翻译\n§7开启后，鼠标悬停在聊天消息上时会翻译提示信息")
                ))
                .build());
        fieldY += 25;
        
        // 书本
        this.addRenderableWidget(Button.builder(
                Component.literal("书本内容: " + (config.translateBook ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateBook = !config.translateBook;
                    button.setMessage(Component.literal("书本内容: " + (config.translateBook ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制书本和成书内容的翻译\n§7开启后，打开书本时会自动翻译内容")
                ))
                .build());
        fieldY += 25;

        // 告示牌
        this.addRenderableWidget(Button.builder(
                Component.literal("告示牌: " + (config.translateSign ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateSign = !config.translateSign;
                    button.setMessage(Component.literal("告示牌: " + (config.translateSign ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制告示牌文字的翻译\n§7开启后，查看告示牌时会自动翻译文字")
                ))
                .build());
        fieldY += 25;
        
        // 告示牌翻译提示（聊天框）
        this.addRenderableWidget(Button.builder(
                Component.literal("告示牌翻译提示: " + (config.showSignTranslationMessages ? "§a开启" : "§c关闭")),
                button -> {
                    config.showSignTranslationMessages = !config.showSignTranslationMessages;
                    button.setMessage(Component.literal("告示牌翻译提示: " + (config.showSignTranslationMessages ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制告示牌翻译时是否在聊天栏显示提示\n§7开启后，翻译告示牌时会在聊天栏显示提示消息")
                ))
                .build());
        fieldY += 25;

        // 物品提示框
        this.addRenderableWidget(Button.builder(
                Component.literal("物品提示: " + (config.translateTooltip ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateTooltip = !config.translateTooltip;
                    button.setMessage(Component.literal("物品提示: " + (config.translateTooltip ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制物品提示框（Tooltip）的翻译\n§7开启后，鼠标悬停在物品上时会翻译提示信息")
                ))
                .build());
        fieldY += 25;
        
        // 成就
        this.addRenderableWidget(Button.builder(
                Component.literal("成就: " + (config.translateAdvancements ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateAdvancements = !config.translateAdvancements;
                    button.setMessage(Component.literal("成就: " + (config.translateAdvancements ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制成就和进度提示的翻译\n§7开启后，获得成就时会翻译成就名称和描述")
                ))
                .build());
        fieldY += 25;
        
        // 实体名称
        this.addRenderableWidget(Button.builder(
                Component.literal("实体名称: " + (config.translateEntityName ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateEntityName = !config.translateEntityName;
                    button.setMessage(Component.literal("实体名称: " + (config.translateEntityName ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制实体名称标签的翻译\n§7开启后，生物和实体的名称标签会被翻译")
                ))
                .build());
        fieldY += 25;
        
        // 计分板
        this.addRenderableWidget(Button.builder(
                Component.literal("计分板: " + (config.translateScoreboard ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateScoreboard = !config.translateScoreboard;
                    button.setMessage(Component.literal("计分板: " + (config.translateScoreboard ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制计分板内容的翻译\n§7开启后，屏幕右侧的计分板内容会被翻译")
                ))
                .build());
        fieldY += 25;
        
        // Boss血条
        this.addRenderableWidget(Button.builder(
                Component.literal("Boss血条: " + (config.translateBossBar ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateBossBar = !config.translateBossBar;
                    button.setMessage(Component.literal("Boss血条: " + (config.translateBossBar ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制Boss血条文字的翻译\n§7开启后，屏幕顶部的Boss血条名称会被翻译")
                ))
                .build());
        fieldY += 25;
        
        // 文字显示实体
        this.addRenderableWidget(Button.builder(
                Component.literal("文字显示实体: " + (config.translateTextDisplay ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateTextDisplay = !config.translateTextDisplay;
                    button.setMessage(Component.literal("文字显示实体: " + (config.translateTextDisplay ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制文字显示实体（Text Display）的翻译\n§7开启后，世界中的文字显示实体会被翻译\n§c⚠ 警告：该功能需要在地图外打开才能实时翻译游戏内的文字实体\n§c⚠ 需要打开永久缓存和独立世界缓存才能恢复原文")
                ))
                .build());
        fieldY += 25;
        
        // Title命令（同时控制 actionbar）
        this.addRenderableWidget(Button.builder(
                Component.literal("Title命令: " + (config.translateTitleCommand ? "§a开启" : "§c关闭")),
                button -> {
                    config.translateTitleCommand = !config.translateTitleCommand;
                    // 同时控制 actionbar
                    config.translateActionbar = config.translateTitleCommand;
                    button.setMessage(Component.literal("Title命令: " + (config.translateTitleCommand ? "§a开启" : "§c关闭")));
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制Title和Actionbar命令的翻译\n§7开启后，服务器发送的Title和Actionbar消息会被翻译\n§7（包括屏幕中央和底部的提示消息）")
                ))
                .build());
        fieldY += 30;
        
        // === 缓存管理 ===
        fieldY += 10;
        
        // 持久化缓存开关
        this.addRenderableWidget(Button.builder(
                Component.literal("§e永久缓存: " + (config.persistentCache ? "§a开启" : "§c关闭")),
                button -> {
                    config.persistentCache = !config.persistentCache;
                    button.setMessage(Component.literal("§e永久缓存: " + (config.persistentCache ? "§a开启" : "§c关闭")));
                    if (config.persistentCache) {
                        if (this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(
                                Component.literal("§a[翻译] 已开启永久缓存，翻译内容将保存到文件")
                            );
                        }
                    }
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制翻译缓存是否保存到文件\n§7开启后，翻译结果会保存到文件，重启游戏后仍可使用\n§7关闭后，缓存仅在游戏运行时有效\n§c该按钮一旦启用，重进保存游戏后的翻译将固定。除非清空所有缓存重新翻译")
                ))
                .build());
        fieldY += 25;
        
        // 按世界独立缓存开关
        this.addRenderableWidget(Button.builder(
                Component.literal("独立世界缓存: " + (config.perWorldCache ? "§a开启" : "§c关闭")),
                button -> {
                    config.perWorldCache = !config.perWorldCache;
                    button.setMessage(Component.literal("独立世界缓存: " + (config.perWorldCache ? "§a开启" : "§c关闭")));
                    if (this.minecraft != null && this.minecraft.player != null) {
                        if (config.perWorldCache) {
                            this.minecraft.player.sendSystemMessage(
                                Component.literal("§a[翻译] 已开启独立世界缓存，每个世界使用独立的缓存文件")
                            );
                        } else {
                            this.minecraft.player.sendSystemMessage(
                                Component.literal("§a[翻译] 已关闭独立世界缓存，所有世界共享同一个缓存文件")
                            );
                        }
                    }
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7控制是否为每个世界使用独立的缓存文件\n§7开启后，不同世界的翻译缓存互不影响\n§7关闭后，所有世界共享同一个缓存文件")
                ))
                .build());
        fieldY += 25;
        
        // 清空缓存按钮
        this.addRenderableWidget(Button.builder(
                Component.literal("§c§l清空所有缓存"),
                button -> {
                    // 清空内存缓存
                    translation.modid.translator.TranslationManager.getInstance().clearCache();
                    // 清空持久化缓存
                    translation.modid.cache.TranslationCacheManager.getInstance().clearAll();
                    // 清空告示牌缓存
                    translation.modid.sign.SignTranslationManager.getInstance().clearAll();
                    
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(
                            Component.literal("§a[翻译] 已清空所有翻译缓存")
                        );
                    }
                })
                .bounds(centerX - 100, fieldY, 200, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal("§7清空所有翻译缓存（内存和文件）\n§c警告：此操作不可恢复\n§7清空后，所有已翻译的内容需要重新翻译")
                ))
                .build());
        fieldY += 30;
        
        // 计算内容总高度并限制滚动范围
        int contentHeight = fieldY - (CONTENT_START_Y - scrollOffset);
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        
        // 保存按钮和取消按钮（最后添加，确保最高优先级）
        // 使用更高的 Y 位置，确保在底部且不被遮挡
        int buttonY = this.height - 30;
        
        // 保存按钮（固定在底部，最后添加以确保最高优先级）
        this.saveButton = Button.builder(Component.literal("§a§l保存配置"), button -> {
            System.out.println("[配置界面] 开始保存配置...");
            
            // 保存百度翻译配置
            if (this.appIdField != null) {
                config.baiduAppId = this.appIdField.getValue();
                System.out.println("[配置界面] 百度AppID: " + config.baiduAppId);
            }
            if (this.secretKeyField != null) {
                config.baiduSecretKey = this.secretKeyField.getValue();
                System.out.println("[配置界面] 百度SecretKey已设置: " + !config.baiduSecretKey.isEmpty());
            }
            
            // 保存百度千帆大模型配置
            if (this.baiduLLMApiKeyField != null) {
                config.baiduLLMApiKey = this.baiduLLMApiKeyField.getValue();
                System.out.println("[配置界面] 百度千帆API Key已设置: " + !config.baiduLLMApiKey.isEmpty());
            }
            if (this.baiduLLMSecretKeyField != null) {
                config.baiduLLMSecretKey = this.baiduLLMSecretKeyField.getValue();
                System.out.println("[配置界面] 百度千帆Secret Key已设置: " + !config.baiduLLMSecretKey.isEmpty());
            }
            if (this.baiduLLMModelField != null) {
                config.baiduLLMModel = this.baiduLLMModelField.getValue();
                System.out.println("[配置界面] 百度千帆Model: " + config.baiduLLMModel);
            }
            
            // 保存LLM配置
            if (this.llmApiKeyField != null) {
                config.llmApiKey = this.llmApiKeyField.getValue();
                System.out.println("[配置界面] LLM API Key已设置: " + !config.llmApiKey.isEmpty() + " (长度: " + config.llmApiKey.length() + ")");
            }
            if (this.llmApiUrlField != null) {
                config.llmApiUrl = this.llmApiUrlField.getValue();
                System.out.println("[配置界面] LLM API URL: " + config.llmApiUrl);
            }
            if (this.llmModelField != null) {
                config.llmModel = this.llmModelField.getValue();
                System.out.println("[配置界面] LLM Model: " + config.llmModel);
            }
            if (this.llmSystemPromptField != null) {
                config.llmSystemPrompt = this.llmSystemPromptField.getValue();
                System.out.println("[配置界面] LLM System Prompt: " + config.llmSystemPrompt);
            }
            
            // 保存语言配置
            config.sourceLang = this.sourceLangField.getValue();
            config.targetLang = this.targetLangField.getValue();
            System.out.println("[配置界面] 语言配置: " + config.sourceLang + " -> " + config.targetLang);
            
            // 打印当前API类型
            System.out.println("[配置界面] API类型: " + config.apiType);
            
            // 保存配置到文件
            System.out.println("[配置界面] 调用 config.save()...");
            config.save();
            System.out.println("[配置界面] config.save() 完成");
            
            // 重新初始化翻译器以应用新配置
            System.out.println("[配置界面] 重新加载翻译器...");
            translation.modid.translator.TranslationManager.getInstance().reload();
            System.out.println("[配置界面] 翻译器重新加载完成");
            
            // 显示保存成功提示
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(
                    Component.literal("§a[翻译] 配置已保存并应用！")
                );
            }
            
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        })
        .bounds(centerX - 100, buttonY, 95, 20)
        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7保存所有配置更改并应用\n§7配置会立即生效，无需重启游戏")
        ))
        .build();
        this.addRenderableWidget(this.saveButton);
        
        // 取消按钮（固定在底部）
        this.cancelButton = Button.builder(Component.literal("取消"), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        })
        .bounds(centerX + 5, buttonY, 95, 20)
        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("§7取消更改并返回上一界面\n§7未保存的配置更改将丢失")
        ))
        .build();
        this.addRenderableWidget(this.cancelButton);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 优先处理保存按钮和取消按钮的点击，确保它们有最高优先级
        // 先处理保存按钮（最高优先级）
        if (this.saveButton != null && this.saveButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 然后处理取消按钮
        if (this.cancelButton != null && this.cancelButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 最后处理其他组件
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 检查鼠标是否在滚动区域内
        int scrollAreaTop = 35;
        int scrollAreaBottom = this.height - 60;
        if (mouseY >= scrollAreaTop && mouseY <= scrollAreaBottom) {
            // 估算内容高度（根据API类型不同，高度也不同）
            TranslationConfig config = TranslationConfig.getInstance();
            int baseHeight = 685; // 增加高度以容纳缓存管理按钮和Title命令按钮
            if ("baidu".equals(config.apiType)) {
                baseHeight += 60;
            } else if ("baidu_llm".equals(config.apiType)) {
                baseHeight += 90;
            } else if ("llm".equals(config.apiType)) {
                baseHeight += 120; // LLM配置现在有4个输入框
            }
            
            int visibleHeight = scrollAreaBottom - scrollAreaTop;
            int maxScroll = Math.max(0, baseHeight - visibleHeight);
            
            // 更新滚动偏移
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * SCROLL_SPEED));
            
            // 重新初始化界面以更新所有组件位置
            this.clearWidgets();
            this.init();
            
            return true;
        }
        return false;
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);
        
        int scrollAreaTop = 40;
        int scrollAreaBottom = this.height - 35;
        
        // 第一层：绘制滚动区域背景
        graphics.fill(0, scrollAreaTop, this.width, scrollAreaBottom, 0x80000000);
        
        // 第二层：启用裁剪，渲染所有滚动内容和按钮
        graphics.enableScissor(0, scrollAreaTop, this.width, scrollAreaBottom);
        
        // 绘制标签（考虑滚动偏移）
        TranslationConfig config = TranslationConfig.getInstance();
        int labelY = 20 + CONTENT_START_Y - scrollOffset;
        
        if ("baidu".equals(config.apiType)) {
            graphics.drawString(this.font, "百度翻译 APP ID:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
            graphics.drawString(this.font, "百度翻译密钥:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
        } else if ("baidu_llm".equals(config.apiType)) {
            graphics.drawString(this.font, "百度千帆 API Key:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
            graphics.drawString(this.font, "百度千帆 Secret Key:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
            graphics.drawString(this.font, "模型名称:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
        } else if ("llm".equals(config.apiType)) {
            // 绘制输入框标签
            graphics.drawString(this.font, "API密钥:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
            graphics.drawString(this.font, "API地址:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
            graphics.drawString(this.font, "模型名称:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
            graphics.drawString(this.font, "系统提示词:", this.width / 2 - 100, labelY, 0xFFFFFF);
            labelY += 30;
        }
        
        graphics.drawString(this.font, "源语言:", this.width / 2 - 100, labelY, 0xFFFFFF);
        graphics.drawString(this.font, "目标语言:", this.width / 2 + 5, labelY, 0xFFFFFF);
        labelY += 40;
        
        // 渲染滚动区域内的所有组件（除了保存/取消按钮）
        this.children().forEach(child -> {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                if (widget instanceof net.minecraft.client.gui.components.Button button) {
                    String message = button.getMessage().getString();
                    // 跳过保存和取消按钮
                    if (message.contains("保存配置") || message.equals("取消")) {
                        return;
                    }
                }
                widget.render(graphics, mouseX, mouseY, delta);
            }
        });
        
        // 禁用裁剪
        graphics.disableScissor();
        
        // 第三层：绘制黑色半透明渐变遮罩（遮住滚动出去的内容）
        // 顶部渐变遮罩（从完全不透明到透明）
        for (int i = 0; i < 30; i++) {
            int alpha = (int)(255 * (30 - i) / 30.0); // 从255渐变到0
            int color = (alpha << 24) | 0x000000; // 黑色
            graphics.fill(0, scrollAreaTop - 30 + i, this.width, scrollAreaTop - 29 + i, color);
        }
        
        // 底部渐变遮罩（从透明到完全不透明）
        for (int i = 0; i < 30; i++) {
            int alpha = (int)(255 * i / 30.0); // 从0渐变到255
            int color = (alpha << 24) | 0x000000; // 黑色
            graphics.fill(0, scrollAreaBottom + i, this.width, scrollAreaBottom + i + 1, color);
        }
        
        // 第四层：绘制标题（在最上层）
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        
        // 第五层：单独渲染保存和取消按钮（在最顶层，最后渲染以确保最高优先级）
        // 先渲染取消按钮，再渲染保存按钮，这样保存按钮在最上层
        this.children().forEach(child -> {
            if (child instanceof net.minecraft.client.gui.components.Button button) {
                String message = button.getMessage().getString();
                if (message.equals("取消")) {
                    button.render(graphics, mouseX, mouseY, delta);
                }
            }
        });
        // 最后渲染保存按钮，确保它在最上层，优先级最高
        this.children().forEach(child -> {
            if (child instanceof net.minecraft.client.gui.components.Button button) {
                String message = button.getMessage().getString();
                if (message.contains("保存配置")) {
                    button.render(graphics, mouseX, mouseY, delta);
                }
            }
        });
    }
    
    private String getApiTypeName(String type) {
        return switch (type) {
            case "free" -> "免费接口";
            case "llm" -> "LLM翻译";
            case "baidu" -> "百度翻译";
            case "baidu_llm" -> "百度千帆大模型";
            default -> "未知";
        };
    }
    
    @Override
    public void onClose() {
        TranslationConfig.getInstance().save();
        this.minecraft.setScreen(parent);
    }
}
