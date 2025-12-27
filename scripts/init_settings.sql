-- Initialize stock and risk settings in database
-- Run this script after the application starts for the first time

-- Insert default stock settings
INSERT OR IGNORE INTO stock_settings (id, initial_shares, share_increment, updated_at, version)
VALUES (1, 70, 27, datetime('now'), 0);

-- Insert default risk settings
INSERT OR IGNORE INTO risk_settings (id, max_position, daily_loss_limit, weekly_loss_limit, max_hold_minutes, updated_at, version)
VALUES (1, 1, 1500, 7000, 45, datetime('now'), 0);