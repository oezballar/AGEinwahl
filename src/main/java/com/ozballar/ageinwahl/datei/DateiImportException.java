package com.ozballar.ageinwahl.datei;

public class DateiImportException extends RuntimeException {

    public DateiImportException(String message) {
        super(message);
    }

    public DateiImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
