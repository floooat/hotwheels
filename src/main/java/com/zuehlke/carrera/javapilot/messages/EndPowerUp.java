package com.zuehlke.carrera.javapilot.messages;

/**
 * Created by bug on 12/03/16.
 */
public class EndPowerUp {
    private int powerMid;

    public EndPowerUp(int power) {
        powerMid = power;
    }
    public int getPowerMid() {
        return powerMid;
    }
}
