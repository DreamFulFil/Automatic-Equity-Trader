package tw.gc.auto.equity.trader.command;

import lombok.RequiredArgsConstructor;
import tw.gc.auto.equity.trader.entities.Signal;

@RequiredArgsConstructor
public class CloseCommand implements TradeCommand {
    private final Signal signal;
    private final StockOrderCommandParams params;

    @Override
    public void execute() {
        // TODO: CLOSE POSITION action not implemented yet
    }
}