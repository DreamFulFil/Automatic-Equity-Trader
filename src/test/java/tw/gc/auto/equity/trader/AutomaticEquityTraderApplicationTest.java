package tw.gc.auto.equity.trader;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class AutomaticEquityTraderApplicationTest {

    @Test
    void main_shouldInvokeSpringApplicationRun() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(eq(AutomaticEquityTraderApplication.class), any(String[].class)))
                    .thenReturn(null);

            AutomaticEquityTraderApplication.main(new String[]{"--test"});

            springApplication.verify(() -> SpringApplication.run(eq(AutomaticEquityTraderApplication.class), any(String[].class)));
        }
    }
}
