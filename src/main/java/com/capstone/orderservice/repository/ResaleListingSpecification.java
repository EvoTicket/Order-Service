package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.ResaleListingStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ResaleListingSpecification {

    public static Specification<ResaleListing> filterActiveListings(
            ResaleListingStatus status,
            Long eventId,
            Long ticketTypeId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String listingCode,
            List<String> categories,
            Integer provinceCode,
            String keywordParam,
            LocalDate startTime,
            LocalDate endTime
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<ResaleListing, TicketAsset> ticketAssetJoin = root.join("ticketAsset");

            // Filter by status
            predicates.add(cb.equal(root.get("status"), status));

            if (eventId != null) {
                predicates.add(cb.equal(ticketAssetJoin.get("eventId"), eventId));
            }

            if (ticketTypeId != null) {
                predicates.add(cb.equal(ticketAssetJoin.get("ticketTypeId"), ticketTypeId));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("listingPrice"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("listingPrice"), maxPrice));
            }

            if (listingCode != null && !listingCode.isBlank()) {
                predicates.add(cb.like(root.get("listingCode"), "%" + listingCode + "%"));
            }

            if (categories != null && !categories.isEmpty()) {
                predicates.add(ticketAssetJoin.get("category").in(categories));
            }

            if (provinceCode != null) {
                predicates.add(cb.equal(ticketAssetJoin.get("provinceCode"), provinceCode));
            }

            if (keywordParam != null && !keywordParam.isBlank()) {
                // keywordParam already contains % from the service layer
                String lowerKeyword = keywordParam.toLowerCase();
                Predicate nameLike = cb.like(cb.lower(ticketAssetJoin.get("eventName")), lowerKeyword);
                Predicate addressLike = cb.like(cb.lower(ticketAssetJoin.get("venueAddress")), lowerKeyword);
                predicates.add(cb.or(nameLike, addressLike));
            }

            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(ticketAssetJoin.get("eventStartTime"), startTime));
            }

            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(ticketAssetJoin.get("eventStartTime"), endTime));
            }

            // Order by created_at desc is usually handled by Pageable, 
            // but we can enforce it here if needed or let the service decide.

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
