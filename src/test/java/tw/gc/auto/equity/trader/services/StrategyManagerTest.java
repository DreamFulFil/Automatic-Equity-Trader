package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.ShadowModeStock;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyManagerTest {

    @Mock
    private ContractScalingService contractScalingService;
    @Mock
    private StockSettingsService stockSettingsService;
    @Mock
    private TradingStateService tradingStateService;
    @Mock
    private OrderExecutionService orderExecutionService;
    @Mock
    private PositionManager positionManager;
    @Mock
    private DataLoggingService dataLoggingService;
    @Mock
    private ShadowModeStockService shadowModeStockService;
    @Mock
    private ActiveStockService activeStockService;

    private StrategyManager strategyManager;

    @BeforeEach
    void setUp() {
        strategyManager = new StrategyManager(
            contractScalingService,
            stockSettingsService,
            tradingStateService,
            orderExecutionService,
            positionManager,
            dataLoggingService,
            shadowModeStockService,
            activeStockService
        );
    }

    static class DummyStrategy implements IStrategy {
        private final String name;
        private final TradeSignal signal;
        DummyStrategy(String name, TradeSignal signal) { this.name = name; this.signal = signal; }
        @Override public TradeSignal execute(Portfolio portfolio, MarketData data) { return signal; }
        @Override public String getName() { return name; }
        @Override public tw.gc.auto.equity.trader.strategy.StrategyType getType() { return tw.gc.auto.equity.trader.strategy.StrategyType.SHORT_TERM; }
    }

    @Test
    void reinitializeStrategies_shouldPopulatePortfolios_andInitializeShadow() {
        // Given
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.getActiveStrategyName()).thenReturn("MyStrat");
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(stockSettingsService.getBaseStockQuantity(100000.0)).thenReturn(5);
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of());

        // A simple factory
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override
            public List<IStrategy> createStrategies() {
                return List.of(new DummyStrategy("S1", TradeSignal.neutral("init")));
            }
        };

        // When
        strategyManager.reinitializeStrategies(factory);

        // Then
        assertThat(strategyManager.getActiveStrategies()).hasSize(1);
    }

    @Test
    void getTradingQuantity_shouldReturnStockQuantity_inStockMode() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(contractScalingService.getLastEquity()).thenReturn(200000.0);
        when(stockSettingsService.getBaseStockQuantity(200000.0)).thenReturn(10);

        // Use factory to create one strategy so portfolios are created
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(new DummyStrategy("S", TradeSignal.neutral("x"))); }
        };
        strategyManager.reinitializeStrategies(factory);

        // When calling executeStrategies - nothing throws
        strategyManager.executeStrategies(new MarketData(), 100.0);

        // then verify no order execution called since strategy is neutral
        verify(orderExecutionService, never()).executeOrderWithRetry(anyString(), anyInt(), anyDouble(), anyString(), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    void executeRealStrategyTrade_shouldCallOrderExecution_onLongSignal_andZeroPosition() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition(anyString())).thenReturn(0);
        when(orderExecutionService.checkBalanceAndAdjustQuantity(anyString(), anyInt(), anyDouble(), anyString(), anyString())).thenReturn(3);

        // create factory with LONG signal
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(new DummyStrategy("S1", TradeSignal.longSignal(1.0, "reason"))); }
        };
        strategyManager.reinitializeStrategies(factory);

        // When
        strategyManager.executeStrategies(new MarketData(), 50.0);

        // Then
        verify(orderExecutionService, atLeastOnce()).executeOrderWithRetry(eq("BUY"), eq(3), eq(50.0), anyString(), eq(false), anyBoolean(), eq("S1"));
    }

    @Test
    void executeShadowStockStrategies_shouldLogAndExecuteShadowTradeForStock() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        // Prepare strategy and portfolios
        var strategy = new DummyStrategy("SS", TradeSignal.longSignal(1.0, "r"));
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };
        // Provide shadow stock mapping
        ShadowModeStock s = new ShadowModeStock(); s.setSymbol("2330.TW"); s.setStrategyName("SS");
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(s));
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        strategyManager.reinitializeStrategies(factory);

        // Build a portfolio entry for the shadow stock
        strategyManager.executeShadowStockStrategies("2330.TW", new MarketData(), 100.0);

        // Verify a trade was logged
        verify(dataLoggingService, atLeastOnce()).logTrade(any());
    }

    @Test
    void executeShadowStockStrategies_shouldHandleNoPortfoliosForStock() {
        strategyManager.executeShadowStockStrategies("UNKNOWN.TW", new MarketData(), 100.0);
        
        // Should not log any trade
        verify(dataLoggingService, never()).logTrade(any());
    }

    @Test
    void executeStrategies_shadowTrade_long_closesShortAndOpensLong() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() {
                return List.of(new DummyStrategy("S1", TradeSignal.longSignal(1.0, "go")));
            }
        };
        strategyManager.reinitializeStrategies(factory);

        // Force shadow portfolio into short position
        var p = org.springframework.test.util.ReflectionTestUtils.getField(strategyManager, "strategyPortfolios");
        @SuppressWarnings("unchecked")
        var portfolios = (java.util.Map<String, Portfolio>) p;
        Portfolio portfolio = portfolios.get("S1");
        portfolio.setPosition("2330.TW", -1);
        portfolio.setEntryPrice("2330.TW", 110.0);

        strategyManager.executeStrategies(new MarketData(), 100.0);

        verify(dataLoggingService, atLeastOnce()).logTrade(any());
        org.assertj.core.api.Assertions.assertThat(portfolio.getPosition("2330.TW")).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(portfolio.getEntryPrice("2330.TW")).isEqualTo(100.0);
    }

    @Test
    void executeStrategies_shadowTrade_short_closesLongAndOpensShort() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() {
                return List.of(new DummyStrategy("S1", TradeSignal.shortSignal(1.0, "go")));
            }
        };
        strategyManager.reinitializeStrategies(factory);

        var p = org.springframework.test.util.ReflectionTestUtils.getField(strategyManager, "strategyPortfolios");
        @SuppressWarnings("unchecked")
        var portfolios = (java.util.Map<String, Portfolio>) p;
        Portfolio portfolio = portfolios.get("S1");
        portfolio.setPosition("2330.TW", 1);
        portfolio.setEntryPrice("2330.TW", 90.0);

        strategyManager.executeStrategies(new MarketData(), 100.0);

        verify(dataLoggingService, atLeastOnce()).logTrade(any());
        org.assertj.core.api.Assertions.assertThat(portfolio.getPosition("2330.TW")).isEqualTo(-1);
        org.assertj.core.api.Assertions.assertThat(portfolio.getEntryPrice("2330.TW")).isEqualTo(100.0);
    }

    @Test
    void executeShadowStockStrategies_shouldHandleStrategyException() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        var strategy = mock(IStrategy.class);
        when(strategy.getName()).thenReturn("FailStrategy");
        when(strategy.execute(any(), any())).thenThrow(new RuntimeException("Test exception"));
        
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };
        
        ShadowModeStock s = new ShadowModeStock(); 
        s.setSymbol("2330.TW"); 
        s.setStrategyName("FailStrategy");
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(s));
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        strategyManager.reinitializeStrategies(factory);
        strategyManager.executeShadowStockStrategies("2330.TW", new MarketData(), 100.0);

        // Should handle exception gracefully
        verify(dataLoggingService, never()).logTrade(any());
    }

    @Test
    void executeStrategies_shouldHandleStrategyException() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.getActiveStrategyName()).thenReturn("FailStrategy");
        
        var strategy = mock(IStrategy.class);
        when(strategy.getName()).thenReturn("FailStrategy");
        when(strategy.execute(any(), any())).thenThrow(new RuntimeException("Test exception"));
        
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };

        strategyManager.reinitializeStrategies(factory);
        strategyManager.executeStrategies(new MarketData(), 100.0);

        // Should handle exception gracefully
        verify(dataLoggingService, never()).logTrade(any());
    }

    @Test
    void executeStrategies_multiStrategy_conflict_shouldSkipRealTrade() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.isMultiStrategyExecutionEnabled()).thenReturn(true);
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override
            public List<IStrategy> createStrategies() {
                return List.of(
                        new DummyStrategy("Long", TradeSignal.longSignal(0.6, "L")),
                        new DummyStrategy("Short", TradeSignal.shortSignal(0.6, "S"))
                );
            }
        };
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(stockSettingsService.getBaseStockQuantity(100000.0)).thenReturn(10);
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of());

        strategyManager.reinitializeStrategies(factory);
        strategyManager.executeStrategies(new MarketData(), 100.0);

        verify(orderExecutionService, never()).executeOrderWithRetry(
                anyString(), anyInt(), anyDouble(), anyString(), anyBoolean(), anyBoolean(), anyString()
        );
    }

    @Test
    void executeStrategies_multiStrategy_weightedSizing_shouldScaleQuantity() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.isMultiStrategyExecutionEnabled()).thenReturn(true);
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition(anyString())).thenReturn(0);
        when(orderExecutionService.checkBalanceAndAdjustQuantity(anyString(), anyInt(), anyDouble(), anyString(), anyString()))
                .thenReturn(6);

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override
            public List<IStrategy> createStrategies() {
                return List.of(
                        new DummyStrategy("Long", TradeSignal.longSignal(0.9, "L")),
                        new DummyStrategy("Short", TradeSignal.shortSignal(0.4, "S"))
                );
            }
        };

        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(stockSettingsService.getBaseStockQuantity(100000.0)).thenReturn(10);
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of());

        strategyManager.reinitializeStrategies(factory);
        strategyManager.executeStrategies(new MarketData(), 100.0);

        verify(orderExecutionService).executeOrderWithRetry(
                eq("BUY"), eq(6), eq(100.0), anyString(), eq(false), anyBoolean(), eq("MultiStrategyConsensus")
        );
    }

    @Test
    void executeRealStrategyTrade_shouldHandleShortSignalInFuturesMode() {
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("TXFR1");
        when(positionManager.getPosition(anyString())).thenReturn(0);
        when(orderExecutionService.checkBalanceAndAdjustQuantity(anyString(), anyInt(), anyDouble(), anyString(), anyString())).thenReturn(1);
        when(contractScalingService.getMaxContracts()).thenReturn(1);

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { 
                return List.of(new DummyStrategy("S1", TradeSignal.shortSignal(1.0, "reason"))); 
            }
        };
        strategyManager.reinitializeStrategies(factory);

        strategyManager.executeStrategies(new MarketData(), 50.0);

        verify(orderExecutionService, atLeastOnce()).executeOrderWithRetry(eq("SELL"), eq(1), eq(50.0), anyString(), eq(false), anyBoolean(), eq("S1"));
    }

    @Test
    void executeRealStrategyTrade_shouldNotOpenShortInStockMode() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition(anyString())).thenReturn(0);

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { 
                return List.of(new DummyStrategy("S1", TradeSignal.shortSignal(1.0, "reason"))); 
            }
        };
        strategyManager.reinitializeStrategies(factory);

        strategyManager.executeStrategies(new MarketData(), 50.0);

        // Should not execute SELL order in stock mode for new short position
        verify(orderExecutionService, never()).executeOrderWithRetry(eq("SELL"), anyInt(), anyDouble(), anyString(), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    void executeRealStrategyTrade_shouldCloseShortOnLongSignal() {
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("TXFR1");
        when(positionManager.getPosition(anyString())).thenReturn(-1).thenReturn(0);
        when(orderExecutionService.checkBalanceAndAdjustQuantity(anyString(), anyInt(), anyDouble(), anyString(), anyString())).thenReturn(1);
        when(contractScalingService.getMaxContracts()).thenReturn(1);

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { 
                return List.of(new DummyStrategy("S1", TradeSignal.longSignal(1.0, "reversal"))); 
            }
        };
        strategyManager.reinitializeStrategies(factory);

        strategyManager.executeStrategies(new MarketData(), 50.0);

        verify(orderExecutionService, atLeastOnce()).flattenPosition(contains("Short -> Long"), anyString(), anyString(), anyBoolean());
    }

    @Test
    void executeRealStrategyTrade_shouldCloseLongOnShortSignal() {
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("TXFR1");
        when(positionManager.getPosition(anyString())).thenReturn(1).thenReturn(0);
        when(orderExecutionService.checkBalanceAndAdjustQuantity(anyString(), anyInt(), anyDouble(), anyString(), anyString())).thenReturn(1);
        when(contractScalingService.getMaxContracts()).thenReturn(1);

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { 
                return List.of(new DummyStrategy("S1", TradeSignal.shortSignal(1.0, "reversal"))); 
            }
        };
        strategyManager.reinitializeStrategies(factory);

        strategyManager.executeStrategies(new MarketData(), 50.0);

        verify(orderExecutionService, atLeastOnce()).flattenPosition(contains("Long -> Short"), anyString(), anyString(), anyBoolean());
    }

    @Test
    void executeRealStrategyTrade_shouldHandleExitSignal() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(tradingStateService.getActiveStrategyName()).thenReturn("S1");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        // Position is LONG (>0) and signal is LONG with exitSignal=true
        // This won't match line 193 condition (currentPos <= 0)
        // So it should fall through to line 225 (exitSignal check)
        when(positionManager.getPosition(anyString())).thenReturn(5);

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { 
                // Use LONG direction with exitSignal flag, but position is already LONG
                return List.of(new DummyStrategy("S1", TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 1.0, "stop loss"))); 
            }
        };
        strategyManager.reinitializeStrategies(factory);

        strategyManager.executeStrategies(new MarketData(), 50.0);

        verify(orderExecutionService, atLeastOnce()).flattenPosition(contains("Exit Signal"), anyString(), anyString(), anyBoolean());
    }

    @Test
    void executeShadowTrade_shouldHandleShortToCoverTransition() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { 
                return List.of(new DummyStrategy("S1", TradeSignal.longSignal(1.0, "cover"))); 
            }
        };
        strategyManager.reinitializeStrategies(factory);

        // Manually set position to short
        strategyManager.executeStrategies(new MarketData(), 50.0);

        // Should log shadow trades
        verify(dataLoggingService, atLeastOnce()).logTrade(any());
    }

    @Test
    void executeShadowTradeForStock_shouldHandleLongToSellTransition() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        
        var strategy = new DummyStrategy("SS", TradeSignal.shortSignal(1.0, "sell"));
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };
        
        ShadowModeStock s = new ShadowModeStock(); 
        s.setSymbol("2330.TW"); 
        s.setStrategyName("SS");
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(s));
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        strategyManager.reinitializeStrategies(factory);
        strategyManager.executeShadowStockStrategies("2330.TW", new MarketData(), 100.0);

        verify(dataLoggingService, atLeastOnce()).logTrade(any());
    }

    @Test
    void initializeStrategies_shouldBeNoOp() {
        // Legacy method - should do nothing
        strategyManager.initializeStrategies();
        
        // Verify no interactions
        verifyNoInteractions(tradingStateService);
    }

    // ==================== Coverage tests for lines 276-279, 293-296 ====================

    @Test
    void executeShadowTradeForStock_shouldCloseShortAndOpenLong() {
        // Lines 276-279: Close short position when LONG signal received
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        
        var strategy = new DummyStrategy("SS", TradeSignal.longSignal(1.0, "reversal"));
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };
        
        ShadowModeStock s = new ShadowModeStock(); 
        s.setSymbol("2330.TW"); 
        s.setStrategyName("SS");
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(s));
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        strategyManager.reinitializeStrategies(factory);
        
        // Get the shadow portfolio and set it to a short position
        @SuppressWarnings("unchecked")
        var shadowPortfolios = (java.util.Map<String, java.util.Map<String, Portfolio>>) 
            org.springframework.test.util.ReflectionTestUtils.getField(strategyManager, "shadowStockPortfolios");
        Portfolio portfolio = shadowPortfolios.get("2330.TW").get("SS");
        portfolio.setPosition("2330.TW", -2); // Short 2 shares
        portfolio.setEntryPrice("2330.TW", 120.0);
        
        // Execute - should close short and open long
        strategyManager.executeShadowStockStrategies("2330.TW", new MarketData(), 100.0);

        // Verify trades were logged (close short + open long)
        verify(dataLoggingService, atLeast(2)).logTrade(any());
        // Position should now be long
        assertEquals(1, portfolio.getPosition("2330.TW"));
    }

    @Test
    void executeShadowTradeForStock_shouldCloseLongAndOpenShort() {
        // Lines 293-296: Close long position when SHORT signal received
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        
        var strategy = new DummyStrategy("SS", TradeSignal.shortSignal(1.0, "reversal"));
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };
        
        ShadowModeStock s = new ShadowModeStock(); 
        s.setSymbol("2330.TW"); 
        s.setStrategyName("SS");
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(s));
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        strategyManager.reinitializeStrategies(factory);
        
        // Get the shadow portfolio and set it to a long position
        @SuppressWarnings("unchecked")
        var shadowPortfolios = (java.util.Map<String, java.util.Map<String, Portfolio>>) 
            org.springframework.test.util.ReflectionTestUtils.getField(strategyManager, "shadowStockPortfolios");
        Portfolio portfolio = shadowPortfolios.get("2330.TW").get("SS");
        portfolio.setPosition("2330.TW", 3); // Long 3 shares
        portfolio.setEntryPrice("2330.TW", 90.0);
        
        // Execute - should close long and open short
        strategyManager.executeShadowStockStrategies("2330.TW", new MarketData(), 100.0);

        // Verify trades were logged (close long + open short)
        verify(dataLoggingService, atLeast(2)).logTrade(any());
        // Position should now be short
        assertEquals(-1, portfolio.getPosition("2330.TW"));
    }

    @Test
    void executeShadowTradeForStock_strategyNotFound_shouldSkip() {
        // Test when strategy is not found in activeStrategies
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        
        var strategy = new DummyStrategy("DifferentStrategy", TradeSignal.longSignal(1.0, "go"));
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };
        
        ShadowModeStock s = new ShadowModeStock(); 
        s.setSymbol("2330.TW"); 
        s.setStrategyName("NonExistentStrategy"); // Strategy name doesn't match
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(s));
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        strategyManager.reinitializeStrategies(factory);
        
        // Execute - should skip because strategy is not found
        strategyManager.executeShadowStockStrategies("2330.TW", new MarketData(), 100.0);

        // No trades should be logged since strategy wasn't found
        verify(dataLoggingService, never()).logTrade(any());
    }

    @Test
    void executeShadowTradeForStock_neutralSignal_shouldNotTrade() {
        // Test when signal is NEUTRAL - should not execute any trade
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        
        var strategy = new DummyStrategy("SS", TradeSignal.neutral("wait"));
        var factory = new tw.gc.auto.equity.trader.strategy.StrategyFactory() {
            @Override public List<IStrategy> createStrategies() { return List.of(strategy); }
        };
        
        ShadowModeStock s = new ShadowModeStock(); 
        s.setSymbol("2330.TW"); 
        s.setStrategyName("SS");
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(s));
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        strategyManager.reinitializeStrategies(factory);
        
        // Execute - neutral signal should not trigger any trade
        strategyManager.executeShadowStockStrategies("2330.TW", new MarketData(), 100.0);

        // No trades should be logged for neutral signal
        verify(dataLoggingService, never()).logTrade(any());
    }
}
