package org.axonframework.extensions.solace.eventhandling;

public abstract class SolaceHeaders {
//	public static final String SOLACE_SENDER_ID = "solace-sender-id";
	public static final String SOLACE_SENDER_ID = "solace.senderId";
	public static final String SOLACE_CORRELATION_ID = "correlationId";
	public static final String SOLACE_DESTINATION_NAME = "destinationName";

	private SolaceHeaders() {
	}

	public String toString() {
		return "[SolaceHeaders]";
	}
}
