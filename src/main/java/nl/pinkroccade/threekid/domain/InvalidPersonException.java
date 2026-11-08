package nl.pinkroccade.threekid.domain;

/**
 * Thrown when an incoming person record is invalid, either on its own (for example a missing ID) or because it
 * conflicts with the people we already know (for example a third parent). The API answers with 400 Bad Request.
 */
public class InvalidPersonException extends RuntimeException {

    public InvalidPersonException(String message) {
        super(message);
    }
}
