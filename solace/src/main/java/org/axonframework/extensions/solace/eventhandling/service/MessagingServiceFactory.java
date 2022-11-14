package org.axonframework.extensions.solace.eventhandling.service;

import com.solace.messaging.MessagingService;

public interface MessagingServiceFactory {

	/**
	 * Connects to a {@link MessagingService}.
	 *
	 * @return a {@link MessagingService}
	 */
	MessagingService createMessagingService();


	/**
	 * Disconnects {@link MessagingService} instance created by this factory.
	 */
	void shutDown();
}
