package com.portfolio.ratelimiter.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DummyController {

    @GetMapping("/products")
    public List<Map<String, Object>> listProducts() {
        return List.of(
            Map.of("id", 1, "name", "Widget"),
            Map.of("id", 2, "name", "Gadget")
        );
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable int id) {
        return Map.of("id", id, "name", "Product " + id);
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        return Map.of("orderId", UUID.randomUUID().toString(), "status", "created");
    }
}