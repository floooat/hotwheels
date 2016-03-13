package com.zuehlke.carrera.javapilot.akka;

/**
 * Florian Böhler
 * fbo2141
 * 12/03/16
 */

public class TrackPart {

    public enum TrackType {
        LEFTCURVE, RIGHTCURVE, STRAIGHT
    }

    public double lenght;
    public TrackPart prev;
    public TrackPart next;
    public TrackType type;
    public double lenghtFactor = -1.0;
    public double breakPoint = 15;
    public boolean perfectBreakPointReached = false;

    public void addNextTrack(TrackPart track) {
        if(this.next == null) {
            this.next = track;
            track.prev = this;
        } else {
            this.next.addNextTrack(track);
        }
    }

    public void closeTrack() {
        double longestTrack = this.lenght;
        TrackPart last = this;
        while(last.next != null) {
            last = last.next;
            if(last.lenght > longestTrack) {
                longestTrack = last.lenght;
            }
        }
        this.prev = last;
        last.next = this;

        last = this;
        while(last.lenghtFactor == -1.0) {
            last.lenghtFactor = last.lenght / longestTrack;
            last.breakPoint = last.breakPoint * last.lenghtFactor;
            System.out.println("Shit: " + last.breakPoint);
            last = last.next;
        }
    }
}
