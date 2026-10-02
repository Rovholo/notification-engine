package com.bitkulcha.notification_engine.exception;

public class BrokerNotFoundException extends RuntimeException {

    public BrokerNotFoundException(String message) {
        super(message);
    }
}
