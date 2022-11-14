package org.axonframework.extensions.solace.eventhandling.publisher;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * An {@link EventMessageHandler} implementation responsible for sending {@link EventMessage} instances to Solace using
 * the provided {@link SolaceHandler}.
 * <p>
 * For correct usage add this class to an {@link org.axonframework.eventhandling.EventProcessor} implementation using
 * the {@link SolaceEventMessageHandler#DEFAULT_PROCESSING_GROUP} constant as the processor and processing group name.
 *
 * @author Alexander Martens
 * @since 1.0
 */
public class SolaceEventMessageHandler implements EventMessageHandler {

	/**
	 * The default Solace Event Handler processing group. Not intended to be used by other Event Handling Components than
	 * {@link SolaceEventMessageHandler}.
	 */
	public static final String DEFAULT_PROCESSING_GROUP = "__axon-solace-event-publishing-group";
	private static final boolean DOES_NOT_SUPPORT_RESET = false;
	private final SolaceHandler solaceHandler;

	/**
	 * Instantiate a Builder to be able to create a {@link SolaceEventMessageHandler}.
	 * <p>
	 * The {@link SolaceHandler} is a <b>hard requirements</b> and as such should be provided.
	 *
	 * @return a Builder to be able to create a {@link SolaceEventMessageHandler}
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Instantiate a {@link SolaceEventMessageHandler} based on the fields contained in the {@link Builder}.
	 * <p>
	 * Will assert that the {@link SolaceHandler} is not {@code null}, and will throw an {@link
	 * AxonConfigurationException} if it is.
	 *
	 * @param builder the {@link Builder} used to instantiate a {@link SolaceEventMessageHandler} instance
	 */
	protected SolaceEventMessageHandler(Builder builder) {
		builder.validate();
		this.solaceHandler = builder.solaceHandler;
	}

	@Override
	public Object handle(EventMessage<?> event) {
		solaceHandler.send(event);
		return null;
	}

	@Override
	public boolean supportsReset() {
		return DOES_NOT_SUPPORT_RESET;
	}

	/**
	 * Builder class to instantiate a {@link SolaceEventMessageHandler}.
	 * <p>
	 * The {@link SolaceHandler} is a <b>hard requirements</b> and as such should be provided.
	 */
	public static class Builder {

		private SolaceHandler solaceHandler;

		/**
		 * Sets the {@link SolaceHandler} to be used by this {@link EventMessageHandler} to publish {@link
		 * EventMessage} on.
		 *
		 * @param solaceHandler the {@link SolaceHandler} to be used by this {@link EventMessageHandler} to publish {@link EventMessage} on
		 * @return the current Builder instance, for fluent interfacing
		 */
		public Builder solaceHandler(SolaceHandler solaceHandler) {
			assertNonNull(solaceHandler, "SolacePublisher may not be null");
			this.solaceHandler = solaceHandler;
			return this;
		}

		/**
		 * Initializes a {@link SolaceEventMessageHandler} as specified through this Builder.
		 *
		 * @return a {@link SolaceEventMessageHandler} as specified through this Builder
		 */
		public SolaceEventMessageHandler build() {
			return new SolaceEventMessageHandler(this);
		}

		/**
		 * Validates whether the fields contained in this Builder are set accordingly.
		 *
		 * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
		 *                                    specifications
		 */
		@SuppressWarnings("WeakerAccess")
		protected void validate() {
			assertNonNull(solaceHandler, "The SolaceHandler is a hard requirement and should be provided");
		}
	}
}