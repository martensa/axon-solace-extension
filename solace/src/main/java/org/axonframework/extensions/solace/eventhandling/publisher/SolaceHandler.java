package org.axonframework.extensions.solace.eventhandling.publisher;

import com.solace.messaging.publisher.DirectMessagePublisher;
import com.solace.messaging.publisher.MessagePublisher;
import com.solace.messaging.publisher.PersistentMessagePublisher;
import com.solace.messaging.resources.Topic;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.solace.eventhandling.DefaultSolaceMessageConverter;
import org.axonframework.extensions.solace.eventhandling.service.MessagingServiceFactory;
import org.axonframework.extensions.solace.eventhandling.SolaceMessageConverter;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.messaging.EventPublicationFailedException;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * Publisher implementation that uses Solace Event Broker to dispatch event messages. All outgoing messages are sent to
 * a configured topics.
 * <p>
 * This terminal does not dispatch Events internally, as it relies on each event processor to listen to it's own Kafka
 * Topic.
 * </p>
 *
 * @author Alexander Martens
 * @since 1.0
 */
public class SolaceHandler {

	private static final Logger logger = LoggerFactory.getLogger(SolaceHandler.class);

	private static final String DEFAULT_TOPIC = "Axon/Events";

	private final MessagePublisherFactory messagePublisherFactory;
	private final SolaceMessageConverter messageConverter;
	private final MessageMonitor<? super EventMessage<?>> messageMonitor;
	private final String topic;
	private final long deliveryConfirmationTimeOutInMilliseconds;

	/**
	 * Instantiate a {@link SolaceHandler} based on the fields contained in the {@link Builder}.
	 * <p>
	 * Will assert that {@link MessagePublisherFactory} is not {@code null}, and will throw an {@link
	 * AxonConfigurationException} if it is.
	 *
	 * @param builder the {@link Builder} used to instantiate a {@link SolaceHandler} instance
	 */
	protected SolaceHandler(Builder builder) {
		builder.validate();
		this.messagePublisherFactory = builder.messagePublisherFactory;
		this.messageConverter = builder.messageConverter;
		this.messageMonitor = builder.messageMonitor;
		this.topic = builder.topic;
		this.deliveryConfirmationTimeOutInMilliseconds = builder.deliveryConfirmationTimeOutInMilliseconds;
	}

	/**
	 * Instantiate a Builder to be able to create a {@link SolaceHandler}.
	 * <p>
	 * The {@link SolaceMessageConverter} is defaulted to a {@link DefaultSolaceMessageConverter}, the {@link
	 * MessageMonitor} to a {@link NoOpMessageMonitor}, the {@code topic} to {code Axon.Events} and the {@code
	 * publisherAckTimeout} to {@code 1000} milliseconds. The {@link MessagePublisherFactory} is a
	 * <b>hard requirements</b> and as such should be provided.
	 *
	 * @return a Builder to be able to create a {@link SolaceHandler}
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Send {@code events} to the configured Solace {@code topic}. It takes the current Unit of Work into account when
	 * available.
	 * <p>
	 * If {@link MessagePublisherFactory} is configured to use:
	 * <ul>
	 * <li>Transactions: use kafka transactions for publishing events.</li>
	 * <li>Ack: send messages and wait for acknowledgement from Kafka. Acknowledgement timeout can be configured via
	 * {@link SolaceHandler.Builder#deliveryConfirmationTimeOutInMilliseconds(long)}).</li>
	 * <li>None: fire and forget.</li>
	 * </ul>
	 *
	 * @param event the events to publish on the Kafka broker.
	 * @param <T>   the implementation of {@link EventMessage} send through this method
	 */
	public <T extends EventMessage<?>> void send(T event) {
		logger.debug("Starting event producing process for [{}].", event.getPayloadType());
		UnitOfWork<?> uow = CurrentUnitOfWork.get();

		MonitorCallback monitorCallback = messageMonitor.onMessageIngested(event);
		DefaultMessagePublisherFactory.MessagePublisherDecorator messagePublisher = (DefaultMessagePublisherFactory.MessagePublisherDecorator) messagePublisherFactory.createMessagePublisher();
		ConfirmationMode confirmationMode = messagePublisherFactory.confirmationMode();
		MessagingServiceFactory messagingServiceFactory = messagePublisherFactory.messagingServiceFactory();

		if (confirmationMode.isNone()) {
			messagePublisher.publish(messageConverter.createSolaceMessage(event, messagingServiceFactory.createMessagingService()), Topic.of(topic));
		} else if (confirmationMode.isAutoAck()) {
			messagePublisher.publish(messageConverter.createSolaceMessage(event, messagingServiceFactory.createMessagingService()), Topic.of(topic));
		} else if (confirmationMode.isWaitForAck()) {
			try {
				messagePublisher.publishAwaitAcknowledgement(messageConverter.createSolaceMessage(event, messagingServiceFactory.createMessagingService()), Topic.of(topic), deliveryConfirmationTimeOutInMilliseconds);
				monitorCallback.reportSuccess();
			}
			catch (InterruptedException e) {
				monitorCallback.reportFailure(e);
				logger.warn("Encountered error while waiting for event publication.", e);
				throw new EventPublicationFailedException(
						"Event publication failed, exception occurred while waiting for event publication.", e
				);
			}
		}

		uow.onPrepareCommit(u -> {
			tryClose(messagePublisher);
		});

		uow.onRollback(u -> {
			tryClose(messagePublisher);
		});
	}

