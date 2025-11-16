# Simple Translation

一个功能强大的 Minecraft 实时翻译模组，支持多种翻译 API 和全面的游戏内文本翻译功能。

A powerful real-time translation mod for Minecraft with support for multiple translation APIs and comprehensive in-game text translation features.

---

## ✨ 主要功能 / Key Features

### 🌐 多种翻译 API 支持 / Multiple Translation API Support

- **免费翻译** - 无需 API 密钥即可使用（Google 翻译、有道翻译）
- **百度翻译 API** - 官方 API 支持，稳定可靠
- **百度千帆大模型** - 支持 ERNIE 系列模型（如 ERNIE-4.0-Turbo-8K）
- **LLM 翻译** - 支持所有 OpenAI 兼容 API：
  - DeepSeek（默认）
  - OpenAI GPT
  - 豆包（火山引擎）
  - 智谱 AI
  - 其他兼容 API

### 📝 全面的翻译功能 / Comprehensive Translation Features

- ✅ **聊天消息翻译** - 实时翻译聊天栏中的所有消息
- ✅ **书本翻译** - 支持翻译游戏内书本内容（支持整本书批量翻译）
- ✅ **告示牌翻译** - 自动翻译告示牌文本（支持范围内全部或视线内）
- ✅ **物品提示框翻译** - 翻译物品悬停提示信息
- ✅ **聊天消息提示框翻译** - 翻译聊天消息的悬停提示
- ✅ **成就翻译** - 翻译成就名称和描述
- ✅ **实体名称翻译** - 翻译生物和实体名称
- ✅ **Title 命令标题翻译** - 翻译服务器发送的标题
- ✅ **Actionbar 标题翻译** - 翻译操作栏文本
- ✅ **计分板翻译** - 翻译计分板内容
- ✅ **Boss 血条翻译** - 翻译 Boss 名称
- ✅ **文字显示实体翻译** - 翻译文字显示实体内容

### ⚙️ 高级功能 / Advanced Features

- 🔄 **智能缓存系统** - 按类型分区的翻译缓存，支持持久化存储
- 🌍 **多世界缓存** - 支持为每个世界独立缓存翻译结果
- ⚡ **自动翻译** - 可配置自动翻译模式
- 👁️ **原文显示** - 可选择同时显示原文和译文
- 🎯 **告示牌翻译模式** - 支持范围内全部翻译或仅翻译视线内的告示牌
- 🕵️ **潜行模式** - 可配置仅在潜行时翻译告示牌
- ⏱️ **翻译延迟控制** - 可自定义翻译延迟，避免频繁请求
- 🎨 **ModMenu 集成** - 通过 ModMenu 轻松访问配置界面

---

## 🚀 快速开始 / Quick Start

1. 安装模组到你的 Minecraft 客户端
2. 启动游戏，按 `F3 + T` 打开配置界面（或通过 ModMenu）
3. 选择翻译 API 类型并配置相关设置
4. 启用你需要的翻译功能
5. 开始享受实时翻译！

### 免费使用 / Free Usage

无需任何配置即可使用免费翻译功能（Google 翻译、有道翻译）。

### API 配置 / API Configuration

如需使用更稳定的翻译服务，可以配置：
- **百度翻译 API**：访问 [百度翻译开放平台](https://fanyi-api.baidu.com/) 获取 AppID 和密钥
- **LLM API**：配置 OpenAI 兼容 API（如 DeepSeek、OpenAI 等）

---

## 📋 系统要求 / Requirements

- **Minecraft 版本**: 1.20.1
- **加载器**: Fabric
- **Java 版本**: 17+
- **必需依赖**: Fabric API
- **推荐依赖**: ModMenu（用于配置界面）

---

## ⚙️ 配置说明 / Configuration

所有配置选项都可以通过游戏内配置界面进行设置，配置文件位于：
```
config/simple-translation/config.json
```

### 主要配置选项 / Main Configuration Options

- `enabled` - 是否启用翻译功能
- `apiType` - 翻译 API 类型（free/baidu/baidu_llm/llm）
- `sourceLang` - 源语言（auto 为自动检测）
- `targetLang` - 目标语言（默认：zh 中文）
- `autoTranslate` - 是否自动翻译
- `showOriginal` - 是否显示原文
- `persistentCache` - 是否启用持久化缓存
- `perWorldCache` - 是否为每个世界独立缓存

---

## 🎮 使用技巧 / Tips

1. **告示牌翻译**：可以配置翻译模式为"范围内全部"来批量翻译多个告示牌
2. **书本翻译**：支持整本书批量翻译，适合翻译长文本内容
3. **缓存管理**：启用持久化缓存可以避免重复翻译相同内容，节省 API 调用
4. **LLM 翻译**：对于长文本（如书本），LLM 翻译提供更好的上下文理解

---

## 🔧 故障排除 / Troubleshooting

- **翻译失败**：检查网络连接和 API 配置
- **超时问题**：模组已针对长文本优化超时时间，如仍有问题请检查网络
- **缓存问题**：可以在配置中清除缓存或禁用持久化缓存

---

## 📝 许可证 / License

CC0-1.0

---

## 🙏 致谢 / Credits

感谢所有贡献者和翻译 API 提供商的支持！

---

**享受你的多语言 Minecraft 体验！** / **Enjoy your multilingual Minecraft experience!**

