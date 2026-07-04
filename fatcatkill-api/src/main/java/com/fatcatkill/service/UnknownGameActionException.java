package com.fatcatkill.service;

public class UnknownGameActionException extends IllegalArgumentException {
    private final String actionType;

    public UnknownGameActionException(String actionType) {
        super("Unknown action type: " + actionType);
        this.actionType = actionType;
    }

    public String getActionType() {
        return actionType;
    }
}
