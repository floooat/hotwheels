package com.zuehlke.carrera.javapilot.akka;

/**
 * Florian Böhler
 * fbo2141
 * 12/03/16
 */

public class TrackPart {

    public enum type {
        LEFTCURVE, RIGHTCURVE, STRAIGHT
    }

    public int lenght;
    public TrackPart prev;
    public TrackPart next;


}
