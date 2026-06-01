package com.uxplima.uxmlib.storage;

import java.nio.file.Path;
import java.util.Objects;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for a {@link Database}. SQLite is the zero-config default: point it at a file with
 * {@link #sqlite(Path)} (or use {@link #sqliteInMemory()} for tests) and it applies the single-writer
 * WAL settings a file database needs. For a network backend, give a full {@link #jdbcUrl(String)} plus
 * credentials and a pool size. {@link #build()} validates the configuration and opens the pool.
 */
public final class DatabaseBuilder {

    // SQLite is single-writer; a file database must serialise writes through one pooled connection.
    private static final int SQLITE_POOL_SIZE = 1;

    private @Nullable String jdbcUrl;
    private @Nullable String username;
    private @Nullable String password;
    private int maxPoolSize = 10;
    private long connectionTimeoutMs = 5_000L;
    private String poolName = "uxmlib-pool";
    private boolean sqlite;

    DatabaseBuilder() {}

    /** Use a SQLite database stored at {@code file}, creating it on first use. */
    public DatabaseBuilder sqlite(Path file) {
        Objects.requireNonNull(file, "file");
        this.jdbcUrl = "jdbc:sqlite:" + file;
        this.sqlite = true;
        return this;
    }

    /** Use an in-memory SQLite database (for tests). */
    public DatabaseBuilder sqliteInMemory() {
        this.jdbcUrl = "jdbc:sqlite::memory:";
        this.sqlite = true;
        return this;
    }

    /** Use an explicit JDBC URL (e.g. {@code jdbc:mariadb://host:3306/db}) for a network backend. */
    public DatabaseBuilder jdbcUrl(String url) {
        this.jdbcUrl = Objects.requireNonNull(url, "url");
        this.sqlite = false;
        return this;
    }

    /** The database username (network backends). */
    public DatabaseBuilder username(String username) {
        this.username = Objects.requireNonNull(username, "username");
        return this;
    }

    /** The database password (network backends). */
    public DatabaseBuilder password(String password) {
        this.password = Objects.requireNonNull(password, "password");
        return this;
    }

    /** The maximum pool size for a network backend (ignored for SQLite, which is fixed at one). */
    public DatabaseBuilder maxPoolSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("maxPoolSize must be >= 1");
        }
        this.maxPoolSize = size;
        return this;
    }

    /** How long to wait for a connection before failing, in milliseconds. */
    public DatabaseBuilder connectionTimeoutMs(long millis) {
        if (millis < 250L) {
            throw new IllegalArgumentException("connectionTimeoutMs must be >= 250");
        }
        this.connectionTimeoutMs = millis;
        return this;
    }

    /** The Hikari pool name (shown in thread names and metrics). */
    public DatabaseBuilder poolName(String name) {
        this.poolName = Objects.requireNonNull(name, "name");
        return this;
    }

    /** Validate the configuration and open the connection pool. */
    public Database build() {
        String url = jdbcUrl;
        if (url == null) {
            throw new IllegalStateException(
                    "a backend must be set: call sqlite(...), sqliteInMemory(), or jdbcUrl(...)");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setPoolName(poolName);
        config.setConnectionTimeout(connectionTimeoutMs);
        if (username != null) {
            config.setUsername(username);
        }
        if (password != null) {
            config.setPassword(password);
        }
        if (sqlite) {
            config.setMaximumPoolSize(SQLITE_POOL_SIZE);
            // WAL + NORMAL sync is the standard durable-but-fast setup for an embedded single-writer file.
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
        } else {
            config.setMaximumPoolSize(maxPoolSize);
        }
        HikariDataSource dataSource = new HikariDataSource(config);
        try {
            return new Database(dataSource);
        } catch (RuntimeException failure) {
            // Don't leak the just-opened pool if wrapping it fails for any reason.
            dataSource.close();
            throw failure;
        }
    }
}
