package translation.modid.translator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import translation.modid.SimpleTranslation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * LLM翻译器 - 支持所有OpenAI兼容API
 * 包括：DeepSeek、OpenAI、豆包(火山引擎)、智谱AI等
 */
public class LLMTranslator {
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String customSystemPrompt; // 用户自定义的系统提示词
    
    public LLMTranslator(String apiKey, String apiUrl, String model, String customSystemPrompt) {
        this.apiKey = apiKey;
        this.customSystemPrompt = customSystemPrompt;
        
        // 处理API URL - 如果用户未填写，使用DeepSeek默认地址
        String finalApiUrl = apiUrl != null && !apiUrl.isEmpty() ? apiUrl : "https://api.deepseek.com/v1";
        
        // 如果URL不包含完整路径，自动添加 /chat/completions
        if (!finalApiUrl.endsWith("/chat/completions")) {
            if (!finalApiUrl.contains("/chat/completions")) {
                // 去除末尾的斜杠
                if (finalApiUrl.endsWith("/")) {
                    finalApiUrl = finalApiUrl.substring(0, finalApiUrl.length() - 1);
                }
                finalApiUrl = finalApiUrl + "/chat/completions";
            }
        }
        this.apiUrl = finalApiUrl;
        
        // 处理模型名称 - 如果用户未填写，使用deepseek-chat默认模型
        this.model = model != null && !model.isEmpty() ? model : "deepseek-chat";
        
        // 初始化时打印配置
        SimpleTranslation.LOGGER.info("=== LLM翻译器初始化 ===");
        SimpleTranslation.LOGGER.info("API地址: {}", this.apiUrl);
        SimpleTranslation.LOGGER.info("模型: {}", this.model);
        SimpleTranslation.LOGGER.info("API密钥已设置: {}", apiKey != null && !apiKey.isEmpty());
        if (apiKey != null && !apiKey.isEmpty()) {
            SimpleTranslation.LOGGER.info("API密钥长度: {}", apiKey.length());
            SimpleTranslation.LOGGER.info("API密钥前缀: {}***", apiKey.substring(0, Math.min(10, apiKey.length())));
        }
        SimpleTranslation.LOGGER.info("=====================");
    }
    
