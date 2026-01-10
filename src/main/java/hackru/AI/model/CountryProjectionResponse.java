package hackru.AI.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryProjectionResponse {
    private CountryMetrics metrics;
    private ProjectionResponse projection;
}

