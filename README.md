# Simple Translation

<div align="center">

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B74A?style=for-the-badge&logo=minecraft)
![Fabric](https://img.shields.io/badge/Fabric-Loader-blue?style=for-the-badge)
![License](https://img.shields.io/badge/License-CC0--1.0-green?style=for-the-badge)

**一个功能强大的 Minecraft 实时翻译模组**

**A powerful real-time translation mod for Minecraft**

[English](#english) | [中文](#中文)

</div>

---

## 中文

### 📖 简介

Simple Translation 是一个功能强大的 Minecraft Fabric 模组，支持实时翻译游戏内的各种文本内容。无论是聊天消息、物品提示、告示牌、书本还是成就，都能轻松翻译成你需要的语言。

### ✨ 主要特性

- 🌐 **多翻译 API 支持**
  - 免费翻译 API（无需配置，开箱即用）
  - 百度翻译 API（需要 API 密钥）
  - 百度千帆大模型（ERNIE 系列）
  - LLM 翻译（支持所有 OpenAI 兼容 API，如 DeepSeek）

- 📝 **全面翻译覆盖**
  - 💬 聊天消息翻译
  - 📖 书本内容翻译
  - 🪧 告示牌翻译（支持范围模式和视线模式）
  - 🎯 物品提示框翻译
  - 💬 聊天消息提示框翻译
  - 🏆 成就翻译
  - 👤 实体名称翻译
  - 📺 标题/命令消息翻译
  - 📊 动作栏翻译
  - 📋 计分板翻译
  - 💀 Boss 血条翻译
  - 📱 文字显示实体翻译

- ⚙️ **灵活配置**
  - 图形化配置界面（支持 Mod Menu 集成）
  - 自动/手动翻译模式
  - 自定义源语言和目标语言
  - 翻译延迟设置
  - 显示原文选项

- 💾 **智能缓存**
  - 内存缓存（减少重复翻译）
  - 持久化缓存（可选，退出游戏后保留）
  - 按世界独立缓存（可选）

### 📦 安装

1. **前置要求**
   - Minecraft 1.20.1
   - Fabric Loader 0.17.3 或更高版本
   - Fabric API 0.92.6+1.20.1 或更高版本
   - Java 17 或更高版本

2. **安装步骤**
   - 下载 [Fabric Loader](https://fabricmc.net/use/)
   - 下载 [Fabric API](https://modrinth.com/mod/fabric-api)
   - 下载 `simple-translation-1.0.0.jar`
   - 将模组文件放入 `.minecraft/mods` 文件夹
   - 启动游戏

### 🚀 使用方法

#### 基本使用

1. **启动游戏后**，按 `ESC` 键打开菜单
2. 如果已安装 **Mod Menu**，点击"模组"按钮，找到 "Simple Translation" 并点击配置
3. 或者直接编辑配置文件：`.minecraft/config/simple-translation/config.json`

#### 配置说明

配置文件位置：`.minecraft/config/simple-translation/config.json`

主要配置项：

```json
{
  "enabled": true,                    // 是否启用翻译
  "apiType": "free",                  // API类型：free/baidu/baidu_llm/llm
  "sourceLang": "auto",               // 源语言：auto自动检测
  "targetLang": "zh",                 // 目标语言：zh中文
  "autoTranslate": false,             // 是否自动翻译
  "showOriginal": false,              // 是否显示原文
  "translateBook": false,             // 是否翻译书本
  "translateSign": false,             // 是否翻译告示牌
  "translateTooltip": false,          // 是否翻译物品提示框
  "translateChatTooltip": false,      // 是否翻译聊天消息提示框
  "translateAdvancements": false,     // 是否翻译成就
  "translateEntityName": false,       // 是否翻译实体名称
  "translationDelay": 500,            // 翻译延迟（毫秒）
  "persistentCache": false,           // 是否启用持久化缓存
  "perWorldCache": false              // 是否为每个世界独立缓存
}
```

#### 翻译 API 配置

**1. 免费翻译 API（推荐新手）**
```json
{
  "apiType": "free"
}
```
无需任何配置，开箱即用。

**2. 百度翻译 API**
```json
{
  "apiType": "baidu",
  "baiduAppId": "你的AppID",
  "baiduSecretKey": "你的密钥"
}
```
获取 API 密钥：[百度翻译开放平台](https://fanyi-api.baidu.com/)

**3. 百度千帆大模型**
```json
{
  "apiType": "baidu_llm",
  "baiduLLMApiKey": "你的API Key",
  "baiduLLMSecretKey": "你的Secret Key",
  "baiduLLMModel": "ernie-4.0-turbo-8k"
}
```
获取 API 密钥：[百度智能云千帆大模型平台](https://cloud.baidu.com/product/wenxinworkshop)

**4. LLM 翻译（OpenAI 兼容）**
```json
{
  "apiType": "llm",
  "llmApiKey": "你的API Key",
  "llmApiUrl": "",                    // 留空使用DeepSeek，或填写自定义API地址
  "llmModel": "",                     // 留空使用deepseek-chat，或填写模型名称
  "llmSystemPrompt": ""               // 自定义系统提示词，留空使用默认
}
```
支持所有 OpenAI 兼容 API，如 DeepSeek、OpenAI、Claude 等。

### 🎮 告示牌翻译模式

- **range（范围模式）**：翻译玩家周围一定范围内的所有告示牌
- **looking（视线模式）**：只翻译玩家正在查看的告示牌
- **translateSignOnSneak**：设置为 `true` 时，只有潜行时才翻译告示牌

### 📸 截图

> 截图功能待添加，欢迎贡献！

### 🔧 开发

#### 构建

```bash
./gradlew build
```

构建产物位于 `build/libs/` 目录。

#### 开发环境

1. 克隆仓库
```bash
git clone https://github.com/baokaixin/simple-translation.git
cd simple-translation
```

2. 导入项目到 IDE（推荐 IntelliJ IDEA）

3. 运行 `./gradlew genSources` 生成源代码映射

4. 运行客户端：`./gradlew runClient`

### 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 📄 许可证

本项目采用 [CC0-1.0](LICENSE) 许可证。

### 🔗 相关链接

- [GitHub Issues](https://github.com/baokaixin/simple-translation/issues)
- [Modrinth](https://modrinth.com/mod/simple-translation)（待发布）
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-translation)（待发布）

---

## English

### 📖 Introduction

Simple Translation is a powerful Minecraft Fabric mod that supports real-time translation of various in-game text content. Whether it's chat messages, item tooltips, signs, books, or advancements, everything can be easily translated into your desired language.

### ✨ Key Features

- 🌐 **Multiple Translation API Support**
  - Free Translation API (no configuration required, works out of the box)
  - Baidu Translate API (requires API key)
  - Baidu Qianfan LLM (ERNIE series)
  - LLM Translation (supports all OpenAI-compatible APIs, such as DeepSeek)

- 📝 **Comprehensive Translation Coverage**
  - 💬 Chat message translation
  - 📖 Book content translation
  - 🪧 Sign translation (supports range mode and looking mode)
  - 🎯 Item tooltip translation
  - 💬 Chat message tooltip translation
  - 🏆 Advancement translation
  - 👤 Entity name translation
  - 📺 Title/command message translation
  - 📊 Action bar translation
  - 📋 Scoreboard translation
  - 💀 Boss bar translation
  - 📱 Text display entity translation

- ⚙️ **Flexible Configuration**
  - Graphical configuration interface (supports Mod Menu integration)
  - Automatic/manual translation modes
  - Custom source and target languages
  - Translation delay settings
  - Show original text option

- 💾 **Smart Caching**
  - Memory cache (reduces duplicate translations)
  - Persistent cache (optional, retained after game exit)
  - Per-world independent cache (optional)

### 📦 Installation

1. **Requirements**
   - Minecraft 1.20.1
   - Fabric Loader 0.17.3 or higher
   - Fabric API 0.92.6+1.20.1 or higher
   - Java 17 or higher

2. **Installation Steps**
   - Download [Fabric Loader](https://fabricmc.net/use/)
   - Download [Fabric API](https://modrinth.com/mod/fabric-api)
   - Download `simple-translation-1.0.0.jar`
   - Place the mod file in `.minecraft/mods` folder
   - Launch the game

### 🚀 Usage

#### Basic Usage

1. **After launching the game**, press `ESC` to open the menu
2. If **Mod Menu** is installed, click the "Mods" button, find "Simple Translation" and click configure
3. Or directly edit the config file: `.minecraft/config/simple-translation/config.json`

#### Configuration

Config file location: `.minecraft/config/simple-translation/config.json`

Main configuration options:

```json
{
  "enabled": true,                    // Enable translation
  "apiType": "free",                  // API type: free/baidu/baidu_llm/llm
  "sourceLang": "auto",               // Source language: auto for auto-detect
  "targetLang": "zh",                 // Target language: zh for Chinese
  "autoTranslate": false,             // Auto-translate
  "showOriginal": false,              // Show original text
  "translateBook": false,             // Translate books
  "translateSign": false,             // Translate signs
  "translateTooltip": false,          // Translate item tooltips
  "translateChatTooltip": false,      // Translate chat message tooltips
  "translateAdvancements": false,     // Translate advancements
  "translateEntityName": false,       // Translate entity names
  "translationDelay": 500,            // Translation delay (milliseconds)
  "persistentCache": false,           // Enable persistent cache
  "perWorldCache": false              // Independent cache per world
}
```

#### Translation API Configuration

**1. Free Translation API (Recommended for beginners)**
```json
{
  "apiType": "free"
}
```
No configuration required, works out of the box.

**2. Baidu Translate API**
```json
{
  "apiType": "baidu",
  "baiduAppId": "Your AppID",
  "baiduSecretKey": "Your Secret Key"
}
```
Get API keys: [Baidu Translate Open Platform](https://fanyi-api.baidu.com/)

**3. Baidu Qianfan LLM**
```json
{
  "apiType": "baidu_llm",
  "baiduLLMApiKey": "Your API Key",
  "baiduLLMSecretKey": "Your Secret Key",
  "baiduLLMModel": "ernie-4.0-turbo-8k"
}
```
Get API keys: [Baidu Intelligent Cloud Qianfan Platform](https://cloud.baidu.com/product/wenxinworkshop)

**4. LLM Translation (OpenAI Compatible)**
```json
{
  "apiType": "llm",
  "llmApiKey": "Your API Key",
  "llmApiUrl": "",                    // Leave empty for DeepSeek, or custom API URL
  "llmModel": "",                     // Leave empty for deepseek-chat, or model name
  "llmSystemPrompt": ""               // Custom system prompt, leave empty for default
}
```
Supports all OpenAI-compatible APIs, such as DeepSeek, OpenAI, Claude, etc.

### 🎮 Sign Translation Modes

- **range**: Translate all signs within a certain range around the player
- **looking**: Only translate the sign the player is looking at
- **translateSignOnSneak**: When set to `true`, only translate signs while sneaking

### 📸 Screenshots

> Screenshots coming soon, contributions welcome!

### 🔧 Development

#### Building

```bash
./gradlew build
```

Build artifacts are located in the `build/libs/` directory.

#### Development Environment

1. Clone the repository
```bash
git clone https://github.com/baokaixin/simple-translation.git
cd simple-translation
```

2. Import the project into your IDE (recommended: IntelliJ IDEA)

3. Run `./gradlew genSources` to generate source mappings

4. Run client: `./gradlew runClient`

### 🤝 Contributing

Issues and Pull Requests are welcome!

### 📄 License

This project is licensed under [CC0-1.0](LICENSE).

### 🔗 Links

- [GitHub Issues](https://github.com/baokaixin/simple-translation/issues)
- [Modrinth](https://modrinth.com/mod/simple-translation) (Coming soon)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-translation) (Coming soon)

---

<div align="center">

**Made with ❤️ for the Minecraft community**

⭐ Star this repo if you find it helpful!

</div>

