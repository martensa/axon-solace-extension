package org.axonframework.extensions.solace.example.order;

import org.axonframework.extensions.solace.autoconfig.SolaceAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({SolaceAutoConfiguration.class})
public class OrderApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderApplication.class, args);
	}
}