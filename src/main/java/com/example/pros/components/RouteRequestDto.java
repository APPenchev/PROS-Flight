package com.example.pros.components;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequestDto {
    public String origin;
    public String destination;
    public Integer maxFlights; // Optional; if null then no limit.
}