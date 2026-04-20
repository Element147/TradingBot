package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class MarketDataCsvSupport {

    private static final String HEADER = "timestamp,symbol,open,high,low,close,volume\n";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public byte[] appendRows(byte[] existingCsv, List<OHLCVData> rows) {
        StringBuilder builder = new StringBuilder();
        if (existingCsv == null || existingCsv.length == 0) {
            builder.append(HEADER);
        } else {
            builder.append(new String(existingCsv, StandardCharsets.UTF_8));
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
        }

        for (OHLCVData row : rows) {
            builder.append(row.getTimestamp().format(TIMESTAMP_FORMATTER)).append(',')
                .append(row.getSymbol()).append(',')
                .append(row.getOpen()).append(',')
                .append(row.getHigh()).append(',')
                .append(row.getLow()).append(',')
                .append(row.getClose()).append(',')
                .append(row.getVolume()).append('\n');
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
