package Time4WorkLogic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import Time4WorkParser.Command;
import Time4WorkParser.Parser;
import Time4WorkStorage.Storage;
import Time4WorkStorage.Tasks;

public class Logic {
    
    // =========================================================================
    // Feedback massage string
    // =========================================================================
    
    private static final String MESSAGE_INVALID_FORMAT = "invalid command: %1$s";
    private static final String MESSAGE_EMPTY_COMMAND = "Command is empty!";
    private static final String MESSAGE_ADDED_ = "Task added %1$s !";
    private static final String MESSAGE_UPDATED_ = "Task updated %1$s !";
    private static final String MESSAGE_DELETED_ = "Task %1$s deleted %2$s !";
    private static final String MESSAGE_SORTED = "Task sorted successfully!";
    private static final String MESSAGE_SEARCH_ = "Task searched %1$s";
    private static final String MESSAGE_CLEAR = "All task cleared successfully!";
    private static final String MESSAGE_UNDO_ = "Undo %1$s!";
    
    private static final String LOGIC_INIT = "Logic initialized";
    private static final Logger logger = Logger.getLogger(Logic.class.getName());
    
    // =========================================================================
    // local variables for Logic
    // =========================================================================
    
    private Storage storage;
    private ArrayList<Tasks> myTaskList = new ArrayList<Tasks>();
    private ArrayList<Tasks> fullTaskList = new ArrayList<Tasks>();
    private CommandHistory commandHistory = new CommandHistory();
    private CommandHistory reversedCommandHistory = new CommandHistory();
    
    
    enum COMMAND_TYPE {
        ADD, UPDATE, DELETE, SEARCH, SORT, INVALID, UNDO, CLEAR, EXIT
    };
    
