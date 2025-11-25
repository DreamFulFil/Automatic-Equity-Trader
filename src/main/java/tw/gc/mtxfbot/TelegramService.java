package tw.gc.mtxfbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.config.TelegramProperties;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramService {

    private final RestTemplate restTemplate;
    private final TelegramProperties telegramProperties;

    public void sendMessage(String message) {
        if (!telegramProperties.isEnabled()) {
            log.info("[Telegram disabled] {}", message);
            return;
        }

        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", 
                    telegramProperties.getBotToken());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", telegramProperties.getChatId());
            body.put("text", message);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);
            log.debug("Telegram message sent");

        } catch (Exception e) {
            log.error("Failed to send Telegram message", e);
        }
    }
}
