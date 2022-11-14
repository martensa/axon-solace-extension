package org.axonframework.extensions.solace;

import com.solace.messaging.config.SolaceConstants;
import com.solace.messaging.util.SecureStoreFormat;
import org.axonframework.extensions.solace.eventhandling.publisher.ConfirmationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Configuration properties for Axon for Solace.
 * <p>
 * Users should refer to Solace documentation for complete descriptions of these properties.
 *
 * @author Alexander Martens
 * @since 1.0
 */
@ConfigurationProperties(prefix = "axon.solace")
public class SolaceProperties {

	/**
	 * The default topic constructed in absence of any configuration.
	 */
	public static final String DEFAULT_TOPIC = "Axon/Events";

	/**
	 * Default topic to which messages will be sent. Defaults to a topic called {@code "Axon.Events"}.
	 */
	private String defaultTopic = DEFAULT_TOPIC;

	private final Service service = new Service();

	private final Handler handler = new Handler();

	private final Publisher publisher = new Publisher();

	public String getDefaultTopic() {
		return defaultTopic;
	}

	public void setDefaultTopic(String defaultTopic) {
		this.defaultTopic = defaultTopic;
	}

	public Service getService() {
		return service;
	}

	public Handler getHandler() {
		return handler;
	}

	public Publisher getPublisher() {
		return publisher;
	}

	/**
	 * Configures the service used to connect to a Solace event broker.
	 */
	public static class Service {
		/**
		 * Message VPN to pass to the server when establishing a connection.
		 */
		private String vpnName;

		private final Transport transport = new Transport();

		private final Authentication authentication = new Authentication();

		private final Client client = new Client();

		private final Tls tls = new Tls();

		public String getVpnName() { return this.vpnName; }

		public void setVpnName(String vpnName) { this.vpnName = vpnName; }

		public Transport getTransport() {
			return this.transport;
		}

		public Authentication getAuthentication() {
			return this.authentication;
		}

		public Client getClient() {
			return this.client;
		}

		public Tls getTls() {
			return this.tls;
		}

		public Properties buildProperties() {
			Properties properties = new Properties();
			if (this.vpnName != null) {
				properties.put(com.solace.messaging.config.SolaceProperties.ServiceProperties.VPN_NAME, this.vpnName);
			}
			properties.putAll(this.getTransport().buildProperties());
			properties.putAll(this.getAuthentication().buildProperties());
			properties.putAll(this.getClient().buildProperties());
			properties.putAll(this.getTls().buildProperties());
			return properties;
		}

		private static String resourceToPath(Resource resource) {
			try {
				return resource.getFile().getAbsolutePath();
			} catch (IOException ex) {
				throw new IllegalStateException("Resource '" + resource + "' must be on a file system", ex);
			}
		}

		public static class Transport {
			/**
			 * Comma-delimited list of host:port pairs to use for establishing the initial connection to the Solace HA Group.
			 */
			private List<String> hostList = new ArrayList<>(Collections.singletonList("localhost:55555"));

			private Integer keepAliveInterval = null;

			public List<String> getHostList() { return this.hostList; }

			public void setHostList(List<String> hostList) { this.hostList = hostList; }

			public Integer getKeepAliveInterval() { return this.keepAliveInterval; }

			public void setKeepAliveInterval(Integer keepAliveInterval) { this.keepAliveInterval = keepAliveInterval; }

			public Properties buildProperties() {
				Properties properties = new Properties();
				if (this.hostList != null) {
					String hostList = this.hostList.stream().collect(Collectors.joining(","));
					properties.put(com.solace.messaging.config.SolaceProperties.TransportLayerProperties.HOST, hostList);
				}
				if (this.keepAliveInterval != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.TransportLayerProperties.KEEP_ALIVE_INTERVAL, this.keepAliveInterval);
				}
				return properties;
			}
		}

		public static class Authentication {
			private String scheme = SolaceConstants.AuthenticationConstants.AUTHENTICATION_SCHEME_BASIC;

			private final Basic basic = new Basic();

			private final ClientCert clientCert = new ClientCert();

			public String getScheme() { return this.scheme; }

			public void setScheme(String scheme) { this.scheme = scheme; }

			public Basic getBasic() { return this.basic; }

			public ClientCert getClientCert() { return this.clientCert; }

			public Properties buildProperties() {
				Properties properties = new Properties();
				if (scheme != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME, this.scheme);
					if (scheme.equals(SolaceConstants.AuthenticationConstants.AUTHENTICATION_SCHEME_BASIC)) {
						properties.putAll(this.basic.buildProperties());
					} else if (scheme.equals(SolaceConstants.AuthenticationConstants.AUTHENTICATION_SCHEME_CLIENT_CERT)) {
						properties.putAll(this.clientCert.buildProperties());
					}
				}
				return properties;
			}

