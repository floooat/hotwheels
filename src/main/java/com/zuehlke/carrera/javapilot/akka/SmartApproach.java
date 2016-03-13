package com.zuehlke.carrera.javapilot.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.relayapi.messages.*;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 *  this logic node increases the power level by 10 units per 0.5 second until it receives a penalty
 *  then reduces by ten units.
 */
public class SmartApproach extends UntypedActor {

    private final ActorRef sexymodderfucka;

    private double currentPower = 100;
    private long lastIncrease = 0;
    private double maxThres = 0;
    private double minThres = 1000;
    private int laps;

    private ArrayList<ArrayList<Double>> tracks;

    private int maxPower = 180; // Max for this phase;

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
                SmartApproach.class, () -> new SmartApproach(pilotActor, duration ));
    }
    private final int duration;

    public SmartApproach(ActorRef pilotActor, int duration) {
        lastIncrease = System.currentTimeMillis();
        this.sexymodderfucka = pilotActor;
        this.duration = duration;
        laps = 0;
        tracks = new ArrayList<ArrayList<Double>>();
        for (int i = 0; i < 10; ++i) {
            ArrayList<Double> aux = new ArrayList<>();
            tracks.add(aux);
        }
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

    private void cout(String str) {
        System.out.println(str);
    }

    private void doTracking() {
        int size1 = tracks.get(1).size();
        int size2 = tracks.get(2).size();
        int size = Math.min(size1,size2);
        for (int i = 0; i < size; ++i) {
            double f = tracks.get(1).get(i);
            double s = tracks.get(2).get(i);
            tracks.get(0).add(round((f+s)/2, 2));
        }
        // print
        char[] aux = new char[size];
        for (int j = 0; j < size; ++j) {
            Double n = tracks.get(0).get(j);
            String str;
            if (n > 0.15) {
                str = "C";
                aux[j] = 'C';
            }
            else if (n > 0.08) {
                str = "D";
                aux[j] = 'D';
            }
            else {
                str = "S";
                aux[j] = 'S';
            }
            System.out.println(aux[j]);
        }
        // the track is:
        boolean finished = false;
        int f = 0;
        char now = aux[0];
        String t = new String();
        while (!finished) {
            cout("START while");
            cout(aux[f]+" "+aux[f+1]);
            if (aux[f] != aux[f+1]) t += aux[f];
            f++;
            if (f == size-1) finished = true;

        }
        // finished
        cout("FINISHED TRACKING");
        for (int i = 0; i < t.length(); ++i) {
            if (t.charAt(i) == 'S') cout("STRAIGHT");
            else if (t.charAt(i) == 'C') cout("CURVE");
            else if (t.charAt(i) == 'D') ;//cout("DANGER");}
        }
    }

    private void handleRoundTimeMessage(RoundTimeMessage message) {
        if(message.getRoundDuration() > 72036854775807.0) {
            // Some fail val.
        } else {
            laps++;
            if (laps == 3) {
                doTracking();
            }
            System.out.println("NEW LAP "+ laps);
            // Second time we pass the goal. --> stop tracking
            if(trackingEnabled) {
                // Stop tracking and close the track.
                trackingEnabled = false;
                completeTheTrack = true;
                //System.out.println("Stop tracking the track.");
            }

            // First time we pass the goal. --> start tracking
            if(firstRound) {

                firstRound = false;
                trackingEnabled = true;
                //System.out.println("Start tracking the track.");
            }
        }
    }

    private double oldTrackTimestamp = 0;
    private void handleVelocityMessage(VelocityMessage message) {
        if(trackingEnabled || completeTheTrack) {
            // Track identification.
            System.out.println("GATE");
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
                double mean = gyrozHistory.currentMean();
                if(mean <= -1000) {
                    trackToAdd.type = TrackPart.TrackType.LEFTCURVE;
                } else if(mean >= 1000) {
                    trackToAdd.type = TrackPart.TrackType.RIGHTCURVE;
                } else {
                    trackToAdd.type = TrackPart.TrackType.STRAIGHT;
                }
                //System.out.println(trackToAdd.type);

                if(startTrack == null) {
                    startTrack = trackToAdd;
                } else {
                    startTrack.addNextTrack(trackToAdd);
                }
                oldTrackTimestamp =  message.getTimeStamp();

                if(completeTheTrack) {
                    // Complete it now.
                    startTrack.closeTrack();
                    //System.out.println("Track closed and completed.");
                    completeTheTrack = false;
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

        if (gyrz >= 0.0) {
            if (gyrz > maxThres) maxThres = gyrz;
            if (!firstRound) {
                double aux = round((gyrz/maxThres), 2);
                tracks.get(laps).add(aux);
                //if (aux > 0.1) System.out.print("R ");
                //else System.out.print("S ");
                //System.out.println("Z: " + aux);
            }
        }
        else {
            if (gyrz < minThres) minThres = gyrz;
            if (!firstRound) {
                double aux = round((gyrz/minThres), 2);
                tracks.get(laps).add(aux);
                //if (aux > 0.1) System.out.print("L ");
                //else System.out.print("S ");
                //System.out.println("Z: " + aux);
            }
        }
        /* if (laps == 0) {
            System.out.println(" MAX: "+ maxThres + " MIN: " + minThres);
        }*/

        //show ((int)gyrz);

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