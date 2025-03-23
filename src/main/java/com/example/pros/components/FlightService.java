package com.example.pros.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FlightService {

    @Autowired
    private FlightRepository flightRepository;

    public Flight createFlight(Flight flight) {
        if (flight.getSource() == null || flight.getDestination() == null || flight.getPrice() == null) {
            throw new IllegalArgumentException("Source, destination and price cannot be null");
        }
        if (flight.getSource().equals(flight.getDestination())) {
            throw new IllegalArgumentException("Source and destination cannot be the same");
        }
        if (flight.getPrice() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (flightRepository.findBySourceAndDestination(flight.getSource(), flight.getDestination()).isPresent()) {
            throw new IllegalArgumentException("Flight from " + flight.getSource() + " to " + flight.getDestination() + " already exists.");
        }
        if (flight.getDestination().length() != 3 || flight.getSource().length() != 3) {
            throw new IllegalArgumentException("Source and destination must be 3 characters long");
        }
        return flightRepository.save(flight);
    }

    // Builds a graph from DB flights and returns a map from source to list of FlightInfo
    private Map<String, List<FlightInfoDto>> buildGraph() {
        Map<String, List<FlightInfoDto>> graph = new HashMap<>();
        List<Flight> flights = flightRepository.findAll();
        for (Flight flight : flights) {
            graph.computeIfAbsent(flight.getSource(), k -> new ArrayList<>())
                    .add(new FlightInfoDto(flight.getDestination(), flight.getPrice()));
        }
        return graph;
    }

    public List<Route> findRoutes(String origin, String destination, Integer maxFlights) {
        maxFlights = (maxFlights != null) ? maxFlights : -1;
        Map<String, List<FlightInfoDto>> graph = buildGraph();
        List<Route> routes = new ArrayList<>();

        dfs(graph, origin, destination, new ArrayList<>(List.of(origin)), 0, routes, maxFlights); // Fix: Create new list for each path

        routes.sort(Comparator.comparingInt(r -> r.totalPrice));
        return routes;
    }

    private void dfs(Map<String, List<FlightInfoDto>> graph, String current, String destination,
                     List<String> currentPath, int currentPrice, List<Route> routes, int maxFlights) {

        if (current.equals(destination)) {
            routes.add(new Route(new ArrayList<>(currentPath), currentPrice)); // Fix: Create a copy of the path
            return;
        }

        if (maxFlights != -1 && currentPath.size() - 1 >= maxFlights) {
            return;
        }

        if (!graph.containsKey(current)) {
            return;
        }

        for (FlightInfoDto flight : graph.get(current)) {
            currentPath.add(flight.destination);
            dfs(graph, flight.destination, destination, currentPath, currentPrice + flight.price, routes, maxFlights);
            currentPath.remove(currentPath.size() - 1);
        }
    }

    public List<Flight> bulkCreateFlights(List<Flight> flights) {
        return flights.stream().map(this::createFlight).toList();
    }
}
