package com.zuehlke.carrera.javapilot.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.services.ChangeActorMessage;
import com.zuehlke.carrera.relayapi.messages.*;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 *  this logic node increases the power level by 10 units per 0.5 second until it receives a penalty
 *  then reduces by ten units.
 */
public class AnalyseTrack extends UntypedActor {

    private final ActorRef sexymodderfucka;

    private double currentPower = 100;
    private long lastIncrease = 0;
    private double maxThres = 0;
    private double minThres = 1000;

    private int maxPower = 180; // Max for this phase;
    private double maxGForce = 0.0;

    private boolean probing = true;
    private boolean completeTheTrack = false;
    private boolean trackingEnabled = false;
    private boolean firstRound = true;
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
            // Second time we pass the goal. --> stop tracking
            if(trackingEnabled) {
                // Stop tracking and close the track.
                trackingEnabled = false;
                completeTheTrack = true;
                System.out.println("Stop tracking the track.");
            }

            // First time we pass the goal. --> start tracking
            if(firstRound) {
                firstRound = false;
                trackingEnabled = true;
                System.out.println("Start tracking the track.");
            }
        }
    }

    private double oldTrackTimestamp = 0;
    private void handleVelocityMessage(VelocityMessage message) {
        if(trackingEnabled || completeTheTrack) {
            // Track identification.
            if(oldTrackTimestamp == 0) {
                oldTrackTimestamp = message.getTimeStamp();
                //System.out.println("Trackpart start.");
            } else {
                double timeforTrack = message.getTimeStamp() - oldTrackTimestamp;
                double velocity = message.getVelocity();

                double lenghtOfTrackPart = velocity * timeforTrack;
                //System.out.println("Got a trackpart with lenght " + lenghtOfTrackPart);

                // addd.
                TrackPart trackToAdd = new TrackPart();
                trackToAdd.lenght = lenghtOfTrackPart;

                // Type
                double curveLimit = maxGForce / 5;
                System.out.println(curveLimit);
                double mean = gyrozHistory.currentMean();
                if(mean <= -curveLimit) {
                    trackToAdd.type = TrackPart.TrackType.LEFTCURVE;
                } else if(mean >= curveLimit) {
                    trackToAdd.type = TrackPart.TrackType.RIGHTCURVE;
                } else {
                    trackToAdd.type = TrackPart.TrackType.STRAIGHT;
                }
                System.out.println(trackToAdd.type);

                if(startTrack == null) {
                    startTrack = trackToAdd;
                } else {
                    startTrack.addNextTrack(trackToAdd);
                }
                oldTrackTimestamp =  message.getTimeStamp();

                if(completeTheTrack) {
                    // Complete it now.
                    startTrack.closeTrack();
                    System.out.println("Track closed and completed.");
                    completeTheTrack = false;

                    this.sexymodderfucka.tell(new ChangeActorMessage("WhereAreWeActor", this.startTrack), getSelf());
                }
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

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    /**
     * Strategy: increase quickly when standing still to overcome haptic friction
     * then increase slowly. Probing phase will be ended by the first penalty
     * @param message the sensor event coming in
     */
    private void handleSensorEvent(SensorEvent message) {

        double gyrz = gyrozHistory.shift(message.getG()[2]);

        if (gyrz >= 0) {
            if (gyrz > maxThres) maxThres = gyrz;
            if (!firstRound) {
                double aux = (gyrz/maxThres);
                //System.out.println("Z: " + round(aux, 2));
            }
        }
        else {
            if (gyrz < minThres) minThres = gyrz;
            if (!firstRound) {
                double aux = (gyrz/minThres);
                //System.out.println("Z: " + round(aux, 2));
            }
        }
        //System.out.println(" MAX: "+ maxThres + " MIN: " + minThres);

        //show ((int)gyrz);

        if(Math.abs(gyrz) > maxGForce) {
            maxGForce = Math.abs(gyrz);
        }

        if (probing) {
            if (iAmStillStanding()) {
                increase(0.5);
            } else if (message.getTimeStamp() > lastIncrease + duration) {
                lastIncrease = message.getTimeStamp();
                increase(3);
            }
            if(probing && currentPower >= 110.0) {
                currentPower = 110.0;
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
