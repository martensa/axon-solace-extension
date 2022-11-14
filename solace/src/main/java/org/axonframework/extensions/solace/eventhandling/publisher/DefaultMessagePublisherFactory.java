package org.axonframework.extensions.solace.eventhandling.publisher;

import com.solace.messaging.MessagingService;
import com.solace.messaging.PubSubPlusClientException;
import com.solace.messaging.publisher.DirectMessagePublisher;
import com.solace.messaging.publisher.MessagePublisher;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.publisher.PersistentMessagePublisher;
import com.solace.messaging.resources.Topic;
import com.solace.messaging.util.CompletionListener;
import com.solace.messaging.util.LifecycleControl;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.extensions.solace.eventhandling.SolaceHeaders;
import org.axonframework.extensions.solace.eventhandling.service.MessagingServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;

public class DefaultMessagePublisherFactory implements MessagePublisherFactory {

	private static final Logger logger = LoggerFactory.getLogger(DefaultMessagePublisherFactory.class);

	private final MessagingServiceFactory messagingServiceFactory;
	private final long terminateTimeout;
	private final BlockingQueue<PoolableMessagePublisher> cache;
	private final Properties configuration;
	private final ConfirmationMode confirmationMode;

	private volatile ShareableMessagePublisher nonTransactionalProducer;

	/**
	 * Instantiate a {@link DefaultMessagePublisherFactory} based on the fields contained in the {@link Builder}.
	 * <p>
	 * Will assert that the {@code configuration} is not {@code null}, and will throw an {@link
	 * AxonConfigurationException} if it is {@code null}.
	 *
	 * @param builder the {@link Builder} used to instantiate a {@link DefaultMessagePublisherFactory} instance
	 */
	@SuppressWarnings("WeakerAccess")
	protected DefaultMessagePublisherFactory(Builder builder) {
		builder.validate();
		this.messagingServiceFactory = builder.messagingServiceFactory;
		this.terminateTimeout = builder.terminateTimeout;
		this.cache = new ArrayBlockingQueue<>(builder.messagePublisherCacheSize);
		this.configuration = builder.configuration;
		this.confirmationMode = builder.confirmationMode;
	}

	/**
	 * Instantiate a Builder to be able to create a {@link DefaultMessagePublisherFactory}.
	 * <p>
	 * The {@code closeTimeout} is defaulted to a {@link Duration#ofSeconds(long)} of {@code 30}, the {@code
	 * producerCacheSize} defaults to {@code 10} and the {@link ConfirmationMode} is defaulted to {@link
	 * ConfirmationMode#NONE}. The {@code configuration} is a <b>hard requirement</b> and as such should be provided.
	 *
	 * @return a Builder to be able to create a {@link DefaultMessagePublisherFactory}
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public MessagePublisher createMessagePublisher() {
		if (this.nonTransactionalProducer == null) {
			synchronized (this) {
				if (this.nonTransactionalProducer == null) {
					logger.debug("Creating a non-transactional MessagePublisher.");
					this.nonTransactionalProducer = new ShareableMessagePublisher(createSolaceMessagePublisher(configuration));
				}
			}
		}

		return this.nonTransactionalProducer;
	}

	@Override
	public ConfirmationMode confirmationMode() {
		return confirmationMode;
	}

	@Override
	public long terminateTimeout() { return terminateTimeout; }

	@Override
	public MessagingServiceFactory messagingServiceFactory() { return this.messagingServiceFactory; }

	@Override
	public void shutDown() {
		logger.debug("Shutting down this MessagePublisher factory.");

		MessagePublisherDecorator messagePublisher = this.nonTransactionalProducer;
		this.nonTransactionalProducer = null;
		if (messagePublisher != null) {
			messagePublisher.delegate.terminate(this.terminateTimeout);
		}
		messagePublisher = this.cache.poll();
		while (messagePublisher != null) {
			try {
				messagePublisher.delegate.terminate(this.terminateTimeout);
			} catch (Exception e) {
				logger.error("Exception closing producer", e);
			}
			messagePublisher = this.cache.poll();
		}

		logger.debug("Shutting down this MessagingService factory.");
		this.messagingServiceFactory.shutDown();
	}

	private MessagePublisher createSolaceMessagePublisher(Properties configuration) {
		if (confirmationMode.isNone()) {
			return this.messagingServiceFactory.createMessagingService().createDirectMessagePublisherBuilder().fromProperties(configuration).build().start();
		} else {
			return this.messagingServiceFactory.createMessagingService().createPersistentMessagePublisherBuilder().fromProperties(configuration).build().start();
		}
	}

	/**
	 * Abstract base class to apply the decorator pattern to a Solace {@link MessagePublisher}. Implements all methods in the
	 * {@link MessagePublisher} interface by calling the wrapped delegate. Subclasses can override any of the methods to add
	 * their specific behaviour.
	 */
	public abstract static class MessagePublisherDecorator implements MessagePublisher {

