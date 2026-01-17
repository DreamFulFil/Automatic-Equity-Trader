package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TaiwanStockNameServiceTest {

    @InjectMocks
    private TaiwanStockNameService service;

    @Test
    void testGetStockName_KnownSymbol() {
        String name = service.getStockName("2330.TW");
        assertThat(name).isEqualTo("Taiwan Semiconductor Manufacturing");
    }

    @Test
    void testGetStockName_UnknownSymbol() {
        String name = service.getStockName("9999.TW");
        assertThat(name).isEqualTo("9999.TW");
    }

    @Test
    void testHasStockName_KnownSymbol() {
        assertThat(service.hasStockName("2454.TW")).isTrue();
    }

    @Test
    void testHasStockName_UnknownSymbol() {
        assertThat(service.hasStockName("UNKNOWN.TW")).isFalse();
    }

    @Test
    void testGetAllSymbols() {
        Set<String> symbols = service.getAllSymbols();
        assertThat(symbols).isNotEmpty();
        assertThat(symbols).contains("2330.TW", "2454.TW", "2881.TW");
    }

    @Test
    void testGetStockName_MediaTek() {
        assertThat(service.getStockName("2454.TW")).isEqualTo("MediaTek");
    }

    @Test
    void testGetStockName_HonHai() {
        assertThat(service.getStockName("2317.TW")).isEqualTo("Hon Hai Precision Industry");
    }

    @Test
    void testGetAllSymbols_ContainsExpectedCount() {
        Set<String> symbols = service.getAllSymbols();
        assertThat(symbols.size()).isGreaterThanOrEqualTo(50);
    }
}
