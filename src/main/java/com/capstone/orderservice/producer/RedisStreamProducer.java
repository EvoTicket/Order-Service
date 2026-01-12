package com.capstone.orderservice.producer;

import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStreamProducer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gửi message vào Redis Stream
     * @param streamKey Tên stream (tương tự topic trong Kafka)
     * @param message Nội dung message
     */
    public void sendMessage(String streamKey, Object message) {
        try {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("payload", objectMapper.writeValueAsString(message));
            messageMap.put("timestamp", String.valueOf(System.currentTimeMillis()));

            ObjectRecord<String, Map<String, String>> objectRecord = StreamRecords
                    .newRecord()
                    .ofObject(messageMap)
                    .withStreamKey(streamKey);

            redisTemplate.opsForStream().add(objectRecord);

            log.info("Message sent to stream '{}': {}", streamKey, message);
        } catch (Exception e) {
            log.error("Error sending message to stream '{}'", streamKey, e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to send message", e);
        }
    }

    /**
     * Gửi message với key cụ thể (cho partitioning)
     */
    public void sendMessageWithKey(String streamKey, String messageKey, Object message) {
        try {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("key", messageKey);
            messageMap.put("payload", objectMapper.writeValueAsString(message));
            messageMap.put("timestamp", String.valueOf(System.currentTimeMillis()));

            ObjectRecord<String, Map<String, String>> objectRecord = StreamRecords
                    .newRecord()
                    .ofObject(messageMap)
                    .withStreamKey(streamKey);

            redisTemplate.opsForStream().add(objectRecord);

            log.info("Message with key '{}' sent to stream '{}': {}", messageKey, streamKey, message);
        } catch (Exception e) {
            log.error("Error sending message to stream '{}'", streamKey, e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to send message", e);
        }
    }
}