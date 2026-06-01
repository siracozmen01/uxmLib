package com.uxplima.uxmlib.storage.sql;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmlib.storage.StorageException;
import org.jspecify.annotations.Nullable;

/**
 * A self-typing column codec: a {@code SqlType<T>} couples a JDBC primitive kind, an encoder
 * {@code T -> primitive}, a decoder {@code primitive -> T}, and the read-class it produces, so persisting
 * a {@link UUID}, {@link Instant} or Adventure {@link Component} is one declarative line with no per-call
 * {@link RowMapper}/{@link StatementBinder}. Derive a higher-level type from a primitive one with
 * {@link #andThen}; the conversion is carried as a value, the way SQLib's {@code SQLibType} composes.
 *
 * <p>The composition pattern is adapted from SQLib's {@code SQLibType} (released under CC0 / public domain).
 *
 * @param <T> the Java type this codec reads and writes
 */
public final class SqlType<T> {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JDBCType jdbcType;
    private final int sqlTypeCode;
    private final Class<T> type;
    private final Function<T, Object> encode;
    private final PrimitiveReader reader;
    private final Function<Object, T> decode;

    private SqlType(
            JDBCType jdbcType,
            Class<T> type,
            Function<T, Object> encode,
            PrimitiveReader reader,
            Function<Object, T> decode) {
        this.jdbcType = Objects.requireNonNull(jdbcType, "jdbcType");
        this.type = Objects.requireNonNull(type, "type");
        this.encode = Objects.requireNonNull(encode, "encode");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.decode = Objects.requireNonNull(decode, "decode");
        Integer code = jdbcType.getVendorTypeNumber();
        this.sqlTypeCode = code != null ? code : java.sql.Types.NULL;
    }

    /** The JDBC primitive kind this codec stores through, e.g. {@link JDBCType#VARCHAR} for a UUID column. */
    public JDBCType jdbcType() {
        return jdbcType;
    }

    /** The Java class {@link #read} produces. */
    public Class<T> type() {
        return type;
    }

    /**
     * Derive a new codec over this one: an {@code R} encodes through {@code encodeR} into this type's {@code T}
     * (then on to the primitive), and a read primitive decodes to {@code T} then through {@code decodeR} into
     * {@code R}. The primitive kind is inherited, so a derived codec stores in the same column type.
     *
     * @param readType the read-class of the derived codec, or {@code null} when {@code R} has no stable class
     *     to advertise (a generic such as {@code List<String>}); then {@link #type()} reports {@code Object}.
     */
    @SuppressWarnings("unchecked")
    public <R> SqlType<R> andThen(Function<T, R> decodeR, Function<R, T> encodeR, @Nullable Class<R> readType) {
        Objects.requireNonNull(decodeR, "decodeR");
        Objects.requireNonNull(encodeR, "encodeR");
        Class<R> resolved = readType != null ? readType : (Class<R>) (Class<?>) Object.class;
        Function<R, Object> chainedEncode = value -> encode.apply(encodeR.apply(value));
        Function<Object, R> chainedDecode = primitive -> decodeR.apply(decode.apply(primitive));
        return new SqlType<>(jdbcType, resolved, chainedEncode, reader, chainedDecode);
    }

    /** Encode {@code value} to the value actually handed to JDBC. Useful for asserting the stored form. */
    public Object write(T value) {
        Objects.requireNonNull(value, "value");
        return encode.apply(value);
    }

    /**
     * Bind {@code value} onto {@code statement} at the 1-based {@code index}; a {@code null} value is bound
     * as SQL {@code NULL} of this codec's {@link #jdbcType()}.
     */
    public void bind(PreparedStatement statement, int index, @Nullable T value) {
        Objects.requireNonNull(statement, "statement");
        if (index < 1) {
            throw new IllegalArgumentException("index must be 1-based, got " + index);
        }
        try {
            if (value == null) {
                statement.setNull(index, sqlTypeCode);
            } else {
                statement.setObject(index, encode.apply(value), sqlTypeCode);
            }
        } catch (SQLException failure) {
            throw new StorageException("failed to bind " + type.getSimpleName() + " at index " + index, failure);
        }
    }

    /** Read this codec's value from {@code row} by column {@code label}; SQL {@code NULL} reads as {@code null}. */
    public @Nullable T read(ResultSet row, String label) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(label, "label");
        try {
            Object primitive = reader.read(row, label);
            return primitive == null ? null : decode.apply(primitive);
        } catch (SQLException failure) {
            throw new StorageException("failed to read " + type.getSimpleName() + " from column " + label, failure);
        }
    }

    // --- built-in primitives -------------------------------------------------

    /** {@code TEXT}/{@code VARCHAR} as {@link String}. */
    public static SqlType<String> text() {
        return new SqlType<>(
                JDBCType.VARCHAR, String.class, v -> v, nullable(ResultSet::getString), String.class::cast);
    }

    /** {@code BIGINT} as {@link Long}. */
    public static SqlType<Long> bigint() {
        return new SqlType<>(
                JDBCType.BIGINT, Long.class, v -> v, nonNull(ResultSet::getLong), v -> ((Number) v).longValue());
    }

    /** {@code INTEGER} as {@link Integer}. */
    public static SqlType<Integer> integer() {
        return new SqlType<>(
                JDBCType.INTEGER, Integer.class, v -> v, nonNull(ResultSet::getInt), v -> ((Number) v).intValue());
    }

    /** {@code DOUBLE} as {@link Double}. */
    public static SqlType<Double> real() {
        return new SqlType<>(
                JDBCType.DOUBLE, Double.class, v -> v, nonNull(ResultSet::getDouble), v -> ((Number) v).doubleValue());
    }

    /** {@code BOOLEAN} as {@link Boolean}. */
    public static SqlType<Boolean> bool() {
        return new SqlType<>(
                JDBCType.BOOLEAN, Boolean.class, v -> v, nonNull(ResultSet::getBoolean), Boolean.class::cast);
    }

    // --- built-in derived codecs ---------------------------------------------

    /** A {@link UUID} stored as its canonical {@code TEXT} form. */
    public static SqlType<UUID> uuid() {
        return text().andThen(UUID::fromString, UUID::toString, UUID.class);
    }

    /** An {@link Instant} stored as epoch-millis in a {@code BIGINT}. */
    public static SqlType<Instant> instant() {
        return bigint().andThen(Instant::ofEpochMilli, Instant::toEpochMilli, Instant.class);
    }

    /** An Adventure {@link Component} stored as its MiniMessage {@code TEXT} form. */
    public static SqlType<Component> component() {
        return text().andThen(MINI::deserialize, MINI::serialize, Component.class);
    }

    // --- internals -----------------------------------------------------------

    /** Wrap a primitive getter that returns {@code 0}/{@code false} on SQL {@code NULL}, reporting null instead. */
    private static PrimitiveReader nonNull(PrimitiveGetter getter) {
        return (row, label) -> {
            Object value = getter.get(row, label);
            return row.wasNull() ? null : value;
        };
    }

    /** Wrap a reference getter (e.g. {@code getString}) that already returns {@code null} on SQL {@code NULL}. */
    private static PrimitiveReader nullable(PrimitiveGetter getter) {
        return getter::get;
    }

    @FunctionalInterface
    private interface PrimitiveReader {
        @Nullable Object read(ResultSet row, String label) throws SQLException;
    }

    @FunctionalInterface
    private interface PrimitiveGetter {
        @Nullable Object get(ResultSet row, String label) throws SQLException;
    }
}
