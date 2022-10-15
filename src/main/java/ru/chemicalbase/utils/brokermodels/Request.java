package ru.chemicalbase.utils.brokermodels;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.File;

@Data
@AllArgsConstructor
public class Request {
    @JsonProperty("chat_id")
    private long chatId;

    @JsonProperty("path")
    private String path;

    public static Request of(long chatId, File file) {
        return new Request(chatId, file.getAbsolutePath());
    }
}
