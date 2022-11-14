package org.axonframework.extensions.solace.eventhandling.publisher;

/**
 * Modes for publishing Axon Event Messages to Solace.
 * <ul>
 * <li>WAIT_FOR_ACK: send messages and wait for acknowledgment</li>
 * <li>AUTO_ACK: send messages and acknowledge acknowledges automatically upon the message callback returns</li>
 * <li>NONE: Fire and forget</li>
 * </ul>
 *
 * @author Alexander Martens
 * @since 1.0
 */
public enum ConfirmationMode {

	/**
	 * Indicates a confirmation mode which sends messages and waits for consumption acknowledgements.
	 */
	WAIT_FOR_ACK,

	/**
	 * Indicates a confirmation mode which sends messages and acknowledges automatically upon the message callback returns.
	 */
	AUTO_ACK,

	/**
	 * Indicates a confirmation mode resembling fire and forget.
	 */
	NONE;

	/**
	 * Verify whether {@code this} confirmation mode is of type {@link #AUTO_ACK}.
	 *
	 * @return {@code true} if {@code this} confirmation mode matches {@link #AUTO_ACK}, {@code false} if it doesn't
	 */
	public boolean isAutoAck() {
		return this == AUTO_ACK;
	}

	/**
	 * Verify whether {@code this} confirmation mode is of type {@link #WAIT_FOR_ACK}.
	 *
	 * @return {@code true} if {@code this} confirmation mode matches {@link #WAIT_FOR_ACK}, {@code false} if it doesn't
	 */
	public boolean isWaitForAck() {
		return this == WAIT_FOR_ACK;
	}

	/**
	 * Verify whether {@code this} confirmation mode is of type {@link #NONE}.
	 *
	 * @return {@code true} if {@code this} confirmation mode matches {@link #NONE}, {@code false} if it doesn't
	 */
	public boolean isNone() { return this == NONE; }
}