			public static class Basic {
				/**
				 * Username for the client.
				 */
				private String username = null;
				/**
				 * Password for the client.
				 */
				private String password = null;

				public String getUsername() { return this.username; }

				public void setUsername(String username) { this.username = username; }

				public String getPassword() { return this.password; }

				public void setPassword(String password) { this.password = password; }

				public Properties buildProperties() {
					Properties properties = new Properties();
					if (this.username != null) {
						properties.put(com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME_BASIC_USER_NAME, this.username);
					}
					if (this.password != null) {
						properties.put(com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME_BASIC_PASSWORD, this.password);
					}

					return properties;
				}
			}

			public static class ClientCert {
				/**
				 * Password for the private key.
				 */
				private String privateKeyPassword = null;

				/**
				 * Location of the key store file.
				 */
				private Resource keyStorePath = null;

				/**
				 * Store password for the key store file.
				 */
				private String keyStorePassword = null;

				/**
				 * Store format for the key store file.
				 */
				private SecureStoreFormat keyStoreFormat = SecureStoreFormat.JKS;

				public String getPrivateKeyPassword() { return this.privateKeyPassword; }

				public void setPrivateKeyPassword(String privateKeyPassword) { this.privateKeyPassword = privateKeyPassword; }

				public Resource getKeyStorePath() { return this.keyStorePath; }

				public void setKeyStorePath(Resource keyStorePath) { this.keyStorePath = keyStorePath; }

				public String getKeyStorePassword() { return this.keyStorePassword; }

				public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }

				public SecureStoreFormat getKeyStoreFormat() { return this.keyStoreFormat; }

				public void setKeyStoreFormat(SecureStoreFormat keyStoreFormat) { this.keyStoreFormat = keyStoreFormat; }

