package com.capstone.orderservice.service;

import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.dto.response.MyTicketsResponse;
import com.capstone.orderservice.dto.response.ResaleEligibilityResponse;
import com.capstone.orderservice.dto.response.TicketAssetResponse;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.repository.TicketAssetRepository;
import com.capstone.orderservice.security.JwtUtil;
import com.capstone.orderservice.security.TokenMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketAssetReadServiceTest {
    @Mock
    private TicketAssetRepository ticketAssetRepository;

    @Mock
    private InventoryFeignClient inventoryFeignClient;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private TicketAssetService ticketAssetService;

    @Test
    void getMyTicketsQueriesByCurrentOwnerAndBuildsSummary() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        TicketAsset active = ticketAsset(1L, 10L, TicketAccessStatus.VALID, TicketChainStatus.WEB2_ONLY);
        active.setEventEndTime(LocalDateTime.now().plusDays(1));

        TicketAsset used = ticketAsset(2L, 10L, TicketAccessStatus.USED, TicketChainStatus.WEB2_ONLY);
        used.setUsedAt(LocalDateTime.now());

        TicketAsset onSale = ticketAsset(3L, 10L, TicketAccessStatus.LOCKED_RESALE, TicketChainStatus.MINT_PENDING);
        onSale.setCurrentResaleListingId(99L);

        when(ticketAssetRepository.findByCurrentOwnerId(10L)).thenReturn(List.of(active, used, onSale));

        MyTicketsResponse response = ticketAssetService.getMyTickets();

        assertThat(response.getTotalTickets()).isEqualTo(3);
        assertThat(response.getActiveCount()).isEqualTo(1);
        assertThat(response.getUsedCount()).isEqualTo(1);
        assertThat(response.getMintPendingCount()).isEqualTo(1);
        assertThat(response.getOnSaleCount()).isEqualTo(1);
        assertThat(response.getTickets()).hasSize(3);
        assertThat(response.getTickets().getFirst().getQrAvailable()).isTrue();
        assertThat(response.getTickets().getFirst().getCanResell()).isTrue();
        verify(ticketAssetRepository).findByCurrentOwnerId(10L);
    }

    @Test
    void getMyTicketDetailUsesCurrentOwnerLookup() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        TicketAsset asset = ticketAsset(1L, 10L, TicketAccessStatus.VALID, TicketChainStatus.WEB2_ONLY);
        when(ticketAssetRepository.findByIdAndCurrentOwnerId(1L, 10L)).thenReturn(Optional.of(asset));

        TicketAssetResponse response = ticketAssetService.getMyTicketDetail(1L);

        assertThat(response.getTicketAssetId()).isEqualTo(1L);
        assertThat(response.getAssetCode()).isEqualTo("ASSET-1");
        verify(ticketAssetRepository).findByIdAndCurrentOwnerId(1L, 10L);
    }

    @Test
    void getResaleEligibilityReturnsNotOwnerDecisionAndPriceCap() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        TicketAsset asset = ticketAsset(1L, 20L, TicketAccessStatus.VALID, TicketChainStatus.WEB2_ONLY);
        when(ticketAssetRepository.findById(1L)).thenReturn(Optional.of(asset));

        ResaleEligibilityResponse response = ticketAssetService.getResaleEligibility(1L);

        assertThat(response.getCanResell()).isFalse();
        assertThat(response.getReasonCode()).isEqualTo("NOT_OWNER");
        assertThat(response.getPriceCap()).isEqualByComparingTo("110000.00");
        assertThat(response.getPlatformFeeRate()).isEqualByComparingTo("0.02");
        assertThat(response.getOrganizerRoyaltyRate()).isEqualByComparingTo("0");
    }

    private TicketAsset ticketAsset(
            Long id,
            Long ownerId,
            TicketAccessStatus accessStatus,
            TicketChainStatus chainStatus
    ) {
        return TicketAsset.builder()
                .id(id)
                .assetCode("ASSET-" + id)
                .originalOrderId(100L)
                .originalOrderCode("300426123456")
                .eventId(200L)
                .eventName("Concert")
                .ticketTypeId(300L)
                .ticketTypeName("VIP")
                .ticketCode("TICKET-" + id)
                .originalPrice(new BigDecimal("100000.00"))
                .originalBuyerId(10L)
                .currentOwnerId(ownerId)
                .accessStatus(accessStatus)
                .chainStatus(chainStatus)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
