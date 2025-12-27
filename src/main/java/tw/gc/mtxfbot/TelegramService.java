package tw.gc.mtxfbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class TelegramService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${telegram.bot-token}")
    private String botToken;
    
    @Value("${telegram.chat-id}")
    private String chatId;
    
    @Value("${telegram.enabled}")
    private boolean enabled;
    
    public void sendMessage(String message) {
        if (!enabled) {
            log.info("📱 [Telegram disabled] {}", message);
            return;
        }
        
        try {
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url = String.format(
                    "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s",
                    botToken, chatId, encodedMessage);
            
            restTemplate.getForObject(url, String.class);
            log.debug("✅ Telegram message sent");
            
        } catch (Exception e) {
            log.error("❌ Failed to send Telegram message", e);
        }
    }
}
