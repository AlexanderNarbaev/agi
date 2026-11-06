---
name: query-patterns
description: SQL patterns, relational database design, query optimization, transactions, analytics, and dialect-aware best practices.
used-by:
  - "Code"
  - "General"
schemaVersion: "v0.1"
---

# SQL Query Patterns

Dialect-aware SQL patterns for designing schemas, writing correct queries, optimizing performance, and working safely with relational and analytical databases.

## When to Activate

- Designing relational or analytical database schemas
- Writing SQL queries, migrations, reports, dashboards, or data pipelines
- Optimizing query performance with indexes, query plans, partitions, or materialized views
- Debugging slow queries, lock contention, deadlocks, or transaction anomalies
- Reviewing SQL for correctness, security, maintainability, or portability
- Migrating between SQL databases such as PostgreSQL, MySQL, SQLite, SQL Server, Oracle, BigQuery, Snowflake, Redshift, or ClickHouse
- Choosing dialect-specific features while keeping portability trade-offs explicit

## Core Rule: Know the Dialect

SQL is a family of dialects, not one perfectly portable language. Before giving exact syntax, identify the target database and version when possible.

If the dialect is unknown:
- Prefer ANSI SQL constructs.
- Mark dialect-specific syntax explicitly.
- Avoid pretending that one database's function names, date arithmetic, DDL, JSON operators, or upsert syntax work everywhere.
- Ask for the target database when exact syntax matters.

Common dialect differences:

| Concern | Examples of differences |
|---------|-------------------------|
| Pagination | `LIMIT/OFFSET`, `FETCH FIRST`, `TOP` |
| Upsert | PostgreSQL `ON CONFLICT`, MySQL `ON DUPLICATE KEY`, SQL Server/Oracle `MERGE` |
| Date arithmetic | `INTERVAL`, `DATEADD`, `TIMESTAMP_ADD`, `datetime()` |
| String aggregation | `STRING_AGG`, `GROUP_CONCAT`, `LISTAGG` |
| JSON | `jsonb`, `JSON_EXTRACT`, `OPENJSON`, semi-structured columns |
| Identifiers | Quoting with `"name"`, `` `name` ``, `[name]` |
| Booleans | Native `BOOLEAN`, `TINYINT(1)`, `BIT` |
| DDL transactions | Fully transactional, partially transactional, or implicit commits |

## Schema Design Patterns

### Model Entities with Keys and Constraints

```sql
-- PASS: explicit primary key, foreign key, required fields, and useful constraints
CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT customers_status_check CHECK (status IN ('active', 'disabled', 'deleted'))
);

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    total_amount DECIMAL(18, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT orders_customer_fk FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT orders_total_amount_check CHECK (total_amount >= 0)
);
```

Prefer database constraints for invariants that must remain true across every application, script, and data import. Application validation is useful, but it is not a substitute for primary keys, foreign keys, uniqueness, and check constraints.

### Normalize First, Denormalize Deliberately

```sql
-- PASS: normalized transactional model
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    sku VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(300) NOT NULL
);

CREATE TABLE order_items (
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(18, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    CHECK (quantity > 0),
    CHECK (unit_price >= 0)
);
```

Denormalize only when reads require it and the write/update cost is understood. For analytics, dimensional models, wide tables, aggregate tables, or materialized views may be appropriate.

### Pick Data Types Intentionally

- Use exact numeric types (`DECIMAL`, `NUMERIC`) for money and accounting values.
- Use integer or UUID surrogate keys when natural keys are unstable.
- Store timestamps consistently, ideally in UTC.
- Avoid storing structured data as text unless it is genuinely opaque.
- Use JSON columns for flexible attributes only when query/index support and validation needs are understood.
- Avoid magic strings when the database can enforce a domain with constraints, lookup tables, or enum-like mechanisms.

## Query Correctness Patterns

### Select Only Needed Columns

```sql
-- PASS: returns only columns needed by the caller
SELECT
    o.id,
    o.order_number,
    o.total_amount,
    o.currency,
    o.created_at
FROM orders AS o
WHERE o.customer_id = ?
ORDER BY o.created_at DESC;

-- FAIL: unnecessary I/O and fragile caller coupling
SELECT *
FROM orders
WHERE customer_id = ?;
```

### Use Joins Explicitly

