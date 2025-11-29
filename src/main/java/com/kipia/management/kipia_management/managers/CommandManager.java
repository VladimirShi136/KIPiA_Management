package com.kipia.management.kipia_management.managers;

import java.util.Stack;
import java.util.function.Consumer;

/**
 * Менеджер команд для undo/redo операций
 * Отвечает за управление историей действий
 *
 * @author vladimir_shi
 * @since 05.11.2025
 */
public class CommandManager {
    /**
     * Интерфейс команды для паттерна Command
     */
    public interface Command {
        void execute();
        void undo();
    }

    private final Stack<Command> undoStack = new Stack<>();
    private final Stack<Command> redoStack = new Stack<>();
    private final Consumer<Boolean> onUndoStateChange;  // Колбэк для обновления UI (активация кнопок)
    private final Consumer<Boolean> onRedoStateChange;

    public CommandManager(Consumer<Boolean> onUndoStateChange, Consumer<Boolean> onRedoStateChange) {
        this.onUndoStateChange = onUndoStateChange;
        this.onRedoStateChange = onRedoStateChange;
        updateUIState();
    }

    /**
     * Выполнение команды с добавлением в историю
     */
    public void execute(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
        updateUIState();
    }

    /**
     * Отмена последней команды
     */
    public void undo() {
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            updateUIState();
        }
    }

    /**
     * Повтор отмененной команды
     */
    public void redo() {
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.execute();
            undoStack.push(command);
            updateUIState();
        }
    }

    /**
     * Очистка истории команд
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        updateUIState();
    }

    /**
     * Проверка возможности undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Проверка возможности redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Обновление состояния UI (активация/деактивация кнопок)
     */
    private void updateUIState() {
        if (onUndoStateChange != null) {
            onUndoStateChange.accept(canUndo());
        }
        if (onRedoStateChange != null) {
            onRedoStateChange.accept(canRedo());
        }
    }
}