		private final MessagePublisher delegate;

		MessagePublisherDecorator(MessagePublisher delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean isReady() {
			return this.delegate.isReady();
		}

		@Override
		public void setPublisherReadinessListener(PublisherReadinessListener publisherReadinessListener) {
			this.delegate.setPublisherReadinessListener(publisherReadinessListener);
		}

		@Override
		public void notifyWhenReady() {
			this.delegate.notifyWhenReady();
		}

		@Override
		public <T> CompletableFuture<T> startAsync() throws PubSubPlusClientException, IllegalStateException {
			return this.delegate.startAsync();
		}

		@Override
		public CompletableFuture<Void> terminateAsync(long l) throws PubSubPlusClientException, IllegalStateException {
			return this.delegate.terminateAsync(l);
		}

		@Override
		public <T> void startAsync(CompletionListener<T> completionListener) throws PubSubPlusClientException, IllegalStateException {
			this.delegate.startAsync(completionListener);
		}

		@Override
		public void terminateAsync(CompletionListener<Void> completionListener, long l) throws PubSubPlusClientException, IllegalStateException {
			this.delegate.terminateAsync(completionListener, l);
		}

		@Override
		public PublisherInfo publisherInfo() {
			return this.delegate.publisherInfo();
		}

		@Override
		public boolean isRunning() {
			return this.delegate.isRunning();
		}

		@Override
		public boolean isTerminating() {
			return this.delegate.isTerminating();
		}

		@Override
		public boolean isTerminated() {
			return this.delegate.isTerminated();
		}

		@Override
		public void setTerminationNotificationListener(TerminationNotificationListener terminationNotificationListener) {
			this.delegate.setTerminationNotificationListener(terminationNotificationListener);
		}

		@Override
		public LifecycleControl start() {
			return this.delegate.start();
		}

		@Override
		public void terminate(long timeout) {
			this.delegate.terminate(timeout);
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + " [delegate=" + this.delegate + "]";
		}

		public void publish(OutboundMessage message, Topic destination) {
			String newDestination = message.getProperties().get(SolaceHeaders.SOLACE_DESTINATION_NAME);
			if (newDestination != null) {
				destination = Topic.of(newDestination);
			}
			if (this.delegate instanceof PersistentMessagePublisher) {
				((PersistentMessagePublisher) this.delegate).publish(message, destination);
			} else if (this.delegate instanceof DirectMessagePublisher) {
				((DirectMessagePublisher) this.delegate).publish(message, destination);
			}
		}

		public void publishAwaitAcknowledgement(OutboundMessage message, Topic destination, long timeout) throws InterruptedException {
			if (this.delegate instanceof PersistentMessagePublisher) {
				((PersistentMessagePublisher) this.delegate).publishAwaitAcknowledgement(message, destination, timeout);
			}
		}
	}

	/**
	 * A decorator for a Solace {@link MessagePublisher} that returns itself to an instance pool when {@link #terminate(long)} is called
	 * instead of actually closing the wrapped {@link MessagePublisher}. If the pool is already full (i.e. has the configured
	 * amount of idle producers), the wrapped producer is closed instead.
	 */
	private static final class PoolableMessagePublisher extends MessagePublisherDecorator {

		private final BlockingQueue<PoolableMessagePublisher> pool;

		PoolableMessagePublisher(MessagePublisher delegate,
				BlockingQueue<PoolableMessagePublisher> pool) {
			super(delegate);
			this.pool = pool;
		}

		@Override
		public void terminate(long timeout) {
			boolean isAdded = this.pool.offer(this);
			if (!isAdded) {
				super.terminate(timeout);
			}
		}
	}

	/**
	 * A decorator for a Kafka {@link MessagePublisher} that ignores any calls to {@link #terminate(long)} so it can be reused and
	 * closed by any number of clients.
	 */
	private static final class ShareableMessagePublisher extends MessagePublisherDecorator {

