package com.ryanpurakal.pariscompass.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CountryInfo {
    private String iso3;
    private String name;
}

