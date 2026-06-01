package com.uxplima.uxmlib.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

/**
 * A pooled database handle over HikariCP. Build one with {@link #builder()}; borrow connections with
 * {@link #connection()} (close them to return them to the pool) and shut the pool down with
 * {@link #close()}. The {@link DataSource} is exposed for callers that need to hand it to other
 * libraries. Created handles are independent; closing one does not affect another.
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;

    Database(HikariDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Start configuring a database; SQLite is the default backend. */
    public static DatabaseBuilder builder() {
        return new DatabaseBuilder();
    }

    /** Borrow a pooled connection. The caller closes it to return it to the pool. */
    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /** The underlying pooled {@link DataSource}. */
    public DataSource dataSource() {
        return dataSource;
    }

    /** Whether the pool has been shut down. */
    public boolean isClosed() {
        return dataSource.isClosed();
    }

    /** Shut the pool down, closing all idle connections. Safe to call more than once. */
    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
