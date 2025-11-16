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
 * 百度千帆大模型翻译器
 */
public class BaiduLLMTranslator {
    private final String apiKey;
    private final String secretKey;
    private final String model;
    private String accessToken;
    private long tokenExpireTime = 0;
    
    // 百度千帆大模型API地址
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    
    // 模型端点映射
    private static String getModelEndpoint(String model) {
        return switch (model) {
            case "ernie-4.0-turbo-8k" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-4.0-turbo-8k";
            case "ernie-4.0-8k" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-4.0-8k";
            case "ernie-3.5-8k" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-3.5-8k";
            case "ernie-speed-128k" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k";
            case "ernie-lite-8k" -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-lite-8k";
            default -> "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-4.0-turbo-8k";
        };
    }
    
    public BaiduLLMTranslator(String apiKey, String secretKey, String model) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.model = model != null && !model.isEmpty() ? model : "ernie-4.0-turbo-8k";
    }
    
    /**
     * 获取Access Token
     */
    private String getAccessToken() throws Exception {
        // 如果token还没过期，直接返回
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        
        String urlStr = TOKEN_URL + "?grant_type=client_credentials"
                + "&client_id=" + apiKey
                + "&client_secret=" + secretKey;
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
            
            if (jsonObject.has("error")) {
                throw new Exception("获取Access Token失败: " + jsonObject.get("error_description").getAsString());
            }
            
            accessToken = jsonObject.get("access_token").getAsString();
            int expiresIn = jsonObject.get("expires_in").getAsInt();
            // 提前5分钟过期
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
            
            SimpleTranslation.LOGGER.info("百度千帆大模型Access Token获取成功");
            return accessToken;
        } else {
            throw new Exception("获取Access Token失败，HTTP响应码: " + responseCode);
        }
    }
    
    /**
     * 翻译文本（异步）
     */
    public CompletableFuture<String> translateAsync(String text, String from, String to) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translate(text, from, to);
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("百度千帆大模型翻译失败", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 翻译文本（同步）
     */
    public String translate(String text, String from, String to) throws Exception {
        if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            throw new Exception("百度千帆大模型API密钥未配置！");
        }
        
        // 获取Access Token
        String token = getAccessToken();
        
        // 构建请求
        String endpoint = getModelEndpoint(model);
        String urlStr = endpoint + "?access_token=" + token;
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        
        // 构建请求体
        JsonObject requestBody = new JsonObject();
        JsonArray messages = new JsonArray();
        
        // 系统提示
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "user");
        systemMessage.addProperty("content", buildSystemPrompt(to));
        messages.add(systemMessage);
        
        // 用户消息
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "assistant");
        userMessage.addProperty("content", "我明白了，我会按照要求进行翻译。");
        messages.add(userMessage);
        
        // 实际翻译请求
        JsonObject actualRequest = new JsonObject();
        actualRequest.addProperty("role", "user");
        actualRequest.addProperty("content", text);
        messages.add(actualRequest);
        
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.3);
        requestBody.addProperty("top_p", 0.8);
        
        // 发送请求
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            return parseResponse(response.toString());
        } else {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            throw new Exception("百度千帆大模型API请求失败: " + errorResponse.toString());
        }
    }
    
    /**
     * 构建系统提示
     */
    private String buildSystemPrompt(String targetLang) {
        String langName = switch (targetLang.toLowerCase()) {
            case "zh", "zh-cn", "zh-hans" -> "简体中文";
            case "zh-tw", "zh-hk", "zh-hant" -> "繁体中文";
            case "en" -> "英语";
            case "ja" -> "日语";
            case "ko" -> "韩语";
            case "fr" -> "法语";
            case "de" -> "德语";
            case "es" -> "西班牙语";
            case "ru" -> "俄语";
            default -> "中文";
        };
        
        return String.format(
            "你是一个专业的Minecraft游戏翻译助手。请将用户提供的文本翻译成%s。\n\n" +
            "重要规则：\n" +
            "1. 直接输出翻译结果，不要添加任何解释或说明\n" +
            "2. 如果原文包含多行，保持相同的行数\n" +
            "3. 保持原文的格式和换行符\n" +
            "4. 不要翻译游戏内的专有名词（如物品名、地名等）\n" +
            "5. 保持简洁，适合游戏内显示",
            langName
        );
    }
    
    /**
     * 解析响应
     */
    private String parseResponse(String json) throws Exception {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            
            // 检查错误
            if (jsonObject.has("error_code")) {
                int errorCode = jsonObject.get("error_code").getAsInt();
                String errorMsg = jsonObject.has("error_msg") ? jsonObject.get("error_msg").getAsString() : "未知错误";
                throw new Exception("百度千帆大模型API错误: " + errorMsg + " (错误码: " + errorCode + ")");
            }
            
            // 提取翻译结果
            if (jsonObject.has("result")) {
                return jsonObject.get("result").getAsString().trim();
            }
            
            throw new Exception("无法解析百度千帆大模型响应");
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("解析响应失败: " + json, e);
            throw new Exception("解析翻译结果失败: " + e.getMessage());
        }
    }
}

