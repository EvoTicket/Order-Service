package com.capstone.orderservice.scheduler;

import com.capstone.orderservice.client.WorkerClient;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.repository.TicketAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInSyncScheduler {

    private final TicketAssetRepository ticketAssetRepository;
    private final WorkerClient workerClient;

    @Scheduled(cron = "${evoticket.checkin-sync.cron:0 59 23 * * ?}")
    @Transactional
    public void syncCheckedInTicketsToBlockchain() {
        log.info("Starting daily batch check-in sync to blockchain...");

        List<TicketAsset> ticketsToSync = ticketAssetRepository.findByAccessStatusAndTokenIdIsNotNullAndChainStatusIn(
                TicketAccessStatus.CHECKED_IN,
                List.of(TicketChainStatus.MINTED, TicketChainStatus.TRANSFERRED, TicketChainStatus.CHECKIN_FAILED)
        );

        if (ticketsToSync.isEmpty()) {
            log.info("No checked-in tickets found requiring blockchain synchronization.");
            return;
        }

        log.info("Found {} ticket(s) to sync to blockchain. Enqueuing batch...", ticketsToSync.size());

        List<Map<String, Object>> ticketsPayload = new ArrayList<>();
        for (TicketAsset ticket : ticketsToSync) {
            // Update chain status to pending
            ticket.setChainStatus(TicketChainStatus.CHECKIN_PENDING);
            ticketAssetRepository.save(ticket);

            // Determine check-in time formatted in ISO-8601
            Instant checkinInstant = ticket.getUsedAt() != null
                    ? ticket.getUsedAt().atZone(ZoneId.systemDefault()).toInstant()
                    : Instant.now();
            String checkinTimeStr = DateTimeFormatter.ISO_INSTANT.format(checkinInstant);

            Map<String, Object> ticketMap = new HashMap<>();
            ticketMap.put("ticketCode", ticket.getTicketCode());
            ticketMap.put("tokenId", ticket.getTokenId());
            ticketMap.put("eventId", String.valueOf(ticket.getEventId()));
            ticketMap.put("checkinTime", checkinTimeStr);

            ticketsPayload.add(ticketMap);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("tickets", ticketsPayload);

        try {
            workerClient.batchUpdateCheckInStatus(payload);
            log.info("Successfully enqueued batch check-in sync request for {} tickets.", ticketsToSync.size());
        } catch (Exception e) {
            log.error("Failed to call web3 worker service for batch check-in sync", e);
            // Revert status so it can be retried
            for (TicketAsset ticket : ticketsToSync) {
                ticket.setChainStatus(TicketChainStatus.CHECKIN_FAILED);
                ticketAssetRepository.save(ticket);
            }
        }
    }
}
