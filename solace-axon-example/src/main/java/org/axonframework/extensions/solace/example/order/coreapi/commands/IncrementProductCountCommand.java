package org.axonframework.extensions.solace.example.order.coreapi.commands;

import java.util.Objects;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class IncrementProductCountCommand {

	@TargetAggregateIdentifier
	private final String orderId;
	private final String productId;

	public IncrementProductCountCommand(String orderId, String productId) {
		this.orderId = orderId;
		this.productId = productId;
	}

	public String getOrderId() {
		return orderId;
	}

	public String getProductId() {
		return productId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		IncrementProductCountCommand that = (IncrementProductCountCommand) o;
		return Objects.equals(orderId, that.orderId) && Objects.equals(productId, that.productId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(orderId, productId);
	}

	@Override
	public String toString() {
		return "IncrementProductCountCommand{" +
				"orderId='" + orderId + '\'' +
				", productId='" + productId + '\'' +
				'}';
	}
}