```sql
-- PASS: explicit join predicate and aliases
SELECT
    o.order_number,
    c.email,
    o.total_amount
FROM orders AS o
JOIN customers AS c ON c.id = o.customer_id
WHERE o.created_at >= ?;

-- FAIL: implicit join is easier to break into a cartesian product
SELECT o.order_number, c.email, o.total_amount
FROM orders o, customers c
WHERE c.id = o.customer_id;
```

### Be Careful with NULL Semantics

```sql
-- PASS: handles NULL explicitly
SELECT id, email
FROM customers
WHERE deleted_at IS NULL;

-- FAIL: comparisons with NULL are UNKNOWN, not true
SELECT id, email
FROM customers
WHERE deleted_at = NULL;
```

Important NULL rules:
- `NULL = NULL` is not true; use `IS NULL` or dialect-specific null-safe comparison.
- `NOT IN (...)` can produce surprising results if the subquery contains NULL.
- Aggregates usually ignore NULL values except `COUNT(*)`.

### Avoid Accidental Row Multiplication

```sql
-- PASS: aggregate child rows before joining when one row per customer is required
WITH order_totals AS (
    SELECT
        customer_id,
        SUM(total_amount) AS lifetime_value
    FROM orders
    GROUP BY customer_id
)
SELECT
    c.id,
    c.email,
    COALESCE(ot.lifetime_value, 0) AS lifetime_value
FROM customers AS c
LEFT JOIN order_totals AS ot ON ot.customer_id = c.id;
```

When a result must have one row per entity, verify join cardinality. Joining several one-to-many tables directly often multiplies rows and corrupts sums/counts.

## Filtering, Sorting, and Pagination

### Sargable Predicates

```sql
-- PASS: lets an index on created_at be used
SELECT id, created_at
FROM orders
WHERE created_at >= ?
  AND created_at < ?;

-- FAIL: applying a function to the indexed column often prevents index usage
SELECT id, created_at
FROM orders
WHERE DATE(created_at) = ?;
```

Sargable predicates let the database search using indexes instead of scanning and computing expressions for every row.

### Stable Pagination

```sql
-- PASS: deterministic ordering
SELECT id, created_at, total_amount
FROM orders
WHERE customer_id = ?
ORDER BY created_at DESC, id DESC
LIMIT 50 OFFSET 100;
```

Always include a deterministic `ORDER BY` for pagination. Without it, page contents can shift between executions.

For deep pagination, prefer keyset pagination when supported by the product requirements:

```sql
-- PASS: keyset pagination avoids large OFFSET scans
SELECT id, created_at, total_amount
FROM orders
WHERE customer_id = ?
  AND (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT 50;
```

Tuple comparison syntax is dialect-dependent. If unsupported, expand it into equivalent boolean predicates.

## Aggregation and Analytics Patterns

### Aggregate at the Right Grain

```sql
-- PASS: each selected non-aggregate column is part of the grouping key
SELECT
    customer_id,
    COUNT(*) AS order_count,
    SUM(total_amount) AS total_spent
FROM orders
WHERE created_at >= ?
GROUP BY customer_id;
```

Define the grain of the result first: one row per customer, per day, per product, per tenant, etc. Then ensure the `GROUP BY` matches that grain.

### Conditional Aggregation

```sql
SELECT
    customer_id,
    COUNT(*) AS total_orders,
    SUM(CASE WHEN status = 'paid' THEN 1 ELSE 0 END) AS paid_orders,
    SUM(CASE WHEN status = 'cancelled' THEN 1 ELSE 0 END) AS cancelled_orders
FROM orders
GROUP BY customer_id;
```

Some dialects support `FILTER` or functions such as `COUNTIF`; `CASE` is broadly portable.

### Window Functions

```sql
-- Rank orders by value within each customer
SELECT
    customer_id,
    id AS order_id,
    total_amount,
    ROW_NUMBER() OVER (
        PARTITION BY customer_id
        ORDER BY total_amount DESC, id DESC
    ) AS order_rank
FROM orders;
```

Use window functions when each row must remain visible while adding rankings, running totals, lag/lead comparisons, percentiles, or per-partition metrics.

### Time Bucketing

Time bucketing is highly dialect-specific. Keep the intent clear and map it to the database:

```sql
-- PostgreSQL-style example
SELECT
    DATE_TRUNC('day', created_at) AS order_day,
    COUNT(*) AS order_count,
    SUM(total_amount) AS revenue
FROM orders
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', created_at)
ORDER BY order_day;
```

