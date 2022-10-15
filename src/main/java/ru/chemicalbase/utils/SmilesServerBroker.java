package ru.chemicalbase.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.chemicalbase.utils.brokermodels.Request;
import ru.chemicalbase.utils.brokermodels.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
@RequiredArgsConstructor
public class SmilesServerBroker {
    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${smiles.server.address}")
    private String address;

    private final ObjectMapper mapper;

    public Response sendRequest(Request request) throws IOException, InterruptedException {
        URI uri = URI.create(address);
        String json = mapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .method("POST", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        Response response = mapper.readValue(httpResponse.body(), Response.class);

        return response;
    }
}
