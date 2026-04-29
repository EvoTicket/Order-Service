package com.capstone.orderservice.service;

import com.capstone.orderservice.client.EventDetailInternalResponse;
import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.repository.TicketAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketAssetServiceTest {
    @Mock
    private TicketAssetRepository ticketAssetRepository;

    @Mock
    private InventoryFeignClient inventoryFeignClient;

    @InjectMocks
    private TicketAssetService ticketAssetService;

    @Test
    void issueTicketsForConfirmedOrderCreatesOneAssetPerOrderItemAndCachesMetadata() {
        Order order = confirmedPrimaryOrder();
        order.addOrderItem(orderItem(101L, 11L));
        order.addOrderItem(orderItem(102L, 11L));

        EventDetailInternalResponse metadata = EventDetailInternalResponse.builder()
                .eventName("Concert")
                .showtime(EventDetailInternalResponse.ShowtimeDetail.builder()
                        .showtimeId(88L)
                        .startDatetime(LocalDateTime.of(2026, 5, 1, 19, 0))
                        .endDatetime(LocalDateTime.of(2026, 5, 1, 21, 0))
                        .venue("Main Hall")
                        .fullAddress("123 Street")
                        .build())
                .build();
        when(inventoryFeignClient.getEventDetailsByTicketTypeId(11L))
                .thenReturn(BaseResponse.ok(metadata));

        ticketAssetService.issueTicketsForConfirmedOrder(order);

        ArgumentCaptor<TicketAsset> assetCaptor = ArgumentCaptor.forClass(TicketAsset.class);
        verify(ticketAssetRepository, times(2)).save(assetCaptor.capture());
        verify(inventoryFeignClient, times(1)).getEventDetailsByTicketTypeId(11L);

        TicketAsset firstAsset = assetCaptor.getAllValues().getFirst();
        assertThat(firstAsset.getOriginalOrderId()).isEqualTo(order.getId());
        assertThat(firstAsset.getOriginalOrderCode()).isEqualTo(order.getOrderCode());
        assertThat(firstAsset.getCurrentOwnerId()).isEqualTo(order.getUserId());
        assertThat(firstAsset.getEventName()).isEqualTo("Concert");
        assertThat(firstAsset.getShowtimeId()).isEqualTo(88L);
        assertThat(firstAsset.getVenueName()).isEqualTo("Main Hall");
        assertThat(firstAsset.getVenueAddress()).isEqualTo("123 Street");
    }

    @Test
    void issueTicketsForConfirmedOrderStillCreatesAssetWhenMetadataFetchFails() {
        Order order = confirmedPrimaryOrder();
        order.addOrderItem(orderItem(101L, 11L));
        when(inventoryFeignClient.getEventDetailsByTicketTypeId(11L))
                .thenThrow(new RuntimeException("inventory unavailable"));

        ticketAssetService.issueTicketsForConfirmedOrder(order);

        ArgumentCaptor<TicketAsset> assetCaptor = ArgumentCaptor.forClass(TicketAsset.class);
        verify(ticketAssetRepository).save(assetCaptor.capture());

        TicketAsset asset = assetCaptor.getValue();
        assertThat(asset.getEventId()).isEqualTo(order.getEventId());
        assertThat(asset.getTicketTypeId()).isEqualTo(11L);
        assertThat(asset.getEventName()).isNull();
        assertThat(asset.getShowtimeId()).isNull();
        assertThat(asset.getEventStartTime()).isNull();
        assertThat(asset.getEventEndTime()).isNull();
        assertThat(asset.getVenueName()).isNull();
        assertThat(asset.getVenueAddress()).isNull();
    }

    @Test
    void issueTicketsForConfirmedOrderSkipsAlreadyIssuedOrderItems() {
        Order order = confirmedPrimaryOrder();
        order.addOrderItem(orderItem(101L, 11L));
        when(ticketAssetRepository.existsByOrderItem_Id(101L)).thenReturn(true);

        ticketAssetService.issueTicketsForConfirmedOrder(order);

        verify(ticketAssetRepository, never()).save(any());
        verifyNoInteractions(inventoryFeignClient);
    }

    private Order confirmedPrimaryOrder() {
        return Order.builder()
                .id(1L)
                .orderCode("300426123456")
                .userId(10L)
                .eventId(99L)
                .orderType(OrderType.PRIMARY)
                .orderStatus(OrderStatus.CONFIRMED)
                .build();
    }

    private OrderItem orderItem(Long id, Long ticketTypeId) {
        return OrderItem.builder()
                .id(id)
                .ticketTypeId(ticketTypeId)
                .ticketTypeName("VIP")
                .ticketCode("TICKET-" + id)
                .unitPrice(BigDecimal.valueOf(100000))
                .build();
    }
}