Equivalent functions differ in MySQL, SQL Server, Oracle, BigQuery, Snowflake, SQLite, and ClickHouse.

## Indexing Patterns

### Index for Access Patterns

```sql
-- Query pattern
SELECT id, total_amount, created_at
FROM orders
WHERE customer_id = ?
  AND status = ?
ORDER BY created_at DESC;

-- Typical supporting composite index
CREATE INDEX orders_customer_status_created_idx
ON orders (customer_id, status, created_at);
```

Index columns should usually follow equality filters first, then range/sort columns. Validate with the database's query plan tool.

### Avoid Over-Indexing

Indexes improve reads but cost storage and slow writes. Do not add indexes blindly. Each index should support a known query, constraint, or operational requirement.

### Partial, Filtered, Covering, and Functional Indexes

Use advanced index types when the dialect supports them:

```sql
-- PostgreSQL-style partial index
CREATE INDEX orders_open_created_idx
ON orders (created_at)
WHERE status = 'open';

-- Functional index syntax is dialect-specific
CREATE INDEX customers_lower_email_idx
ON customers (LOWER(email));
```

These can be powerful, but portability is limited.

## Query Plan Review

Use the database's plan/explain tooling before and after optimization:

```sql
EXPLAIN
SELECT id, total_amount
FROM orders
WHERE customer_id = ?
ORDER BY created_at DESC;
```

Look for:
- Full table scans on large tables when selective indexes exist.
- Bad join order or missing join predicates.
- Large sorts, hash builds, spills to disk, or repartitioning.
- Cardinality estimates that are far from actual row counts.
- Filters applied too late in the plan.

Do not optimize from vibes alone. Query plans are the NEI recipe here.

## Transactions and Concurrency

### Keep Transactions Short

```sql
BEGIN;

UPDATE accounts
SET balance = balance - ?
WHERE id = ?;

UPDATE accounts
SET balance = balance + ?
WHERE id = ?;

INSERT INTO account_transfers (source_account_id, target_account_id, amount, created_at)
VALUES (?, ?, ?, CURRENT_TIMESTAMP);

COMMIT;
```

Best practices:
- Keep transactions as short as possible.
- Do not perform network calls or user interaction inside an open transaction.
- Update rows in a consistent order to reduce deadlocks.
- Choose isolation level intentionally.
- Retry only errors that are safe to retry, such as serialization failures or deadlocks, and only around idempotent logic.

### Lock Rows Explicitly When Needed

```sql
-- Syntax and locking behavior differ by database
SELECT id, balance
FROM accounts
WHERE id = ?
FOR UPDATE;
```

Use explicit locks for read-modify-write workflows when optimistic concurrency is insufficient.

### Optimistic Concurrency

```sql
UPDATE documents
SET content = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE id = ?
  AND version = ?;
```

If zero rows are updated, another transaction changed the row first.

## Data Modification Patterns

### Parameterize Inputs

```sql
-- PASS: placeholders, bind variables, or prepared statements
SELECT id, email
FROM customers
WHERE email = ?;

-- FAIL: SQL injection risk
SELECT id, email
FROM customers
WHERE email = '" || userInput || "';
```

Never concatenate untrusted values into SQL. Identifiers such as table or column names cannot usually be bound as parameters; validate them against an allowlist.

### Upsert Carefully

```sql
-- PostgreSQL-style example
INSERT INTO customers (id, email, display_name, created_at)
VALUES (?, ?, ?, CURRENT_TIMESTAMP)
ON CONFLICT (email)
DO UPDATE SET
    display_name = EXCLUDED.display_name;
```

Upsert syntax differs significantly across databases. Also verify whether update triggers, generated columns, timestamps, and conflict targets behave as expected.

### Batch Writes

```sql
-- PASS: batch insert through prepared statements or bulk-load APIs
INSERT INTO order_events (order_id, event_type, created_at)
VALUES (?, ?, ?);
```

Use driver-supported batching, bulk-load APIs, staging tables, or database-native import tools for large writes. Avoid one round-trip per row.

## Migrations

### Safe Migration Flow

1. Add backward-compatible schema changes.
2. Deploy application code that can read/write both old and new shapes if needed.
3. Backfill in bounded batches.
4. Verify counts, constraints, and application behavior.
5. Remove old columns/tables only after all readers are migrated.

