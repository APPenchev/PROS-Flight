package com.example.pros.components;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FlightControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FlightRepository flightRepository;

    @BeforeEach
    public void setUp() {
        // Clean database before each test
        flightRepository.deleteAll();

        // Setup common test data
        List<Flight> testFlights = Arrays.asList(
                new Flight(null, "NYC", "LAX", 300),
                new Flight(null, "LAX", "SFO", 100),
                new Flight(null, "NYC", "CHI", 200),
                new Flight(null, "CHI", "LAX", 150),
                new Flight(null, "SFO", "SEA", 120),
                new Flight(null, "NYC", "BOS", 150),
                new Flight(null, "BOS", "SEA", 400)
        );
        flightRepository.saveAll(testFlights);
    }

    @AfterEach
    public void tearDown() {
        flightRepository.deleteAll();
    }

    // 1. Basic route finding tests
    @Test
    public void testFindDirectRoute() {
        RouteRequestDto request = new RouteRequestDto("NYC", "LAX", null);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);
        assertTrue(routes.length > 0);

        // Verify direct route exists
        boolean foundDirectRoute = false;
        for (Route route : routes) {
            if (route.getCities().equals(Arrays.asList("NYC", "LAX")) && route.getTotalPrice() == 300) {
                foundDirectRoute = true;
                break;
            }
        }
        assertTrue(foundDirectRoute, "Direct route from NYC to LAX should exist");
    }

    @Test
    public void testFindMultiHopRoute() {
        RouteRequestDto request = new RouteRequestDto("NYC", "SEA", null);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);
        assertTrue(routes.length > 0);

        // Verify NYC -> LAX -> SFO -> SEA route exists
        boolean foundMultiHopRoute = false;
        for (Route route : routes) {
            if (route.getCities().equals(Arrays.asList("NYC", "LAX", "SFO", "SEA"))) {
                assertEquals(520, route.getTotalPrice()); // 300 + 100 + 120
                foundMultiHopRoute = true;
                break;
            }
        }
        assertTrue(foundMultiHopRoute, "Multi-hop route from NYC to SEA should exist");
    }

    // 2. Edge cases and constraints
    @Test
    public void testMaxFlightsConstraint() {
        // Create a route with maxFlights=1 (only direct flights)
        RouteRequestDto request = new RouteRequestDto("NYC", "SEA", 1);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();

        // Should be empty as there's no direct flight from NYC to SEA
        assertNotNull(routes);
        assertEquals(0, routes.length);

        // Now try with maxFlights=2
        request = new RouteRequestDto("NYC", "SEA", 2);
        response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        routes = response.getBody();

        // Should find the NYC -> BOS -> SEA route
        assertNotNull(routes);
        assertTrue(routes.length > 0);

        // Verify routes have max 2 flights (3 cities)
        for (Route route : routes) {
            assertTrue(route.getCities().size() <= 3, "Route should have at most 3 cities with maxFlights=2");
        }
    }

    @Test
    public void testRouteOrderingByPrice() {
        // Add an expensive direct flight
        Flight expensiveFlight = new Flight(null, "NYC", "SEA", 1000);
        flightRepository.save(expensiveFlight);

        RouteRequestDto request = new RouteRequestDto("NYC", "SEA", null);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);
        assertTrue(routes.length >= 2);

        // Verify routes are ordered by price (cheaper first)
        for (int i = 0; i < routes.length - 1; i++) {
            assertTrue(routes[i].getTotalPrice() <= routes[i+1].getTotalPrice(),
                    "Routes should be ordered by ascending price");
        }
    }

    @Test
    public void testNoRoutesFound() {
        RouteRequestDto request = new RouteRequestDto("NYC", "MOON", null);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);
        assertEquals(0, routes.length, "No routes should be found to non-existent destination");
    }

    @Test
    public void testCyclicalRoutesAvoidance() {
        // Add a cycle
        Flight cycleFlight = new Flight(null, "SEA", "NYC", 250);
        flightRepository.save(cycleFlight);

        RouteRequestDto request = new RouteRequestDto("NYC", "SEA", null);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);

        // Verify no route visits the same city twice
        for (Route route : routes) {
            List<String> cities = route.getCities();
            assertEquals(cities.size(), cities.stream().distinct().count(),
                    "Route should not visit the same city twice");
        }
    }

    // 3. Flight management tests
    @Test
    public void testCreateFlight() {
        Flight newFlight = new Flight(null, "DEN", "PHX", 175);
        ResponseEntity<Flight> response = restTemplate.postForEntity("/api/create", newFlight, Flight.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Flight createdFlight = response.getBody();
        assertNotNull(createdFlight);
        assertNotNull(createdFlight.getId());
        assertEquals("DEN", createdFlight.getSource());
        assertEquals("PHX", createdFlight.getDestination());
        assertEquals(175, createdFlight.getPrice());
    }

    @Test
    public void testCreateInvalidFlight_SameSourceAndDestination() {
        Flight invalidFlight = new Flight(null, "DEN", "DEN", 200);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/create", invalidFlight, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String errorMsg = response.getBody();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("Source and destination cannot be the same"));
    }

    @Test
    public void testCreateInvalidFlight_NegativePrice() {
        Flight invalidFlight = new Flight(null, "DEN", "PHX", -50);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/create", invalidFlight, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String errorMsg = response.getBody();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("Price cannot be negative"));
    }

    @Test
    public void testCreateDuplicateFlight() {
        // First, create a valid flight
        Flight flight = new Flight(null, "DEN", "PHX", 175);
        ResponseEntity<Flight> response = restTemplate.postForEntity("/api/create", flight, Flight.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Flight createdFlight = response.getBody();
        assertNotNull(createdFlight);
        assertNotNull(createdFlight.getId());

        // Attempt to create a duplicate flight with the same source and destination
        ResponseEntity<String> duplicateResponse = restTemplate.postForEntity("/api/create", flight, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, duplicateResponse.getStatusCode());

        String errorMsg = duplicateResponse.getBody();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("already exists"), "Error message should indicate duplicate flight exists");
    }

    @Test
    public void testBulkCreateFlights() {
        List<Flight> newFlights = Arrays.asList(
                new Flight(null, "ATL", "MIA", 250),
                new Flight(null, "MIA", "CUN", 180)
        );

        ResponseEntity<Flight[]> response = restTemplate.postForEntity("/api/bulkcreate", newFlights, Flight[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Flight[] createdFlights = response.getBody();
        assertNotNull(createdFlights);
        assertEquals(2, createdFlights.length);

        // Verify the flights were saved
        ResponseEntity<Flight[]> getAllResponse = restTemplate.getForEntity("/api/flights", Flight[].class);
        Flight[] allFlights = getAllResponse.getBody();
        assertNotNull(allFlights);

        boolean foundAtlMia = false;
        boolean foundMiaCun = false;

        for (Flight flight : allFlights) {
            if ("ATL".equals(flight.getSource()) && "MIA".equals(flight.getDestination()) && flight.getPrice() == 250) {
                foundAtlMia = true;
            }
            if ("MIA".equals(flight.getSource()) && "CUN".equals(flight.getDestination()) && flight.getPrice() == 180) {
                foundMiaCun = true;
            }
        }

        assertTrue(foundAtlMia && foundMiaCun, "Bulk created flights should be found in database");
    }

    @Test
    public void testDeleteAllFlights() {
        // First verify we have flights
        ResponseEntity<Flight[]> getBeforeDelete = restTemplate.getForEntity("/api/flights", Flight[].class);
        assertTrue(getBeforeDelete.getBody().length > 0, "Should have flights before delete");

        // Delete all flights
        restTemplate.delete("/api/flights");

        // Verify all flights were deleted
        ResponseEntity<Flight[]> getAfterDelete = restTemplate.getForEntity("/api/flights", Flight[].class);
        assertEquals(0, getAfterDelete.getBody().length, "Should have no flights after delete");
    }

    // 4. Advanced route finding tests
    @Test
    public void testRoutesWithSameNumberOfHops() {
        // Add alternative route NYC -> ATL -> SEA
        List<Flight> alternativeRoute = Arrays.asList(
                new Flight(null, "NYC", "ATL", 220),
                new Flight(null, "ATL", "SEA", 330)
        );
        flightRepository.saveAll(alternativeRoute);

        RouteRequestDto request = new RouteRequestDto("NYC", "SEA", 2);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);

        // Should find both NYC -> BOS -> SEA and NYC -> ATL -> SEA routes
        boolean foundBosRoute = false;
        boolean foundAtlRoute = false;

        for (Route route : routes) {
            if (route.getCities().equals(Arrays.asList("NYC", "BOS", "SEA"))) {
                assertEquals(550, route.getTotalPrice()); // 150 + 400
                foundBosRoute = true;
            }
            if (route.getCities().equals(Arrays.asList("NYC", "ATL", "SEA"))) {
                assertEquals(550, route.getTotalPrice()); // 220 + 330
                foundAtlRoute = true;
            }
        }

        assertTrue(foundBosRoute && foundAtlRoute, "Both alternative routes should be found");
    }

    @Test
    public void testImpossibleRouteWithNoInterconnections() {
        // Setup isolated cities
        List<Flight> isolatedCities = Arrays.asList(
                new Flight(null, "IS1", "IS2", 100),
                new Flight(null, "IS2", "IS3", 100),
                new Flight(null, "SE1", "SE2", 100)
        );
        flightRepository.saveAll(isolatedCities);

        RouteRequestDto request = new RouteRequestDto("IS1", "SE2", null);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);
        assertEquals(0, routes.length, "No route should be found between disconnected city groups");
    }

    @Test
    public void testSameOriginAndDestination() {
        RouteRequestDto request = new RouteRequestDto("NYC", "NYC", null);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);
        assertEquals(1, routes.length, "Should find one route (staying at origin)");
        assertEquals(Arrays.asList("NYC"), routes[0].getCities());
        assertEquals(0, routes[0].getTotalPrice());
    }

    @Test
    public void testPerformanceWithLargeDataset() {
        // Create a larger dataset to test performance
        List<Flight> largeDataset = new ArrayList<>();
        String[] cities = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

        // Create a fully connected graph of 10 cities (90 flights)
        for (int i = 0; i < cities.length; i++) {
            for (int j = 0; j < cities.length; j++) {
                if (i != j) {
                    largeDataset.add(new Flight(null, cities[i], cities[j], 100 + (i * j)));
                }
            }
        }

        flightRepository.saveAll(largeDataset);

        // Test route finding with timeout
        long startTime = System.currentTimeMillis();

        RouteRequestDto request = new RouteRequestDto("A", "J", 5);
        ResponseEntity<Route[]> response = restTemplate.postForEntity("/api/routes", request, Route[].class);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Route[] routes = response.getBody();
        assertNotNull(routes);
        assertTrue(routes.length > 0, "Should find at least one route");

        // Verify the execution time is reasonable (adjust as needed)
        assertTrue(duration < 5000, "Route finding should complete within 5 seconds");
    }
}