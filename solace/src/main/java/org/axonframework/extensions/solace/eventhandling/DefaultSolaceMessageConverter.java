package org.axonframework.extensions.solace.eventhandling;

import com.solace.messaging.MessagingService;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.publisher.OutboundMessageBuilder;
import com.solace.messaging.receiver.InboundMessage;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.LazyDeserializingObject;
import org.axonframework.serialization.SerializedMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.UnknownSerializedType;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.extensions.solace.eventhandling.HeaderUtils.*;
import static org.axonframework.messaging.Headers.*;
import static org.axonframework.extensions.solace.eventhandling.SolaceHeaders.*;
import static com.solace.messaging.config.SolaceProperties.MessageProperties.*;

/**
 * Converts and {@link EventMessage} to a {@link OutboundMessage} Solace message and from a {@link InboundMessage} Solace
 * message back to an EventMessage (if possible).
 * <p>
 * During conversion metadata entries with the {@code 'axon-metadata-'} prefix are passed to the user-defined header properties. Other
 * message-specific attributes are added as metadata. The {@link EventMessage#getPayload()} is serialized using the
 * configured {@link Serializer} and passed as the Solace Message's body.
 * <p>
 * This implementation will suffice in most cases.
 *
 * @author Alexander Martens
 * @since 1.0
 */
public class DefaultSolaceMessageConverter implements SolaceMessageConverter{

	private static final Logger logger = LoggerFactory.getLogger(SolaceMessageConverter.class);

	private final Serializer serializer;
	private final SequencingPolicy<? super EventMessage<?>> sequencingPolicy;
	private final EventUpcasterChain upcasterChain;


	/**
	 * Instantiate a {@link DefaultSolaceMessageConverter} based on the fields contained in the {@link Builder}.
	 * <p>
	 * Will assert that the {@link Serializer} is not {@code null} and will throw an {@link AxonConfigurationException}
	 * if it is {@code null}.
	 *
	 * @param builder the {@link Builder} used to instantiate a {@link DefaultSolaceMessageConverter} instance
	 */
	@SuppressWarnings("WeakerAccess")
	protected DefaultSolaceMessageConverter(Builder builder) {
		builder.validate();
		this.serializer = builder.serializer;
		this.sequencingPolicy = builder.sequencingPolicy;
		this.upcasterChain = builder.upcasterChain;
	}

