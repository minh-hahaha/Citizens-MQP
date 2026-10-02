package edu.wpi.citizens.tokenservice;

/** Produces candidate token values. Uniqueness is enforced by the database, not here. */
public interface TokenGenerator {

    String next();
}
