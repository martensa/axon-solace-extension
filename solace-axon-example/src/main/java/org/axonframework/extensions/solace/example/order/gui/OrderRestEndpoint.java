package org.axonframework.extensions.solace.example.order.gui;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.axonframework.extensions.solace.example.order.coreapi.commands.AddProductCommand;
import org.axonframework.extensions.solace.example.order.coreapi.commands.ConfirmOrderCommand;
import org.axonframework.extensions.solace.example.order.coreapi.commands.CreateOrderCommand;
import org.axonframework.extensions.solace.example.order.coreapi.commands.DecrementProductCountCommand;
import org.axonframework.extensions.solace.example.order.coreapi.commands.IncrementProductCountCommand;
import org.axonframework.extensions.solace.example.order.coreapi.commands.ShipOrderCommand;
import org.axonframework.extensions.solace.example.order.coreapi.queries.FindAllOrderedProductsQuery;
import org.axonframework.extensions.solace.example.order.coreapi.queries.Order;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderRestEndpoint {

	private final CommandGateway commandGateway;
	private final QueryGateway queryGateway;

	public OrderRestEndpoint(CommandGateway commandGateway, QueryGateway queryGateway) {
		this.commandGateway = commandGateway;
		this.queryGateway = queryGateway;
	}

	@PostMapping("/ship-order")
	public CompletableFuture<Void> shipOrder() {
		String orderId = UUID.randomUUID().toString();
		return commandGateway.send(new CreateOrderCommand(orderId))
				.thenCompose(result -> commandGateway.send(new AddProductCommand(orderId, "Deluxe Chair")))
				.thenCompose(result -> commandGateway.send(new ConfirmOrderCommand(orderId)))
				.thenCompose(result -> commandGateway.send(new ShipOrderCommand(orderId)));
	}

	@PostMapping("/ship-unconfirmed-order")
	public CompletableFuture<Void> shipUnconfirmedOrder() {
		String orderId = UUID.randomUUID().toString();
		return commandGateway.send(new CreateOrderCommand(orderId))
				.thenCompose(result -> commandGateway.send(new AddProductCommand(orderId, "Deluxe Chair")))
				// This throws an exception, as an Order cannot be shipped if it has not been confirmed yet.
				.thenCompose(result -> commandGateway.send(new ShipOrderCommand(orderId)));
	}

	@PostMapping("/order")
	public CompletableFuture<String> createOrder() {
		return createOrder(UUID.randomUUID().toString());
	}

	@PostMapping("/order/{order-id}")
	public CompletableFuture<String> createOrder(@PathVariable("order-id") String orderId) {
		return commandGateway.send(new CreateOrderCommand(orderId));
	}

	@PostMapping("/order/{order-id}/product/{product-id}")
	public CompletableFuture<Void> addProduct(@PathVariable("order-id") String orderId,
			@PathVariable("product-id") String productId) {
		return commandGateway.send(new AddProductCommand(orderId, productId));
	}

	@PostMapping("/order/{order-id}/product/{product-id}/increment")
	public CompletableFuture<Void> incrementProduct(@PathVariable("order-id") String orderId,
			@PathVariable("product-id") String productId) {
		return commandGateway.send(new IncrementProductCountCommand(orderId, productId));
	}

	@PostMapping("/order/{order-id}/product/{product-id}/decrement")
	public CompletableFuture<Void> decrementProduct(@PathVariable("order-id") String orderId,
			@PathVariable("product-id") String productId) {
		return commandGateway.send(new DecrementProductCountCommand(orderId, productId));
	}

	@PostMapping("/order/{order-id}/confirm")
	public CompletableFuture<Void> confirmOrder(@PathVariable("order-id") String orderId) {
		return commandGateway.send(new ConfirmOrderCommand(orderId));
	}

	@PostMapping("/order/{order-id}/ship")
	public CompletableFuture<Void> shipOrder(@PathVariable("order-id") String orderId) {
		return commandGateway.send(new ShipOrderCommand(orderId));
	}

	@GetMapping("/all-orders")
	public CompletableFuture<List<Order>> findAllOrders() {
		return queryGateway.query(new FindAllOrderedProductsQuery(), ResponseTypes.multipleInstancesOf(Order.class));
	}
}
