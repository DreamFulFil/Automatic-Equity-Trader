package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.repositories.ShadowModeStockRepository;

@ExtendWith(MockitoExtension.class)
class ShadowModeStockServiceInitializeTest {

    @Mock
    private ShadowModeStockRepository repo;

    @InjectMocks
    private ShadowModeStockService service;

    @Test
    void initialize_shouldNotThrow() {
        service.initialize();
    }
}
