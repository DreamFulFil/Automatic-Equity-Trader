package tw.gc.auto.equity.trader.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.entities.StockRiskSettings;
import tw.gc.auto.equity.trader.entities.StockSettings;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ShioajiSettingsRepository shioajiSettingsRepository;

    @Autowired
    private StockSettingsRepository stockSettingsRepository;

    @Autowired
    private StockRiskSettingsRepository stockRiskSettingsRepository;

    @Test
    void testShioajiSettingsRepositoryFindFirst() {
        ShioajiSettings settings = ShioajiSettings.builder()
                .simulation(true)
                .build();
        
        entityManager.persist(settings);
        entityManager.flush();

        ShioajiSettings found = shioajiSettingsRepository.findFirst();
        assertNotNull(found);
        assertTrue(found.isSimulation());
    }

    @Test
    void testShioajiSettingsRepositoryFindFirstEmpty() {
        ShioajiSettings found = shioajiSettingsRepository.findFirst();
        assertNull(found);
    }

    @Test
    void testStockSettingsRepositoryFindFirst() {
        StockSettings settings = StockSettings.builder()
                .shares(70)
                .shareIncrement(27)
                .build();
        
        entityManager.persist(settings);
        entityManager.flush();

        StockSettings found = stockSettingsRepository.findFirst();
        assertNotNull(found);
        assertEquals(70, found.getShares());
    }

    @Test
    void testStockSettingsRepositoryFindFirstByOrderByIdDesc() {
        StockSettings settings1 = StockSettings.builder()
                .shares(70)
                .shareIncrement(27)
                .build();
        
        StockSettings settings2 = StockSettings.builder()
                .shares(100)
                .shareIncrement(30)
                .build();
        
        entityManager.persist(settings1);
        entityManager.persist(settings2);
        entityManager.flush();

        var found = stockSettingsRepository.findFirstByOrderByIdDesc();
        assertTrue(found.isPresent());
        assertEquals(100, found.get().getShares());
    }

    @Test
    void testStockRiskSettingsRepositoryFindFirst() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(50)
                .dailyLossLimitTwd(1000)
                .weeklyLossLimitTwd(4000)
                .build();
        
        entityManager.persist(settings);
        entityManager.flush();

        StockRiskSettings found = stockRiskSettingsRepository.findFirst();
        assertNotNull(found);
        assertEquals(50, found.getMaxSharesPerTrade());
    }

    @Test
    void testStockRiskSettingsRepositoryFindFirstEmpty() {
        StockRiskSettings found = stockRiskSettingsRepository.findFirst();
        assertNull(found);
    }
}
