package org.axonframework.extensions.solace.example.order.coreapi.commands;

import java.util.Objects;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class ConfirmOrderCommand {

	@TargetAggregateIdentifier
	private final String orderId;

	public ConfirmOrderCommand(String orderId) {
		this.orderId = orderId;
	}

	public String getOrderId() {
		return orderId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(orderId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		final ConfirmOrderCommand other = (ConfirmOrderCommand) obj;
		return Objects.equals(this.orderId, other.orderId);
	}

	@Override
	public String toString() {
		return "ConfirmOrderCommand{" +
				"orderId='" + orderId + '\'' +
				'}';
	}
}