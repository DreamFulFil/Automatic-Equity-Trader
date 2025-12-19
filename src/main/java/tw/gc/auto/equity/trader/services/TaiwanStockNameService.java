package tw.gc.auto.equity.trader.services;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Taiwan Stock Name Service - Maps stock symbols to company names.
 * 
 * This service provides human-readable company names for Taiwan stock symbols.
 * Used for Telegram notifications and UI display.
 */
@Service
public class TaiwanStockNameService {
    
    private static final Map<String, String> STOCK_NAMES = new HashMap<>();
    
    static {
        // Technology & Electronics
        STOCK_NAMES.put("2330.TW", "Taiwan Semiconductor Manufacturing");
        STOCK_NAMES.put("2454.TW", "MediaTek");
        STOCK_NAMES.put("2317.TW", "Hon Hai Precision Industry");
        STOCK_NAMES.put("2382.TW", "Quanta Computer");
        STOCK_NAMES.put("2308.TW", "Delta Electronics");
        STOCK_NAMES.put("2303.TW", "United Microelectronics");
        STOCK_NAMES.put("2357.TW", "Asustek Computer");
        STOCK_NAMES.put("3008.TW", "Largan Precision");
        STOCK_NAMES.put("2344.TW", "Advanced Semiconductor Engineering");
        STOCK_NAMES.put("2345.TW", "Accton Technology");
        STOCK_NAMES.put("2347.TW", "Synnex Technology");
        STOCK_NAMES.put("2353.TW", "Acer");
        STOCK_NAMES.put("3711.TW", "ASE Technology Holding");
        STOCK_NAMES.put("2356.TW", "Inventec");
        STOCK_NAMES.put("2377.TW", "Micro-Star International");
        STOCK_NAMES.put("2379.TW", "Realtek Semiconductor");
        STOCK_NAMES.put("2408.TW", "Nanya Technology");
        STOCK_NAMES.put("3034.TW", "Novatek Microelectronics");
        STOCK_NAMES.put("6505.TW", "Taiwan Mask");
        STOCK_NAMES.put("2301.TW", "Lite-On Technology");
        STOCK_NAMES.put("2498.TW", "HTC Corporation");
        STOCK_NAMES.put("5269.TW", "Asmedia Technology");
        STOCK_NAMES.put("2395.TW", "Advantech");
        STOCK_NAMES.put("3037.TW", "Unimicron Technology");
        STOCK_NAMES.put("3231.TW", "Wiwynn");
        STOCK_NAMES.put("3443.TW", "Global Unichip");
        STOCK_NAMES.put("4938.TW", "Pegatron");
        STOCK_NAMES.put("6669.TW", "Wistron NeWeb");
        STOCK_NAMES.put("2327.TW", "Yageo");
        STOCK_NAMES.put("3105.TW", "Walsin Technology");
        STOCK_NAMES.put("2412.TW", "Chunghwa Telecom");
        STOCK_NAMES.put("6770.TW", "Gintech Energy");
        
        // Financial Services
        STOCK_NAMES.put("2881.TW", "Fubon Financial Holding");
        STOCK_NAMES.put("2882.TW", "Cathay Financial Holding");
        STOCK_NAMES.put("2886.TW", "Mega Financial Holding");
        STOCK_NAMES.put("2891.TW", "CTBC Financial Holding");
        STOCK_NAMES.put("2892.TW", "First Financial Holding");
        STOCK_NAMES.put("2884.TW", "E.Sun Financial Holding");
        STOCK_NAMES.put("2883.TW", "China Development Financial");
        STOCK_NAMES.put("2885.TW", "Yuanta Financial Holding");
        STOCK_NAMES.put("5880.TW", "Taiwan Cooperative Bank");
        
        // Petrochemicals & Materials
        STOCK_NAMES.put("1303.TW", "Nan Ya Plastics");
        STOCK_NAMES.put("1301.TW", "Formosa Plastics");
        STOCK_NAMES.put("2002.TW", "China Steel");
        STOCK_NAMES.put("1216.TW", "Uni-President Enterprises");
        
        // Retail & Consumer
        STOCK_NAMES.put("2603.TW", "Evergreen Marine");
        STOCK_NAMES.put("2609.TW", "Yang Ming Marine Transport");
        STOCK_NAMES.put("2615.TW", "Wan Hai Lines");
        STOCK_NAMES.put("2610.TW", "China Airlines");
        STOCK_NAMES.put("9910.TW", "Feng TAY Enterprise");
    }
    
    /**
     * Get the human-readable company name for a stock symbol.
     * 
     * @param symbol Stock symbol (e.g., "2330.TW")
     * @return Company name, or the symbol itself if not found
     */
    public String getStockName(String symbol) {
        return STOCK_NAMES.getOrDefault(symbol, symbol);
    }
    
    /**
     * Check if a stock symbol exists in the mapping.
     * 
     * @param symbol Stock symbol to check
     * @return true if the symbol has a known name
     */
    public boolean hasStockName(String symbol) {
        return STOCK_NAMES.containsKey(symbol);
    }
    
    /**
     * Get all stock symbols that have name mappings.
     * 
     * @return Set of all known stock symbols
     */
    public java.util.Set<String> getAllSymbols() {
        return STOCK_NAMES.keySet();
    }
}
