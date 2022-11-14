package org.axonframework.extensions.solace.example.order.querymodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.axonframework.extensions.solace.example.order.coreapi.events.OrderConfirmedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.OrderCreatedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.OrderShippedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.ProductAddedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.ProductCountDecrementedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.ProductCountIncrementedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.events.ProductRemovedEvent;
import org.axonframework.extensions.solace.example.order.coreapi.queries.FindAllOrderedProductsQuery;
import org.axonframework.extensions.solace.example.order.coreapi.queries.Order;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;

import org.springframework.stereotype.Service;

@Service
@ProcessingGroup("orders")
public class OrdersEventHandler {

	private final Map<String, Order> orders = new HashMap<>();

	@EventHandler
	public void on(OrderCreatedEvent event) {
		String orderId = event.getOrderId();
		orders.put(orderId, new Order(orderId));
	}

	@EventHandler
	public void on(ProductAddedEvent event) {
		orders.computeIfPresent(event.getOrderId(), (orderId, order) -> {
			order.addProduct(event.getProductId());
			return order;
		});
	}

	@EventHandler
	public void on(ProductCountIncrementedEvent event) {
		orders.computeIfPresent(event.getOrderId(), (orderId, order) -> {
			order.incrementProductInstance(event.getProductId());
			return order;
		});
	}

	@EventHandler
	public void on(ProductCountDecrementedEvent event) {
		orders.computeIfPresent(event.getOrderId(), (orderId, order) -> {
			order.decrementProductInstance(event.getProductId());
			return order;
		});
	}

	@EventHandler
	public void on(ProductRemovedEvent event) {
		orders.computeIfPresent(event.getOrderId(), (orderId, order) -> {
			order.removeProduct(event.getProductId());
			return order;
		});
	}

	@EventHandler
	public void on(OrderConfirmedEvent event) {
		orders.computeIfPresent(event.getOrderId(), (orderId, order) -> {
			order.setOrderConfirmed();
			return order;
		});
	}

	@EventHandler
	public void on(OrderShippedEvent event) {
		orders.computeIfPresent(event.getOrderId(), (orderId, order) -> {
			order.setOrderShipped();
			return order;
		});
	}

	@QueryHandler
	public List<Order> handle(FindAllOrderedProductsQuery query) {
		return new ArrayList<>(orders.values());
	}
}