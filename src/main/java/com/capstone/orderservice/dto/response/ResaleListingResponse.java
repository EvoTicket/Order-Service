package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.client.TicketTypeInternalResponse;
import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.ResaleListingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResaleListingResponse {
    private Long listingId;
    private String listingCode;
    private Long ticketAssetId;
    private Long eventId;
    private String eventName;
    private Long showtimeId;
    private LocalDateTime eventStartTime;
    private LocalDateTime eventEndTime;
    private String venueName;
    private String venueAddress;
    private String bannerImage;
    private Long ticketTypeId;
    private String ticketTypeName;
    private Long sellerId;
    private BigDecimal originalPrice;
    private BigDecimal listingPrice;
    private BigDecimal priceCap;
    private BigDecimal platformFeeAmount;
    private BigDecimal organizerRoyaltyAmount;
    private BigDecimal sellerPayoutAmount;
    private ResaleListingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime soldAt;
    private String tokenId;
    private String contractAddress;
    private Long fromBlock;
    private Long toBlock;
    private Long viewCount;

    public static ResaleListingResponse fromEntity(ResaleListing listing) {
        return fromEntity(listing, null);
    }

    public static ResaleListingResponse fromEntity(ResaleListing listing, TicketTypeInternalResponse details) {
        TicketAsset asset = listing.getTicketAsset();
        ResaleListingResponseBuilder builder = ResaleListingResponse.builder()
                .listingId(listing.getId())
                .listingCode(listing.getListingCode())
                .ticketAssetId(asset.getId())
                .sellerId(listing.getSellerId())
                .originalPrice(listing.getOriginalPrice())
                .listingPrice(listing.getListingPrice())
                .priceCap(listing.getPriceCap())
                .platformFeeAmount(listing.getPlatformFeeAmount())
                .organizerRoyaltyAmount(listing.getOrganizerRoyaltyAmount())
                .sellerPayoutAmount(listing.getSellerPayoutAmount())
                .status(listing.getStatus())
                .createdAt(listing.getCreatedAt())
                .expiresAt(listing.getExpiresAt())
                .cancelledAt(listing.getCancelledAt())
                .soldAt(listing.getSoldAt())
                .tokenId(asset.getTokenId())
                .contractAddress(asset.getContractAddress())
                .fromBlock(asset.getFromBlock())
                .toBlock(asset.getToBlock())
                .viewCount(listing.getViewCount());

        if (details != null) {
            builder.eventId(details.getEventId())
                    .eventName(details.getEventName())
                    .showtimeId(details.getShowtimeId())
                    .eventStartTime(details.getEventStartTime())
                    .eventEndTime(details.getEventEndTime())
                    .venueName(details.getVenueName())
                    .venueAddress(details.getVenueAddress())
                    .bannerImage(details.getBannerImage())
                    .ticketTypeId(details.getTicketTypeId())
                    .ticketTypeName(details.getTicketTypeName());
        } else {
            builder.eventId(asset.getEventId())
                    .eventName(asset.getEventName())
                    .showtimeId(asset.getShowtimeId())
                    .eventStartTime(asset.getEventStartTime())
                    .eventEndTime(asset.getEventEndTime())
                    .venueName(asset.getVenueName())
                    .venueAddress(asset.getVenueAddress())
                    .ticketTypeId(asset.getTicketTypeId())
                    .ticketTypeName(asset.getTicketTypeName());
        }

        return builder.build();
    }
}
