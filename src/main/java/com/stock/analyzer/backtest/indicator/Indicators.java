package com.stock.analyzer.backtest.indicator;

import java.util.Arrays;

/**
 * Technical indicator computations matching the Python implementations exactly.
 * All methods operate on primitive double arrays for performance.
 * NaN is used to indicate insufficient data (matching numpy behavior).
 */
public final class Indicators {

    private Indicators() {}

    // ── True Range ────────────────────────────────────────────────

    public static double[] trueRange(double[] high, double[] low, double[] close) {
        int n = high.length;
        double[] tr = new double[n];
        tr[0] = high[0] - low[0];
        for (int i = 1; i < n; i++) {
            tr[i] = Math.max(high[i] - low[i],
                    Math.max(Math.abs(high[i] - close[i - 1]),
                             Math.abs(low[i] - close[i - 1])));
        }
        return tr;
    }

    // ── RMA (Wilder's Smoothing) ──────────────────────────────────

    public static double[] rma(double[] values, int period) {
        int n = values.length;
        double[] out = new double[n];
        Arrays.fill(out, Double.NaN);
        if (period > n) return out;

        double sum = 0;
        for (int i = 0; i < period; i++) sum += values[i];
        out[period - 1] = sum / period;

        double alpha = 1.0 / period;
        for (int i = period; i < n; i++) {
            out[i] = alpha * values[i] + (1 - alpha) * out[i - 1];
        }
        return out;
    }

    // ── EMA (Exponential Moving Average) ──────────────────────────

    public static double[] ema(double[] values, int period) {
        int n = values.length;
        double[] out = new double[n];
        Arrays.fill(out, Double.NaN);
        if (period <= 0 || period > n) return out;

        double sum = 0;
        for (int i = 0; i < period; i++) sum += values[i];
        out[period - 1] = sum / period;

        double k = 2.0 / (period + 1);
        for (int i = period; i < n; i++) {
            out[i] = k * values[i] + (1 - k) * out[i - 1];
        }
        return out;
    }

    // ── SMA (Simple Moving Average) ───────────────────────────────

    public static double[] sma(double[] values, int period) {
        int n = values.length;
        double[] out = new double[n];
        Arrays.fill(out, Double.NaN);
        if (period > n) return out;

        double[] cs = new double[n];
        cs[0] = values[0];
        for (int i = 1; i < n; i++) cs[i] = cs[i - 1] + values[i];

        out[period - 1] = cs[period - 1] / period;
        for (int i = period; i < n; i++) {
            out[i] = (cs[i] - cs[i - period]) / period;
        }
        return out;
    }

    // ── ATR (Average True Range) ──────────────────────────────────

    public static double[] atr(double[] high, double[] low, double[] close, int period) {
        return rma(trueRange(high, low, close), period);
    }

    // ── RSI (Relative Strength Index — Wilder's smoothing) ────────

    public static double[] rsi(double[] close, int period) {
        int n = close.length;
        double[] rsiArr = new double[n];
        Arrays.fill(rsiArr, Double.NaN);
        if (n < period + 1) return rsiArr;

        double[] deltas = new double[n];
        deltas[0] = 0;
        for (int i = 1; i < n; i++) deltas[i] = close[i] - close[i - 1];

        double[] gain = new double[n];
        double[] loss = new double[n];
        for (int i = 0; i < n; i++) {
            gain[i] = deltas[i] > 0 ? deltas[i] : 0;
            loss[i] = deltas[i] < 0 ? -deltas[i] : 0;
        }

        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            avgGain += gain[i];
            avgLoss += loss[i];
        }
        avgGain /= period;
        avgLoss /= period;

        double rs = avgLoss > 0 ? avgGain / avgLoss : 100.0;
        rsiArr[period] = 100.0 - 100.0 / (1.0 + rs);

