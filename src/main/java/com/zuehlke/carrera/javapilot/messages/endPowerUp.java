package com.zuehlke.carrera.javapilot.messages;

/**
 * Created by bug on 12/03/16.
 */
public class endPowerUp {
    private int powerMid;

    public endPowerUp(int power) {
        powerMid = power;
    }
    public int getPowerMid() {
        return powerMid;
    }
}