		ShareableMessagePublisher(MessagePublisher delegate) {
			super(delegate);
		}

		@Override
		public void terminate(long timeout) {
			// Do nothing
		}
	}

	/**
	 * Builder class to instantiate a {@link DefaultMessagePublisherFactory}.
	 * <p>
	 * The {@code terminateTimeout} is defaulted to a {@link Duration#ofSeconds(long).toMillis()} of {@code 30}, the {@code
	 * producerCacheSize} defaults to {@code 10} and the {@link ConfirmationMode} is defaulted to {@link
	 * ConfirmationMode#NONE}. The {@code configuration} is a <b>hard requirement</b> and as such should be provided.
	 */
	public static final class Builder {

		private MessagingServiceFactory messagingServiceFactory;
		private long terminateTimeout = Duration.ofSeconds(30).toMillis();
		private int messagePublisherCacheSize = 10;
		private Properties configuration;
		private ConfirmationMode confirmationMode = ConfirmationMode.NONE;

		/**
		 * Sets the {@code messagingServiceFactory} for creating {@link MessagingService} instances.
		 *
		 * @param messagingServiceFactory a Solace {@link MessagingServiceFactory} for creating {@link MessagingService} instances
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder messagingServiceFactory(MessagingServiceFactory messagingServiceFactory) {
			assertNonNull(messagingServiceFactory, "The MessagingService factory may not be null");
			this.messagingServiceFactory = messagingServiceFactory;
			return this;
		}

		/**
		 * Set the {@code terminateTimeout} specifying how long to wait when {@link MessagePublisher#terminate(long)} is invoked.
		 *
		 * @param terminateTimeout the duration to wait before invoking {@link MessagePublisher#terminate(long)}
		 * @return the current Builder instance, for fluent interfacing
		 */
		@SuppressWarnings("WeakerAccess")
		public Builder terminateTimeout(long terminateTimeout) {
			assertThat(terminateTimeout, timeout -> timeout > 0, "The terminateTimeout should be a positive number");
			this.terminateTimeout = terminateTimeout;
			return this;
		}

		/**
		 * Sets the number of {@link MessagePublisher} instances to cache. Defaults to {@code 10}.
		 * <p>
		 * Will instantiate an {@link ArrayBlockingQueue} based on this number.
		 *
		 * @param messagePublisherCacheSize an {@code int} specifying the number of {@link MessagePublisher} instances to cache
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder messagePublisherCacheSize(int messagePublisherCacheSize) {
			assertThat(messagePublisherCacheSize, size -> size > 0, "The messagePublisherCacheSize should be a positive number");
			this.messagePublisherCacheSize = messagePublisherCacheSize;
			return this;
		}

		/**
		 * Sets the {@code configuration} properties for creating {@link MessagePublisher} instances.
		 *
		 * @param configuration a {@link Properties} instance containing Solace properties for creating {@link MessagePublisher} instances
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder configuration(Properties configuration) {
			assertNonNull(configuration, "The configuration may not be null");
			this.configuration = configuration;
			return this;
		}

		/**
		 * Sets the {@link ConfirmationMode} for producing {@link MessagePublisher} instances. Defaults to {@link
		 * ConfirmationMode#NONE}.
		 *
		 * @param confirmationMode the {@link ConfirmationMode} for producing {@link MessagePublisher} instances
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder confirmationMode(ConfirmationMode confirmationMode) {
			assertNonNull(confirmationMode, "ConfirmationMode may not be null");
			this.confirmationMode = confirmationMode;
			return this;
		}

		/**
		 * Initializes a {@link DefaultMessagePublisherFactory} as specified through this Builder.
		 *
		 * @return a {@link DefaultMessagePublisherFactory} as specified through this Builder
		 */
		public DefaultMessagePublisherFactory build() {
			return new DefaultMessagePublisherFactory(this);
		}

		/**
		 * Validates whether the fields contained in this Builder are set accordingly.
		 *
		 * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
		 *                                    specifications
		 */
		@SuppressWarnings({"WeakerAccess", "ProtectedMemberInFinalClass"})
		protected void validate() throws AxonConfigurationException {
			assertNonNull(messagingServiceFactory, "The MessagingService factory is a hard requirement and should be provided");
			assertNonNull(configuration, "The configuration is a hard requirement and should be provided");
		}
	}
}