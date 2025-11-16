package translation.modid.translator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
 * 有道免费翻译器 - 使用公开接口
 */
public class YoudaoFreeTranslator {
    private static final String API_URL = "https://fanyi.youdao.com/translate";
    
    /**
     * 翻译文本（异步）
     */
    public CompletableFuture<String> translateAsync(String text, String from, String to) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translate(text, from, to);
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("有道免费翻译失败: " + e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * 翻译文本（同步）
     */
    public String translate(String text, String from, String to) throws Exception {
        SimpleTranslation.LOGGER.info("有道免费翻译API - 开始翻译: {}", text);
        
        // 构建请求参数
        String params = "i=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                "&from=AUTO" +
                "&to=AUTO" +
                "&smartresult=dict" +
                "&client=fanyideskweb" +
                "&doctype=json" +
                "&version=2.1" +
                "&keyfrom=fanyi.web" +
                "&action=FY_BY_REALTlME";
        
        URL url = new URL(API_URL + "?_o=n&smartresult=dict&smartresult=rule");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Referer", "https://fanyi.youdao.com/");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        // 发送POST数据
        conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));
        
        int responseCode = conn.getResponseCode();
        SimpleTranslation.LOGGER.info("有道API响应码: {}", responseCode);
        
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
            SimpleTranslation.LOGGER.debug("有道API响应: {}", jsonResponse);
            
            // 解析响应
            String result = parseYoudaoResponse(jsonResponse);
            SimpleTranslation.LOGGER.info("有道免费翻译 - 翻译结果: {}", result);
            return result;
        } else {
            throw new Exception("HTTP请求失败，响应码: " + responseCode);
        }
    }
    
    /**
     * 解析有道翻译响应
     */
    private String parseYoudaoResponse(String json) throws Exception {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            
            // 检查错误码
            if (jsonObject.has("errorCode")) {
                int errorCode = jsonObject.get("errorCode").getAsInt();
                if (errorCode != 0) {
                    throw new Exception("有道翻译API错误码: " + errorCode);
                }
            }
            
            // 提取翻译结果
            if (jsonObject.has("translateResult")) {
                JsonArray translateResult = jsonObject.getAsJsonArray("translateResult");
                if (translateResult.size() > 0) {
                    JsonArray firstArray = translateResult.get(0).getAsJsonArray();
                    if (firstArray.size() > 0) {
                        JsonObject firstObj = firstArray.get(0).getAsJsonObject();
                        if (firstObj.has("tgt")) {
                            return firstObj.get("tgt").getAsString();
                        }
                    }
                }
            }
            
            throw new Exception("无法解析有道翻译结果");
        } catch (Exception e) {
            SimpleTranslation.LOGGER.error("解析有道响应失败: " + json, e);
            throw new Exception("解析翻译结果失败: " + e.getMessage());
        }
    }
}

