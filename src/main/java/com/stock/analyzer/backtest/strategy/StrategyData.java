package com.stock.analyzer.backtest.strategy;

import com.stock.analyzer.backtest.model.Bar;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds OHLCV arrays plus any indicator arrays computed by a strategy.
 * This replaces the pandas DataFrame used in the Python implementation.
 */
public class StrategyData {

    private final int length;
    private final LocalDate[] dates;
    private final double[] open;
    private final double[] high;
    private final double[] low;
    private final double[] close;
    private final double[] volume;
    private final Map<String, double[]> indicators = new HashMap<>();

    public StrategyData(List<Bar> bars) {
        this.length = bars.size();
        this.dates = new LocalDate[length];
        this.open = new double[length];
        this.high = new double[length];
        this.low = new double[length];
        this.close = new double[length];
        this.volume = new double[length];

        for (int i = 0; i < length; i++) {
            Bar b = bars.get(i);
            dates[i] = b.getDate();
            open[i] = b.getOpen();
            high[i] = b.getHigh();
            low[i] = b.getLow();
            close[i] = b.getClose();
            volume[i] = b.getVolume();
        }
    }

    public int length() { return length; }
    public LocalDate[] dates() { return dates; }
    public double[] open() { return open; }
    public double[] high() { return high; }
    public double[] low() { return low; }
    public double[] close() { return close; }
    public double[] volume() { return volume; }

    public void put(String name, double[] values) {
        indicators.put(name, values);
    }

    public double[] get(String name) {
        return indicators.get(name);
    }

    public boolean has(String name) {
        return indicators.containsKey(name);
    }

    public double getVal(String name, int i) {
        double[] arr = indicators.get(name);
        return arr != null ? arr[i] : Double.NaN;
    }
}
