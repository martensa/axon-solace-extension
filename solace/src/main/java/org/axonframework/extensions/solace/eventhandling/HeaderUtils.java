package org.axonframework.extensions.solace.eventhandling;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.serialization.SerializedObject;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.axonframework.common.Assert.isTrue;
import static org.axonframework.common.Assert.notNull;
import static org.axonframework.messaging.Headers.MESSAGE_METADATA;
import static org.axonframework.messaging.Headers.defaultHeaders;

/**
 * Utility class for dealing with user-defined header properties of a Solace Message. Mostly for internal use.
 *
 * @author Alexander Martens
 * @since 1.0
 */
public abstract class HeaderUtils {

	private static final Charset UTF_8 = StandardCharsets.UTF_8;
	private static final String KEY_DELIMITER = "-";

	private HeaderUtils() {
		// Utility class
	}

	/**
	 * Converts bytes to long.
	 *
	 * @param value the bytes to convert in to a long
	 * @return the long build from the given bytes
	 */
	public static Long asLong(byte[] value) {
		return value != null ? ByteBuffer.wrap(value).getLong() : null;
	}

	/**
	 * Return a {@link Long} representation of the {@code value} stored under a given {@code key} inside the {@link
	 * Map<String, Object>}. In case of a missing entry {@code null} is returned.
	 *
	 * @param headerProperties the Solace user-defined {@code headers} to pull the {@link Long} value from
	 * @param key the key corresponding to the expected {@link Long} value
	 * @return the value as a {@link Long} corresponding to the given {@code key} in the {@code headerProperties}
	 */
	public static Long valueAsLong(Map<String, String> headerProperties, String key) {
		return asLong(value(headerProperties, key));
	}

	/**
	 * Converts bytes to {@link String}.
	 *
	 * @param value the bytes to convert in to a {@link String}
	 * @return the {@link String} build from the given bytes
	 */
	public static String asString(byte[] value) {
		return value != null ? new String(value, UTF_8) : null;
	}

	/**
	 * Return a {@link String} representation of the {@code value} stored under a given {@code key} inside the {@link
	 * Map<String, Object>}. In case of a missing entry {@code null} is returned.
	 *
	 * @param headerProperties the Solace user-defined {@code headers} to pull the {@link String} value from
	 * @param key the key corresponding to the expected {@link String} value
	 * @return the value as a {@link String} corresponding to the given {@code key} in the {@code headerProperties}
	 */
	public static String valueAsString(Map<String, String> headerProperties, String key) {
		return asString(value(headerProperties, key));
	}

	/**
	 * Return a {@link String} representation of the {@code value} stored under a given {@code key} inside the {@link
	 * Map<String, Object>}. In case of a missing entry the {@code defaultValue} is returned.
	 *
	 * @param headerProperties the Solace user-defined {@code headers} to pull the {@link String} value from
	 * @param key the key corresponding to the expected {@link String} value
	 * @param defaultValue the default value to return when {@code key} does not exist in the given {@code headerProperties}
	 * @return the value as a {@link String} corresponding to the given {@code key} in the {@code headerProperties}
	 */
	public static String valueAsString(Map<String, String> headerProperties, String key, String defaultValue) {
		return Objects.toString(asString(value(headerProperties, key)), defaultValue);
	}

	/**
	 * Return the {@code value} stored under a given {@code key} inside the {@link Map<String, Object>}. In case of missing entry
	 * {@code null} is returned.
	 *
	 * @param headerProperties the Solace user-defined {@code headerProperties} to pull the value from
	 * @param key the key corresponding to the expected value
	 * @return the value corresponding to the given {@code key} in the {@code headerProperties}
	 */
	@SuppressWarnings("ConstantConditions") // Null check performed by `Assert.isTrue`
	public static byte[] value(Map<String, String> headerProperties, String key) {
		isTrue(headerProperties != null, () -> "Header Properties may not be null");
		Object header = headerProperties.get(key);
		return header != null ? header.toString().getBytes(UTF_8) : null;
	}

	/**
	 * Converts primitive arithmetic types to String.
	 *
	 * @param value the {@link Number} to convert into a String
	 * @return the String converted from the given {@code value}
	 */
	public static String toString(Number value) {
		if (value instanceof Short) {
			return toString((Short) value);
		} else if (value instanceof Integer) {
			return toString((Integer) value);
		} else if (value instanceof Long) {
			return toString((Long) value);
		} else if (value instanceof Float) {
			return toString((Float) value);
		} else if (value instanceof Double) {
			return toString((Double) value);
		}
		throw new IllegalArgumentException("Cannot convert [" + value + "] to String");
	}

	private static String toString(Short value) {
		ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
		buffer.putShort(value);
		return buffer.toString();
	}

	private static String toString(Integer value) {
		ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
		buffer.putInt(value);
		return buffer.toString();
	}

