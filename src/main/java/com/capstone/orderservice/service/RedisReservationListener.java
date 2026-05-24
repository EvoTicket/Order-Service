package com.capstone.orderservice.service;

import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.enums.ResaleListingStatus;
import com.capstone.orderservice.repository.ResaleListingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
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
    private final CacheManager cacheManager;
    private static final String RESALE_RESERVATION_KEY_PREFIX = "resale:reservation:";

    public RedisReservationListener(ResaleListingRepository resaleListingRepository, CacheManager cacheManager) {
        this.resaleListingRepository = resaleListingRepository;
        this.cacheManager = cacheManager;
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
                    evictResaleCaches(listing.getListingCode());
                }
            }
        }
    }

    private void evictResaleCaches(String listingCode) {
        try {
            if (listingCode != null) {
                var detailCache = cacheManager.getCache("resaleListingDetail");
                if (detailCache != null) {
                    detailCache.evict(listingCode);
                }
            }
            var listCache = cacheManager.getCache("resaleActiveListings");
            if (listCache != null) {
                listCache.clear();
            }
            log.info("Evicted resale caches from RedisReservationListener. ListingCode={}", listingCode);
        } catch (Exception e) {
            log.error("Failed to evict resale caches", e);
        }
    }
}
