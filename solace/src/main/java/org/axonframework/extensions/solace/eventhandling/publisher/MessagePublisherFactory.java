package org.axonframework.extensions.solace.eventhandling.publisher;

import com.solace.messaging.publisher.MessagePublisher;
import org.axonframework.extensions.solace.eventhandling.service.MessagingServiceFactory;

/**
 * A functional interface towards building {@link MessagePublisher} instances.
 *
 * @author Alexander Martens
 * @since 1.0
 */
public interface MessagePublisherFactory {

	/**
	 * Create a {@link MessagePublisher}.
	 *
	 * @return a {@link MessagePublisher}
	 */
	MessagePublisher createMessagePublisher();

	/**
	 * The {@link ConfirmationMode} all created {@link MessagePublisher} instances should comply to. Defaults to {@link
	 * ConfirmationMode#NONE}.
	 *
	 * @return the configured confirmation mode
	 */
	default ConfirmationMode confirmationMode() {
		return ConfirmationMode.NONE;
	}

	MessagingServiceFactory messagingServiceFactory();

	long terminateTimeout();

	/**
	 * Closes all {@link MessagePublisher} instances created by this factory.
	 */
	void shutDown();
}