        for (int i = period + 1; i < n; i++) {
            avgGain = (avgGain * (period - 1) + gain[i]) / period;
            avgLoss = (avgLoss * (period - 1) + loss[i]) / period;
            rs = avgLoss > 0 ? avgGain / avgLoss : 100.0;
            rsiArr[i] = 100.0 - 100.0 / (1.0 + rs);
        }
        return rsiArr;
    }

    // ── ADX (Average Directional Index) ───────────────────────────

    public static double[] adx(double[] high, double[] low, double[] close, int period) {
        int n = high.length;
        double[] tr = trueRange(high, low, close);

        double[] plusDm = new double[n];
        double[] minusDm = new double[n];
        for (int i = 1; i < n; i++) {
            double up = high[i] - high[i - 1];
            double dn = low[i - 1] - low[i];
            plusDm[i] = (up > dn && up > 0) ? up : 0.0;
            minusDm[i] = (dn > up && dn > 0) ? dn : 0.0;
        }

        double[] smoothedTr = new double[n];
        double[] smoothedPdm = new double[n];
        double[] smoothedMdm = new double[n];
        Arrays.fill(smoothedTr, Double.NaN);
        Arrays.fill(smoothedPdm, Double.NaN);
        Arrays.fill(smoothedMdm, Double.NaN);

        if (period >= n) {
            double[] result = new double[n];
            Arrays.fill(result, Double.NaN);
            return result;
        }

        double sumTr = 0, sumPdm = 0, sumMdm = 0;
        for (int i = 1; i <= period; i++) {
            sumTr += tr[i];
            sumPdm += plusDm[i];
            sumMdm += minusDm[i];
        }
        smoothedTr[period] = sumTr;
        smoothedPdm[period] = sumPdm;
        smoothedMdm[period] = sumMdm;

        for (int i = period + 1; i < n; i++) {
            smoothedTr[i] = smoothedTr[i - 1] - smoothedTr[i - 1] / period + tr[i];
            smoothedPdm[i] = smoothedPdm[i - 1] - smoothedPdm[i - 1] / period + plusDm[i];
            smoothedMdm[i] = smoothedMdm[i - 1] - smoothedMdm[i - 1] / period + minusDm[i];
        }

        double[] dx = new double[n];
        Arrays.fill(dx, Double.NaN);
        for (int i = period; i < n; i++) {
            if (!Double.isNaN(smoothedTr[i]) && smoothedTr[i] > 0) {
                double dip = 100.0 * smoothedPdm[i] / smoothedTr[i];
                double dim = 100.0 * smoothedMdm[i] / smoothedTr[i];
                if ((dip + dim) > 0) {
                    dx[i] = 100.0 * Math.abs(dip - dim) / (dip + dim);
                }
            }
        }

        double[] adxArr = new double[n];
        Arrays.fill(adxArr, Double.NaN);
        int first = 2 * period - 1;
        if (first >= n) return adxArr;

        int validCount = 0;
        double dxSum = 0;
        for (int j = period; j <= first; j++) {
            if (!Double.isNaN(dx[j])) {
                dxSum += dx[j];
                validCount++;
            }
        }
        if (validCount == period) {
            adxArr[first] = dxSum / period;
            for (int i = first + 1; i < n; i++) {
                if (!Double.isNaN(dx[i]) && !Double.isNaN(adxArr[i - 1])) {
                    adxArr[i] = (adxArr[i - 1] * (period - 1) + dx[i]) / period;
                }
            }
        }
        return adxArr;
    }

    // ── Bollinger Band Width % ────────────────────────────────────

    public static double[] bbWidth(double[] close, int period, double stdMult) {
        int n = close.length;
        double[] width = new double[n];
        Arrays.fill(width, Double.NaN);
        for (int i = period - 1; i < n; i++) {
            double sum = 0, sumSq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += close[j];
                sumSq += close[j] * close[j];
            }
            double mean = sum / period;
            double variance = sumSq / period - mean * mean;
            // Use sample std (ddof=1) to match Python np.std(ddof=1)
            double std = Math.sqrt(variance * period / (period - 1));
            if (mean > 0) {
                width[i] = (2 * stdMult * std) / mean * 100;
            }
        }
        return width;
    }

    // ── Donchian Channels ─────────────────────────────────────────

    public static double[][] donchian(double[] high, double[] low, int entryLen, int exitLen) {
        int n = high.length;
        double[] entryHigh = new double[n];
        double[] exitLow = new double[n];
        Arrays.fill(entryHigh, Double.NaN);
        Arrays.fill(exitLow, Double.NaN);

        for (int i = entryLen; i < n; i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int j = i - entryLen; j < i; j++) max = Math.max(max, high[j]);
            entryHigh[i] = max;
        }
        for (int i = exitLen; i < n; i++) {
            double min = Double.POSITIVE_INFINITY;
            for (int j = i - exitLen; j < i; j++) min = Math.min(min, low[j]);
            exitLow[i] = min;
        }
        return new double[][] { entryHigh, exitLow };
    }

    // ── Keltner Squeeze ───────────────────────────────────────────

    public static double[][] keltnerSqueeze(double[] high, double[] low, double[] close,
                                             int kcLen, double kcMult, int bbLen, double bbMult) {
        int n = close.length;
        double[] kcMid = ema(close, kcLen);
        double[] atrVals = atr(high, low, close, kcLen);

        double[] kcUpper = new double[n];
        double[] kcLower = new double[n];
        for (int i = 0; i < n; i++) {
            kcUpper[i] = Double.isNaN(kcMid[i]) ? Double.NaN : kcMid[i] + kcMult * atrVals[i];
            kcLower[i] = Double.isNaN(kcMid[i]) ? Double.NaN : kcMid[i] - kcMult * atrVals[i];
        }

        double[] bbMid = sma(close, bbLen);
        double[] bbStd = new double[n];
        Arrays.fill(bbStd, Double.NaN);
        for (int i = bbLen - 1; i < n; i++) {
            double sum = 0;
            for (int j = i - bbLen + 1; j <= i; j++) sum += close[j];
            double mean = sum / bbLen;
            double sumSq = 0;
            for (int j = i - bbLen + 1; j <= i; j++) sumSq += (close[j] - mean) * (close[j] - mean);
            bbStd[i] = Math.sqrt(sumSq / bbLen); // ddof=0
        }

        double[] bbUpper = new double[n];
        double[] bbLower = new double[n];
        for (int i = 0; i < n; i++) {
            bbUpper[i] = Double.isNaN(bbMid[i]) ? Double.NaN : bbMid[i] + bbMult * bbStd[i];
            bbLower[i] = Double.isNaN(bbMid[i]) ? Double.NaN : bbMid[i] - bbMult * bbStd[i];
        }

        boolean[] squeezeOn = new boolean[n];
        int minLen = Math.max(kcLen, bbLen);
        for (int i = minLen - 1; i < n; i++) {
            if (!Double.isNaN(bbLower[i]) && !Double.isNaN(kcLower[i])) {
                squeezeOn[i] = (bbLower[i] > kcLower[i]) && (bbUpper[i] < kcUpper[i]);
            }
        }

        // Momentum: linear regression of (close - midline)
        double[] midline = new double[n];
        for (int i = 0; i < n; i++) {
            double ku = Double.isNaN(kcUpper[i]) ? 0 : kcUpper[i];
            double kl = Double.isNaN(kcLower[i]) ? 0 : kcLower[i];
            midline[i] = (ku + kl) / 2.0;
        }

        double[] val = new double[n];
        for (int i = 0; i < n; i++) val[i] = close[i] - midline[i];

        double[] momentum = new double[n];
        Arrays.fill(momentum, Double.NaN);
        for (int i = bbLen - 1; i < n; i++) {
            boolean hasNan = false;
            for (int j = i - bbLen + 1; j <= i; j++) {
                if (Double.isNaN(val[j])) { hasNan = true; break; }
            }
            if (hasNan) continue;

            double xMean = (bbLen - 1) / 2.0;
            double yMean = 0;
            for (int j = 0; j < bbLen; j++) yMean += val[i - bbLen + 1 + j];
            yMean /= bbLen;

            double ssXx = 0, ssXy = 0;
            for (int j = 0; j < bbLen; j++) {
                double x = j;
                double y = val[i - bbLen + 1 + j];
                ssXx += (x - xMean) * (x - xMean);
                ssXy += (x - xMean) * (y - yMean);
            }
            if (ssXx > 0) {
                double slope = ssXy / ssXx;
                double intercept = yMean - slope * xMean;
                momentum[i] = intercept + slope * (bbLen - 1);
            }
        }

        // Pack: squeezeOn as 1.0/0.0, momentum, kcMid
        double[] sqArr = new double[n];
        for (int i = 0; i < n; i++) sqArr[i] = squeezeOn[i] ? 1.0 : 0.0;

        return new double[][] { sqArr, momentum, kcMid };
    }

    // ── Heikin Ashi ───────────────────────────────────────────────

    public static double[][] heikinAshi(double[] open, double[] high, double[] low, double[] close) {
        int n = close.length;
        double[] haClose = new double[n];
        double[] haOpen = new double[n];
        double[] haHigh = new double[n];
        double[] haLow = new double[n];

        for (int i = 0; i < n; i++) {
            haClose[i] = (open[i] + high[i] + low[i] + close[i]) / 4.0;
        }
        haOpen[0] = (open[0] + close[0]) / 2.0;
        for (int i = 1; i < n; i++) {
            haOpen[i] = (haOpen[i - 1] + haClose[i - 1]) / 2.0;
        }
        for (int i = 0; i < n; i++) {
            haHigh[i] = Math.max(Math.max(high[i], haOpen[i]), haClose[i]);
            haLow[i] = Math.min(Math.min(low[i], haOpen[i]), haClose[i]);
        }
        return new double[][] { haOpen, haHigh, haLow, haClose };
    }

    // ── Chandelier Exit ───────────────────────────────────────────

    public static double[] chandelierExit(double[] high, double[] low, double[] close,
                                           int atrPeriod, double atrMult) {
        int n = close.length;
        double[] atrVals = atr(high, low, close, atrPeriod);
        double[] chandelier = new double[n];
        Arrays.fill(chandelier, Double.NaN);

        for (int i = atrPeriod; i < n; i++) {
            double hh = Double.NEGATIVE_INFINITY;
            for (int j = Math.max(0, i - atrPeriod + 1); j <= i; j++) {
                hh = Math.max(hh, high[j]);
            }
            if (!Double.isNaN(atrVals[i])) {
                chandelier[i] = hh - atrMult * atrVals[i];
            }
        }
        return chandelier;
    }

    // ── MACD Histogram ────────────────────────────────────────────

    public static double[] macdHistogram(double[] close, int fast, int slow, int signal) {
        double[] emaFast = emaAdjustFalse(close, fast);
        double[] emaSlow = emaAdjustFalse(close, slow);
        int n = close.length;
        double[] macdLine = new double[n];
        for (int i = 0; i < n; i++) {
            macdLine[i] = emaFast[i] - emaSlow[i];
        }
        double[] macdSig = emaAdjustFalse(macdLine, signal);
        double[] hist = new double[n];
        for (int i = 0; i < n; i++) {
            hist[i] = macdLine[i] - macdSig[i];
        }
        return hist;
    }

    // ── EMA with adjust=False (pandas behavior) ───────────────────

    public static double[] emaAdjustFalse(double[] values, int span) {
        int n = values.length;
        double[] out = new double[n];
        double k = 2.0 / (span + 1);
        out[0] = values[0];
        for (int i = 1; i < n; i++) {
            out[i] = k * values[i] + (1 - k) * out[i - 1];
        }
        return out;
    }

    // ── Volume SMA ────────────────────────────────────────────────

    public static double[] volumeSma(double[] volume, int period) {
        int n = volume.length;
        double[] out = new double[n];
        Arrays.fill(out, Double.NaN);
        if (period > n) return out;

        double sum = 0;
        for (int i = 0; i < period; i++) sum += volume[i];
        out[period - 1] = sum / period;
        for (int i = period; i < n; i++) {
            out[i] = out[i - 1] + (volume[i] - volume[i - period]) / period;
        }
        return out;
    }

    // ── Supertrend ────────────────────────────────────────────────
    // Returns double[3][n]: [0]=supertrend value, [1]=direction (1=bull, -1=bear), [2]=flip (1/-1/0)
    public static double[][] supertrend(double[] high, double[] low, double[] close, int atrPeriod, double factor) {
        int n = close.length;
        double[] atrArr = atr(high, low, close, atrPeriod);
        double[] st = new double[n];
        double[] dir = new double[n];
        double[] flip = new double[n];
        Arrays.fill(st, Double.NaN);
        Arrays.fill(dir, Double.NaN);
        Arrays.fill(flip, 0);

        double prevUpper = Double.NaN, prevLower = Double.NaN;
        int prevDir = 1;

        for (int i = 0; i < n; i++) {
            if (Double.isNaN(atrArr[i])) continue;

            double hl2 = (high[i] + low[i]) / 2.0;
            double upperBand = hl2 + factor * atrArr[i];
            double lowerBand = hl2 - factor * atrArr[i];

            // Clamp bands (prevent from moving against the trend)
            if (!Double.isNaN(prevUpper)) {
                if (lowerBand > prevLower || close[i > 0 ? i - 1 : 0] > prevLower)
                    lowerBand = Math.max(lowerBand, prevLower);
                if (upperBand < prevUpper || close[i > 0 ? i - 1 : 0] < prevUpper)
                    upperBand = Math.min(upperBand, prevUpper);
            }

            int curDir;
            if (Double.isNaN(prevUpper)) {
                curDir = close[i] > upperBand ? 1 : -1;
            } else if (prevDir == 1) {
                curDir = close[i] < lowerBand ? -1 : 1;
            } else {
                curDir = close[i] > upperBand ? 1 : -1;
            }

            st[i] = curDir == 1 ? lowerBand : upperBand;
            dir[i] = curDir;
            if (!Double.isNaN(prevUpper) && curDir != prevDir) {
                flip[i] = curDir; // 1=bullish flip, -1=bearish flip
            }

            prevUpper = upperBand;
            prevLower = lowerBand;
            prevDir = curDir;
        }
        return new double[][] { st, dir, flip };
    }

    // ── ROC (Rate of Change) ──────────────────────────────────────

    public static double[] roc(double[] close, int lookback) {
        int n = close.length;
        double[] out = new double[n];
        Arrays.fill(out, Double.NaN);
        for (int i = lookback; i < n; i++) {
            if (close[i - lookback] > 0) {
                out[i] = (close[i] / close[i - lookback] - 1.0) * 100.0;
            }
        }
        return out;
    }
}
