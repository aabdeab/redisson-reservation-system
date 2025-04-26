package com.Aabdane;

import com.Aabdane.entity.Ticket;
import com.Aabdane.repository.TicketRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);
    private final TicketRepository ticketRepository;
    private final RedissonClient redissonClient;

    public ReservationService(TicketRepository ticketRepository, RedissonClient redissonClient) {
        this.ticketRepository = ticketRepository;
        this.redissonClient = redissonClient;
    }

    @Transactional
    public String reserveTicket(Long ticketId) {
        String lockKey = "ticket-lock-" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        logger.info("Attempting to acquire lock for ticket: {}", ticketId);

        try {
            // Wait for up to 5 seconds to acquire the lock, hold it for max 30 seconds
            boolean isLocked = lock.tryLock(5, 30, TimeUnit.SECONDS);

            if (!isLocked) {
                logger.warn("Failed to acquire lock for ticket: {}", ticketId);
                return "Could not acquire lock, please try again later";
            }

            logger.info("Lock acquired for ticket: {}", ticketId);

            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

            if (ticket.isReserved()) {
                logger.info("Ticket {} is already reserved", ticketId);
                return "Ticket already reserved";
            }

            // Simulate some processing time
            try {
                Thread.sleep(200); // Simulates DB or other operations
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            ticket.setReserved(true);
            ticketRepository.save(ticket);
            logger.info("Successfully reserved ticket: {}", ticketId);
            return "Ticket reserved successfully";

        } catch (Exception e) {
            logger.error("Error while reserving ticket: {}", ticketId, e);
            return "Error: " + e.getMessage();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                logger.info("Lock released for ticket: {}", ticketId);
            }
        }
    }

    @Transactional
    public String releaseTicket(Long ticketId) {
        String lockKey = "ticket-lock-" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!isLocked) {
                return "Could not acquire lock to release ticket";
            }

            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

            if (!ticket.isReserved()) {
                return "Ticket is not currently reserved";
            }

            ticket.setReserved(false);
            ticketRepository.save(ticket);
            return "Ticket released successfully";
        } catch (Exception e) {
            logger.error("Error while releasing ticket: {}", ticketId, e);
            return "Error: " + e.getMessage();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}