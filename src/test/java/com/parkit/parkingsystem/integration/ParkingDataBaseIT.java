package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.parkit.parkingsystem.model.ParkingSpot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    private static void setUp() throws Exception {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;

        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;

        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    private static void tearDown() {

    }

    @Test
    public void testParkingACar() {

        ParkingService parkingService =
                new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        parkingService.processIncomingVehicle();

        // Vérifie qu'un ticket a bien été enregistré en base
        Ticket ticket = ticketDAO.getTicket("ABCDEF");

        assertNotNull(ticket);
        assertEquals("ABCDEF", ticket.getVehicleRegNumber());
        assertNotNull(ticket.getInTime());
        assertNull(ticket.getOutTime());

        // La place 1 est maintenant occupée :
        // la prochaine place CAR disponible doit donc être la 2
        assertEquals(
                2,
                parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)
        );
    }

    @Test
    public void testParkingLotExit() throws Exception {

        testParkingACar();

        ParkingService parkingService =
                new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        parkingService.processExitingVehicle();

        // Vérifie que le prix et l'heure de sortie
        // ont bien été enregistrés dans la base
        try (
                Connection connection = dataBaseTestConfig.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT PRICE, OUT_TIME " +
                                "FROM ticket " +
                                "WHERE VEHICLE_REG_NUMBER = ? " +
                                "ORDER BY ID DESC LIMIT 1"
                )
        ) {
            statement.setString(1, "ABCDEF");

            ResultSet resultSet = statement.executeQuery();

            assertTrue(resultSet.next());

            // Entrée puis sortie immédiate :
            // moins de 30 minutes => stationnement gratuit
            assertEquals(
                    0.0,
                    resultSet.getDouble("PRICE"),
                    0.001
            );

            assertNotNull(
                    resultSet.getTimestamp("OUT_TIME")
            );
        }
    }
    @Test
    public void testParkingLotExitRecurringUser() throws Exception {

        // Arrange : création d'un ancien ticket pour simuler un utilisateur récurrent
        ParkingSpot parkingSpot =
                new ParkingSpot(1, ParkingType.CAR, true);

        Date previousOutTime =
                new Date(System.currentTimeMillis() - (2 * 60 * 60 * 1000L));

        Date previousInTime =
                new Date(previousOutTime.getTime() - (60 * 60 * 1000L));

        Ticket previousTicket = new Ticket();
        previousTicket.setParkingSpot(parkingSpot);
        previousTicket.setVehicleRegNumber("ABCDEF");
        previousTicket.setPrice(Fare.CAR_RATE_PER_HOUR);
        previousTicket.setInTime(previousInTime);
        previousTicket.setOutTime(previousOutTime);

        ticketDAO.saveTicket(previousTicket);

        ParkingService parkingService =
                new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // Deuxième passage du même véhicule
        parkingService.processIncomingVehicle();

        assertEquals(2, ticketDAO.getNbTicket("ABCDEF"));

        /*
         * On simule une heure de stationnement.
         * Sinon le ticket serait inférieur à 30 minutes et donc gratuit.
         */
        try (
                Connection connection = dataBaseTestConfig.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE ticket " +
                                "SET IN_TIME = ? " +
                                "WHERE VEHICLE_REG_NUMBER = ? " +
                                "AND OUT_TIME IS NULL"
                )
        ) {
            statement.setTimestamp(
                    1,
                    new Timestamp(
                            System.currentTimeMillis() - (60 * 60 * 1000L)
                    )
            );

            statement.setString(2, "ABCDEF");

            assertEquals(1, statement.executeUpdate());
        }

        // Act
        parkingService.processExitingVehicle();

        // Assert : vérification du tarif avec remise de 5 %
        try (
                Connection connection = dataBaseTestConfig.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT PRICE, OUT_TIME " +
                                "FROM ticket " +
                                "WHERE VEHICLE_REG_NUMBER = ? " +
                                "ORDER BY ID DESC LIMIT 1"
                )
        ) {
            statement.setString(1, "ABCDEF");

            ResultSet resultSet = statement.executeQuery();

            assertTrue(resultSet.next());

            double expectedPrice =
                    Fare.CAR_RATE_PER_HOUR * 0.95;

            assertEquals(
                    expectedPrice,
                    resultSet.getDouble("PRICE"),
                    0.01
            );

            assertNotNull(
                    resultSet.getTimestamp("OUT_TIME")
            );
        }
    }

}