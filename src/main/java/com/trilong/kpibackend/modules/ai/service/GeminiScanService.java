package com.trilong.kpibackend.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GeminiScanService — chấm điểm bài đăng mạng xã hội bằng Google Gemini.
 *
 * <p>API key được giữ Ở BACKEND (biến môi trường {@code GEMINI_API_KEY}), KHÔNG đưa
 * xuống trình duyệt. Trước đây web gọi thẳng Google kèm key nên ai mở DevTools cũng
 * lấy được key — đã từng dẫn tới việc Google đình chỉ project.
 *
 * <p>Luồng:
 * <pre>
 *   Web ──(JWT)──► AiScanController ──► GeminiScanService ──(API key)──► Google Gemini
 * </pre>
 */
@Slf4j
@Service
public class GeminiScanService {

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

    /** Ảnh lớn hơn mức này sẽ bị bỏ qua (chỉ phân tích phần chữ) để tránh tốn băng thông. */
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024; // 4MB

    private final RestClient restClient = RestClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api-key:}")
    private String apiKey;

    /** Dùng alias "-latest" để Google tự trỏ sang bản Flash mới nhất, tránh lỗi model bị khai tử. */
    @Value("${gemini.model:gemini-flash-latest}")
    private String model;

    /**
     * Chấm điểm một bài đăng.
     *
     * @param caption       nội dung bài đăng
     * @param screenshotUrl link ảnh chụp màn hình (có thể null/rỗng)
     * @return map gồm {@code score}, {@code suggestion} (RECOMMEND|REVIEW), {@code reason}
     */
    public Map<String, Object> scanPost(String caption, String screenshotUrl) {
        String cap = caption == null ? "" : caption.trim();
        String shot = screenshotUrl == null ? "" : screenshotUrl.trim();

        if (cap.isEmpty() && shot.isEmpty()) {
            return result(0, "REVIEW", "Không có dữ liệu bài đăng.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[AI] Chưa cấu hình GEMINI_API_KEY — bỏ qua chấm điểm.");
            return result(0, "REVIEW",
                    "Máy chủ chưa cấu hình khóa API Gemini. Vui lòng liên hệ quản trị viên.");
        }

        try {
            Map<String, Object> body = buildPayload(cap, shot);

            JsonNode root = null;
            int lastCode = 0;
            String lastMsg = "";

            // Gemini (nhất là gói miễn phí) hay trả 503 "overloaded" hoặc 429 khi bận.
            // Thử lại tối đa 3 lần, giãn dần 1s → 2s trước khi báo lỗi cho người dùng.
            for (int attempt = 1; attempt <= 3; attempt++) {
                String raw = restClient.post()
                        .uri(GEMINI_BASE + "/" + model + ":generateContent?key=" + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(status -> status.isError(), (req, res) -> {
                            // Không ném exception mặc định — tự đọc nội dung lỗi để báo tiếng Việt
                        })
                        .body(String.class);

                root = mapper.readTree(raw == null ? "{}" : raw);

                if (!root.has("error")) break;

                lastCode = root.path("error").path("code").asInt();
                lastMsg = root.path("error").path("message").asText("");

                boolean canRetry = (lastCode == 503 || lastCode == 429) && attempt < 3;
                if (!canRetry) break;

                log.warn("[AI] Gemini bận (lỗi {}), thử lại lần {}/3...", lastCode, attempt + 1);
                Thread.sleep(1000L * attempt);
            }

            // Google trả lỗi (sau khi đã thử lại)
            if (root != null && root.has("error")) {
                log.error("[AI] Gemini lỗi {}: {}", lastCode, lastMsg);
                return result(0, "REVIEW", friendlyError(lastCode, lastMsg));
            }

            // Bị bộ lọc an toàn chặn
            String blockReason = root.path("promptFeedback").path("blockReason").asText("");
            if (!blockReason.isEmpty()) {
                return result(0, "REVIEW", "Gemini từ chối phân tích nội dung này (lý do: " + blockReason + ").");
            }

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return result(0, "REVIEW", "Gemini không trả về kết quả phân tích.");
            }

            String text = extractText(candidates.get(0));
            if (text.isBlank()) {
                return result(0, "REVIEW", "Gemini trả về kết quả rỗng.");
            }

            return parseResult(text);

        } catch (Exception e) {
            log.error("[AI] Lỗi khi gọi Gemini: {}", e.getMessage(), e);
            return result(0, "REVIEW", "Không gọi được dịch vụ AI: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────── build payload

    private Map<String, Object> buildPayload(String caption, String screenshotUrl) {
        var parts = new java.util.ArrayList<Map<String, Object>>();

        // Ảnh (nếu tải được) đặt trước phần chữ
        if (!screenshotUrl.isEmpty()) {
            try {
                byte[] bytes = downloadImage(screenshotUrl);
                if (bytes != null && bytes.length > 0) {
                    parts.add(Map.of("inline_data", Map.of(
                            "mime_type", guessMime(screenshotUrl),
                            "data", java.util.Base64.getEncoder().encodeToString(bytes)
                    )));
                }
            } catch (Exception ex) {
                log.warn("[AI] Không tải được ảnh {} — chỉ phân tích phần chữ. Lý do: {}",
                        screenshotUrl, ex.getMessage());
            }
        }

        parts.add(Map.of("text", buildPrompt(caption)));

        return Map.of("contents", java.util.List.of(Map.of("parts", parts)));
    }

    private String buildPrompt(String caption) {
        return """
                Bạn là trợ lý AI kiểm duyệt bài đăng mạng xã hội của nhân viên môi giới Bất Động Sản.
                Yêu cầu: Đánh giá xem bài đăng này có đáp ứng tốt việc quảng bá dự án Bất Động Sản không \
                (chứa thông tin bán nhà, dự án, đất nền, vị trí, giá bán, tiện ích...).
                Đánh giá theo thang điểm từ 0-100.
                Nếu bài đăng rất tốt, hãy Khuyên duyệt (RECOMMEND). Nếu bài đăng lạc đề \
                (ví dụ đi chơi, ăn uống cá nhân) hoặc không có thông tin rõ ràng, hãy Cần xem xét (REVIEW).
                Caption bài đăng: "%s"

                Vui lòng trả về kết quả dưới định dạng JSON hợp lệ (chỉ trả về chuỗi JSON, không kèm markdown block):
                {
                  "score": <số điểm từ 0-100>,
                  "suggestion": "RECOMMEND" hoặc "REVIEW",
                  "reason": "<Giải thích phân tích ngắn gọn bằng tiếng Việt>"
                }
                """.formatted(caption);
    }

    /**
     * Tải ảnh về để gửi kèm cho Gemini.
     * Chặn các địa chỉ nội bộ để tránh bị lợi dụng dò quét mạng nội bộ (SSRF),
     * vì screenshotUrl là dữ liệu do người dùng gửi lên.
     */
    private byte[] downloadImage(String imageUrl) throws Exception {
        URI uri = URI.create(imageUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Chỉ chấp nhận link http/https");
        }
        InetAddress addr = InetAddress.getByName(uri.getHost());
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
            throw new IllegalArgumentException("Không cho phép tải ảnh từ địa chỉ nội bộ");
        }

        var conn = (java.net.HttpURLConnection) new URL(imageUrl).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(true);
        try (var in = conn.getInputStream()) {
            byte[] data = in.readNBytes(MAX_IMAGE_BYTES);
            return data;
        } finally {
            conn.disconnect();
        }
    }

    private String guessMime(String url) {
        String u = url.toLowerCase();
        if (u.contains(".png")) return "image/png";
        if (u.contains(".webp")) return "image/webp";
        if (u.contains(".gif")) return "image/gif";
        return "image/jpeg";
    }

    // ─────────────────────────────────────────────────────────── parse & error

    private String extractText(JsonNode candidate) {
        JsonNode parts = candidate.path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode p : parts) {
                String t = p.path("text").asText("");
                if (!t.isBlank()) return t;
            }
        }
        return "";
    }

    private Map<String, Object> parseResult(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.replaceFirst("^```json\\s*", "").replaceFirst("\\s*```$", "");
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            JsonNode n = mapper.readTree(cleaned);
            int score = n.path("score").asInt(0);
            String suggestion = n.path("suggestion").asText("REVIEW");
            String reason = n.path("reason").asText("Đã phân tích nội dung thành công.");
            if (!"RECOMMEND".equals(suggestion)) suggestion = "REVIEW";
            return result(Math.max(0, Math.min(100, score)), suggestion, reason);
        } catch (Exception e) {
            log.error("[AI] Không đọc được JSON từ Gemini: {}", cleaned);
            return result(0, "REVIEW", "Kết quả AI trả về không đúng định dạng, cần xem xét thủ công.");
        }
    }

    private String friendlyError(int code, String msg) {
        String m = msg == null ? "" : msg;
        if (code == 403 && m.toLowerCase().contains("suspend")) {
            return "Khóa API Gemini đã bị Google đình chỉ. Cần tạo khóa mới ở project khác.";
        }
        if (code == 400 && m.toLowerCase().contains("api key not valid")) {
            return "Khóa API Gemini không hợp lệ. Kiểm tra lại biến môi trường GEMINI_API_KEY.";
        }
        if (code == 429) {
            return "Đã vượt hạn mức gọi Gemini. Vui lòng thử lại sau ít phút.";
        }
        if (code == 503) {
            return "Dịch vụ Gemini đang quá tải. Vui lòng thử lại sau giây lát.";
        }
        if (code == 404) {
            return "Không tìm thấy model \"" + model + "\". Kiểm tra lại cấu hình GEMINI_MODEL.";
        }
        return "Gemini báo lỗi " + code + ".";
    }

    private Map<String, Object> result(int score, String suggestion, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", score);
        m.put("suggestion", suggestion);
        m.put("reason", reason);
        return m;
    }
}
