package org.axonframework.extensions.solace.eventhandling.service;

import java.util.Properties;

import com.solace.messaging.MessagingService;
import com.solace.messaging.config.profile.ConfigurationProfile;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.extensions.solace.eventhandling.publisher.DefaultMessagePublisherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.axonframework.common.BuilderUtils.assertNonNull;

public class DefaultMessagingServiceFactory implements MessagingServiceFactory {

	private static final Logger logger = LoggerFactory.getLogger(DefaultMessagePublisherFactory.class);

	private volatile MessagingService service;

	private final Properties configuration;

	/**
	 * Instantiate a {@link DefaultMessagingServiceFactory} based on the fields contained in the {@link Builder}.
	 * <p>
	 * Will assert that the {@code configuration} is not {@code null}, and will throw an {@link
	 * AxonConfigurationException} if it is {@code null}.
	 *
	 * @param builder the {@link Builder} used to instantiate a {@link DefaultMessagingServiceFactory} instance
	 */
	@SuppressWarnings("WeakerAccess")
	protected DefaultMessagingServiceFactory(Builder builder) {
		builder.validate();
		this.configuration = builder.configuration;
	}

	/**
	 * Instantiate a Builder to be able to create a {@link DefaultMessagingServiceFactory}.
	 *
	 * @return a Builder to be able to create a {@link DefaultMessagingServiceFactory}
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public MessagingService createMessagingService() {
		if (this.service == null) {
			synchronized (this) {
				if (this.service == null) {
					logger.debug("Connects to a MessagingService.");
					this.service = MessagingService.builder(ConfigurationProfile.V1).fromProperties(configuration).build().connect();
				}
			}
		}
		return this.service;
	}

	@Override
	public void shutDown() {
		logger.debug("Disconnects this MessagingService.");

		if (this.service != null) {
			service.disconnect();
		}
	}

	/**
	 * Builder class to instantiate a {@link DefaultMessagingServiceFactory}.
	 */
	public static final class Builder {

		private Properties configuration;

		/**
		 * Sets the {@code configuration} properties for creating {@link MessagingService} instances.
		 *
		 * @param configuration a {@link Properties} instance containing Solace properties for
		 *                      creating {@link MessagingService} instances
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder configuration(Properties configuration) {
			assertNonNull(configuration, "The configuration may not be null");
			this.configuration = configuration;
			return this;
		}

		/**
		 * Initializes a {@link DefaultMessagingServiceFactory} as specified through this Builder.
		 *
		 * @return a {@link DefaultMessagingServiceFactory} as specified through this Builder
		 */
		public DefaultMessagingServiceFactory build() {
			return new DefaultMessagingServiceFactory(this);
		}

		/**
		 * Validates whether the fields contained in this Builder are set accordingly.
		 *
		 * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
		 *                                    specifications
		 */
		@SuppressWarnings({"WeakerAccess", "ProtectedMemberInFinalClass"})
		protected void validate() throws AxonConfigurationException {
			assertNonNull(configuration, "The configuration is a hard requirement and should be provided");
		}
	}
}