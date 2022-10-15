package ru.chemicalbase.utils.brokermodels;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Response {
    @JsonProperty("chat_id")
    private long chatId;

    @JsonProperty("smiles")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String smiles;

    @JsonProperty("error")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;
}
