import com.ticket.domain.*;
import com.ticket.exception.InvalidSeatException;
import com.ticket.service.ReportingEngine;
import com.ticket.service.StorageService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class Main {
    private static final Path STORAGE_PATH = Paths.get("reservations.json");
    private static final StorageService storageService = new StorageService();
    private static final ReportingEngine reportingEngine = new ReportingEngine();

    // In-Memory state management as specified by Phase 1 foundations
    private static final List<Venue> venues = new ArrayList<>();
    private static final List<Event> events = new ArrayList<>();
    private static final List<Seat> seats = new ArrayList<>();
    private static final List<Reservation> reservations = new ArrayList<>();
    private static final List<ReservationSeat> reservationSeats = new ArrayList<>();

    public static void main(String[] args) {
        bootstrapInitialData();
        loadSavedDataQuietly();

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("=================================================");
        System.out.println("   WELCOME TO THE JAVA TICKETING SYSTEM (CLI)    ");
        System.out.println("=================================================");

        while (running) {
            System.out.println("\n--- MAIN MENU ---");
            System.out.println("1. Browse Available Events & Venues");
            System.out.println("2. View Seat Availability & Status");
            System.out.println("3. Place a Temporary Seat Hold");
            System.out.println("4. Generate Venue Systems Report (Streams)");
            System.out.println("5. Save Current Progress to JSON");
            System.out.println("6. Exit Application");
            System.out.print("Select an option (1-6): ");

            String choice = scanner.nextLine();
            try {
                switch (choice) {
                    case "1" -> handleBrowseEvents();
                    case "2" -> handleViewSeats();
                    case "3" -> handlePlaceHold(scanner);
                    case "4" -> handleGenerateReport();
                    case "5" -> handleSaveData();
                    case "6" -> {
                        System.out.println("Saving data and closing system context safely... Goodbye!");
                        handleSaveData();
                        running = false;
                    }
                    default -> System.out.println("⚠️ Invalid input. Please select a valid numerical option between 1 and 6.");
                }
            } catch (InvalidSeatException ex) {
                System.out.println("❌ DOMAIN ERROR: " + ex.getMessage());
            } catch (Exception ex) {
                System.out.println("❌ CRITICAL UNEXPECTED ERROR: " + ex.getMessage());
            }
        }
        scanner.close();
    }

    private static void bootstrapInitialData() {
        Venue venue = new Venue("V-01", "Metropolitan Grand Opera", "742 Evergreen Terrace", ZoneId.of("America/New_York"));
        venues.add(venue);

        ZonedDateTime eventStart = ZonedDateTime.of(2026, 12, 15, 19, 30, 0, 0, venue.timezone());
        Event event = new Event("E-01", venue.id(), "The Phantom of the Opera Grand Premiere", eventStart, eventStart.plusHours(3), EventStatus.ACTIVE);
        events.add(event);

        // Bootstrap explicit seats array matrix ordered by requirement sorting parameters
        seats.add(new Seat("S-01", venue.id(), "Orchestra", "Row-A", 1, Map.of("premium", true)));
        seats.add(new Seat("S-02", venue.id(), "Orchestra", "Row-A", 2, Map.of("premium", true)));
        seats.add(new Seat("S-03", venue.id(), "Balcony", "Row-B", 1, Map.of("accessible", false)));
        seats.add(new Seat("S-04", venue.id(), "Balcony", "Row-B", 2, Map.of("accessible", false)));
    }

    private static void loadSavedDataQuietly() {
        try {
            List<Reservation> loaded = storageService.loadReservations(STORAGE_PATH);
            if (!loaded.isEmpty()) {
                reservations.addAll(loaded);
                System.out.println("💾 [System Data Restore] Loaded " + loaded.size() + " active transaction holds from local storage registry file.");
            }
        } catch (IOException e) {
            System.out.println("⚠️ Warning: Data persistence layer unavailable. Starting cleanly with volatile parameters.");
        }
    }

    private static void handleBrowseEvents() {
        System.out.println("\n--- CURRENT ACTIVATED EVENTS & LOCATIONS ---");
        for (Event event : events) {
            Venue venue = venues.stream().filter(v -> v.id().equals(event.venueId())).findFirst().orElse(null);
            String venueName = (venue != null) ? venue.name() : "Unknown Venue Workspace";
            System.out.printf("Event Code: [%s] -> \"%s\" at %s (Starts: %s)\n",
                    event.id(), event.title(), venueName, event.startAt());
        }
    }

    private static void handleViewSeats() {
        System.out.println("\n--- VENUE SEATING DESIGN SHEETS ---");
        // Sort dynamically using the standard record Comparable sorting contract implemented earlier
        List<Seat> sortedSeats = new ArrayList<>(seats);
        Collections.sort(sortedSeats);

        for (Seat seat : sortedSeats) {
            boolean isHeld = reservationSeats.stream().anyMatch(rs -> rs.seatId().equals(seat.id()));
            String availability = isHeld ? "[OCCUPIED/HOLD]" : "[AVAILABLE]";
            System.out.printf("  Seat ID: %s | Section: %-10s | Row: %-6s | Seat No: %-3d | %s\n",
                    seat.id(), seat.section(), seat.row(), seat.number(), availability);
        }
    }

    private static void handlePlaceHold(Scanner scanner) {
        System.out.println("\n--- RECORD NEW TEMPORARY SEAT RESERVATION HOLD ---");
        System.out.print("Enter Target Event Code (e.g. E-01): ");
        String eventId = scanner.nextLine();

        System.out.print("Enter Target Seat ID to hold (e.g. S-01): ");
        String seatId = scanner.nextLine();

        // Exact Fail-Fast processing rules validation required by the micro-prep checklist
        Seat targetSeat = seats.stream().filter(s -> s.id().equalsIgnoreCase(seatId)).findFirst()
                .orElseThrow(() -> new InvalidSeatException("Verification error. Seat code reference does not exist in internal records structure."));

        boolean alreadyHeld = reservationSeats.stream().anyMatch(rs -> rs.seatId().equals(targetSeat.id()));
        if (alreadyHeld) {
            throw new InvalidSeatException("Transaction conflict rule broken. The requested seat is currently blocked under a valid hold transaction.");
        }

        System.out.print("Enter Customer Contact Email address: ");
        String email = scanner.nextLine();

        String resId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plusSeconds(300); // 5-minute temporary standard timeout rule metric

        Reservation reservation = new Reservation(resId, eventId, email, ReservationStatus.HOLD, createdAt, null, expiresAt);
        ReservationSeat reservationSeat = new ReservationSeat(resId, targetSeat.id(), Money.of(125.50), "NONE");

        reservations.add(reservation);
        reservationSeats.add(reservationSeat);

        System.out.printf("🎉 Success! Temporary reservation %s registered. Hold active for 5 minutes.\n", resId);
    }

    private static void handleGenerateReport() {
        System.out.println("\n--- STREAM COMPLIANT AUDITING SYSTEMS REPORT ---");
        Map<String, Long> resMap = reportingEngine.getReservationsCountPerEvent(reservations);

        if (resMap.isEmpty()) {
            System.out.println("No transactional telemetry datasets available to compute stream operations graphs.");
            return;
        }

        resMap.forEach((eventId, count) ->
                System.out.printf("  Event Tracking Code [%s] currently records: %d secure platform holds.\n", eventId, count));
    }

    private static void handleSaveData() {
        try {
            storageService.saveReservations(reservations, STORAGE_PATH);
            System.out.println("✅ Hardened state engine data written successfully to file storage asset mapping: (" + STORAGE_PATH.toAbsolutePath() + ")");
        } catch (IOException e) {
            System.out.println("❌ Core System storage error. Failed to commit changes to disk: " + e.getMessage());
        }
    }
}
