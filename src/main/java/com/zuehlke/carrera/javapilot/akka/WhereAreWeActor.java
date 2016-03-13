package com.zuehlke.carrera.javapilot.akka;

/**
 * Created by lukasboehler on 12.03.16.
 */

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.relayapi.messages.*;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;

/**
 *  this logic node increases the power level by 10 units per 0.5 second until it receives a penalty
 *  then reduces by ten units.
 */
public class WhereAreWeActor extends UntypedActor {

    private final ActorRef sexymodderfucka;

    private double curveAcceleration = 3.5;
    private double yoloAcceleration = 219;
    private double tryHardAcceleration = 1.8;

    private double currentPower = 110;
    private double breakAfterTicks = -1;

    private int maxPower = 220; // Max for this phase;

    private TrackPart startTrack = null;
    private TrackPart currentTrackPart = null;

    private FloatingHistory gyrozHistory = new FloatingHistory(8);

    /**
     * @param pilotActor The central pilot actor
     * @param duration the period between two increases
     * @return the actor props
     */
    public static Props props( ActorRef pilotActor, int duration, TrackPart track ) {
        return Props.create(
                WhereAreWeActor.class, () -> new WhereAreWeActor(pilotActor, duration, track ));
    }
    private final int duration;

    public WhereAreWeActor(ActorRef pilotActor, int duration, TrackPart track) {
        this.sexymodderfucka = pilotActor;
        this.duration = duration;
        this.startTrack = track;
        this.currentTrackPart = startTrack;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if ( message instanceof SensorEvent) {
            handleSensorEvent((SensorEvent) message);

        } else if(message instanceof RoundTimeMessage) {
            handleRoundTimeMessage((RoundTimeMessage)message);

        } else if (message instanceof VelocityMessage) {
            handleVelocityMessage((VelocityMessage) message);

        } else if (message instanceof PenaltyMessage) {
            handlePenaltyMessage((PenaltyMessage)message);

        } else if ( message instanceof RaceStartMessage) {
            handleRaceStart();

        } else {
            unhandled(message);
        }
    }

    private void handleRoundTimeMessage(RoundTimeMessage message) {
        //currentSpeedUpFac += 2; // GO YOLO!
    }

    private void handleVelocityMessage(VelocityMessage message) {
        double currentVel = message.getVelocity();

        if(!currentTrackPart.perfectBreakPointReached) {
            currentTrackPart.breakPoint += tryHardAcceleration;
        }

        currentTrackPart = currentTrackPart.next;
        System.out.println("We are entering a " + currentTrackPart.type + " part.");

        handleUpcomingTrackPart(currentTrackPart);
    }

    private void handleUpcomingTrackPart(TrackPart trackPart) {
        if(trackPart.type == TrackPart.TrackType.STRAIGHT) {
            currentPower = yoloAcceleration;
            System.out.println("This track has the lenghtfactro of " + trackPart.lenghtFactor);
            breakAfterTicks = trackPart.breakPoint;
        }
        sexymodderfucka.tell(new PowerAction((int) currentPower), getSelf());
    }

    private void handleRaceStart() {
        currentPower = 0;
        maxPower = 220; // Max for this phase;
        gyrozHistory = new FloatingHistory(8);
    }

    private void handlePenaltyMessage(PenaltyMessage message) {
        System.out.println("We run too fast.");

        // This happens after the change --> use prev.g
        currentTrackPart.prev.breakPoint -= tryHardAcceleration;
        currentTrackPart.prev.perfectBreakPointReached = true;
    }

    /**
     * Strategy: increase quickly when standing still to overcome haptic friction
     * then increase slowly. Probing phase will be ended by the first penalty
     * @param message the sensor event coming in
     */
    private void handleSensorEvent(SensorEvent message) {

        double gyrz = gyrozHistory.shift(message.getG()[2]);
        //show ((int)gyrz);

        if(breakAfterTicks > -1) {
            breakAfterTicks--;
            if(breakAfterTicks <= 0) {
                System.out.println("Break baby.");
                currentPower = 110;
            }
        }

        if(currentTrackPart.type == TrackPart.TrackType.LEFTCURVE || currentTrackPart.type == TrackPart.TrackType.RIGHTCURVE) {
            // We better break.
            currentPower += curveAcceleration;
            //System.out.println("YO: " + currentPower);
        }

        sexymodderfucka.tell(new PowerAction((int) currentPower), getSelf());
    }

    private boolean iAmStillStanding() {
        return gyrozHistory.currentStDev() < 3;
    }

    private void show(int gyr2) {
        int scale = 120 * (gyr2 - (-10000) ) / 20000;
        System.out.println(StringUtils.repeat(" ", scale) + gyr2);
    }


}
