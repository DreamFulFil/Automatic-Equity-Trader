package tw.gc.auto.equity.trader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class MtxfBotApplication {
    
    static {
        // Set default timezone to Asia/Taipei for all scheduled tasks and time operations
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
    }
    
    public static void main(String[] args) {
        SpringApplication.run(MtxfBotApplication.class, args);
    }
}