	private void tryClose(MessagePublisher messagePublisher) {
		try {
			messagePublisher.terminate(messagePublisherFactory.terminateTimeout());
		} catch (Exception e) {
			logger.debug("Unable to close producer.", e);
			// Not re-throwing exception, can't do anything.
		}
	}

	/**
	 * Shuts down this component by calling {@link MessagePublisherFactory#shutDown()} ensuring no new {@link MessagePublisher}
	 * instances can be created. Upon shutdown of an application, this method is invoked in the {@link
	 * Phase#INBOUND_EVENT_CONNECTORS} phase.
	 */
	@ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
	public void shutDown() {
		messagePublisherFactory.shutDown();
	}

	/**
	 * Builder class to instantiate a {@link SolaceHandler}.
	 * <p>
	 * The {@link SolaceMessageConverter} is defaulted to a {@link DefaultSolaceMessageConverter}, the {@link
	 * MessageMonitor} to a {@link NoOpMessageMonitor}, the {@code topic} to {code Axon.Events} and the {@code
	 * publisherAckTimeout} to {@code 1000} milliseconds. The {@link MessagePublisherFactory} is a
	 * <b>hard requirements</b> and as such should be provided.
	 */
	public static class Builder {


		private MessagePublisherFactory messagePublisherFactory;
		@SuppressWarnings("unchecked")
		private SolaceMessageConverter messageConverter =
				(SolaceMessageConverter) DefaultSolaceMessageConverter.builder()
						.serializer(XStreamSerializer.defaultSerializer())
						.build();
		private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.instance();
		private String topic = DEFAULT_TOPIC;
		private long deliveryConfirmationTimeOutInMilliseconds = 10000;
		
		/**
		 * Sets the {@link MessagePublisherFactory} which will instantiate {@link MessagePublisher} instances to publish {@link
		 * EventMessage}s on the Kafka topic.
		 *
		 * @param messagePublisherFactory a {@link MessagePublisherFactory} which will instantiate {@link MessagePublisher} instances to publish {@link EventMessage}s on the Solace topic
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder messagePublisherFactory(MessagePublisherFactory messagePublisherFactory) {
			assertNonNull(messagePublisherFactory, "ProducerFactory may not be null");
			this.messagePublisherFactory = messagePublisherFactory;
			return this;
		}

		/**
		 * Sets the {@link SolaceMessageConverter} used to convert {@link EventMessage}s into Solace messages. Defaults to
		 * a {@link DefaultSolaceMessageConverter} using the {@link XStreamSerializer}.
		 * <p>
		 * Note that configuring a MessageConverter on the builder is mandatory if the value type is not {@code
		 * byte[]}.
		 *
		 * @param messageConverter a {@link SolaceMessageConverter} used to convert {@link EventMessage}s into Kafka
		 *                         messages
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder messageConverter(SolaceMessageConverter messageConverter) {
			assertNonNull(messageConverter, "MessageConverter may not be null");
			this.messageConverter = messageConverter;
			return this;
		}

		/**
		 * Sets the {@link MessageMonitor} of generic type {@link EventMessage} used the to monitor this Kafka
		 * publisher. Defaults to a {@link NoOpMessageMonitor}.
		 *
		 * @param messageMonitor a {@link MessageMonitor} used to monitor this Kafka publisher
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder messageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
			assertNonNull(messageMonitor, "MessageMonitor may not be null");
			this.messageMonitor = messageMonitor;
			return this;
		}

		/**
		 * Set the Kafka {@code topic} to publish {@link EventMessage}s on. Defaults to {@code Axon.Events}.
		 *
		 * @param topic the Kafka {@code topic} to publish {@link EventMessage}s on
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder topic(String topic) {
			assertThat(topic, name -> Objects.nonNull(name) && !"".equals(name), "The topic may not be null or empty");
			this.topic = topic;
			return this;
		}

		/**
		 * Sets the publisher acknowledge timeout in milliseconds specifying how long to wait for a publisher to
		 * acknowledge a message has been sent. Defaults to {@code 1000} milliseconds.
		 *
		 * @param deliveryConfirmationTimeOutInMilliseconds a {@code long} specifying how long to wait for a publisher to acknowledge a
		 *                            message has been sent
		 * @return the current Builder instance, for fluent interfacing
		 */
		@SuppressWarnings("WeakerAccess")
		public Builder deliveryConfirmationTimeOutInMilliseconds(long deliveryConfirmationTimeOutInMilliseconds) {
			assertThat(
					deliveryConfirmationTimeOutInMilliseconds, timeout -> timeout >= 0,
					"The deliveryConfirmationTimeOutInMilliseconds should be a positive number or zero"
			);
			this.deliveryConfirmationTimeOutInMilliseconds = deliveryConfirmationTimeOutInMilliseconds;
			return this;
		}

		/**
		 * Initializes a {@link SolaceHandler} as specified through this Builder.
		 *
		 * @return a {@link SolaceHandler} as specified through this Builder
		 */
		public SolaceHandler build() {
			return new SolaceHandler(this);
		}

		/**
		 * Validates whether the fields contained in this Builder are set accordingly.
		 *
		 * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
		 *                                    specifications
		 */
		@SuppressWarnings("WeakerAccess")
		protected void validate() throws AxonConfigurationException {
			assertNonNull(messagePublisherFactory, "The MessagePublisherFactory is a hard requirement and should be provided");
		}
	}
}