    /**
     * 翻译文本（异步）
     */
    public CompletableFuture<String> translateAsync(String text, String targetLang) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translate(text, targetLang);
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("LLM翻译失败: " + e.getMessage(), e);
                return null;
            }
        });
    }
    
    /**
     * 批量翻译多个文本（异步）
     * @param texts 要翻译的文本列表
     * @param targetLang 目标语言
     * @return 翻译结果的CompletableFuture，返回Map<原文, 译文>
     */
    public CompletableFuture<java.util.Map<String, String>> translateBatchAsync(java.util.List<String> texts, String targetLang) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(new java.util.HashMap<>());
        }
        
        // 如果只有一个文本，直接使用单文本翻译
        if (texts.size() == 1) {
            return translateAsync(texts.get(0), targetLang)
                    .thenApply(result -> {
                        java.util.Map<String, String> map = new java.util.HashMap<>();
                        if (result != null) {
                            map.put(texts.get(0), result);
                        }
                        return map;
                    });
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translateBatch(texts, targetLang);
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("LLM批量翻译失败: " + e.getMessage(), e);
                return new java.util.HashMap<>();
            }
        });
    }
    
    /**
     * 批量翻译多个文本（同步）
     */
    private java.util.Map<String, String> translateBatch(java.util.List<String> texts, String targetLang) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("LLM API密钥未配置！");
        }
        
        // 合并所有文本，用特殊分隔符分隔
        String separator = "\n\n---TRANSLATE_SEPARATOR---\n\n";
        StringBuilder combinedText = new StringBuilder();
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                combinedText.append(separator);
            }
            combinedText.append("[").append(i).append("] ").append(texts.get(i));
        }
        
        String systemPrompt = buildBatchSystemPrompt(targetLang, texts.size());
        
        // 构建请求JSON
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model);
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", combinedText.toString());
        messages.add(userMessage);
        
        requestJson.add("messages", messages);
        requestJson.addProperty("temperature", 0.3);
        int totalLength = combinedText.length();
        int maxTokens = Math.max(500, Math.min(2000, totalLength * 2 + 200));
        requestJson.addProperty("max_tokens", maxTokens);
        
        String requestBody = requestJson.toString();
        
        // 发送HTTP请求
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        // 批量翻译需要更长的超时时间
        int readTimeout;
        if (totalLength > 2000) {
            // 超长文本（整本书等）：每1000字符40秒，最多120秒
            readTimeout = Math.max(60000, Math.min(120000, totalLength * 40));
        } else if (totalLength > 1000) {
            // 长文本：每1000字符30秒，最多60秒
            readTimeout = Math.max(30000, Math.min(60000, totalLength * 30));
        } else {
            // 中等文本：每100字符15秒，最多45秒
            readTimeout = Math.max(15000, Math.min(45000, totalLength * 15));
        }
        SimpleTranslation.LOGGER.debug("批量翻译 - 总长度: {} 字符, 设置超时时间: {} 秒", totalLength, readTimeout / 1000);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(readTimeout);
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            String jsonResponse = response.toString();
            String result = parseLLMResponse(jsonResponse);
            
            // 解析批量翻译结果
            return parseBatchResult(result, texts, separator);
        } else {
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)
            );
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            
            throw new Exception("LLM API请求失败，响应码: " + responseCode + ", 错误: " + errorResponse.toString());
        }
    }
    
    /**
     * 构建批量翻译系统提示
     */
    private String buildBatchSystemPrompt(String targetLang, int count) {
        String langName = switch (targetLang.toLowerCase()) {
            case "zh", "zh-cn", "zh_cn" -> "简体中文";
            case "zh-tw", "zh_tw" -> "繁体中文";
            case "en" -> "English";
            case "ja" -> "日本語";
            case "ko" -> "한국어";
            default -> targetLang;
        };
        
        return String.format(
            "你是一个专业的Minecraft游戏翻译助手。请将用户发送的%d个文本翻译成%s。\n\n" +
            "文本格式：每个文本以 [序号] 开头，用 ---TRANSLATE_SEPARATOR--- 分隔。\n\n" +
            "翻译要求：\n" +
            "1. **只返回翻译结果**，不要添加任何解释、注释或额外内容\n" +
            "2. **保持格式**：每个翻译结果必须以对应的 [序号] 开头，用 ---TRANSLATE_SEPARATOR--- 分隔\n" +
            "3. **理解完整上下文**：每个文本请理解整体含义后再翻译，保持语义连贯\n" +
            "4. **保持行数**：原文有多少行，译文就必须有多少行\n" +
            "5. **保留特殊标记**：颜色代码（如§7、§a等）必须原样保留\n" +
            "6. **术语处理**：游戏物品、方块、实体名称要使用通用的中文译名\n\n" +
            "输出格式示例：\n" +
            "[0] 第一个文本的翻译\n" +
            "---TRANSLATE_SEPARATOR---\n" +
            "[1] 第二个文本的翻译\n" +
            "---TRANSLATE_SEPARATOR---\n" +
            "[2] 第三个文本的翻译",
            count, langName
        );
    }
    
    /**
     * 解析批量翻译结果
     */
    private java.util.Map<String, String> parseBatchResult(String result, java.util.List<String> texts, String separator) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        
        if (result == null || result.trim().isEmpty()) {
            return map;
        }
        
        // 按分隔符分割结果
        String[] parts = result.split(separator);
        
        for (int i = 0; i < Math.min(parts.length, texts.size()); i++) {
            String translated = parts[i].trim();
            // 移除 [序号] 前缀
            if (translated.startsWith("[" + i + "]")) {
                translated = translated.substring(translated.indexOf("]") + 1).trim();
            }
            if (!translated.isEmpty()) {
                map.put(texts.get(i), translated);
            }
        }
        
        return map;
    }
    
    /**
     * 翻译文本（同步）
     */
    public String translate(String text, String targetLang) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("LLM API密钥未配置！");
        }
        
        // 只在debug模式下输出详细日志
        if (SimpleTranslation.LOGGER.isDebugEnabled()) {
            SimpleTranslation.LOGGER.debug("LLM翻译 - 开始翻译: {}", text);
            SimpleTranslation.LOGGER.debug("API地址: {}", apiUrl);
            SimpleTranslation.LOGGER.debug("模型: {}", model);
        }
        
        try {
            // 构建系统提示
            String systemPrompt = buildSystemPrompt(targetLang);
        
        // 构建请求JSON
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model);
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", text);
        messages.add(userMessage);
        
        requestJson.add("messages", messages);
        requestJson.addProperty("temperature", 0.3);
        // 根据文本长度动态调整max_tokens，减少不必要的token限制
        int textLength = text.length();
        int maxTokens = Math.max(200, Math.min(1000, textLength * 2 + 100));
        requestJson.addProperty("max_tokens", maxTokens);
        
        String requestBody = requestJson.toString();
        SimpleTranslation.LOGGER.debug("LLM请求: {}", requestBody);
        
        // 发送HTTP请求
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        // 根据文本长度动态调整超时时间（使用上面已定义的textLength变量）
        // 对于长文本（如书本翻译、告示牌批量翻译），需要更长的超时时间
        int readTimeout;
        if (textLength > 2000) {
            // 超长文本（整本书等）：每1000字符40秒，最多120秒
            readTimeout = Math.max(60000, Math.min(120000, textLength * 40));
        } else if (textLength > 1000) {
            // 长文本（告示牌等）：每1000字符30秒，最多60秒
            readTimeout = Math.max(30000, Math.min(60000, textLength * 30));
        } else if (textLength > 500) {
            // 中等文本（书本单页等）：每100字符15秒，最多45秒
            readTimeout = Math.max(15000, Math.min(45000, textLength * 15));
        } else {
            // 短文本：10-30秒
            readTimeout = Math.max(10000, Math.min(30000, textLength * 20));
        }
        
        SimpleTranslation.LOGGER.debug("文本长度: {} 字符, 设置超时时间: {} 秒", textLength, readTimeout / 1000);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(readTimeout);
        
        // 写入请求体
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        if (SimpleTranslation.LOGGER.isDebugEnabled()) {
            SimpleTranslation.LOGGER.debug("LLM API响应码: {}", responseCode);
        }
        
        if (responseCode == 200) {
            // 读取响应
            BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            String jsonResponse = response.toString();
            SimpleTranslation.LOGGER.debug("LLM响应: {}", jsonResponse);
            
            // 解析响应
            String result = parseLLMResponse(jsonResponse);
            if (SimpleTranslation.LOGGER.isDebugEnabled()) {
                SimpleTranslation.LOGGER.debug("LLM翻译 - 翻译结果: {}", result);
            }
            return result;
        } else {
            // 读取错误信息
            String errorResponseText = "";
            try {
                if (conn.getErrorStream() != null) {
                    BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)
                    );
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    errorReader.close();
                    errorResponseText = errorResponse.toString();
                } else {
                    errorResponseText = "无法获取错误详情（服务器未返回错误流）";
                }
            } catch (Exception e) {
                errorResponseText = "读取错误信息失败: " + e.getMessage();
            }
            
            // 根据错误码给出更详细的提示
            String errorDetail = switch (responseCode) {
                case 401 -> "API密钥无效或未授权。请检查：\n" +
                            "1. API密钥是否正确填写\n" +
                            "2. API密钥是否已激活\n" +
                            "3. 提供商选择是否正确（OpenAI/DeepSeek）";
                case 403 -> "权限不足，请检查API密钥权限设置";
                case 404 -> "API地址错误，请检查API URL配置";
                case 429 -> "请求频率过高，请稍后再试或升级API套餐";
                case 500, 502, 503 -> "API服务暂时不可用，请稍后再试";
                default -> "未知错误";
            };
            
            String errorMsg = String.format(
                "LLM API请求失败\n" +
                "API地址: %s\n" +
                "响应码: %d\n" +
                "错误详情: %s\n" +
                "服务器响应: %s",
                apiUrl, responseCode, errorDetail, errorResponseText
            );
            SimpleTranslation.LOGGER.error(errorMsg);
            throw new Exception(errorMsg);
        }
        } catch (java.net.UnknownHostException e) {
            String errorMsg = String.format(
                "无法连接到LLM API服务器\n" +
                "API地址: %s\n" +
                "错误: 主机名无法解析\n" +
                "请检查：\n" +
                "1. API地址是否正确\n" +
                "2. 网络连接是否正常\n" +
                "3. 如果使用OpenAI官方地址，可能需要配置代理或使用中转地址",
                apiUrl
            );
            SimpleTranslation.LOGGER.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        } catch (java.net.SocketTimeoutException e) {
            String errorMsg = String.format(
                "LLM API请求超时\n" +
                "API地址: %s\n" +
                "建议：\n" +
                "1. 检查网络连接\n" +
                "2. 稍后重试\n" +
                "3. 如果在中国大陆使用OpenAI官方地址，可能需要配置代理",
                apiUrl
            );
            SimpleTranslation.LOGGER.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        } catch (java.io.IOException e) {
            String errorMsg = String.format(
                "LLM API网络连接失败\n" +
                "API地址: %s\n" +
                "错误: %s\n" +
                "请检查网络连接和API地址配置",
                apiUrl, e.getMessage()
            );
            SimpleTranslation.LOGGER.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format(
                "LLM翻译发生未知错误\n" +
                "API地址: %s\n" +
                "错误类型: %s\n" +
                "错误信息: %s",
                apiUrl, e.getClass().getName(), e.getMessage()
            );
            SimpleTranslation.LOGGER.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }
    
    /**
     * 构建系统提示
     */
    private String buildSystemPrompt(String targetLang) {
        String langName = switch (targetLang.toLowerCase()) {
            case "zh", "zh-cn", "zh_cn" -> "简体中文";
            case "zh-tw", "zh_tw" -> "繁体中文";
            case "en" -> "English";
            case "ja" -> "日本語";
            case "ko" -> "한국어";
            default -> targetLang;
        };
        
        // 如果用户设置了自定义提示词，使用自定义的
        if (customSystemPrompt != null && !customSystemPrompt.trim().isEmpty()) {
            // 在自定义提示词前添加目标语言说明
            return String.format(
                "请将用户发送的文本翻译成%s。\n\n" +
                "附加要求：\n%s\n\n" +
                "注意：\n" +
                "1. 只返回翻译结果，不要添加任何解释\n" +
                "2. 严格保持原文的行数和格式\n" +
                "3. 保留所有特殊标记（如§7、§a等颜色代码）",
                langName, customSystemPrompt.trim()
            );
        }
        
        // 使用默认的详细提示词
        return String.format(
            "你是一个专业的Minecraft游戏翻译助手。请将用户发送的文本翻译成%s。\n\n" +
            "翻译要求：\n" +
            "1. **只返回翻译结果**，不要添加任何解释、注释或额外内容\n" +
            "2. **理解完整上下文**：文本可能包含多行，请理解整体含义后再翻译，保持语义连贯\n" +
            "3. **物品描述翻译**：如果是物品描述（tooltip），使用流畅自然的中文表达，不要逐字直译\n" +
            "4. **严格保持行数**（重要）：\n" +
            "   - 原文有多少行，译文就必须有多少行\n" +
            "   - 每一行原文对应一行译文\n" +
            "   - 不要合并多行或拆分成更多行\n" +
            "   - 使用实际换行符分隔，不要写成\\n字符串字面量\n" +
            "5. **保留特殊标记**：\n" +
            "   - 颜色代码（如§7、§a、§f等）必须原样保留在对应位置\n" +
            "   - 分页标记（如===第2页===）必须原样保留\n" +
            "6. **术语处理**：\n" +
            "   - 游戏物品、方块、实体名称要使用通用的中文译名\n" +
            "   - 玩家名称、地名等专有名词可以保持原样或音译\n" +
            "7. **语言检测**：如果文本已经是目标语言，直接返回原文\n\n" +
            "翻译示例：\n" +
            "原文(4行):\n" +
            "\"Runic Vessel\n" +
            "An arcane crate dotted with\n" +
            "magical runes. It can be used as\n" +
            "a portable storage device.\"\n\n" +
            "正确译文(4行，行数匹配):\n" +
            "\"符文容器\n" +
            "一个点缀着魔法符文的\n" +
            "奥术箱子。可以用作\n" +
            "便携式存储装置。\"\n\n" +
            "错误译文(3行，行数不匹配): \"符文容器\\n点缀着神秘符文的奥术箱。\\n可用作便携式存储装置。\" (缺少1行)",
            langName
        );
    }
    
    /**
     * 解析LLM响应
     */
    private String parseLLMResponse(String json) throws Exception {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            
            // 检查错误
            if (jsonObject.has("error")) {
                JsonObject error = jsonObject.getAsJsonObject("error");
                String errorMsg = error.has("message") ? error.get("message").getAsString() : "未知错误";
                throw new Exception("LLM API错误: " + errorMsg);
            }
            
            // 提取翻译结果
            if (jsonObject.has("choices")) {
                JsonArray choices = jsonObject.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("message")) {
                        JsonObject message = choice.getAsJsonObject("message");
                        if (message.has("content")) {
                            return message.get("content").getAsString().trim();
                        }
                    }
                }
            }
            
            throw new Exception("无法解析LLM响应");
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("解析LLM响应失败: " + json, e);
            throw new Exception("解析翻译结果失败: " + e.getMessage());
        }
    }
}

