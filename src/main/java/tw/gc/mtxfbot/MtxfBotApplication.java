package tw.gc.mtxfbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MtxfBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(MtxfBotApplication.class, args);
    }
}
