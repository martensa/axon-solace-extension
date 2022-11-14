package org.axonframework.extensions.solace.autoconfig;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.extensions.solace.SolaceProperties;
import org.axonframework.extensions.solace.eventhandling.service.DefaultMessagingServiceFactory;
import org.axonframework.extensions.solace.eventhandling.DefaultSolaceMessageConverter;
import org.axonframework.extensions.solace.eventhandling.service.MessagingServiceFactory;
import org.axonframework.extensions.solace.eventhandling.SolaceMessageConverter;
import org.axonframework.extensions.solace.eventhandling.publisher.ConfirmationMode;
import org.axonframework.extensions.solace.eventhandling.publisher.DefaultMessagePublisherFactory;
import org.axonframework.extensions.solace.eventhandling.publisher.MessagePublisherFactory;
import org.axonframework.extensions.solace.eventhandling.publisher.SolaceEventMessageHandler;
import org.axonframework.extensions.solace.eventhandling.publisher.SolaceHandler;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.spring.config.AxonConfiguration;
import org.axonframework.springboot.autoconfig.AxonAutoConfiguration;
import org.axonframework.springboot.autoconfig.InfraConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.invoke.MethodHandles;

import static org.axonframework.extensions.solace.eventhandling.publisher.SolaceEventMessageHandler.DEFAULT_PROCESSING_GROUP;

/**
 * Autoconfiguration for the Axon Solace Extension as an Event Message distribution solution.
 *
 * @author Alexander Martens
 * @since 1.0
 */
@Configuration
@ConditionalOnExpression(
		"${axon.solace.publisher.enabled:true}"
)
@AutoConfigureAfter(AxonAutoConfiguration.class)
@AutoConfigureBefore(InfraConfiguration.class)
@EnableConfigurationProperties(SolaceProperties.class)
public class SolaceAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SolaceProperties properties;

	public SolaceAutoConfiguration(SolaceProperties properties) {
		this.properties = properties;
	}

	@Bean("axonSolaceMessagingServiceFactory")
	@ConditionalOnMissingBean
	public MessagingServiceFactory solaceMessagingServiceFactory() {
		DefaultMessagingServiceFactory.Builder builder = DefaultMessagingServiceFactory.builder().configuration(properties.buildServiceProperties());
		return builder.build();
	}

	@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	@Bean
	@ConditionalOnMissingBean
	public SolaceMessageConverter solaceMessageConverter(
			@Qualifier("eventSerializer") Serializer eventSerializer,
			org.axonframework.config.Configuration configuration
	) {
		return DefaultSolaceMessageConverter.builder().serializer(eventSerializer)
				.upcasterChain(configuration.upcasterChain()
						!= null ? configuration.upcasterChain() : new EventUpcasterChain())
				.build();
	}

	@Bean("axonSolaceMessagePublisherFactory")
	@ConditionalOnMissingBean
	@ConditionalOnBean({MessagingServiceFactory.class})
	@ConditionalOnProperty(name = "axon.solace.publisher.enabled", havingValue = "true", matchIfMissing = true)
	public MessagePublisherFactory solaceMessagePublisherFactory(MessagingServiceFactory axonSolaceMessagingServiceFactory) {
		ConfirmationMode confirmationMode = properties.getPublisher().getConfirmationMode();
		int messagePublisherCacheSize = properties.getPublisher().getMessagePublisherCacheSize();
		long terminateTimeout = properties.getPublisher().getTerminateTimeout();

		DefaultMessagePublisherFactory.Builder builder =
				DefaultMessagePublisherFactory.builder().messagingServiceFactory(axonSolaceMessagingServiceFactory)
						.configuration(properties.getPublisher().buildProperties())
						.confirmationMode(confirmationMode)
						.messagePublisherCacheSize(messagePublisherCacheSize)
						.terminateTimeout(terminateTimeout);
		return builder.build();
	}

	@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	@ConditionalOnMissingBean
	@Bean(destroyMethod = "shutDown")
	@ConditionalOnBean({MessagePublisherFactory.class, SolaceMessageConverter.class})
	@ConditionalOnProperty(name = "axon.solace.publisher.enabled", havingValue = "true", matchIfMissing = true)
	public SolaceHandler solacePublisher(MessagePublisherFactory axonSolaceMessagePublisherFactory,
			SolaceMessageConverter solaceMessageConverter,
			AxonConfiguration configuration) {
		return SolaceHandler.builder()
				.messagePublisherFactory(axonSolaceMessagePublisherFactory)
				.messageConverter(solaceMessageConverter)
				.messageMonitor(configuration.messageMonitor(SolaceHandler.class, "solaceHandler"))
				.topic(properties.getDefaultTopic())
				.deliveryConfirmationTimeOutInMilliseconds(properties.getHandler().getDeliveryConfirmationTimeOutInMilliseconds())
				.build();
	}

	@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({SolaceHandler.class})
	@ConditionalOnProperty(name = "axon.solace.publisher.enabled", havingValue = "true", matchIfMissing = true)
	public SolaceEventMessageHandler solaceEventMessageHandler(SolaceHandler solaceHandler,
			SolaceProperties solaceProperties,
			EventProcessingConfigurer eventProcessingConfigurer) {
		SolaceEventMessageHandler solaceEventMessageHandler =
				SolaceEventMessageHandler.builder().solaceHandler(solaceHandler).build();

		/*
		 * Register an invocation error handler which re-throws any exception.
		 * This will ensure a StreamingEventProcessor to enter the error mode which will retry, and it will ensure the
		 * SubscribingEventProcessor to bubble the exception to the caller. For more information see
		 *  https://docs.axoniq.io/reference-guide/configuring-infrastructure-components/event-processing/event-processors#error-handling
		 */
		eventProcessingConfigurer.registerEventHandler(configuration -> solaceEventMessageHandler)
				.registerListenerInvocationErrorHandler(
						DEFAULT_PROCESSING_GROUP, configuration -> PropagatingErrorHandler.instance()
				)
				.assignHandlerTypesMatching(
						DEFAULT_PROCESSING_GROUP,
						clazz -> clazz.isAssignableFrom(SolaceEventMessageHandler.class)
				);

		SolaceProperties.EventProcessorMode processorMode = solaceProperties.getHandler().getEventProcessorMode();
		if (processorMode == SolaceProperties.EventProcessorMode.SUBSCRIBING) {
			eventProcessingConfigurer.registerSubscribingEventProcessor(DEFAULT_PROCESSING_GROUP);
		} else if (processorMode == SolaceProperties.EventProcessorMode.TRACKING) {
			eventProcessingConfigurer.registerTrackingEventProcessor(DEFAULT_PROCESSING_GROUP);
		} else if (processorMode == SolaceProperties.EventProcessorMode.POOLED_STREAMING) {
			eventProcessingConfigurer.registerPooledStreamingEventProcessor(DEFAULT_PROCESSING_GROUP);
		} else {
			throw new AxonConfigurationException("Unknown Event Processor Mode [" + processorMode + "] detected");
		}

		return solaceEventMessageHandler;
	}
}