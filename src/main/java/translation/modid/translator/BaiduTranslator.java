package translation.modid.translator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import translation.modid.SimpleTranslation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

public class BaiduTranslator {
    private static final String API_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";
    
    private final String appId;
    private final String secretKey;
    
    public BaiduTranslator(String appId, String secretKey) {
        this.appId = appId;
        this.secretKey = secretKey;
    }
    
    /**
     * 翻译文本（异步）
     * @param text 要翻译的文本
     * @param from 源语言（auto自动检测）
     * @param to 目标语言（zh中文）
     * @return 翻译结果
     */
    public CompletableFuture<String> translateAsync(String text, String from, String to) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translate(text, from, to);
            } catch (Exception e) {
                SimpleTranslation.LOGGER.error("翻译失败: " + e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * 翻译文本（同步）
     */
    public String translate(String text, String from, String to) throws Exception {
        if (appId == null || appId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            throw new Exception("百度翻译API密钥未配置！请在配置文件中设置 baiduAppId 和 baiduSecretKey");
        }
        
        // 生成随机数
        String salt = String.valueOf(System.currentTimeMillis());
        
        // 生成签名：MD5(appid+q+salt+密钥)
        String sign = md5(appId + text + salt + secretKey);
        
        // 构建请求URL
        String urlStr = API_URL + "?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&from=" + from
                + "&to=" + to
                + "&appid=" + appId
                + "&salt=" + salt
                + "&sign=" + sign;
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
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
            
            // 解析JSON响应
            return parseResponse(response.toString());
        } else {
            throw new Exception("HTTP请求失败，响应码: " + responseCode);
        }
    }
    
    /**
     * 解析百度翻译API响应
     */
    private String parseResponse(String json) throws Exception {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        
        // 检查是否有错误
        if (jsonObject.has("error_code")) {
            String errorCode = jsonObject.get("error_code").getAsString();
            String errorMsg = getErrorMessage(errorCode);
            throw new Exception("翻译API错误: " + errorMsg + " (错误码: " + errorCode + ")");
        }
        
        // 提取翻译结果 - 百度翻译会将多行文本分成多个结果返回
        if (jsonObject.has("trans_result") && jsonObject.get("trans_result").isJsonArray()) {
            com.google.gson.JsonArray transResults = jsonObject.getAsJsonArray("trans_result");
            StringBuilder result = new StringBuilder();
            
            // 百度翻译将每行作为独立结果返回，需要拼接
            for (int i = 0; i < transResults.size(); i++) {
                if (i > 0) {
                    result.append("\n"); // 用换行符连接多个结果
                }
                String dst = transResults.get(i).getAsJsonObject().get("dst").getAsString();
                result.append(dst);
            }
            
            String finalResult = result.toString();
            if (transResults.size() > 1) {
                translation.modid.SimpleTranslation.LOGGER.debug("百度翻译返回{}行结果，已合并", transResults.size());
            }
            
            return finalResult;
        }
        
        throw new Exception("无法解析翻译结果");
    }
    
    /**
     * 计算MD5
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 获取错误信息
     */
    private String getErrorMessage(String errorCode) {
        return switch (errorCode) {
            case "52001" -> "请求超时，请重试";
            case "52002" -> "系统错误，请重试";
            case "52003" -> "未授权用户，请检查appid是否正确";
            case "54000" -> "必填参数为空，请检查是否少传参数";
            case "54001" -> "签名错误，请检查您的签名生成方法";
            case "54003" -> "访问频率受限，请降低您的调用频率";
            case "54004" -> "账户余额不足";
            case "54005" -> "长query请求频繁，请降低长query的发送频率";
            case "58000" -> "客户端IP非法";
            case "58001" -> "译文语言方向不支持";
            case "58002" -> "服务当前已关闭，请前往管理控制台开启服务";
            default -> "未知错误";
        };
    }
}