    // Constructor
    public Logic() {
        try {
            storage = new Storage();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        logger.log(Level.INFO, LOGIC_INIT);
    }
    
    // =========================================================================
    // Method to interact with UI and parser
    // =========================================================================
    
    public FeedbackMessage executeCommand (String userInput) throws Exception {
        logger.log(Level.INFO, "start processing user input");
        String userCommand = userInput;
        fullTaskList = getFullTaskList();
        
        if (myTaskList.isEmpty()) {
            myTaskList = fullTaskList;
        }
        
        if(checkIfEmptyString(userCommand)) {
            return new FeedbackMessage(String.format(MESSAGE_INVALID_FORMAT, MESSAGE_EMPTY_COMMAND), myTaskList);
        }
        
        Parser parser = new Parser();
        
        Command parsedCommand = parser.parse(userCommand);
        
        Tasks task = parsedCommand.getTask();
        
        COMMAND_TYPE commandType = determineCommandType(parsedCommand.getCommand());
        
        switch (commandType) {
            case ADD:
                return executeAdd(task);
            case UPDATE:
                return executeUpdate(task);
            case DELETE:
                ArrayList<Integer> userInputIndexes = parsedCommand.getSelectedIndexNumbers();
                if (userInputIndexes.isEmpty()) {
                    int userInputIndex = parsedCommand.getSelectedIndexNumber();
                    return executeDeleteOneTask(userInputIndex);
                } else {
                    return executeDeleteTasks(userInputIndexes);
                }
            case SORT:
                return executeSort();
            case UNDO:
                return executeUndo();
            case CLEAR:
                return executeClear();
            case SEARCH:
                String searchKeyword = parsedCommand.getSearchKeyword();
                return executeSearch(searchKeyword);
            case INVALID:
                return new FeedbackMessage(String.format(MESSAGE_INVALID_FORMAT, "invalid"), myTaskList);
            case EXIT:
                logger.log(Level.INFO, "exit");
                System.exit(0);
            default:
                return new FeedbackMessage(String.format(MESSAGE_INVALID_FORMAT, "invalid"), myTaskList);
        }
    }
    
    private static COMMAND_TYPE determineCommandType(String commandTypeString) {
        if (commandTypeString.equalsIgnoreCase("add")) {
            return COMMAND_TYPE.ADD;
        } else if (commandTypeString.equalsIgnoreCase("delete")) {
            return COMMAND_TYPE.DELETE;
        } else if (commandTypeString.equalsIgnoreCase("update")) {
            return COMMAND_TYPE.UPDATE;
        } else if (commandTypeString.equalsIgnoreCase("sort")) {
            return COMMAND_TYPE.SORT;
        } else if (commandTypeString.equalsIgnoreCase("undo")) {
            return COMMAND_TYPE.UNDO;
        } else if (commandTypeString.equalsIgnoreCase("search")) {
            return COMMAND_TYPE.SEARCH;
        } else if (commandTypeString.equalsIgnoreCase("clear")) {
            return COMMAND_TYPE.CLEAR;
        } else if (commandTypeString.equalsIgnoreCase("exit")) {
            return COMMAND_TYPE.EXIT;
        } else {
            return COMMAND_TYPE.INVALID;
        }
    }
    
    private static boolean checkIfEmptyString(String userCommand) {
        return userCommand.trim().equals("");
    }
    
    
    public ArrayList<Tasks> getFullTaskList() {
        try {
            return storage.readFile();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return fullTaskList;
    }
    
    // =========================================================================
    // Execute Command
    // =========================================================================
    private FeedbackMessage executeAdd(Tasks newTask) throws IOException {
        try{
            logger.log(Level.INFO, "start processing add command");
            storage.appendTask(newTask);
        } catch (IOException e) {
            logger.log(Level.WARNING, "add error");
            return new FeedbackMessage(String.format(MESSAGE_ADDED_, "failed"), myTaskList);
        }
        
        myTaskList = getFullTaskList();
        
        // Set up for undo operation
        int taskListSize = myTaskList.size();
        int newTaskID = myTaskList.get(taskListSize - 1).getTaskID();
        Command reversedCommand = new Command("delete", newTaskID); //only can store taskID here
        reversedCommandHistory.addReversedCommand(reversedCommand);
        
        commandHistory.addCommand(new Command("add", newTask));
        logger.log(Level.INFO, "end of processing add command");
        return new FeedbackMessage(String.format(MESSAGE_ADDED_, "successfully"), myTaskList);
    }
    
    private FeedbackMessage executeDeleteOneTask(int userInputIndex) throws IOException {
        logger.log(Level.INFO, "start processing delete command");
        int taskListSize = myTaskList.size();
        
        if (taskListSize < userInputIndex || userInputIndex < 1) {
            return new FeedbackMessage(String.format(MESSAGE_DELETED_, userInputIndex, "failed"), myTaskList);
        } else {
            int taskID = getTaskIDFromUserInput(userInputIndex);
            Tasks deletedTask = getTaskFromTaskID(myTaskList, taskID); //store the task to be deleted
            
            try {
                storage.deleteTask(taskID);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, "delete error");
                e.printStackTrace();
            }
            myTaskList.remove(userInputIndex - 1);
            
            // Set up for undo operation
            Command reversedCommand = new Command("add", deletedTask);
            reversedCommandHistory.addReversedCommand(reversedCommand);
            commandHistory.addCommand(new Command("delete", userInputIndex));
            
            logger.log(Level.INFO, "end of processing delete command");
            return new FeedbackMessage(String.format(MESSAGE_DELETED_, userInputIndex, "successfully"), myTaskList);
        }
        
    }
    
    private FeedbackMessage executeDeleteTasks(ArrayList<Integer> userInputIndexes) throws IOException {
        logger.log(Level.INFO, "start processing delete command");
        
        int taskListSize = myTaskList.size();
        int numToBeDeleted = userInputIndexes.size();
        int deletedTaskNum = 0;
        
        String indexDeletedSuccessfully = "";
        String indexDeletedFailed = "";
        
        ArrayList<Integer> arrangedIndexes = new ArrayList<Integer>();
        
        userInputIndexes.sort(null);
        for (int i = numToBeDeleted - 1; i >= 0; i--) {
            arrangedIndexes.add(userInputIndexes.get(i));
        }
        
        for (int i = 0; i < numToBeDeleted; i++) {
            int userInputIndex = arrangedIndexes.get(i);
            if (taskListSize < userInputIndex || userInputIndex < 1) {
                indexDeletedFailed += String.valueOf(userInputIndex + " ");
            } else {
                int taskID = getTaskIDFromUserInput(userInputIndex);
                Tasks deletedTask = getTaskFromTaskID(myTaskList, taskID); //store the task to be deleted
                
                try {
                    storage.deleteTask(taskID);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "delete error");
                    e.printStackTrace();
                }
                myTaskList.remove(userInputIndex - 1);
                
                // Set up for undo operation
                Command reversedCommand = new Command("add", deletedTask);
                reversedCommandHistory.addReversedCommand(reversedCommand);
                
                deletedTaskNum++;
                indexDeletedSuccessfully = String.valueOf(userInputIndex + " ") + indexDeletedSuccessfully;
                logger.log(Level.INFO, "end of processing delete command");
            }
        }
        
        if (indexDeletedSuccessfully.isEmpty()) {
            return new FeedbackMessage(String.format(MESSAGE_DELETED_, indexDeletedFailed, "failed")
                                       , myTaskList);
        } else {
            commandHistory.addCommand(new Command("deleteMulTask", deletedTaskNum));
            return new FeedbackMessage(String.format(MESSAGE_DELETED_, indexDeletedSuccessfully, "successfully")
                                       , myTaskList);
        }
        
    }
    
