package com.example.pros.components;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class Route {
    private List<String> cities;
    Integer totalPrice;

}