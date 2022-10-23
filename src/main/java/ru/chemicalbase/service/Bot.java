package ru.chemicalbase.service;

import com.epam.indigo.IndigoException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.chemicalbase.config.BotConfigurator;
import ru.chemicalbase.exception.InvalidFileIdException;
import ru.chemicalbase.exception.SmilesParsingException;
import ru.chemicalbase.exception.UnrecognizedRequestException;
import ru.chemicalbase.exception.UnsupportedFileExtensionException;
import ru.chemicalbase.repository.reagent.Reagent;
import ru.chemicalbase.repository.reagent.ReagentRepository;
import ru.chemicalbase.repository.user.User;
import ru.chemicalbase.repository.user.UserRepository;
import ru.chemicalbase.utils.ChemicalVision;
import ru.chemicalbase.utils.SmilesServerBroker;
import ru.chemicalbase.utils.brokermodels.Request;
import ru.chemicalbase.utils.brokermodels.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static ru.chemicalbase.utils.Math.incrementIfContainsRemainder;

@Service
@Slf4j
public class Bot extends TelegramLongPollingBot {
    private final BotConfigurator config;
    private final ReagentRepository reagentRepository;
    private final UserRepository userRepository;
    //этот объект распознает изображения с помощью Molvec NN а также умеет сравнивать две молекулы по смайлс
    private final ChemicalVision vision;

    //этот объект общается с внешним сервером, использующием Decimer NN
    private final SmilesServerBroker broker;
    //В таблицу сохраняется последний результат поиска пользователя
    private final Map<Long, List<Reagent>> userSearchResultCache = new ConcurrentHashMap<>();

    //В таблицу сохраняется последняя просмотренная страница с разметкой html
    private final Map<Long, List<String>> userLastViewedPageCache = new ConcurrentHashMap<>();
    private static final int REAGENTS_ON_PAGE = 5;
    private static final int MAX_INLINEKEYBOARD_BUTTONS_IN_ROW = 5;
    private static final String REAGENT_INFO_MESSAGE = "<b>➤%s</b>\nКомната: <b>%s</b>, " + "Шкаф: <b>%s</b>, полка: <b>%s</b>";
    private static final String REAGENT_INFO_MESSAGE_SHELF_IS_NULL = "<b>➤%s</b>\nКомната: <b>%s</b>, Шкаф: <b>%s</b>";

    private static final String DESCRIPTION = """
            <b>Чат-бот для поиска реактивов по базе данных реактивов Баранова М.С.</b>
                        
            Бот работает в <b>двух</b> режимах:
                        
            <b>1.</b> Текстовый поиск реактива. Для этого отправьте боту сообщение с текстом. Поиск осуществляется по непрерывной последовательности 
            заданных символов в базе данных по трем столбцам: name, alternative_name, another_name. Например, по сообщению "benzene" будут найдены:
            "benzene", "fluorobenzene", "benzene-d6".
             
            <b>2.</b> Распознавание изображений, содержащих структурные формулы (alpha). Для этого нужно отправить боту сообщение с прикрепленной
            фотографией или файлом с пустым телом сообщения (без текста). Если изображение отправляется посредством файла, то поддерживаемыми
            расширениями в данный момент являются только <b>*.jpeg</b> и <b>*.png</b>. Распознавание будет тем лучше, чем выше качество изображения,
            чем меньше фоновые шумы и другие элементы, расположенные на картинках, пересылаемых боту. Если необходимо распознать структуру с фотографии, лучше пересылать изображение
            через прикрепленный файл (в таком случае изображение не будет сжиматься). Поиск осуществляется по полному совпадению структуры в базе.
            Фича работает в тестовом режиме, поэтому может допускать ошибки. Датасет можно найти <a href="https://disk.yandex.ru/d/1jOjfDepSZ-E8Q">здесь</a>.
                        
            Это закрытый бот; для получения доступа напишите @tony_chem.
               
            """;

