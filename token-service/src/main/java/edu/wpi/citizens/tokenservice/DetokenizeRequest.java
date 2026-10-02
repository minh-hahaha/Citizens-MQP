package edu.wpi.citizens.tokenservice;

/** No validation annotations on purpose: a bad request is answered with the generic denial. */
public record DetokenizeRequest(String routingNumber, String tokenValue) {
}