    private FeedbackMessage executeUpdate(Tasks task) throws IOException {
        logger.log(Level.INFO, "start processing update command");
        int taskListSize = myTaskList.size();
        int indexToBeDeleted = task.getTaskID();   // get index to be deleted by retrieving taskID
        
        if (taskListSize < indexToBeDeleted || indexToBeDeleted < 1) {
            return new FeedbackMessage(String.format(MESSAGE_UPDATED_, "failed"), myTaskList);
        } else {
            int taskIDToBeDeleted = getTaskIDFromUserInput(indexToBeDeleted);
            task.setTaskID(taskIDToBeDeleted);
            
            Tasks taskBeforeUpdated = null;
            try {
                taskBeforeUpdated = storage.UpdateTask(taskIDToBeDeleted, task);
            } catch (Exception e) {
                logger.log(Level.WARNING, "update error");
                e.printStackTrace();
            }
            myTaskList.remove(indexToBeDeleted - 1);
            myTaskList.add(task);
            
            // Set up for undo operation
            Command reversedCommand = new Command("add", taskBeforeUpdated); // add the original one back
            reversedCommandHistory.addReversedCommand(reversedCommand);
            reversedCommand = new Command("delete", taskIDToBeDeleted); // delete the updated one
            reversedCommandHistory.addReversedCommand(reversedCommand);
            commandHistory.addCommand(new Command("update", task));
            
            logger.log(Level.INFO, "end of processing update command");
            return new FeedbackMessage(String.format(MESSAGE_UPDATED_, "successfully"), myTaskList);
        }
        
    }
    
    private FeedbackMessage executeSort() throws IOException {
        logger.log(Level.INFO, "start processing sort command");
        myTaskList.sort(null);
        logger.log(Level.INFO, "end of processing sort command");
        return new FeedbackMessage(MESSAGE_SORTED, myTaskList);
    }
    
    private FeedbackMessage executeSearch(String keyword) throws IOException {
        logger.log(Level.INFO, "start processing search command");
        ArrayList<Tasks> searchList = storage.SearchTask(keyword);
        
        if (searchList.size() != 0) {
            myTaskList = searchList;
            logger.log(Level.INFO, "end of processing search command");
            return new FeedbackMessage(String.format(MESSAGE_SEARCH_, "successfully"), searchList);
        } else {
            logger.log(Level.INFO, "end of processing search command");
            return new FeedbackMessage(String.format(MESSAGE_SEARCH_, "failed: no such task"), myTaskList);
        }
    }
    
