package com.capstone.orderservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@Getter
@RequiredArgsConstructor
public enum ResaleSortOption {
    PRICE_ASC(Sort.by(Sort.Direction.ASC, "listingPrice")),
    PRICE_DESC(Sort.by(Sort.Direction.DESC, "listingPrice")),
    DATE_ASC(Sort.by(Sort.Direction.ASC, "ticketAsset.eventStartTime")),
    NEWEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    POPULAR(Sort.by(Sort.Direction.DESC, "viewCount"));

    private final Sort sort;

    public static ResaleSortOption fromString(String value) {
        if (value == null) return NEWEST;
        try {
            return ResaleSortOption.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEWEST;
        }
    }
}
