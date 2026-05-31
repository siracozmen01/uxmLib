package com.uxplima.uxmlib.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A pooled data source handle. The HikariCP-backed implementation and the cache seam land with the
 * storage module's first feature pass; this contract names the borrow-a-connection and close seams.
 */
public interface Database extends AutoCloseable {

    /** Borrow a pooled connection; the caller closes it to return it to the pool. */
    Connection connection() throws SQLException;

    /** Shut the pool down. Narrowed from {@link AutoCloseable} so it carries no checked exception. */
    @Override
    void close();
}
