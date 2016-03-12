package com.zuehlke.carrera.javapilot.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.PowerControl;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;
import com.zuehlke.carrera.javapilot.messages.*;

/**
 *  this logic node increases the power level by 10 units per 0.5 second until it receives a penalty
 *  then reduces by ten units.
 */
public class PowerUpUntilPenalty extends UntypedActor {

    private final ActorRef kobayashi;

    private int cPenalties;

    private double currentPower = 0;

    private long lastIncrease = 0;

    private int maxPower = 180; // Max for this phase;

    private boolean probing = true;

    private FloatingHistory gyrozHistory = new FloatingHistory(8);

    /**
     * @param pilotActor The central pilot actor
     * @param duration the period between two increases
     * @return the actor props
     */
    public static Props props( ActorRef pilotActor, int duration ) {
        return Props.create(
                PowerUpUntilPenalty.class, () -> new PowerUpUntilPenalty(pilotActor, duration ));
    }
    private final int duration;

    public PowerUpUntilPenalty(ActorRef pilotActor, int duration) {
        lastIncrease = System.currentTimeMillis();
        this.kobayashi = pilotActor;
        this.duration = duration;
        cPenalties = 0;
    }


    @Override
    public void onReceive(Object message) throws Exception {

        if ( message instanceof SensorEvent ) {
            handleSensorEvent((SensorEvent) message);

        } else if ( message instanceof PenaltyMessage) {
            System.out.println("PENALTY");
            handlePenaltyMessage ();

        } else if ( message instanceof RaceStartMessage) {
            handleRaceStart();

        } else {
            unhandled(message);
        }
    }

    private void handleRaceStart() {
        cPenalties = 0;
        currentPower = 0;
        lastIncrease = 0;
        maxPower = 180; // Max for this phase;
        probing = true;
        gyrozHistory = new FloatingHistory(8);
    }

    private void handlePenaltyMessage() {
        cPenalties +=1;
        if (cPenalties > 1) {
            currentPower -= 3;
        } else if (cPenalties > 0) {
            currentPower -= 6;
        } else {
            currentPower -= 8;
        }
        kobayashi.tell(new PowerAction((int)currentPower), getSelf());
        probing = false;
        if (cPenalties == 3) {
            System.out.println("END FIRST PHASE");
            kobayashi.tell(new endPowerUp((int) currentPower), getSelf());
            //kobayashi.tell(new endPowerUp(f(int) currentPower), getSelf());
        }
    }

    /**
     * Strategy: increase quickly when standing still to overcome haptic friction
     * then increase slowly. Probing phase will be ended by the first penalty
     * @param message the sensor event coming in
     */
    private void handleSensorEvent(SensorEvent message) {

        double gyrz = gyrozHistory.shift(message.getG()[2]);
         show ((int)gyrz);

        if (cPenalties < 3) {
            if (iAmStillStanding()) {
                increase(0.5);
            } else if (message.getTimeStamp() > lastIncrease + duration) {
                lastIncrease = message.getTimeStamp();
                if (cPenalties > 1) {
                    increase(1);
                } else if (cPenalties > 0) {
                    increase(2.5);
                } else {
                    increase(4);
                }
            }
        }

        kobayashi.tell(new PowerAction((int)currentPower), getSelf());
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
