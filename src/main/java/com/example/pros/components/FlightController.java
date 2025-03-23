package com.example.pros.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FlightController {

    @Autowired
    private FlightService flightRouteService;

    @Autowired
    private FlightRepository flightRepository;


    @PostMapping("/routes")
    public List<Route> getRoutes(@RequestBody RouteRequestDto request) {
        return flightRouteService.findRoutes(request.origin, request.destination, request.maxFlights);
    }

    @PostMapping("/create")
    public Flight createFlight(@RequestBody Flight flight) {
        return flightRouteService.createFlight(flight);
    }

    @GetMapping("/flights")
    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    @PostMapping("/bulkcreate")
    public List<Flight> bulkCreateFlights(@RequestBody List<Flight> flights) {
        return flightRouteService.bulkCreateFlights(flights);
    }

    @DeleteMapping("/flights")
    public void deleteAllFlights() {
        flightRepository.deleteAll();
    }
}