    public Bot(BotConfigurator config, ReagentRepository reagentRepository, UserRepository userRepository, ChemicalVision vision, SmilesServerBroker broker) throws TelegramApiException {
        this.config = config;
        this.reagentRepository = reagentRepository;
        this.userRepository = userRepository;
        this.vision = vision;
        this.broker = broker;
        setCommands();
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message message = update.getMessage();

                if (message.hasText()) {
                    if (message.getText().equals("/description")) {
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(message.getChatId());
                        sendMessage.setText(DESCRIPTION);
                        sendMessage.setParseMode(ParseMode.HTML);
                        execute(sendMessage);
                        return;
                    } else if (message.getText().startsWith("/")) {
                        SendMessage sendMessage = new SendMessage();
                        sendMessage.setChatId(message.getChatId());
                        sendMessage.setText("Неизвестная команда.");
                        sendMessage.setParseMode(ParseMode.HTML);
                        execute(sendMessage);
                        return;
                    }
                }

                if (userRepository.existsByChatId(message.getChatId()) && userRepository.getAcceptedByChatId(message.getChatId())) {
                    List<SendMessage> sendMessageList = handleIncomingMessage(message.getChatId(), message);

                    if (sendMessageList.isEmpty()) {
                        execute(prepareSendMessage(message.getChatId(), List.of("Реактив не найден."), null));
                        return;
                    }

                    for (SendMessage s : sendMessageList) {
                        execute(s);
                    }
                } else {
                    saveNewUser(message);
                    execute(prepareSendMessage(message.getChatId(), List.of("Закрытый бот. Пишите @tony_chem для получения доступа."), null));
                }

            } else if (update.hasCallbackQuery()) {
                EditMessageText text = handleCallBackQuery(update.getCallbackQuery());
                execute(text);
            }
        } catch (UnrecognizedRequestException requestException) {
            execute(prepareSendMessage(update.getMessage().getChatId(), List.of("Неизвестный запрос"), null));
            log.warn(requestException.getMessage());
        } catch (InvalidFileIdException | IOException exc) {
            log.warn(exc.toString());
        } catch (UnsupportedFileExtensionException fileExtensionException) {
            execute(prepareSendMessage(update.getMessage().getChatId(), List.of("Неподдерживаемое расширение документа"), null));
            log.warn(fileExtensionException.getMessage());
        } catch (SmilesParsingException parsingException) {
            execute(prepareSendMessage(update.getMessage().getChatId(), List.of("Ошибка при парсинге изображения"), null));
            log.warn(parsingException.getMessage());
        } catch (InterruptedException | TelegramApiException exc) {
            log.warn(exc.toString());
        }
    }

    private EditMessageText handleCallBackQuery(CallbackQuery query) {
        EditMessageText editMessageText = new EditMessageText();

        int messageId = query.getMessage().getMessageId();
        long chatId = query.getMessage().getChatId();
        String buttonData = query.getData();

        //Извлекаем, какой последний запрос делал пользователь
        List<Reagent> userQueryResultList = userSearchResultCache.get(chatId);

        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setParseMode(ParseMode.HTML);

        if (buttonData.equals("MORE")) {
            //При нажатии на кнопку "..." извлекаем последнюю просмотренный список реактивов в виде
            //списка строк. Нужно для сохранения разметки сообщений, отправляемых пользователю.
            String text = String.join("\n\n", userLastViewedPageCache.get(chatId));
            editMessageText.setText(text);
            editMessageText.setReplyMarkup(generateInlineKeyBoardMarkup(userQueryResultList.size(), true));
        } else {
            //Парсим номер запрашиваемой страницы, преобразуем paginated-список реактивов в список форматированных строк
            int page = Integer.parseInt(buttonData);
            List<String> reagentInfo = new ArrayList<>();

            for (Reagent r : paginate(userQueryResultList, REAGENTS_ON_PAGE, page)) {
                reagentInfo.add(formatReagentMessage(r));
            }

            editMessageText.setText(String.join("\n\n", reagentInfo));
            editMessageText.setReplyMarkup(generateInlineKeyBoardMarkup(userQueryResultList.size(), false));
        }
        return editMessageText;
    }

    private List<SendMessage> handleIncomingMessage(long chatId, Message message) throws InvalidFileIdException, UnrecognizedRequestException, TelegramApiException, IOException, InterruptedException {

        List<SendMessage> sendMessageList = new ArrayList<>();
        List<Reagent> foundReagents;

        //Определить, что делать с входящим сообщением
        if (message.hasPhoto()) {
            java.io.File savedFile = saveImageFromPicture(message);
            foundReagents = arrangeReagentListFromMoleculeFile(message.getChatId(), savedFile);
        } else if (message.hasDocument()) {
            java.io.File savedFile = saveImageFromDocument(message);
            foundReagents = arrangeReagentListFromMoleculeFile(message.getChatId(), savedFile);
        } else if (message.hasText()) {
            String text = message.getText();
            foundReagents = reagentRepository.search(text);
        } else {
            throw new UnrecognizedRequestException("Неизвестный запрос. chat_id = " + chatId);
        }

        //Если список реактивов пуст, возвращаем пустой список
        if (foundReagents.isEmpty()) {
            return Collections.emptyList();

        }

        //добавить поиск пользователя в кэш, чтобы можно было обрабатывать правильно клавиатуру
        userSearchResultCache.put(chatId, foundReagents);

        //подготовить список SendMessage
        List<String> messagesList = new ArrayList<>();

        //подготавливаем список строк с реактивами
        for (Reagent r : paginate(foundReagents, REAGENTS_ON_PAGE, 1)) {
            messagesList.add(formatReagentMessage(r));
        }

        //этот же список сохраняем за пользователем, чтобы далее можно было парсить кнопку "..."
        userLastViewedPageCache.put(chatId, messagesList);

        InlineKeyboardMarkup markup = generateInlineKeyBoardMarkup(foundReagents.size(), false);

        sendMessageList.add(prepareSendMessage(chatId, messagesList, markup));
        return sendMessageList;
    }

    private SendMessage prepareSendMessage(long chatId, List<String> messages, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setReplyMarkup(markup);

        String text = String.join("\n\n", messages);
        message.setText(text);
        return message;
    }

    private java.io.File saveImageFromPicture(Message message) throws InvalidFileIdException, TelegramApiException {

        List<PhotoSize> params = message.getPhoto();
        Optional<String> fileId = Optional.empty();
        for (PhotoSize param : params) {
            fileId = Optional.of(param.getFileId());
        }
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId.orElseThrow(() -> new InvalidFileIdException("Ошибка при извлечении File Id. chat_id = " + message.getChatId())));

        File file = execute(getFile);
        java.io.File savedFile = downloadFile(file, new java.io.File("cache/" + message.getChat().getFirstName() + "_" + Instant.now().toEpochMilli() + ".jpg"));
        return savedFile;
    }

    private java.io.File saveImageFromDocument(Message message) throws UnsupportedFileExtensionException, TelegramApiException {

        Document doc = message.getDocument();

        if (doc.getMimeType() == null) {
            throw new UnsupportedFileExtensionException("Тип файла не поддерживается.");
        }

        GetFile getFile = new GetFile();
        getFile.setFileId(doc.getFileId());

        File file = execute(getFile);

        java.io.File ioFile = null;

        if (doc.getMimeType().equals("image/png")) {
            ioFile = downloadFile(file, new java.io.File("cache/" + message.getChat().getFirstName() + "_" + Instant.now().toEpochMilli() + ".png"));
        } else if (doc.getMimeType().equals("image/jpeg")) {
            ioFile = downloadFile(file, new java.io.File("cache/" + message.getChat().getFirstName() + "_" + Instant.now().toEpochMilli() + ".jpg"));
        }

        if (ioFile == null) {
            throw new UnsupportedFileExtensionException("Тип файла не поддерживается. chat_id = " + message.getChatId());
        }

        return ioFile;
    }

    //Формирует список реактивов на основе входящего файла
    private List<Reagent> arrangeReagentListFromMoleculeFile(long chatId, java.io.File file) throws IOException, InterruptedException, SmilesParsingException {
        //String smiles = vision.loadImage(file); предыдущий вариант через Molvec

        //С Decimer NN, через flask python сервер
        Response resp = broker.sendRequest(Request.of(chatId, file));
        if (resp.getSmiles() == null) {
            throw new SmilesParsingException("Ошибка при парсинге изображения. chat_id = " + chatId + "; file_location = " + file.getAbsolutePath());
        }
        String smiles = resp.getSmiles();

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

    //Если пользователь написал сообщение впервые, то заносится в базу данных с неподтвержденным статусом
    private void saveNewUser(Message message) {
        User user = new User();
        user.setChatId(message.getChatId());
        user.setName(message.getChat().getFirstName());
        user.setAccepted(false);
        userRepository.save(user);
    }

    //Возвращает инлайн клавитуру для сообщения. Анализирует размер списка реактивов listSize.
    //Создает клавиатуру, из любого количества рядов, в ряду - количество кнопок составляет MAX_INLINEKEYBOARD_BUTTONS_IN_ROW
    //Если флаг false - возвращает клавиатуру из одного ряда с кнопками 1, 2, .., MAX_INLINEKEYBOARD_BUTTONS_IN_ROW.
    //Если флаг true - возвращает клавиатуру, покрывающую полностью все страницы.
    private InlineKeyboardMarkup generateInlineKeyBoardMarkup(int listSize, boolean printAll) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int numberOfButtons = incrementIfContainsRemainder(listSize, MAX_INLINEKEYBOARD_BUTTONS_IN_ROW);
        int numberOfRows = incrementIfContainsRemainder(numberOfButtons, MAX_INLINEKEYBOARD_BUTTONS_IN_ROW);

        if (numberOfButtons < 2) {
            return null;
        }

        if (printAll) {
            //создаем ряды
            for (int i = 1; i < numberOfRows + 1; i++) {
                List<InlineKeyboardButton> row;

                if (i == numberOfRows) {
                    row = prepareInlineKeyboardRow(MAX_INLINEKEYBOARD_BUTTONS_IN_ROW * (i - 1) + 1, numberOfButtons);
                } else {
                    row = prepareInlineKeyboardRow(MAX_INLINEKEYBOARD_BUTTONS_IN_ROW * (i - 1) + 1, i * MAX_INLINEKEYBOARD_BUTTONS_IN_ROW);
                }
                rows.add(row);
            }
        } else {
            if (numberOfButtons < MAX_INLINEKEYBOARD_BUTTONS_IN_ROW) {
                List<InlineKeyboardButton> row = prepareInlineKeyboardRow(1, numberOfButtons);
                rows.add(row);
            } else {
                List<InlineKeyboardButton> row = prepareInlineKeyboardRow(1, MAX_INLINEKEYBOARD_BUTTONS_IN_ROW - 1);
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText("...");
                button.setCallbackData("MORE");
                row.add(button);

                rows.add(row);
            }
        }

        markup.setKeyboard(rows);
        return markup;
    }

    //Разбивает заданный список реактивов на страницы. reagentsOnPage - количество реактивов на странице,
    //pageNumber - номер страницы
    private List<Reagent> paginate(List<Reagent> reagentList, int reagentsOnPage, int pageNumber) {
        List<Reagent> paginatedList = new ArrayList<>();

        for (int i = 0, j = reagentsOnPage * (pageNumber - 1); i < reagentsOnPage && (j + i) < reagentList.size(); i++) {
            paginatedList.add(reagentList.get(j + i));
        }

        return paginatedList;
    }

    //подготавливает ряд кнопок от start до endInclusive включительно. Текст - число, data - соответствующее число
    private List<InlineKeyboardButton> prepareInlineKeyboardRow(int start, int endInclusive) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int j = start; j < endInclusive + 1; j++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(String.valueOf(j));
            button.setCallbackData(String.valueOf(j));
            row.add(button);
        }
        return row;
    }

    private void setCommands() throws TelegramApiException {
        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("/description", "Инструкция по использованию бота"));
        execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
    }

    //Формирует выводимые сообщения в зависимости от наличия данных в базе реактивов
    private String formatReagentMessage(Reagent r) {
        if (r.getShelf() == null) {
            return String.format(REAGENT_INFO_MESSAGE_SHELF_IS_NULL, r.getName(), r.getRoom(), r.getShed());
        } else {
            return String.format(REAGENT_INFO_MESSAGE, r.getName(), r.getRoom(), r.getShed(), r.getShelf());
        }
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
