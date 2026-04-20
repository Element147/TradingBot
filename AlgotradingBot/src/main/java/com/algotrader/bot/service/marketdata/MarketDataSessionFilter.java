package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.backtest.OHLCVData;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
public class MarketDataSessionFilter {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);

    public List<OHLCVData> regularSessionOnly(List<OHLCVData> bars) {
        return bars.stream()
            .filter(bar -> {
                ZonedDateTime newYorkTimestamp = bar.getTimestamp()
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(NEW_YORK);
                DayOfWeek dayOfWeek = newYorkTimestamp.getDayOfWeek();
                if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                    return false;
                }

                LocalTime localTime = newYorkTimestamp.toLocalTime();
                return !localTime.isBefore(REGULAR_OPEN) && !localTime.isAfter(REGULAR_CLOSE);
            })
            .toList();
    }
}
