package tw.gc.auto.equity.trader.command;

import lombok.RequiredArgsConstructor;
import tw.gc.auto.equity.trader.services.OrderExecutionService;
import tw.gc.auto.equity.trader.entities.Signal;

@RequiredArgsConstructor
public class BuyCommand implements TradeCommand {
    private final OrderExecutionService orderExecutionService;
    private final Signal signal;
    private final StockOrderCommandParams params;

    @Override
    public void execute() {
        orderExecutionService.executeOrderWithRetry(
            "BUY",
            params.getQuantity(),
            params.getPrice(),
            params.getInstrument(),
            false,
            params.isEmergencyShutdown()
        );
    }
}