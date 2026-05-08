package com.capstone.orderservice.service;

import com.capstone.orderservice.repository.ResaleListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResaleListingStatsService {
    private final ResaleListingRepository resaleListingRepository;

    @Async
    @Transactional
    public void incrementListingViewCount(Long id) {
        try {
            resaleListingRepository.incrementViewCount(id);
        } catch (Exception e) {
            log.error("Failed to increment view count for listing {}", id, e);
        }
    }
}
