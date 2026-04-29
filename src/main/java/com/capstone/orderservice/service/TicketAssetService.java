package com.capstone.orderservice.service;

import com.capstone.orderservice.client.EventDetailInternalResponse;
import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.repository.TicketAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAssetService {
    private final TicketAssetRepository ticketAssetRepository;
    private final InventoryFeignClient inventoryFeignClient;

    @Transactional
    public void issueTicketsForConfirmedOrder(Order order) {
        OrderType orderType = order.getOrderType() != null ? order.getOrderType() : OrderType.PRIMARY;
        if (orderType != OrderType.PRIMARY || order.getOrderStatus() != OrderStatus.CONFIRMED) {
            return;
        }

        Map<Long, Optional<EventDetailInternalResponse>> metadataByTicketTypeId = new HashMap<>();

        for (OrderItem item : order.getOrderItems()) {
            if (ticketAssetRepository.existsByOrderItem_Id(item.getId())) {
                continue;
            }

            Optional<EventDetailInternalResponse> eventMetadata = metadataByTicketTypeId.computeIfAbsent(
                    item.getTicketTypeId(),
                    this::fetchEventMetadata
            );

            TicketAsset asset = buildTicketAsset(order, item, eventMetadata.orElse(null));
            ticketAssetRepository.save(asset);
        }
    }

    private Optional<EventDetailInternalResponse> fetchEventMetadata(Long ticketTypeId) {
        try {
            return Optional.ofNullable(inventoryFeignClient.getEventDetailsByTicketTypeId(ticketTypeId).getData());
        } catch (Exception e) {
            log.warn("Failed to fetch event metadata for ticketTypeId={}. Issuing TicketAsset without display metadata.",
                    ticketTypeId, e);
            return Optional.empty();
        }
    }

    private TicketAsset buildTicketAsset(
            Order order,
            OrderItem item,
            EventDetailInternalResponse eventMetadata
    ) {
        EventDetailInternalResponse.ShowtimeDetail showtime = eventMetadata != null
                ? eventMetadata.getShowtime()
                : null;

        TicketAsset.TicketAssetBuilder builder = TicketAsset.builder()
                .assetCode(UUID.randomUUID().toString())
                .orderItem(item)
                .originalOrderId(order.getId())
                .originalOrderCode(order.getOrderCode())
                .eventId(order.getEventId())
                .ticketTypeId(item.getTicketTypeId())
                .ticketTypeName(item.getTicketTypeName())
                .ticketCode(item.getTicketCode())
                .originalPrice(item.getUnitPrice())
                .originalBuyerId(order.getUserId())
                .currentOwnerId(order.getUserId())
                .accessStatus(TicketAccessStatus.VALID)
                .chainStatus(TicketChainStatus.WEB2_ONLY)
                .tokenId(item.getTokenId())
                .qrSecretHash(null)
                .qrSecretVersion(1)
                .currentResaleListingId(null);

        if (eventMetadata != null) {
            builder.eventName(eventMetadata.getEventName());

            if (showtime != null) {
                builder.showtimeId(showtime.getShowtimeId())
                        .eventStartTime(showtime.getStartDatetime())
                        .eventEndTime(showtime.getEndDatetime())
                        .venueName(showtime.getVenue())
                        .venueAddress(showtime.getFullAddress() != null
                                ? showtime.getFullAddress()
                                : showtime.getAddress());
            } else {
                builder.eventStartTime(eventMetadata.getEventStartTime())
                        .eventEndTime(eventMetadata.getEventEndTime())
                        .venueName(eventMetadata.getVenue())
                        .venueAddress(eventMetadata.getAddress());
            }
        }

        return builder.build();
    }
}