	/**
	 * Instantiate a Builder to be able to create a {@link DefaultSolaceMessageConverter}.
	 * <p>
	 * The {@link SequencingPolicy} is defaulted to an {@link SequentialPerAggregatePolicy}, and the {@code
	 * headerValueMapper} is defaulted to the string mapper function. The {@link Serializer} is a
	 * <b>hard requirement</b> and as such should be provided.
	 *
	 * @return a Builder to be able to create a {@link DefaultSolaceMessageConverter}
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public OutboundMessage createSolaceMessage(EventMessage<?> eventMessage, MessagingService service) {
		byte[] data;
		try {
			SerializedObject<byte[]> serializedObject = eventMessage.serializePayload(serializer, byte[].class);
			data = serializedObject.getData();
		} catch (Exception ex) {
			if (eventMessage.getPayloadType() == UnknownSerializedType.class) {
				UnknownSerializedType payload = (UnknownSerializedType) eventMessage.getPayload();
				data = payload.readData(byte[].class);
			} else {
				throw ex;
			}
		}

		Properties properties = new Properties();

		MetaData metaData = eventMessage.getMetaData();
		if (metaData.get(SOLACE_CORRELATION_ID) != null) {
			properties.put(CORRELATION_ID, metaData.get(SOLACE_CORRELATION_ID));
		}

		if (metaData.get(SOLACE_SENDER_ID) != null) {
			properties.put(SENDER_ID, metaData.get(SOLACE_SENDER_ID));
		}

		if (metaData.get(SOLACE_DESTINATION_NAME) != null) {
			properties.put(SOLACE_DESTINATION_NAME, metaData.get(SOLACE_DESTINATION_NAME));
		}

		if (recordKey(eventMessage) != null) {
			properties.put(SEQUENCE_NUMBER, Long.valueOf(recordKey(eventMessage)));
		}

		properties.put(APPLICATION_MESSAGE_TYPE, eventMessage.getPayloadType().getSimpleName());
		properties.put(APPLICATION_MESSAGE_ID, eventMessage.getIdentifier());

		// create individual message
		final OutboundMessage solaceMessage = service.messageBuilder().withPriority(255).build(data, properties);
		return solaceMessage;
	}

	private String recordKey(EventMessage<?> eventMessage) {
		Object sequenceIdentifier = sequencingPolicy.getSequenceIdentifierFor(eventMessage);
		return sequenceIdentifier != null ? sequenceIdentifier.toString() : null;
	}

	@Override
	public Optional<EventMessage<?>> readSolaceMessage(InboundMessage inboundMessage) {
		try {
			if (isAxonMessage(inboundMessage.getProperties())) {
				byte[] messageBody = inboundMessage.getPayloadAsBytes();
				SerializedMessage<?> message = extractSerializedMessage(inboundMessage.getProperties(), messageBody);
				return buildMessage(inboundMessage.getProperties(), message);
			}
		} catch (Exception e) {
			logger.trace("Error converting InboundMessage [{}] to an EventMessage", inboundMessage, e);
		}

		return Optional.empty();
	}

	private boolean isAxonMessage(Map<String, String> headerProperties) {
		return keys(headerProperties).containsAll(Arrays.asList(MESSAGE_ID, MESSAGE_TYPE));
	}

	private SerializedMessage<?> extractSerializedMessage(Map<String, String> headerProperties, byte[] messageBody) {
		SimpleSerializedObject<byte[]> serializedObject = new SimpleSerializedObject<>(
				messageBody,
				byte[].class,
				valueAsString(headerProperties, MESSAGE_TYPE),
				valueAsString(headerProperties, MESSAGE_REVISION, null)
		);

		return new SerializedMessage<>(
				valueAsString(headerProperties, MESSAGE_ID),
				new LazyDeserializingObject<>(serializedObject, serializer),
				new LazyDeserializingObject<>(MetaData.from(extractAxonMetadata(headerProperties)))
		);
	}

	private Optional<EventMessage<?>> buildMessage(Map<String, String> headerProperties, SerializedMessage<?> message) {
		long timestamp = valueAsLong(headerProperties, MESSAGE_TIMESTAMP);
		return headerProperties.get(AGGREGATE_ID) != null
				? buildDomainEvent(headerProperties, message, timestamp)
				: buildEvent(message, timestamp);
	}

	private Optional<EventMessage<?>> buildDomainEvent(Map<String, String> headerProperties, SerializedMessage<?> message, long timestamp) {
		return Optional.of(new GenericDomainEventMessage<>(
				valueAsString(headerProperties, AGGREGATE_TYPE),
				valueAsString(headerProperties, AGGREGATE_ID),
				valueAsLong(headerProperties, AGGREGATE_SEQ),
				message,
				() -> Instant.ofEpochMilli(timestamp)
		));
	}

	private Optional<EventMessage<?>> buildEvent(SerializedMessage<?> message, long timestamp) {
		return Optional.of(new GenericEventMessage<>(message, () -> Instant.ofEpochMilli(timestamp)));
	}

	/**
	 * Builder class to instantiate a {@link DefaultSolaceMessageConverter}.
	 * <p>
	 * The {@link SequencingPolicy} is defaulted to an {@link SequentialPerAggregatePolicy}.
	 * The {@link Serializer} is a <b>hard requirement</b> and as such should be provided.
	 */
	public static class Builder {

		private Serializer serializer;
		private SequencingPolicy<? super EventMessage<?>> sequencingPolicy = SequentialPerAggregatePolicy.instance();
		private EventUpcasterChain upcasterChain = new EventUpcasterChain();

		/**
		 * Sets the serializer to serialize the Event Message's payload with.
		 *
		 * @param serializer The serializer to serialize the Event Message's payload with
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder serializer(Serializer serializer) {
			assertNonNull(serializer, "Serializer may not be null");
			this.serializer = serializer;
			return this;
		}

		/**
		 * Sets the {@link SequencingPolicy}, with a generic of being a super of {@link EventMessage}, used to generate
		 * the key for the {@link OutboundMessage}. Defaults to a {@link SequentialPerAggregatePolicy} instance.
		 *
		 * @param sequencingPolicy a {@link SequencingPolicy} used to generate the key for the {@link OutboundMessage}
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder sequencingPolicy(SequencingPolicy<? super EventMessage<?>> sequencingPolicy) {
			assertNonNull(sequencingPolicy, "SequencingPolicy may not be null");
			this.sequencingPolicy = sequencingPolicy;
			return this;
		}

		/**
		 * Sets the {@code upcasterChain} to be used during the consumption of events.
		 *
		 * @param upcasterChain upcaster chain to be used on event reading.
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder upcasterChain(EventUpcasterChain upcasterChain) {
			assertNonNull(upcasterChain, "UpcasterChain must not be null");
			this.upcasterChain = upcasterChain;
			return this;
		}

		/**
		 * Initializes a {@link DefaultSolaceMessageConverter} as specified through this Builder.
		 *
		 * @return a {@link DefaultSolaceMessageConverter} as specified through this Builder
		 */
		public DefaultSolaceMessageConverter build() {
			return new DefaultSolaceMessageConverter(this);
		}

		/**
		 * Validates whether the fields contained in this Builder are set accordingly.
		 *
		 * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
		 *                                    specifications
		 */
		@SuppressWarnings("WeakerAccess")
		protected void validate() throws AxonConfigurationException {
			assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
		}
	}
}