package com.zuehlke.carrera.javapilot.akka;

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
public class AnalyseTrack extends UntypedActor {

    private final ActorRef sexymodderfucka;

    private double currentPower = 0;
    private long lastIncrease = 0;

    private int maxPower = 180; // Max for this phase;

    private boolean probing = true;
    private boolean trackCompleted = false;
    private boolean startTrackingTheTrack = false;
    private TrackPart startTrack = null;

    private FloatingHistory gyrozHistory = new FloatingHistory(8);

    /**
     * @param pilotActor The central pilot actor
     * @param duration the period between two increases
     * @return the actor props
     */
    public static Props props( ActorRef pilotActor, int duration ) {
        return Props.create(
                AnalyseTrack.class, () -> new AnalyseTrack(pilotActor, duration ));
    }
    private final int duration;

    public AnalyseTrack(ActorRef pilotActor, int duration) {
        lastIncrease = System.currentTimeMillis();
        this.sexymodderfucka = pilotActor;
        this.duration = duration;
    }

    @Override
    public void onReceive(Object message) throws Exception {

        if ( message instanceof SensorEvent ) {
            handleSensorEvent((SensorEvent) message);

        } else if(message instanceof RoundTimeMessage) {
            handleRoundTimeMessage((RoundTimeMessage)message);

        } else if (message instanceof VelocityMessage) {
            handleVelocityMessage((VelocityMessage) message);

        } else if (message instanceof PenaltyMessage) {
            handlePenaltyMessage();

        } else if ( message instanceof RaceStartMessage) {
            handleRaceStart();

        } else {
            unhandled(message);
        }
    }

    private void handleRoundTimeMessage(RoundTimeMessage message) {
        if(message.getRoundDuration() > 72036854775807.0) {
            // Some fail val.
        } else {
            if(startTrackingTheTrack) {
                startTrackingTheTrack = false;
                trackCompleted = true;
                startTrack.closeTrack();
                System.out.println("Track closed and completed.");

            } else {
                System.out.println("Start tracking the track.");
                startTrackingTheTrack = true;
            }
        }
    }

    private double oldTrackTimestamp = 0;
    private void handleVelocityMessage(VelocityMessage message) {
        if(startTrackingTheTrack) {
            // Track identification.
            if(oldTrackTimestamp == 0) {
                oldTrackTimestamp = message.getTimeStamp();
                System.out.println("Trackpart start.");
            } else {
                double timeforTrack = message.getTimeStamp() - oldTrackTimestamp;
                double velocity = message.getVelocity();

                double lenghtOfTrackPart = velocity * timeforTrack;
                System.out.println("Got a trackpart with lenght " + lenghtOfTrackPart);

                // addd.
                TrackPart trackToAdd = new TrackPart();
                trackToAdd.lenght = lenghtOfTrackPart;
                if(startTrack == null) {
                    startTrack = trackToAdd;
                } else {
                    startTrack.addNextTrack(trackToAdd);
                }
                oldTrackTimestamp =  message.getTimeStamp();
            }
        }
    }

    private void handleRaceStart() {
        currentPower = 0;
        lastIncrease = 0;
        maxPower = 180; // Max for this phase;
        probing = true;
        gyrozHistory = new FloatingHistory(8);
    }

    private void handlePenaltyMessage() {
        currentPower -= 10;
        sexymodderfucka.tell(new PowerAction((int) currentPower), getSelf());
        probing = false;
    }

    /**
     * Strategy: increase quickly when standing still to overcome haptic friction
     * then increase slowly. Probing phase will be ended by the first penalty
     * @param message the sensor event coming in
     */
    private void handleSensorEvent(SensorEvent message) {

        double gyrz = gyrozHistory.shift(message.getG()[2]);
        //show ((int)gyrz);

        if (probing) {
            if (iAmStillStanding()) {
                increase(0.5);
            } else if (message.getTimeStamp() > lastIncrease + duration) {
                lastIncrease = message.getTimeStamp();
                increase(3);
            }
            if(currentPower >= 120.0) {
                currentPower = 120.0;
            }
        }

        sexymodderfucka.tell(new PowerAction((int) currentPower), getSelf());
    }

    private int increase ( double val ) {
        currentPower = Math.min ( currentPower + val, maxPower );
        return (int)currentPower;
    }

    private boolean iAmStillStanding() {
        return gyrozHistory.currentStDev() < 3;
    }

    private void show(int gyr2) {
        int scale = 120 * (gyr2 - (-10000) ) / 20000;
        System.out.println(StringUtils.repeat(" ", scale) + gyr2);
    }


}
