package com.capstone.orderservice.service;

import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.enums.ResaleListingStatus;
import com.capstone.orderservice.repository.ResaleListingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class RedisReservationListener implements MessageListener {

    private final ResaleListingRepository resaleListingRepository;
    private static final String RESALE_RESERVATION_KEY_PREFIX = "resale:reservation:";

    public RedisReservationListener(ResaleListingRepository resaleListingRepository) {
        this.resaleListingRepository = resaleListingRepository;
    }

    @Override
    @Transactional
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        
        if (expiredKey.startsWith(RESALE_RESERVATION_KEY_PREFIX)) {
            String sessionId = expiredKey.replace(RESALE_RESERVATION_KEY_PREFIX, "");
            log.info("Received expiration event for resale session: {}", sessionId);
            
            // Find listing by reservation session ID
            Optional<ResaleListing> listingOpt = resaleListingRepository.findByReservationSessionId(sessionId);
            
            if (listingOpt.isPresent()) {
                ResaleListing listing = listingOpt.get();
                // Only release if it's still in RESERVED status and expired
                if (listing.getStatus() == ResaleListingStatus.RESERVED) {
                    log.info("Releasing expired resale reservation for listing: {}", listing.getListingCode());
                    listing.setStatus(ResaleListingStatus.ACTIVE);
                    listing.setReservationSessionId(null);
                    listing.setReservedUntil(null);
                    resaleListingRepository.save(listing);
                }
            }
        }
    }
}