				public Properties buildProperties() {
					Properties properties = new Properties();
					if (this.privateKeyPassword != null) {
						properties.put(com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME_CLIENT_CERT_PRIVATE_KEY_PASSWORD, this.privateKeyPassword);
					}
					if (this.keyStorePath != null) {
						properties.put(com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME_CLIENT_CERT_KEYSTORE, resourceToPath(this.keyStorePath));
					}
					if (this.keyStorePassword != null) {
						properties.put(com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME_CLIENT_CERT_KEYSTORE_PASSWORD, this.keyStorePassword);
					}
					if (this.keyStoreFormat != null) {
						properties.put(com.solace.messaging.config.SolaceProperties.AuthenticationProperties.SCHEME_CLIENT_CERT_KEYSTORE_FORMAT, this.keyStoreFormat);
					}
					return properties;
				}
			}
		}

		public static class Client {
			/**
			 * Name to pass to the server when making requests; used for server-side logging.
			 */
			private String name = null;

			public String getName() { return this.name; }

			public void setName(String name) { this.name = name; }

			public Properties buildProperties() {
				Properties properties = new Properties();
				if (this.name != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.ClientProperties.NAME, this.name);
				}
				return properties;
			}
		}

		/**
		 * Configures SSL specifics for a Broker, Consumer and/or Producer.
		 */
		public static class Tls {
			/**
			 * Location of the trust store file.
			 */
			private Resource trustStorePath = null;

			/**
			 * Store password for the trust store file.
			 */
			private String trustStorePassword = null;

			/**
			 * Store format for the trust store file.
			 */
			private SecureStoreFormat trustStoreFormat = SecureStoreFormat.JKS;

			public Resource getTrustStorePath() { return this.trustStorePath; }

			public void setTrustStorePath(Resource trustStorePath) { this.trustStorePath = trustStorePath; }

			public String getTrustStorePassword() { return this.trustStorePassword; }

			public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }

			public SecureStoreFormat getTrustStoreFormat() { return this.trustStoreFormat; }

			public void setTrustStoreFormat(SecureStoreFormat trustStoreFormat) { this.trustStoreFormat = trustStoreFormat; }

			public Properties buildProperties() {
				Properties properties = new Properties();
				if (this.trustStorePath != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.TransportLayerSecurityProperties.TRUST_STORE_PATH, resourceToPath(this.trustStorePath));
				}
				if (this.trustStorePassword != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.TransportLayerSecurityProperties.TRUST_STORE_PASSWORD, this.trustStorePassword);
				}
				if (this.trustStoreFormat != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.TransportLayerSecurityProperties.TRUST_STORE_TYPE, this.trustStoreFormat);
				}
				return properties;
			}
		}
	}

	public Properties buildServiceProperties() {
		Properties properties = new Properties();
		properties.putAll(this.service.buildProperties());
		return properties;
	}

	/**
	 * Configures the Handler which produces new records for a Solace topic.
	 */
	public static class Handler {

		/**
		 * When greater than zero, enables waiting before considering that message is not delivered to the broker.
		 */
		private Long deliveryConfirmationTimeOutInMilliseconds = 10000L;

		/**
		 * Controls the mode of event processor responsible for sending messages to Kafka. Depending on this, different
		 * error handling behaviours are taken in case of any errors during Kafka publishing.
		 * <p>
		 * Defaults to {@link EventProcessorMode#SUBSCRIBING}, using a {@link org.axonframework.eventhandling.SubscribingEventProcessor}
		 * to publish events.
		 */
		private EventProcessorMode eventProcessorMode = EventProcessorMode.SUBSCRIBING;

		public Long getDeliveryConfirmationTimeOutInMilliseconds() {
			return this.deliveryConfirmationTimeOutInMilliseconds;
		}

		public void setDeliveryConfirmationTimeOutInMilliseconds(Long deliveryConfirmationTimeOutInMilliseconds) {
			this.deliveryConfirmationTimeOutInMilliseconds = deliveryConfirmationTimeOutInMilliseconds;
		}

		public EventProcessorMode getEventProcessorMode() {
			return eventProcessorMode;
		}

		public void setEventProcessorMode(EventProcessorMode eventProcessorMode) {
			this.eventProcessorMode = eventProcessorMode;
		}
	}

	/**
	 * Configures the publisher used to provide Axon Event Messages as Solace outbound messages to a MessagePublisher.
	 */
	public static class Publisher {

		/**
		 * Enables autoconfiguration for publishing events to Solace. Defaults to {@code true}.
		 */
		private boolean enabled = true;

		/**
		 * The confirmation mode used when publishing messages. Defaults to {@link ConfirmationMode#NONE}.
		 */
		private ConfirmationMode confirmationMode = ConfirmationMode.NONE;

		private Integer messagePublisherCacheSize = 10;

		private Long terminateTimeout = 10000L;

		private BackPressure backPressure = new BackPressure();

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public ConfirmationMode getConfirmationMode() {
			return confirmationMode;
		}

		public void setConfirmationMode(ConfirmationMode confirmationMode) {
			this.confirmationMode = confirmationMode;
		}

		public void setMessagePublisherCacheSize(Integer messagePublisherCacheSize) { this.messagePublisherCacheSize = messagePublisherCacheSize; }

		public Integer getMessagePublisherCacheSize() { return messagePublisherCacheSize; }

		public void setTerminateTimeout(Long terminateTimeout) { this.terminateTimeout = terminateTimeout; }

		public Long getTerminateTimeout() { return terminateTimeout; }

		public BackPressure getBackPressure() { return this.backPressure; }

		public Properties buildProperties() {
			Properties properties = new Properties();
			properties.putAll(this.backPressure.buildProperties());
			return properties;
		}

		public static class BackPressure {
			/**
			 * Strategy for back-pressure.
			 */
			private String strategy = null;
			/**
			 * Back pressure capacity measured in messages.
			 */
			private Integer bufferCapacity = null;

			public String getStrategy() { return this.strategy; }
			public void setStrategy(String strategy) { this.strategy = strategy; }

			public Integer getBufferCapacity() { return this.bufferCapacity; }
			public void setBufferCapacity(Integer bufferCapacity) { this.bufferCapacity = bufferCapacity; }

			public Properties buildProperties() {
				Properties properties = new Properties();
				if (this.strategy != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.PublisherProperties.PUBLISHER_BACK_PRESSURE_STRATEGY, this.strategy);
				}
				if (this.bufferCapacity != null) {
					properties.put(com.solace.messaging.config.SolaceProperties.PublisherProperties.PUBLISHER_BACK_PRESSURE_BUFFER_CAPACITY, this.bufferCapacity);
				}
				return properties;
			}
		}
	}

	/**
	 * Modes for message production and consumption.
	 */
	public enum EventProcessorMode {
		/**
		 * For producing messages a {@link org.axonframework.eventhandling.SubscribingEventProcessor} will be used, that
		 * will utilize Kafka's transactions for sending.
		 */
		SUBSCRIBING,
		/**
		 * Use a {@link org.axonframework.eventhandling.TrackingEventProcessor} to publish messages.
		 */
		TRACKING,
		/**
		 * Use a {@link org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor} to publish messages.
		 */
		POOLED_STREAMING
	}
}