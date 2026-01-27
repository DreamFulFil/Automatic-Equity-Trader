package tw.gc.auto.equity.trader.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestServiceWebScrapingTest {

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private HistoryDataService historyDataService;

    @Mock
    private SystemStatusService systemStatusService;

    @Mock
    private DataSource dataSource;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StrategyStockMappingService strategyStockMappingService;

    @Mock
    private FundamentalDataService fundamentalDataService;

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        backtestService = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService,
            fundamentalDataService
        );
    }

    @Test
    void fetchFromTWSE_shouldParseRows_fromMockedJsoupConnection() throws Exception {
        Document twseDoc = Jsoup.parse("""
            <html><body>
              <table><tbody>
                <tr><td>2330</td><td>TSMC</td><td>1,234B</td></tr>
                <tr><td>2317</td><td>Hon Hai</td><td>999B</td></tr>
                <tr><td>2454</td><td>MediaTek</td><td>888B</td></tr>
                <tr><td>1301</td><td>Formosa Plastics</td><td>100B</td></tr>
                <tr><td>2882</td><td>Cathay</td><td>50B</td></tr>
              </tbody></table>
            </body></html>
            """);

        Connection twseConn = mock(Connection.class);
        mockConnectionChain(twseConn, twseDoc);

        try (MockedStatic<Jsoup> jsoup = org.mockito.Mockito.mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenAnswer(inv -> {
                String url = inv.getArgument(0);
                if (url.contains("marketValue")) {
                    return twseConn;
                }
                throw new IllegalArgumentException("Unexpected URL: " + url);
            });

            Set<?> candidates = (Set<?>) invokePrivate("fetchFromTWSE");

            assertThat(candidates).hasSize(5);
            for (Object c : candidates) {
                String symbol = (String) c.getClass().getDeclaredMethod("getSymbol").invoke(c);
                assertThat(symbol).endsWith(".TW");
            }
        }
    }

    @Test
    void fetchFromTWSE_shouldReturnEmpty_whenConnectionFails() throws Exception {
        Connection twseConn = mock(Connection.class);
        when(twseConn.userAgent(anyString())).thenReturn(twseConn);
        when(twseConn.timeout(anyInt())).thenReturn(twseConn);
        when(twseConn.get()).thenThrow(new IOException("boom"));

        try (MockedStatic<Jsoup> jsoup = org.mockito.Mockito.mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(twseConn);

            Set<?> candidates = (Set<?>) invokePrivate("fetchFromTWSE");
            assertThat(candidates).isEmpty();
        }
    }

    @Test
    void fetchFromYahooFinanceTW_shouldFilterNonNumericSymbols_andSuffixTw() throws Exception {
        Document yahooDoc = Jsoup.parse("""
            <html><body>
              <div data-symbol=\"2330\"><span class=\"symbol-name\">TSMC</span><span class=\"volume\">1.2M</span></div>
              <div data-symbol=\"ABCD\"><span class=\"symbol-name\">Bad</span><span class=\"volume\">9M</span></div>
              <div data-symbol=\"\"><span class=\"symbol-name\">Empty</span><span class=\"volume\">9M</span></div>
              <div data-symbol=\"2317\"><span class=\"symbol-name\">Hon Hai</span><span class=\"volume\">800K</span></div>
            </body></html>
            """);

        Connection yahooConn = mock(Connection.class);
        mockConnectionChain(yahooConn, yahooDoc);

        try (MockedStatic<Jsoup> jsoup = org.mockito.Mockito.mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenAnswer(inv -> {
                String url = inv.getArgument(0);
                if (url.contains("most-active")) {
                    return yahooConn;
                }
                throw new IllegalArgumentException("Unexpected URL: " + url);
            });

            @SuppressWarnings("unchecked")
            Set<Object> candidates = (Set<Object>) invokePrivate("fetchFromYahooFinanceTW");

            assertThat(candidates).hasSize(2);
            assertThat(candidates)
                .extracting(c -> {
                    try {
                        return c.getClass().getDeclaredMethod("getSymbol").invoke(c);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .containsExactlyInAnyOrder("2330.TW", "2317.TW");
        }
    }

    @Test
    void fetchTAIEXComponents_shouldHandleMalformedHtml_gracefully() throws Exception {
        Document taiexDoc = Jsoup.parse("""
            <html><body>
              <table>
                <tr><td>NOT_A_CODE</td></tr>
                <tr><td>2330</td></tr>
                <tr><td></td><td>MissingCode</td></tr>
              </table>
            </body></html>
            """);

        Connection taiexConn = mock(Connection.class);
        mockConnectionChain(taiexConn, taiexDoc);

        try (MockedStatic<Jsoup> jsoup = org.mockito.Mockito.mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenAnswer(inv -> {
                String url = inv.getArgument(0);
                if (url.contains("indices/taiex/components")) {
                    return taiexConn;
                }
                throw new IllegalArgumentException("Unexpected URL: " + url);
            });

            Set<?> candidates = (Set<?>) invokePrivate("fetchTAIEXComponents");
            assertThat(candidates).isEmpty();
        }
    }

    private void mockConnectionChain(Connection conn, Document doc) throws IOException {
        when(conn.userAgent(anyString())).thenReturn(conn);
        when(conn.timeout(anyInt())).thenReturn(conn);
        when(conn.get()).thenReturn(doc);
    }

    private Object invokePrivate(String methodName) throws Exception {
        Method m = BacktestService.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return m.invoke(backtestService);
    }
}
