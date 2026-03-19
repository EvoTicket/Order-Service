package com.capstone.orderservice.consumer;

import com.capstone.orderservice.dto.event.PaymentSuccessEvent;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> LIST_STREAM_KEY = List.of(
            "payment-success",
            "commit-ticket-success",
            "commit-ticket-failed"
    );
    private static final String CONSUMER_GROUP = "order-service-group";
    private static final String CONSUMER_NAME = "order";

    private final List<Subscription> subscriptions = new ArrayList<>();

    @PostConstruct
    public void init() {
        LIST_STREAM_KEY.forEach(streamKey -> {
            try {
                redisTemplate.opsForStream().createGroup(streamKey, CONSUMER_GROUP);
                log.info("Created consumer group '{}' for stream '{}'", CONSUMER_GROUP, streamKey);
            } catch (Exception e) {
                log.info("Consumer group '{}' already exists for stream '{}'", CONSUMER_GROUP, streamKey);
            }
        });

        LIST_STREAM_KEY.forEach(streamKey -> {

            Subscription sub = listenerContainer.receive(
                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                    this);

            subscriptions.add(sub);

            log.info("Subscribed consumer '{}' to stream '{}'", CONSUMER_NAME, streamKey);
        });

        listenerContainer.start();
        log.info("Redis Stream consumer started for all streams: {}", LIST_STREAM_KEY);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            String messageId = message.getId().getValue();
            Map<String, String> body = message.getValue();

            String payload = body.get("payload");

            log.info("Received message [ID: {}]: {}", messageId, payload);

            filterMessage(payload, message.getRequiredStream());

            redisTemplate.opsForStream()
                    .acknowledge(message.getRequiredStream(), CONSUMER_GROUP, messageId);

            log.info("Message acknowledged: {}", messageId);

        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }

    private void filterMessage(String payload, String stream) {
        log.info("Processing: {}", payload);

        switch (stream) {
            case "payment-success" -> handlePaymentSuccessEvent(payload);
            case "commit-ticket-success" -> handleCommitSuccessEvent(payload);
            case "commit-ticket-failed" -> handleCommitFailedEvent(payload);
            default -> throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Unknown stream: " + stream);
        }
    }

    private void handleCommitSuccessEvent(String payload) {
        try {
            String orderCode = objectMapper.readValue(payload, String.class);
            orderService.markPaid(orderCode);
        } catch (Exception e) {
            log.error("Error processing OTP event", e);
        }
    }

    private void handleCommitFailedEvent(String payload) {
        try {
            String orderCode = objectMapper.readValue(payload, String.class);
            orderService.markFailed(orderCode);
        } catch (Exception e) {
            log.error("Error processing OTP event", e);
        }
    }

    private void handlePaymentSuccessEvent(String payload) {
        try {
            PaymentSuccessEvent paymentSuccessEvent = objectMapper.readValue(payload, PaymentSuccessEvent.class);
            log.info("Processing event: {}", PaymentSuccessEvent.class);

            orderService.commitTicket(paymentSuccessEvent);
            log.info("successfully for: {}", PaymentSuccessEvent.class);

        } catch (Exception e) {
            log.error("Error processing OTP event", e);
        }
    }

    @PreDestroy
    public void destroy() {
        subscriptions.forEach(Subscription::cancel);
        listenerContainer.stop();
        log.info("Redis Stream consumer stopped");
    }
}