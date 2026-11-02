package com.parkit.parkingsystem;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ParkingService}.
 *
 * <p>Mockito is used to isolate ParkingService from its external dependencies:
 * user input through {@link InputReaderUtil} and database access through
 * {@link ParkingSpotDAO} and {@link TicketDAO}.</p>
 *
 * <p>The tests cover the main vehicle entry and exit scenarios,
 * parking spot availability and error cases.</p>
 */
@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private ParkingService parkingService;

    @Mock
    private InputReaderUtil inputReaderUtil;

    @Mock
    private ParkingSpotDAO parkingSpotDAO;

    @Mock
    private TicketDAO ticketDAO;

    /**
     * Creates a new ParkingService instance before each test
     * using mocked dependencies.
     */
    @BeforeEach
    private void setUpPerTest() {
        parkingService = new ParkingService(
                inputReaderUtil,
                parkingSpotDAO,
                ticketDAO
        );
    }

    /**
     * Verifies the normal vehicle exit process.
     *
     * <p>The ticket is retrieved and updated, then the parking spot
     * is released and made available again.</p>
     */
    @Test
    public void processExitingVehicleTest() throws Exception {

        // Arrange
        when(inputReaderUtil.readVehicleRegistrationNumber())
                .thenReturn("ABCDEF");

        ParkingSpot parkingSpot =
                new ParkingSpot(1, ParkingType.CAR, false);

        Ticket ticket = new Ticket();
        ticket.setInTime(
                new Date(System.currentTimeMillis() - (60 * 60 * 1000L))
        );
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("ABCDEF");

        when(ticketDAO.getTicket("ABCDEF"))
                .thenReturn(ticket);

        when(ticketDAO.getNbTicket("ABCDEF"))
                .thenReturn(1);

        when(ticketDAO.updateTicket(any(Ticket.class)))
                .thenReturn(true);

        when(parkingSpotDAO.updateParking(any(ParkingSpot.class)))
                .thenReturn(true);

        // Act
        parkingService.processExitingVehicle();

        // Assert
        verify(ticketDAO).getNbTicket("ABCDEF");
        verify(ticketDAO).updateTicket(any(Ticket.class));
        verify(parkingSpotDAO).updateParking(any(ParkingSpot.class));
    }

    /**
     * Verifies the normal vehicle entry process.
     *
     * <p>An available parking spot must be allocated and a new ticket
     * must be saved for the vehicle.</p>
     */
    @Test
    public void testProcessIncomingVehicle() throws Exception {

        // Arrange
        when(inputReaderUtil.readSelection())
                .thenReturn(1);

        when(inputReaderUtil.readVehicleRegistrationNumber())
                .thenReturn("ABCDEF");

        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR))
                .thenReturn(1);

        // Simulates a recurring user.
        when(ticketDAO.getNbTicket("ABCDEF"))
                .thenReturn(1);

        // Act
        parkingService.processIncomingVehicle();

        // Assert
        verify(parkingSpotDAO)
                .updateParking(any(ParkingSpot.class));

        verify(ticketDAO)
                .saveTicket(any(Ticket.class));

        verify(ticketDAO)
                .getNbTicket("ABCDEF");
    }

    /**
     * Verifies that the parking spot is not released when
     * the ticket update fails during the vehicle exit process.
     */
    @Test
    public void processExitingVehicleTestUnableUpdate() throws Exception {

        // Arrange
        when(inputReaderUtil.readVehicleRegistrationNumber())
                .thenReturn("ABCDEF");

        ParkingSpot parkingSpot =
                new ParkingSpot(1, ParkingType.CAR, false);

        Ticket ticket = new Ticket();
        ticket.setInTime(
                new Date(System.currentTimeMillis() - (60 * 60 * 1000L))
        );
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("ABCDEF");

        when(ticketDAO.getTicket("ABCDEF"))
                .thenReturn(ticket);

        when(ticketDAO.getNbTicket("ABCDEF"))
                .thenReturn(1);

        when(ticketDAO.updateTicket(any(Ticket.class)))
                .thenReturn(false);

        // Act
        parkingService.processExitingVehicle();

        // Assert
        verify(ticketDAO)
                .updateTicket(any(Ticket.class));

        verify(parkingSpotDAO, never())
                .updateParking(any(ParkingSpot.class));
    }

    /**
     * Verifies that an available parking spot is correctly returned
     * when a valid vehicle type is selected.
     */
    @Test
    public void testGetNextParkingNumberIfAvailable() {

        // Arrange
        when(inputReaderUtil.readSelection())
                .thenReturn(1);

        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR))
                .thenReturn(1);

        // Act
        ParkingSpot parkingSpot =
                parkingService.getNextParkingNumberIfAvailable();

        // Assert
        assertNotNull(parkingSpot);
        assertEquals(1, parkingSpot.getId());
        assertEquals(ParkingType.CAR, parkingSpot.getParkingType());
        assertTrue(parkingSpot.isAvailable());
    }

    /**
     * Verifies that no parking spot is returned when
     * no parking space is available.
     */
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() {

        // Arrange
        when(inputReaderUtil.readSelection())
                .thenReturn(1);

        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR))
                .thenReturn(0);

        // Act
        ParkingSpot parkingSpot =
                parkingService.getNextParkingNumberIfAvailable();

        // Assert
        assertNull(parkingSpot);
    }

    /**
     * Verifies that an invalid vehicle type selection does not
     * trigger a parking spot search and returns no parking spot.
     */
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() {

        // Arrange
        when(inputReaderUtil.readSelection())
                .thenReturn(3);

        // Act
        ParkingSpot parkingSpot =
                parkingService.getNextParkingNumberIfAvailable();

        // Assert
        assertNull(parkingSpot);

        verify(parkingSpotDAO, never())
                .getNextAvailableSlot(any(ParkingType.class));
    }
}