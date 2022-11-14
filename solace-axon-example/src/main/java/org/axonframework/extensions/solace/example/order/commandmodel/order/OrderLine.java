package org.axonframework.extensions.solace.example.order.commandmodel.order;

import java.util.Objects;

import org.axonframework.extensions.solace.example.order.coreapi.commands.DecrementProductCountCommand;
import org.axonframework.extensions.solace.example.order.coreapi.commands.IncrementProductCountCommand;
import org.axonframework.extensions.solace.example.order.coreapi.events.OrderConfirmedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.ProductCountDecrementedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.ProductCountIncrementedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.ProductRemovedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.exceptions.OrderAlreadyConfirmedException;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.EntityId;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

public class OrderLine {

	@EntityId
	private final String productId;
	private Integer count;
	private boolean orderConfirmed;

	public OrderLine(String productId) {
		this.productId = productId;
		this.count = 1;
	}

	@CommandHandler
	public void handle(IncrementProductCountCommand command) {
		if (orderConfirmed) {
			throw new OrderAlreadyConfirmedException(command.getOrderId());
		}

		apply(new ProductCountIncrementedEvent(command.getOrderId(), productId));
	}

	@CommandHandler
	public void handle(DecrementProductCountCommand command) {
		if (orderConfirmed) {
			throw new OrderAlreadyConfirmedException(command.getOrderId());
		}

		if (count <= 1) {
			apply(new ProductRemovedEvent(command.getOrderId(), productId));
		} else {
			apply(new ProductCountDecrementedEvent(command.getOrderId(), productId));
		}
	}

	@EventSourcingHandler
	public void on(ProductCountIncrementedEvent event) {
		this.count++;
	}

	@EventSourcingHandler
	public void on(ProductCountDecrementedEvent event) {
		this.count--;
	}

	@EventSourcingHandler
	public void on(OrderConfirmedEvent event) {
		this.orderConfirmed = true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OrderLine orderLine = (OrderLine) o;
		return Objects.equals(productId, orderLine.productId) && Objects.equals(count, orderLine.count);
	}

	@Override
	public int hashCode() {
		return Objects.hash(productId, count);
	}
}