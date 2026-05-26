package com.questnpc.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.questnpc.QuestNPCLogger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialogueManager {

    // Кеш диалогов: Имя_Файла -> (ID_Узла -> Сам_Узел)
    private static final Map<String, Map<String, DialogueNode>> DIALOGUE_CACHE = new HashMap<>();

    /**
     * Получить конкретный узел диалога. Если диалог еще не загружен в память, он загрузится автоматически.
     * @param dialogueId Имя JSON файла (например, "village_elder")
     * @param nodeId ID нужного узла (например, "start_greeting")
     * @return Объект узла диалога или null, если не найден
     */
    public static DialogueNode getDialogueNode(String dialogueId, String nodeId) {
        // Если этого диалога нет в кеше — пытаемся загрузить файл
        if (!DIALOGUE_CACHE.containsKey(dialogueId)) {
            loadDialogueFromFile(dialogueId);
        }

        Map<String, DialogueNode> nodes = DIALOGUE_CACHE.get(dialogueId);
        if (nodes != null) {
            return nodes.get(nodeId);
        }
        return null;
    }

    /**
     * Внутренний метод для чтения JSON из ресурсов мода (/assets/questnpc/dialogues/)
     */
    private static void loadDialogueFromFile(String dialogueId) {
        String path = "/assets/questnpc/dialogues/" + dialogueId + ".json";
        Map<String, DialogueNode> nodesMap = new HashMap<>();

        try (InputStream is = DialogueManager.class.getResourceAsStream(path)) {
            if (is == null) {
                QuestNPCLogger.error("Файл диалога не найден по пути: " + path);
                // Записываем пустую мапу, чтобы не спамить попытками чтения диска
                DIALOGUE_CACHE.put(dialogueId, nodesMap);
                return;
            }

            // Читаем JSON файл с поддержкой UTF-8 (для русского языка)
            JsonObject rootObject = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();

            // Пробегаемся по всем узлам в JSON файле
            for (Map.Entry<String, JsonElement> entry : rootObject.entrySet()) {
                String nodeId = entry.getKey();
                JsonObject nodeJson = entry.getValue().getAsJsonObject();

                // Читаем текст NPC
                String npcText = nodeJson.has("text") ? nodeJson.get("text").getAsString() : "...";

                // Читаем массив вариантов ответа (options)
                List<DialogueOption> options = new ArrayList<>();
                if (nodeJson.has("options") && nodeJson.get("options").isJsonArray()) {
                    JsonArray optionsArray = nodeJson.getAsJsonArray("options");

                    for (JsonElement optionElement : optionsArray) {
                        JsonObject optionJson = optionElement.getAsJsonObject();

                        String optionText = optionJson.has("text") ? optionJson.get("text").getAsString() : "";
                        // Если в JSON не указан next_node, по умолчанию ставим "close" (закрытие диалога)
                        String nextNodeId = optionJson.has("next_node") ? optionJson.get("next_node").getAsString() : "close";
                        String action = optionJson.has("action") ? optionJson.get("action").getAsString() : "";

                        options.add(new DialogueOption(optionText, nextNodeId, action));
                    }
                }

                // Собираем готовый узел и кладем в локальную мапу файла
                DialogueNode dialogueNode = new DialogueNode(nodeId, npcText, options);
                nodesMap.put(nodeId, dialogueNode);
            }

            // Сохраняем загруженный диалог в глобальный кеш
            DIALOGUE_CACHE.put(dialogueId, nodesMap);
            QuestNPCLogger.debug("Диалог '" + dialogueId + "' успешно загружен! Узлов: " + nodesMap.size());

        } catch (Exception e) {
            QuestNPCLogger.error("Ошибка при парсинге файла диалога: " + path, e);
            DIALOGUE_CACHE.put(dialogueId, nodesMap); // Защита от бесконечного ретрая при ошибках
        }
    }

    /**
     * Очистить кеш (полезно, если ты захочешь сделать команду перезагрузки диалогов в игре /questnpc reload)
     */
    public static void clearCache() {
        DIALOGUE_CACHE.clear();
        QuestNPCLogger.debug("Кеш диалогов успешно очищен!");
    }
}