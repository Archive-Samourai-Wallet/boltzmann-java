package com.samourai.efficiencyscore;

import com.samourai.efficiencyscore.beans.BoltzmannResult;
import com.samourai.efficiencyscore.beans.BoltzmannSettings;
import com.samourai.efficiencyscore.beans.Txos;

/** Example of Boltzmann usage. */
public class Example {

  public static void main(String[] args) {

    // configure and initialize Boltzmann
    BoltzmannSettings settings = new BoltzmannSettings();
    // settings.setXXX()

    // initialize Boltzmann
    Bolzmann bolzmann = new Bolzmann(settings);

    // configure TXOS to process
    Txos txos = new Txos();
    txos.getInputs().put("1KHWnqHHx3fQuRwPmwhZGbSYzDbN3SdhoR", 4900000000L);
    txos.getInputs().put("15Z5YJaaNSxeynvr6uW6jQZLwq3n1Hu6RX", 100000000L);

    txos.getOutputs().put("1NKToQ48X5qaMo1ndexWmHKnn6FNNViivq", 4900000000L);
    txos.getOutputs().put("15Z5YJaaNSxeynvr6uW6jQZLwq3n1Hu6RX", 100000000L);

    // process
    BoltzmannResult result = bolzmann.process(txos);

    // get results
    // result.getXXX
  }
}