	private static String toString(Long value) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(value);
		return buffer.toString();
	}

	private static String toString(Float value) {
		ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
		buffer.putFloat(value);
		return buffer.toString();
	}

	private static String toString(Double value) {
		ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
		buffer.putDouble(value);
		return buffer.toString();
	}

	/**
	 * Creates a new {@link Map.Entry<String, Object>} based on {@code key} and {@code
	 * value} and adds it to {@code headerProperties}. The {@code value} is converted to bytes and follows this logic:
	 * <ul>
	 * <li>Instant - calls {@link Instant#toEpochMilli()}</li>
	 * <li>Number - calls {@link HeaderUtils#toString} </li>
	 * <li>String/custom object - calls {@link String#toString()} </li>
	 * <li>null - <code>null</code> </li>
	 * </ul>
	 *
	 * @param headerProperties the Solace user-defined {@code headerProperties} to add a {@code key}/{@code value} pair to
	 * @param key the key you want to add to the {@code headerProperties}
	 * @param value the value you want to add to the {@code headerProperties}
	 */
	public static void addHeader(Map<String, String> headerProperties, String key, Object value) {
		notNull(headerProperties, () -> "headers may not be null");
		if (value instanceof Instant) {
			headerProperties.put(key, toString((Number) ((Instant) value).toEpochMilli()));
		} else if (value instanceof Number) {
			headerProperties.put(key, toString((Number) value));
		} else if (value instanceof String) {
			headerProperties.put(key, ((String) value));
		} else if (value == null) {
			headerProperties.put(key, "");
		} else {
			headerProperties.put(key, value.toString());
		}
	}

	/**
	 * Extract the keys as a {@link Set} from the given {@code headers}.
	 *
	 * @param headerProperties the Solace user-defined {@code headerProperties} to extract a key {@link Set<String>} from
	 * @return the keys of the given {@code headerProperties}
	 */
	public static Set<String> keys(Map<String, String> headerProperties) {
		notNull(headerProperties, () -> "Headers may not be null");
		return headerProperties.keySet();
	}

	/**
	 * Generates a meta data {@code key} used to identify Axon {@link org.axonframework.messaging.MetaData} in a {@link
	 * Map.Entry<String, Object>}.
	 *
	 * @param key the key to create an identifiable {@link org.axonframework.messaging.MetaData} key out of
	 * @return the generated metadata key
	 */
	public static String generateMetadataKey(String key) {
		return MESSAGE_METADATA + KEY_DELIMITER + key;
	}

	/**
	 * Extracts the actual key name used to send over Axon {@link org.axonframework.messaging.MetaData} values. E.g.
	 * from {@code 'axon-metadata-foo'} this method will extract {@code foo}.
	 *
	 * @param metaDataKey the generated metadata key to extract from
	 * @return the extracted key
	 */
	@SuppressWarnings("ConstantConditions") // Null check performed by `Assert.isTrue` call
	public static String extractKey(String metaDataKey) {
		isTrue(
				metaDataKey != null && metaDataKey.startsWith(MESSAGE_METADATA + KEY_DELIMITER),
				() -> "Cannot extract Axon MetaData key from given String [" + metaDataKey + "]"
		);

		return metaDataKey.substring((MESSAGE_METADATA + KEY_DELIMITER).length());
	}

	/**
	 * Extract all Axon {@link org.axonframework.messaging.MetaData} (if any) attached in the given {@code headerProperties}. If
	 * a {@link Map.Entry<String, Object>#getKey()} matches with the {@link #isValidMetadataKey(String)} call, the given {@code header} will
	 * be added to the map
	 *
	 * @param headerProperties the Solace user-defined {@link Map<String, Object>} to extract a {@link org.axonframework.messaging.MetaData} map for
	 * @return the map of all Axon related {@link org.axonframework.messaging.MetaData} retrieved from the given {@code
	 * headerProperties}
	 */
	public static Map<String, Object> extractAxonMetadata(Map<String, String> headerProperties) {
		notNull(headerProperties, () -> "Header Properties may not be null");

		return StreamSupport.stream(headerProperties.entrySet().spliterator(), false)
				.filter(header -> isValidMetadataKey(header.getKey()))
				.collect(Collectors.toMap(
						header -> extractKey(header.getKey()),
						header -> header.getValue()
				));
	}

	private static boolean isValidMetadataKey(String key) {
		return key.startsWith(MESSAGE_METADATA + KEY_DELIMITER);
	}

	/**
	 * Generates Solace user-defined {@link Map<String, Object>} based on an {@link EventMessage} and {@link SerializedObject}, using the given
	 * {@code headerValueMapper} to correctly map the values to byte arrays.
	 *
	 * @param eventMessage      the {@link EventMessage} to create headers for
	 * @param serializedObject  the serialized payload of the given {@code eventMessage}
	 * @param headerValueMapper function for converting {@code values} to Strings. Since {@link Map<String, String>} can handle
	 *                          only Strings this function needs to define the logic how to convert a given value to
	 *                          a String. See string mapper for sample implementation
	 * @return the generated Solace user-defined {@link Map<String, String>} properties based on an {@link EventMessage} and {@link SerializedObject}
	 */
	public static Map<String, String> toHeaders(EventMessage<?> eventMessage,
			SerializedObject<byte[]> serializedObject,
			BiFunction<String, Object, Map.Entry<String, String>> headerValueMapper) {
		notNull(eventMessage, () -> "EventMessage may not be null");
		notNull(serializedObject, () -> "SerializedObject may not be null");
		notNull(headerValueMapper, () -> "Header key-value mapper function may not be null");

		Map<String, String> headerProperties = new HashMap<String, String>();
		eventMessage.getMetaData()
				.forEach((k, v) -> headerProperties.entrySet().add(headerValueMapper.apply(generateMetadataKey(k), v)));
		defaultHeaders(eventMessage, serializedObject).forEach((k, v) -> addHeader(headerProperties, k, v));
		return headerProperties;
	}

	/**
	 * A {@link BiFunction} that converts {@code values} to String entries.
	 *
	 * @return an {@link Object} to {@code String} mapping function
	 */
	/**
	public static BiFunction<String, String, Map.Entry<String, String>> stringMapper() {
		return (key, value) -> value instanceof String
				? Map.entry((String) key, (String) value)
				: Map.entry(key, value == null ? null : value.toString());
	}*/
}