package com.capstone.orderservice.service;

import com.capstone.orderservice.client.OrderInternalResponse;
import com.capstone.orderservice.client.PaymentFeignClient;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.ResaleCheckoutRequest;
import com.capstone.orderservice.dto.response.PaymentLinkResponse;
import com.capstone.orderservice.dto.response.ResaleCheckoutResponse;
import com.capstone.orderservice.dto.response.ResalePaymentStatusResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResaleCheckoutFacade {

    private final ResaleService resaleService;
    private final PaymentFeignClient paymentFeignClient;
    private final OrderRepository orderRepository;

    public ResaleCheckoutResponse checkout(ResaleCheckoutRequest request) {
        ResaleCheckoutResponse response = resaleService.createResaleCheckout(request);

        try {
            PaymentLinkResponse paymentLink = createPaymentLinkOrThrow(response.getOrderCode(), request.getLocale());
            response.setRedirectUrl(paymentLink.getRedirectUrl());
            return response;
        } catch (Exception e) {
            try {
                resaleService.restoreFailedPaymentInitialization(response.getOrderCode());
            } catch (Exception restoreException) {
                log.error("Failed to restore resale checkout after payment initialization failure for order {}",
                        response.getOrderCode(), restoreException);
            }
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR, "Unable to initialize resale payment", e);
        }
    }

    public ResalePaymentStatusResponse continuePayment(String orderCode) {
        ResalePaymentStatusResponse response = resaleService.validateContinuePayment(orderCode);

        try {
            PaymentLinkResponse paymentLink = createPaymentLinkOrThrow(orderCode, "vi");
            response.setRedirectUrl(paymentLink.getRedirectUrl());
            return response;
        } catch (Exception e) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR, "Unable to initialize resale payment", e);
        }
    }

    private PaymentLinkResponse createPaymentLinkOrThrow(String orderCode, String locale) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        OrderInternalResponse request = OrderInternalResponse.fromEntity(order, locale);
        BaseResponse<PaymentLinkResponse> response = paymentFeignClient.createPaymentLink(request);
        PaymentLinkResponse paymentLink = response != null ? response.getData() : null;
        if (paymentLink == null
                || paymentLink.getRedirectUrl() == null
                || paymentLink.getRedirectUrl().isBlank()) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR, "Unable to initialize resale payment");
        }
        return paymentLink;
    }
}
