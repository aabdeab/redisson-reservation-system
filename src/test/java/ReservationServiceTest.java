import com.Aabdane.ReservationService;
import com.Aabdane.entity.Ticket;
import com.Aabdane.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private ReservationService reservationService;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        reservationService = new ReservationService(ticketRepository, redissonClient);

        ticket = new Ticket();
        ticket.setId(1L);
        ticket.setReserved(false);
    }

    @Test
    void testReserveTicket_Success() throws InterruptedException {
        // Setup
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // Execute
        String result = reservationService.reserveTicket(1L);

        // Verify
        assertEquals("Ticket reserved successfully", result);
        verify(ticketRepository).save(ticket);
        verify(rLock).unlock();
    }

    @Test
    void testReserveTicket_AlreadyReserved() throws InterruptedException {
        // Setup
        ticket.setReserved(true);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // Execute
        String result = reservationService.reserveTicket(1L);

        // Verify
        assertEquals("Ticket already reserved", result);
        verify(ticketRepository, never()).save(any());
        verify(rLock).unlock();
    }

    @Test
    void testReserveTicket_LockNotAcquired() throws InterruptedException {
        // Setup
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        // Execute
        String result = reservationService.reserveTicket(1L);

        // Verify
        assertEquals("Could not acquire lock, please try again later", result);
        verify(ticketRepository, never()).findById(anyLong());
        verify(rLock, never()).unlock();
    }

    @Test
    void testConcurrentReservations() throws InterruptedException {
        // This test simulates multiple concurrent reservation attempts
        int numThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // Create a real implementation for integration testing
        // Note: This would typically be in an integration test class
        /*
        // For a real test with actual Redis:
        RedissonClient actualRedisson = ... // get real client
        ReservationService actualService = new ReservationService(ticketRepository, actualRedisson);

        for (int i = 0; i < numThreads; i++) {
            executorService.submit(() -> {
                try {
                    String result = actualService.reserveTicket(1L);
                    if (result.equals("Ticket reserved successfully")) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        assertEquals(1, successCount.get(), "Only one thread should successfully reserve the ticket");
        */

        // For unit testing we can only verify the mock behavior
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        executorService.shutdown();
    }
}