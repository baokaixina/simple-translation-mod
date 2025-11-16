package translation.modid.translator;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import translation.modid.SimpleTranslation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 免费翻译器 - 无需API密钥
 * 使用Google翻译的公开接口
 */
public class FreeTranslator {
    private static final String GOOGLE_API_URL = "https://translate.googleapis.com/translate_a/single";
    
    /**
     * 翻译文本（异步）
     * @param text 要翻译的文本
     * @param from 源语言（auto自动检测）
     * @param to 目标语言（zh-CN中文）
     * @return 翻译结果
     */
    public CompletableFuture<String> translateAsync(String text, String from, String to) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translate(text, from, to);
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("免费翻译失败: " + e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * 翻译文本（同步）
     */
    public String translate(String text, String from, String to) throws Exception {
        // 转换语言代码
        String sourceLang = convertLangCode(from);
        String targetLang = convertLangCode(to);
        
        SimpleTranslation.LOGGER.info("免费翻译API - 开始翻译: {} (从 {} 到 {})", text, sourceLang, targetLang);
        
        // 构建请求URL
        String urlStr = GOOGLE_API_URL + 
                "?client=gtx" +
                "&sl=" + sourceLang +
                "&tl=" + targetLang +
                "&dt=t" +
                "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        
        SimpleTranslation.LOGGER.debug("请求URL: {}", urlStr);
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        int responseCode = conn.getResponseCode();
        SimpleTranslation.LOGGER.info("HTTP响应码: {}", responseCode);
        
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
            SimpleTranslation.LOGGER.debug("API响应: {}", jsonResponse);
            
            // 解析JSON响应
            String result = parseGoogleResponse(jsonResponse);
            SimpleTranslation.LOGGER.info("免费翻译API - 翻译结果: {}", result);
            return result;
        } else {
            // 读取错误信息
            String errorMsg = "HTTP请求失败，响应码: " + responseCode;
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorMsg += ", 错误信息: " + errorResponse.toString();
            } catch (Exception e) {
                // 忽略读取错误流的异常
            }
            SimpleTranslation.LOGGER.error(errorMsg);
            throw new Exception(errorMsg);
        }
    }
    
    /**
     * 解析Google翻译响应
     */
    private String parseGoogleResponse(String json) throws Exception {
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            if (array.size() > 0 && array.get(0).isJsonArray()) {
                JsonArray translations = array.get(0).getAsJsonArray();
                StringBuilder result = new StringBuilder();
                
                for (int i = 0; i < translations.size(); i++) {
                    if (translations.get(i).isJsonArray()) {
                        JsonArray translation = translations.get(i).getAsJsonArray();
                        if (translation.size() > 0) {
                            result.append(translation.get(0).getAsString());
                        }
                    }
                }
                
                return result.toString();
            }
            throw new Exception("无法解析翻译结果");
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("解析响应失败: " + json, e);
            throw new Exception("解析翻译结果失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换语言代码
     * 将常用的语言代码转换为Google支持的格式
     */
    private String convertLangCode(String lang) {
        if (lang == null || lang.isEmpty()) {
            return "auto";
        }
        
        // 标准化语言代码
        lang = lang.toLowerCase();
        
        return switch (lang) {
            case "zh", "zh-cn", "zh_cn", "chinese" -> "zh-CN";
            case "zh-tw", "zh_tw" -> "zh-TW";
            case "en", "english" -> "en";
            case "ja", "japanese" -> "ja";
            case "ko", "korean" -> "ko";
            case "fr", "french" -> "fr";
            case "de", "german" -> "de";
            case "es", "spanish" -> "es";
            case "ru", "russian" -> "ru";
            case "auto" -> "auto";
            default -> lang;
        };
    }
}