```sql
-- Example: additive migration
ALTER TABLE customers
ADD COLUMN normalized_email VARCHAR(320);

-- Backfill in batches using application code or dialect-specific SQL.
-- Then add constraints/indexes after data is valid.
```

Avoid long blocking migrations on large production tables. Many databases have online DDL options, but syntax and guarantees vary.

## Analytical and OLAP Databases

For warehouses and analytical engines such as BigQuery, Snowflake, Redshift, ClickHouse, DuckDB, or columnar extensions:

- Design storage around scan patterns, partition pruning, clustering/sort keys, and compression.
- Prefer large batch ingestion over tiny frequent writes when the engine is optimized for batches.
- Precompute heavy aggregates with materialized views, aggregate tables, or scheduled jobs when freshness requirements allow it.
- Minimize wide `SELECT *` scans on large tables.
- Be explicit about approximate vs exact distinct counts and percentile functions.
- Understand cost model: bytes scanned, slots/warehouses, distribution keys, shards, or replicas depending on the system.

### Materialized Views and Aggregate Tables

```sql
CREATE MATERIALIZED VIEW daily_order_metrics AS
SELECT
    CAST(created_at AS DATE) AS order_date,
    COUNT(*) AS order_count,
    SUM(total_amount) AS revenue
FROM orders
GROUP BY CAST(created_at AS DATE);
```

Materialized view syntax, refresh behavior, indexes, and maintenance costs vary widely. Treat them as physical design, not just query sugar.

## Operational Diagnostics

### Slow Query Investigation

Use dialect-specific system views and logs. Examples include:
- PostgreSQL: `pg_stat_statements`, `EXPLAIN (ANALYZE, BUFFERS)`
- MySQL: slow query log, `performance_schema`, `EXPLAIN ANALYZE`
- SQL Server: Query Store, execution plans, DMVs
- Oracle: AWR/ASH, `DBMS_XPLAN`
- BigQuery: job details, execution graph, bytes processed
- Snowflake: Query Profile, warehouse metrics
- ClickHouse: `system.query_log`, `system.parts`

General checklist:
- Is the query reading far more rows than it returns?
- Are predicates selective and sargable?
- Are statistics fresh?
- Are joins using expected keys and indexes?
- Is the query blocked on locks rather than CPU/I/O?
- Is the workload limited by memory, temp disk, network, or warehouse capacity?

### Table and Index Health

Monitor:
- Table size and row counts.
- Index size and usage.
- Vacuum/analyze/statistics freshness where applicable.
- Fragmentation, bloat, clustering, or partition counts.
- Long-running transactions and lock waits.
- Replication lag and backup/restore health.

## Security and Multi-Tenancy

- Use least-privilege database users.
- Parameterize values and validate dynamic identifiers with allowlists.
- Avoid logging secrets, tokens, credentials, or full sensitive query parameters.
- Use row-level security or tenant predicates consistently for multi-tenant systems.
- Make tenant isolation testable; missing tenant filters are production-grade footguns.
- Encrypt sensitive data according to the database and compliance requirements.

## Portability Checklist

Before claiming SQL is portable, verify:
- Data types and precision.
- Identifier quoting and case sensitivity.
- Date/time functions and timezone behavior.
- `LIMIT`, `OFFSET`, `FETCH`, or `TOP` syntax.
- Upsert/merge syntax.
- JSON functions and operators.
- Window function support.
- Transaction isolation and locking behavior.
- DDL transactional behavior.
- Index features and optimizer hints.

## Review Checklist

When reviewing SQL, check:

1. Correctness
   - Does it return the intended grain and cardinality?
   - Are NULLs handled deliberately?
   - Are joins complete and non-duplicating?
   - Are aggregates grouped at the right level?

2. Performance
   - Are predicates sargable?
   - Are relevant indexes/partitions/clusters available?
   - Is pagination stable and scalable?
   - Has the plan been checked for important queries?

3. Safety
   - Are untrusted values parameterized?
   - Are dynamic identifiers allowlisted?
   - Are transactions short and retry behavior safe?
   - Are migrations backward-compatible and bounded?

4. Maintainability
   - Are aliases clear?
   - Are magic literals named or centralized in application code when appropriate?
   - Is dialect-specific syntax documented?
   - Are comments explaining intent rather than restating syntax?

**Remember**: SQL work starts by identifying the target database and workload. OLTP, OLAP, embedded databases, and cloud warehouses optimize for different things; one engine's perfect recipe can gregg another engine into a full table scan factory.
