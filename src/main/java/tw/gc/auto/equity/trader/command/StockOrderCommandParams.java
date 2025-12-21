package tw.gc.auto.equity.trader.command;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StockOrderCommandParams {
    private int quantity;
    private double price;
    private String instrument;
    private boolean emergencyShutdown;
}
