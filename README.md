[![](https://jitpack.io/v/io.samourai.code.wallet/boltzmann-java.svg)](https://jitpack.io/#io.samourai.code.wallet/boltzmann-java)

# Boltzmann-Java

A Java library computing the entropy of Bitcoin transactions and the linkability of their inputs and outputs.

For a description of the metrics :

- Bitcoin Transactions & Privacy (part 1) : https://gist.github.com/LaurentMT/e758767ca4038ac40aaf

- Bitcoin Transactions & Privacy (part 2) : https://gist.github.com/LaurentMT/d361bca6dc52868573a2

- Bitcoin Transactions & Privacy (part 3) : https://gist.github.com/LaurentMT/e8644d5bc903f02613c6


## Usage
### Run from command-line
```
java -jar target/boltzmann-java-[VERSION]-run.jar <txid> [maxCjIntrafeesRatio]
```
Example:
```
java -jar target/boltzmann-java-[VERSION]-run.jar 323df21f0b0756f98336437aa3d2fb87e02b59f1946b714a7b09df04d429dec2
```


Large txs:
```
java -Xmx20G -Xms20G -jar target/boltzmann-java-[VERSION]-run.jar <txid> [maxCjIntrafeesRatio]
```

### Developers
See [src/test/java/com/samourai/boltzmann/Example.java](src/test/java/com/samourai/boltzmann/Example.java)


## Requirements
Java >= 6 or Android API level >= 21


## Build instructions
Build with maven:

```
cd boltzmann-java
mvn clean install -Dmaven.test.skip=true
```

Or retrieve from [JitPack](https://jitpack.io/#Samourai-Wallet/boltzmann-java) repository


## Resources
Boltzmann is also available for Python: https://github.com/Samourai-Wallet/boltzmann