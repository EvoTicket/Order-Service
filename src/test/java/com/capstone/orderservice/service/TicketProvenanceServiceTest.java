package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.response.TicketProvenanceResponse;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.entity.TicketProvenance;
import com.capstone.orderservice.enums.ProvenanceActionType;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.repository.TicketAssetRepository;
import com.capstone.orderservice.repository.TicketProvenanceRepository;
import com.capstone.orderservice.security.JwtUtil;
import com.capstone.orderservice.security.TokenMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketProvenanceServiceTest {
    @Mock
    private TicketProvenanceRepository ticketProvenanceRepository;

    @Mock
    private TicketAssetRepository ticketAssetRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private TicketProvenanceService ticketProvenanceService;

    @Test
    void recordPrimaryIssuedCreatesProvenanceWhenMissing() {
        TicketAsset asset = ticketAsset();
        when(ticketProvenanceRepository.existsByTicketAssetIdAndActionTypeAndOrderCode(
                1L,
                ProvenanceActionType.PRIMARY_ISSUED,
                "300426123456"
        )).thenReturn(false);

        ticketProvenanceService.recordPrimaryIssued(asset);

        ArgumentCaptor<TicketProvenance> provenanceCaptor = ArgumentCaptor.forClass(TicketProvenance.class);
        verify(ticketProvenanceRepository).save(provenanceCaptor.capture());

        TicketProvenance provenance = provenanceCaptor.getValue();
        assertThat(provenance.getTicketAssetId()).isEqualTo(1L);
        assertThat(provenance.getFromUserId()).isNull();
        assertThat(provenance.getToUserId()).isEqualTo(10L);
        assertThat(provenance.getActionType()).isEqualTo(ProvenanceActionType.PRIMARY_ISSUED);
        assertThat(provenance.getOrderCode()).isEqualTo("300426123456");
        assertThat(provenance.getPrice()).isEqualByComparingTo("100000.00");
        assertThat(provenance.getChainStatus()).isEqualTo("WEB2_ONLY");
    }

    @Test
    void recordPrimaryIssuedSkipsDuplicate() {
        TicketAsset asset = ticketAsset();
        when(ticketProvenanceRepository.existsByTicketAssetIdAndActionTypeAndOrderCode(
                1L,
                ProvenanceActionType.PRIMARY_ISSUED,
                "300426123456"
        )).thenReturn(true);

        ticketProvenanceService.recordPrimaryIssued(asset);

        verify(ticketProvenanceRepository, never()).save(any());
    }

    @Test
    void getProvenanceForMyTicketChecksCurrentOwnerAndReturnsAscendingRecords() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        when(ticketAssetRepository.findByIdAndCurrentOwnerId(1L, 10L)).thenReturn(Optional.of(ticketAsset()));

        TicketProvenance provenance = TicketProvenance.builder()
                .id(11L)
                .ticketAssetId(1L)
                .toUserId(10L)
                .actionType(ProvenanceActionType.PRIMARY_ISSUED)
                .orderCode("300426123456")
                .price(new BigDecimal("100000.00"))
                .chainStatus("WEB2_ONLY")
                .description("Ticket issued after primary order confirmation")
                .createdAt(LocalDateTime.now())
                .build();
        when(ticketProvenanceRepository.findByTicketAssetIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(provenance));

        List<TicketProvenanceResponse> response = ticketProvenanceService.getProvenanceForMyTicket(1L);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getId()).isEqualTo(11L);
        assertThat(response.getFirst().getActionType()).isEqualTo(ProvenanceActionType.PRIMARY_ISSUED);
        verify(ticketAssetRepository).findByIdAndCurrentOwnerId(1L, 10L);
    }

    private TicketAsset ticketAsset() {
        return TicketAsset.builder()
                .id(1L)
                .assetCode("ASSET-1")
                .originalOrderId(100L)
                .originalOrderCode("300426123456")
                .eventId(200L)
                .ticketTypeId(300L)
                .originalPrice(new BigDecimal("100000.00"))
                .originalBuyerId(10L)
                .currentOwnerId(10L)
                .accessStatus(TicketAccessStatus.VALID)
                .chainStatus(TicketChainStatus.WEB2_ONLY)
                .build();
    }
}
