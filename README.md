Net Worth Backend

## Display currency

Every money value the API returns is expressed in the owner's preferred currency
(`users.preferred_currency`, one of INR / USD / EUR / GBP). Each response carries the currency it
used, so the client formats and never converts:

| DTO              | label field         | amounts                                                                  |
|------------------|---------------------|--------------------------------------------------------------------------|
| `SummaryMetrics` | `displayCurrency`   | `currentDayValue`, `previousDayValue`, `invested`, `gain`, `dayChange`    |
| `HoldingSummary` | `displayCurrency`   | the above, plus `units` unless `unitsAreShares`                          |
| `Transaction`    | `displayCurrency`   | `invested`, and `value` unless `valueInUnits`                            |
| `ChartData`      | `displayCurrency`   | `value`, `invested`                                                      |
| `Income`         | `convertedCurrency` | `convertedNetAmount`, `convertedTaxPaid`, `convertedGrossAmount`         |

The label is always populated. Normally it is the preferred currency; when no rate can be resolved
it is the amount's own native currency, and the amount is left untouched. Aggregates only ever sum
rows sharing one label — an unconvertible member is excluded from the total and logged, because a
headline net-worth figure must never be the sum of two currencies.

**Conversion date.** Each amount is translated at the rate of the date it is stamped with: a chart
point at its own date, a transaction at its transaction date, today's balance and the carried-forward
cost basis at today's rate. `gain` and `dayChange` are derived server-side from already-converted
amounts, so a client can't accidentally mix two conventions.

Transactions need one extra step. The stored columns are *cumulative* per holding, so differencing
two of them — each translated at its own date — would leave the FX drift between those dates inside
the difference. Instead each row's **increment** is converted at that row's own date and shipped as
`investedDelta` / `valueDelta`, with `invested` / `value` as the running sum of those increments.
The newest row's cumulative amount therefore equals the sum of the deltas above it, and a client
never subtracts. Because that cumulative field is a running sum, a holding converts as a whole or
not at all: if any of its rows predates the stored FX history, the whole holding stays in the
account's own currency. Converting only the datable rows would restart the running sum after the
others and understate the cost basis on every row that followed.

That transaction total is a *different quantity* from a summary's `invested`, which restates the
same cost basis at today's rate. Both are correct; they answer different questions — "what I paid,
when I paid it" versus "what that basis is worth today" — and each is self-consistent within its
own response.

`xirr` is a money-weighted annualised return as a fraction (`0.12` == 12%), computed from each
holding's invested increments at their historical rates and closed with today's value. Historical
rates are the only correct input to a time-weighted figure, so the result is the return actually
earned measured in the display currency.

### Where rates come from

Rates live in `fx_rates_daily`, written once a day by `DailyDataRunner`. **Read requests never call
an FX provider.** `FxService` reads the table and memoises each pair per JVM, so a warm container
usually answers without touching the database either.

`FxRateBackfillService` derives all twelve ordered pairs from three USD legs
(`rate(A→B) = usdTo(B) / usdTo(A)`), so a full refresh is three outbound calls, not twelve. If the
table has no rows for a pair — a fresh deployment before the first daily run — that pair is
bootstrapped from the provider once per JVM and persisted, so freshness degrades before correctness
does.

## Database migrations

Schema changes go in `scripts/migrations/` as a new `.sql` file. On every push to `main`, the
`migrate` job in `deploy-lambda.yml` runs `scripts/migrate.sh` before the Lambdas deploy: it applies
each file not yet recorded in the `schema_migrations` table, in the order git added them, and a
failure stops the deploy. Never edit a file once it has run; the job fails on a changed checksum.
Files listed in `scripts/migrations/baseline.txt` predate this and are recorded without running.
The job reads `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` and `DB_NAME` from repository secrets.

## Deploying this change

1. Apply `scripts/migrations/add-fx-rates-daily.sql` (idempotent; also present in `src/main/resources/schema.sql`).
2. Deploy, then fill the table by running `DailyDataRunner.main` directly. Prefer that over
   `POST /refresh` for the first run: the job is synchronous inside the request, and a full-history
   pass will not fit API Gateway's 29-second integration timeout, so the caller gets a 504 with no
   way to tell whether the job completed. (The job itself keeps running, and only one runs at a
   time — a concurrent `POST /refresh` gets a 409.)

Until the table is filled, a read request will do one rate-limited emergency fetch so values are
still converted; it is correct, just slower. Amounts older than the stored history are labelled
with their native currency rather than converted at the wrong rate — see `FxRates.convertAsOf`.

### Environment

| Variable             | Default              | Purpose                                    |
|----------------------|----------------------|--------------------------------------------|
| `DB_URL`             | *required*           | JDBC URL                                   |
| `DB_USERNAME`        | empty                |                                            |
| `DB_PASSWORD`        | empty                |                                            |
| `DB_POOL_MAX_SIZE`   | 8                    | Hikari `maximumPoolSize`                   |
| `DB_POOL_MIN_IDLE`   | 1                    | Hikari `minimumIdle`                       |
| `HTTP_SERVER_THREADS`| `max(8, cores × 2)`  | Local HTTP server worker threads           |
| `KITE_API_KEY`       | —                    | Kite Connect                               |
| `KITE_API_SECRET`    | —                    | Kite Connect                               |

Size `DB_POOL_MAX_SIZE` against the database's `max_connections` and the number of concurrent
Lambda containers: each container holds its own pool.

## Known issue: gold valuation

`GoldPriceClient.GRAMS_PER_TROY_OUNCE` is `28.3495` (one avoirdupois ounce) while `GC=F` is quoted
per troy ounce (`31.1035` g), so INR/gram comes out ~9.7% high. It is currently the only thing
standing in for the "Indian market premium" that `convertToINRPerGram` never actually applies.
Correcting the divisor alone drops every gold holding's stored value ~8.8%; decide the premium and
the divisor together. This sits upstream of all currency conversion.
