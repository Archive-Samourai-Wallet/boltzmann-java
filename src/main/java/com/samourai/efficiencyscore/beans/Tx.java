package com.samourai.efficiencyscore.beans;

public class Tx {

    private Txos txos;

    public Tx(Txos txos) {
        this.txos = txos;
    }

    public Txos getTxos() {
        return txos;
    }
}
