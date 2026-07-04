package com.fatcatkill.model;

public class InvalidGameActionPayloadException extends RuntimeException implements MessagePayloadException {
    private final MessagePayload messagePayload;

    public InvalidGameActionPayloadException(MessagePayload messagePayload) {
        super(messagePayload == null ? null : messagePayload.getFallback());
        this.messagePayload = messagePayload;
    }

    @Override
    public MessagePayload getMessagePayload() {
        return messagePayload;
    }
}