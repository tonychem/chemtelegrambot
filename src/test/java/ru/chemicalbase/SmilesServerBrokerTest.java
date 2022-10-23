package ru.chemicalbase;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import ru.chemicalbase.utils.brokermodels.Request;
import ru.chemicalbase.utils.brokermodels.Response;
import ru.chemicalbase.utils.SmilesServerBroker;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class SmilesServerBrokerTest {
    private static final SmilesServerBroker broker = new SmilesServerBroker(new JsonMapper());

    @Test
    public void requestingFileThatDoesntExist() throws IOException, InterruptedException {
        Request request = Request.of(1234l, new File("C:\\hello\\world.png"));
        Response resp = broker.sendRequest(request);
        assertThat(resp.getError()).isEqualTo("file not found");
        assertThat(resp.getChatId()).isEqualTo(1234l);
        assertThat(resp.getSmiles()).isNull();
    }

    @Test
    public void requestingIndole() throws IOException, InterruptedException {
        Request request = Request.of(1234l, new File("C:\\Users\\tonyc\\Desktop\\Java _projects\\ChemBaseTelegramUI\\cache\\Ivan_1665334347486.png"));
        Response resp = broker.sendRequest(request);
        assertThat(resp.getSmiles()).isEqualToIgnoringCase("C1=CC=C2C(=C1)C=CN2");
        assertThat(resp.getError()).isNull();
        assertThat(resp.getChatId()).isEqualTo(1234l);
    }

}
