package com.zuehlke.carrera.javapilot.services;

import com.zuehlke.carrera.javapilot.akka.TrackPart;

/**
 * Created by lukasboehler on 12.03.16.
 */
public class ChangeActorMessage {
    public String changeTo;
    public TrackPart track;

    public ChangeActorMessage(String to, TrackPart track) {
        this.changeTo = to;
        this.track = track;
    }
}
