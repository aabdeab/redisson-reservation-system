
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.Aabdane.ReservationService;
import com.Aabdane.entity.Ticket;
import com.Aabdane.repository.TicketRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
public class RedlockIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        String redisAddress = String.format("redis://%s:%d",
                redis.getHost(),
                redis.getMappedPort(6379));
        registry.add("redisson.config.singleServerConfig.address", () -> redisAddress);
    }

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();

        // Create a test ticket
        Ticket ticket = new Ticket();
        ticket.setReserved(false);
        ticketRepository.save(ticket);
    }

    @Test
    void testConcurrentReservations() throws Exception {
        // Get the ticket ID
        Long ticketId = ticketRepository.findAll().get(0).getId();

        // Number of concurrent reservation attempts
        int numThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch readyLatch = new CountDownLatch(numThreads); // Ensures all threads are ready
        CountDownLatch startLatch = new CountDownLatch(1); // Controls when threads start
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<String>> futures = new ArrayList<>();

        // Create concurrent reservation tasks
        for (int i = 0; i < numThreads; i++) {
            futures.add(executorService.submit(() -> {
                readyLatch.countDown(); // Signal that this thread is ready
                startLatch.await(); // Wait for the signal to start
                String result = reservationService.reserveTicket(ticketId);
                if (result.equals("Ticket reserved successfully")) {
                    successCount.incrementAndGet();
                }
                return result;
            }));
        }

        // Wait until all threads are ready
        readyLatch.await();
        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all tasks to complete and collect results
        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get());
        }

        executorService.shutdown();

        // Verify outcomes
        assertEquals(1, successCount.get(), "Only one thread should successfully reserve the ticket");

        // Validate the ticket state in database
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(true, ticket.isReserved(), "Ticket should be marked as reserved");

        // Count the different types of responses
        long successfulReservations = results.stream()
                .filter(r -> r.equals("Ticket reserved successfully"))
                .count();
        long alreadyReserved = results.stream()
                .filter(r -> r.equals("Ticket already reserved"))
                .count();

        System.out.println("Successful reservations: " + successfulReservations);
        System.out.println("Already reserved responses: " + alreadyReserved);
        System.out.println("Other responses: " +
                (results.size() - successfulReservations - alreadyReserved));
    }
}