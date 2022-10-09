package ru.chemicalbase.service;

import com.epam.indigo.IndigoException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.chemicalbase.Utils.ChemicalVision;
import ru.chemicalbase.config.BotConfigurator;
import ru.chemicalbase.exception.InvalidFileIdException;
import ru.chemicalbase.exception.UnrecognizedRequestException;
import ru.chemicalbase.exception.UnsupportedFileExtensionException;
import ru.chemicalbase.repository.Reagent;
import ru.chemicalbase.repository.ReagentRepository;
import ru.chemicalbase.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class Bot extends TelegramLongPollingBot {
    private final BotConfigurator config;
    private final ReagentRepository reagentRepository;
    private final UserRepository userRepository;
    private final ChemicalVision vision;
    private static final String REAGENT_INFO_MESSAGE_ONE_NAME = "<b>%s</b>\nШкаф %s, полка: %s";
    private static final String REAGENT_INFO_MESSAGE_TWO_NAMES = "<b>%s%s</b>\nШкаф %s, полка: %s";
    private static final String REAGENT_INFO_MESSAGE_THREE_NAMES = "<b>%s%s%s</b>\nШкаф %s, полка: %s";


    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (userRepository.existsByChatId(message.getChatId()) && userRepository.getAcceptedByChatId(message.getChatId())) {
                List<SendMessage> sendMessageList = handleIncomingMessage(message.getChatId(), message);

                for (SendMessage s : sendMessageList) {
                    try {
                        execute(s);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                ru.chemicalbase.repository.User user = new ru.chemicalbase.repository.User();
                user.setChatId(message.getChatId());
                user.setName(message.getChat().getFirstName());
                user.setAccepted(false);
                userRepository.save(user);

                execute(prepareSendMessage(message.getChatId(), "Закрытый бот. Пишите @tony_chem для получения доступа."));
            }
        }
    }

    private List<SendMessage> handleIncomingMessage(long chatId, Message message)
            throws InvalidFileIdException, UnrecognizedRequestException,
            TelegramApiException, IOException {

        List<SendMessage> sendMessageList = new ArrayList<>();
        List<Reagent> foundReagents;

        //определить, что делать с входящим сообщением
        if (message.hasPhoto()) {
            java.io.File savedFile = saveImageFromPicture(message);
            foundReagents = arrangeReagentListFromMoleculeFile(savedFile);
        } else if (message.hasDocument()) {
            java.io.File savedFile = saveImageFromDocument(message);
            foundReagents = arrangeReagentListFromMoleculeFile(savedFile);
        } else if (message.hasText()) {
            String text = message.getText();
            foundReagents = reagentRepository.search(text);
        } else {
            throw new UnrecognizedRequestException("Неизвестный запрос.");
        }

        //подготовить список SendMessage
        for (Reagent r : foundReagents) {
            sendMessageList.add(prepareSendMessage(chatId, String.format(REAGENT_INFO_MESSAGE_ONE_NAME, r.getName(),
                    r.getShed(), r.getShelf())));
        }

        return sendMessageList;
    }

    private SendMessage prepareSendMessage(long chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setText(messageText);
        return message;
    }

    private java.io.File saveImageFromPicture(Message message)
            throws InvalidFileIdException, TelegramApiException {

        List<PhotoSize> params = message.getPhoto();
        Optional<String> fileId = Optional.empty();
        for (PhotoSize param : params) {
            fileId = Optional.of(param.getFileId());
        }
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId.orElseThrow(() -> new InvalidFileIdException("Ошибка при извлечении File Id.")));

        File file = execute(getFile);
        java.io.File savedFile = downloadFile(file, new java.io.File("cache/" +
                message.getChat().getFirstName() + "_" + Instant.now().toEpochMilli() + ".jpg"));
        return savedFile;
    }

    private java.io.File saveImageFromDocument(Message message) throws UnsupportedFileExtensionException,
            TelegramApiException {

        Document doc = message.getDocument();

        if (doc.getMimeType() == null) {
            throw new UnsupportedFileExtensionException("Тип файла не поддерживается.");
        }

        GetFile getFile = new GetFile();
        getFile.setFileId(doc.getFileId());

        File file = execute(getFile);

        java.io.File ioFile = null;

        if (doc.getMimeType().equals("image/png")) {
            ioFile = downloadFile(file, new java.io.File("cache/" + message.getChat().getFirstName()
                    + "_" + Instant.now().toEpochMilli() + ".png"));
        } else if (doc.getMimeType().equals("image/jpeg")) {
            ioFile = downloadFile(file, new java.io.File("cache/" + message.getChat().getFirstName()
                    + "_" + Instant.now().toEpochMilli() + ".jpg"));
        }

        if (ioFile == null) {
            throw new UnsupportedFileExtensionException("Тип файла не поддерживается.");
        }

        return ioFile;
    }

    private List<Reagent> arrangeReagentListFromMoleculeFile(java.io.File file) throws IOException {
        String smiles = vision.parseImage(file);
        List<Reagent> reagentsWithSmiles = reagentRepository.findAllBySmilesNotNull();
        List<Reagent> foundReagents = new ArrayList<>();

        for (Reagent r : reagentsWithSmiles) {
            try {
                if (vision.exactMoleculeMatch(smiles, r.getSmiles())) {
                    foundReagents.add(r);
                }
            } catch (IndigoException exc) {
            }
        }

        return foundReagents;
    }

    @Override
    public String getBotUsername() {
        return config.getName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }
}