    private FeedbackMessage executeUndo() throws IOException {
        logger.log(Level.INFO, "start processing undo command");
        if (commandHistory.isEmpty()) {
            logger.log(Level.INFO, "end of processing undo command");
            return new FeedbackMessage(String.format(MESSAGE_UNDO_, "failed: no more command for undo"), myTaskList);
        } else {
            Command lastCommand = commandHistory.getLastCommand();
            String lastCommandType = lastCommand.getCommand();
            if (lastCommandType == "add") { //undo add operation
                Command commandToUndo = reversedCommandHistory.getLastReversedCommand();
                int taskIDToBeDeleted = commandToUndo.getSelectedIndexNumber();
                try {
                    storage.deleteTask(taskIDToBeDeleted);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "undo error");
                    e.printStackTrace();
                }
                int indexInPreviouslist = getIndexFromTaskID(myTaskList, taskIDToBeDeleted);
                myTaskList.remove(indexInPreviouslist);
            } else if (lastCommandType == "delete"){ // undo delete one task operation
               	Command commandToUndo = reversedCommandHistory.getLastReversedCommand();
                Tasks task = commandToUndo.getTask();
                storage.appendTask(task);
                myTaskList.add(task);
            } else if (lastCommandType == "deleteMulTask"){ //undo delete multiple task
                int undoDeleteNum = lastCommand.getSelectedIndexNumber();
                System.out.println(undoDeleteNum);
                for (int i = 0; i < undoDeleteNum; i++) {
                    Command commandToUndo = reversedCommandHistory.getLastReversedCommand();
                    Tasks task = commandToUndo.getTask();
                    storage.appendTask(task);
                    myTaskList.add(task);
                }
            } else if (lastCommandType == "update"){  // undo update operation
                Command commandToUndo = reversedCommandHistory.getLastReversedCommand();
                int taskIDToBeDeleted = commandToUndo.getSelectedIndexNumber();
                try {
                    storage.deleteTask(taskIDToBeDeleted);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "undo error");
                    e.printStackTrace();
                }
                int indexInPreviousList = getIndexFromTaskID(myTaskList, taskIDToBeDeleted);
                myTaskList.remove(indexInPreviousList);
                
                commandToUndo = reversedCommandHistory.getLastReversedCommand();
                storage.appendTask(commandToUndo.getTask());
                myTaskList.add(commandToUndo.getTask());
            } else if (lastCommandType == "clear") {
                
            }
            
            logger.log(Level.INFO, "end of processing undo command");
            return new FeedbackMessage(String.format(MESSAGE_UNDO_, "successfully!"), myTaskList);
            
        }
    }
    
    private FeedbackMessage executeClear() throws Exception {
        logger.log(Level.INFO, "start processing clear command");
        ArrayList<Tasks> previousTaskList = storage.ClearAll();
        int previousTaskListSize = previousTaskList.size();
        
        commandHistory.addCommand(new Command("clear"));
        
        logger.log(Level.INFO, "end of processing clear command");
        return new FeedbackMessage(MESSAGE_CLEAR, myTaskList);
    }
    
    // =========================================================================
    // get task's other information from known info
    // =========================================================================
    
    private int getTaskIDFromUserInput(int userInput) {
        Tasks requriedTask = myTaskList.get(userInput - 1);
        int taskID = requriedTask.getTaskID();
        return taskID;
    }
    
    private Tasks getTaskFromTaskID(ArrayList<Tasks> taskList, int taskID) {
        int taskListSize = taskList.size();
        Tasks currentTask = null;
        for (int i = 0; i < taskListSize; i++) {
            currentTask = taskList.get(i);
            if (currentTask.getTaskID() == taskID) {
                break;
            }
        }
        return currentTask;
    }
    
    private int getIndexFromTaskID(ArrayList<Tasks> taskList, int taskID) {
        myTaskList = taskList;
        int index = -1;
        for  (int i = 0; i < myTaskList.size(); i++) {
            if (myTaskList.get(i).getTaskID() == taskID) {
                index = i;
                break;
            }
        }
        return index;
    }
    
}