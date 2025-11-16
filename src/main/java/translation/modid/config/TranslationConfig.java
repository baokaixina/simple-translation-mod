package translation.modid.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class TranslationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("simple-translation")
            .resolve("config.json");
    
    // 配置选项
    public boolean enabled = false;
    public String apiType = "free"; // free(免费翻译), baidu(百度翻译), baidu_llm(百度千帆大模型), llm(LLM翻译)
    public String baiduAppId = "";
    public String baiduSecretKey = "";
    
    // 百度千帆大模型配置
    public String baiduLLMApiKey = "";
    public String baiduLLMSecretKey = "";
    public String baiduLLMModel = "ernie-4.0-turbo-8k"; // 默认使用ERNIE-4.0-Turbo-8K
    
    // LLM配置（支持所有OpenAI兼容API）
    public String llmApiKey = "";
    public String llmApiUrl = ""; // 留空自动使用DeepSeek官方地址
    public String llmModel = ""; // 留空自动使用deepseek-chat模型
    public String llmSystemPrompt = ""; // 自定义系统提示词，留空使用默认提示
    
    public String sourceLang = "auto"; // 源语言：auto自动检测
    public String targetLang = "zh"; // 目标语言：zh中文
    public boolean showOriginal = false; // 是否显示原文
    public boolean autoTranslate = false; // 是否自动翻译
    public boolean translateBook = false; // 是否翻译书本
    public boolean translateSign = false; // 是否翻译告示牌
    public boolean translateTooltip = false; // 是否翻译物品提示框
    public boolean translateChatTooltip = false; // 是否翻译聊天消息提示框
    public boolean translateAdvancements = false; // 是否翻译成就
    public boolean translateEntityName = false; // 是否翻译实体名称
    public boolean translateTitleCommand = false; // 是否翻译title命令标题
    public boolean translateActionbar = false; // 是否翻译actionbar标题
    public boolean translateScoreboard = false; // 是否翻译计分板
    public boolean translateBossBar = false; // 是否翻译boss血条
    public boolean translateTextDisplay = false; // 是否翻译文字显示实体
    public String signTranslationMode = "range"; // 告示牌翻译模式：range(范围内全部), looking(面前视线)
    public boolean translateSignOnSneak = false; // 是否潜行时才翻译告示牌
    public boolean showSignTranslationMessages = false; // 是否显示告示牌翻译提示
    public int translationDelay = 500; // 翻译延迟（毫秒）
    
    // 缓存配置
    public boolean persistentCache = false; // 是否启用持久化缓存（退出游戏后保留）
    public boolean perWorldCache = false; // 是否为每个世界独立缓存
    
    private static TranslationConfig instance;
    
    public static TranslationConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    public static TranslationConfig load() {
        // 检查旧配置文件是否存在，如果存在则迁移
        Path oldConfigPath = FabricLoader.getInstance().getConfigDir().resolve("simple-translation.json");
        if (Files.exists(oldConfigPath) && !Files.exists(CONFIG_PATH)) {
            try {
                // 从旧文件加载配置
                TranslationConfig config;
                try (Reader reader = Files.newBufferedReader(oldConfigPath)) {
                    config = GSON.fromJson(reader, TranslationConfig.class);
                }
                
                // 保存到新位置
                config.save();
                
                // 删除旧文件
                Files.delete(oldConfigPath);
                System.out.println("[简易翻译] 已将配置从旧位置迁移到: " + CONFIG_PATH.toAbsolutePath());
                
                return config;
            } catch (IOException e) {
                System.err.println("[简易翻译] 迁移配置文件失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 加载新位置的配置文件
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, TranslationConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // 创建默认配置
        TranslationConfig config = new TranslationConfig();
        config.save();
        return config;
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
            System.out.println("[简易翻译] 配置已保存到: " + CONFIG_PATH.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[简易翻译] 保存配置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void reload() {
        TranslationConfig loaded = load();
        this.enabled = loaded.enabled;
        this.apiType = loaded.apiType;
        this.baiduAppId = loaded.baiduAppId;
        this.baiduSecretKey = loaded.baiduSecretKey;
        this.baiduLLMApiKey = loaded.baiduLLMApiKey;
        this.baiduLLMSecretKey = loaded.baiduLLMSecretKey;
        this.baiduLLMModel = loaded.baiduLLMModel;
        this.llmApiKey = loaded.llmApiKey;
        this.llmApiUrl = loaded.llmApiUrl;
        this.llmModel = loaded.llmModel;
        this.llmSystemPrompt = loaded.llmSystemPrompt;
        this.sourceLang = loaded.sourceLang;
        this.targetLang = loaded.targetLang;
        this.showOriginal = loaded.showOriginal;
        this.autoTranslate = loaded.autoTranslate;
        this.translateBook = loaded.translateBook;
        this.translateSign = loaded.translateSign;
        this.translateTooltip = loaded.translateTooltip;
        this.translateChatTooltip = loaded.translateChatTooltip;
        this.translateAdvancements = loaded.translateAdvancements;
        this.translateEntityName = loaded.translateEntityName;
        this.translateTitleCommand = loaded.translateTitleCommand;
        this.translateActionbar = loaded.translateActionbar;
        this.translateScoreboard = loaded.translateScoreboard;
        this.translateBossBar = loaded.translateBossBar;
        this.translateTextDisplay = loaded.translateTextDisplay;
        this.signTranslationMode = loaded.signTranslationMode;
        this.translateSignOnSneak = loaded.translateSignOnSneak;
        this.showSignTranslationMessages = loaded.showSignTranslationMessages;
        this.translationDelay = loaded.translationDelay;
        this.persistentCache = loaded.persistentCache;
        this.perWorldCache = loaded.perWorldCache;
    }
}

