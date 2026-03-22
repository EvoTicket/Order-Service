package com.capstone.orderservice.util;

import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderUtil {
    private final OrderRepository orderRepository;

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
    }

    public Order getOrderByOrderCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode).orElseThrow(() -> {
            log.error("Cannot find order code {}", orderCode);
            return new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found");
        });
    }
}
