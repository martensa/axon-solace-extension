package org.axonframework.extensions.solace.eventhandling;

import com.solace.messaging.MessagingService;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.receiver.InboundMessage;
import org.axonframework.eventhandling.EventMessage;

import java.util.Optional;

/**
 * Converts Solace messages from Axon {@link EventMessage}s and vice versa.
 *
 * @author Alexander Martens
 * @since 1.0
 */
public interface SolaceMessageConverter {

	/**
	 * Creates a {@link OutboundMessage} for a given {@link EventMessage} to be published on a Solace Producer.
	 *
	 * @param eventMessage the event message to convert into a {@link OutboundMessage} for Solace
	 * @param service        the instance of a messaging service
	 * @return the converted {@code eventMessage} as a {@link OutboundMessage}
	 */
	OutboundMessage createSolaceMessage(EventMessage<?> eventMessage, MessagingService service);

	/**
	 * Reconstruct an {@link EventMessage} from the given  {@link InboundMessage}. The returned optional resolves to a
	 * message if the given input parameters represented a correct EventMessage.
	 *
	 * @param inboundMessage the Event Message represented inside Solace
	 * @return the converted {@code inboundMessage} as an {@link EventMessage}
	 */
	Optional<EventMessage<?>> readSolaceMessage(InboundMessage inboundMessage);
}
