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

    public double lenght;
    public TrackPart prev;
    public TrackPart next;

    public void addNextTrack(TrackPart track) {
        if(this.next == null) {
            this.next = track;
            track.prev = this;
        } else {
            this.next.addNextTrack(track);
        }
    }

    public void closeTrack() {
        TrackPart last = this;
        while(last.next != null) {
            last = last.next;
        }
        this.prev = last;
        last.next = this;
    }
}
