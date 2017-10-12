package com.samourai.efficiencyscore.linker;

import com.samourai.efficiencyscore.aggregator.TxosAggregates;
import com.samourai.efficiencyscore.aggregator.TxosAggregatesMatches;
import com.samourai.efficiencyscore.aggregator.TxosAggregator;
import com.samourai.efficiencyscore.aggregator.TxosAggregatorResult;
import com.samourai.efficiencyscore.beans.Txos;
import com.samourai.efficiencyscore.processor.TxProcessorConst;
import com.samourai.efficiencyscore.utils.ListsUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Compute the entropy and the inputs/outputs linkability of a of Bitcoin transaction.
 */
public class TxosLinker {
    // Default maximum duration in seconds
    private static final int MAX_DURATION = 180;

    // Max number of inputs (or outputs) which can be processed by this algorithm
    private static final int MAX_NB_TXOS = 12;

    // Markers
    private static final String MARKER_FEES = "FEES";
    private static final String MARKER_PACK = "PACK_I";

    // fees associated to the transaction
    private long feesOrig;
    private long fees;

    private List<Pack> packs = new ArrayList<>();

    // max number of txos. Txs with more than max_txos inputs or outputs are not processed.
    int maxTxos;

    // Maximum duration of the script (in seconds)
    int maxDuration = MAX_DURATION;

    /**
     * Constructor.
     * @param fees amount of fees associated to the transaction
     * @param maxDuration max duration allocated to processing of a single tx (in seconds)
     * @param maxTxos max number of txos. Txs with more than max_txos inputs or outputs are not processed.
     */
    public TxosLinker(long fees, int maxDuration, int maxTxos) {
        this.feesOrig = fees;
        this.maxDuration = maxDuration;
        this.maxTxos = maxTxos;
    }

    /**
     * Computes the linkability between a set of input txos and a set of output txos.
     * @param txos list of inputs/ouputs txos [(v1_id, v1_amount), ...]
     * @param linkedTxos list of sets storing linked input txos. Each txo is identified by its id
     * @param options actions to be applied
     * @param intraFees tuple (fees_maker, fees_taker) of max "fees" paid among participants used for joinmarket transactions
                        fees_maker are potential max "fees" received by a participant from another participant
                        fees_taker are potential max "fees" paid by a participant to all others participants
     * @return
     */
    public TxosLinkerResult process(Txos txos, Collection<Set<String>> linkedTxos, Set<TxosLinkerOptionEnum> options, IntraFees intraFees) {
        // Packs txos known as being controlled by a same entity
        // It decreases the entropy and speeds-up computations
        if(linkedTxos!=null && !linkedTxos.isEmpty()) {
            txos = packLinkedTxos(linkedTxos, txos); // TODO test
        }

        // Manages fees
        if (options.contains(TxosLinkerOptionEnum.MERGE_FEES) && this.feesOrig > 0) {
            // Manages fees as an additional output (case of sharedsend by blockchain.info).
            // Allows to reduce the volume of computations to be done.
            this.fees = 0;
            txos.getOutputs().put(MARKER_FEES, this.feesOrig);
        }
        else {
            this.fees = this.feesOrig;
        }

        TxosAggregator aggregator = new TxosAggregator();

        int nbOuts = txos.getOutputs().size();
        int nbIns = txos.getInputs().size();
        boolean hasIntraFees = intraFees != null && intraFees.hasFees();

        // Checks deterministic links
        int nbCmbn = 0;
        int[][] matLnk = new int[nbOuts][nbIns];
        Set<int[]> dtrmLnks = new LinkedHashSet<>();
        if (options.contains(TxosLinkerOptionEnum.PRECHECK) && this.checkLimitOk(txos) && !hasIntraFees) {

            // Prepares the data
            TxosAggregates allAgg = aggregator.prepareData(txos);
            txos = new Txos(allAgg.getInAgg().getTxos(), allAgg.getOutAgg().getTxos());
            TxosAggregatesMatches aggMatches = aggregator.matchAggByVal(allAgg, fees, intraFees);

            // Checks deterministic links
            dtrmLnks = aggregator.checkDtrmLinks(txos, allAgg, aggMatches);

            // If deterministic links have been found, fills the linkability matrix
            // (returned as result if linkability is not processed)
            if (!dtrmLnks.isEmpty()) {
                final int[][] matLnkFinal = matLnk;
                dtrmLnks.forEach(dtrmLnk ->
                    matLnkFinal[dtrmLnk[0]][dtrmLnk[1]] = 1
                );
            }
        }

        // Checks if all inputs and outputs have already been merged
        if (nbIns == 0 || nbOuts == 0) {
            nbCmbn = 1;
            for (int[] line : matLnk) {
                Arrays.fill(line, 1);
            }
        }
        else if(options.contains(TxosLinkerOptionEnum.LINKABILITY) && this.checkLimitOk(txos)) {
            // Packs deterministic links if needed
            if (!dtrmLnks.isEmpty()) {

                final Txos txosFinal = txos;
                List<Set<String>> dtrmCoordsList = dtrmLnks.stream().map(array -> {
                    Set<String> set = new LinkedHashSet<>();
                    set.add(""+txosFinal.getOutputs().keySet().toArray()[array[0]]);
                    set.add(""+txosFinal.getInputs().keySet().toArray()[array[1]]);
                    return set;
                }).collect(Collectors.toList());
                txos = packLinkedTxos(dtrmCoordsList, txos);
            }

            // Prepares data
            TxosAggregates allAgg = aggregator.prepareData(txos);
            txos = new Txos(allAgg.getInAgg().getTxos(), allAgg.getOutAgg().getTxos());
            TxosAggregatesMatches aggMatches = aggregator.matchAggByVal(allAgg, fees, intraFees);

            // Computes a matrix storing a tree composed of valid pairs of input aggregates
            Map<Integer,List<int[]>> matInAggCmbn = aggregator.computeInAggCmbn(aggMatches);

            // Builds the linkability matrix
            TxosAggregatorResult result = aggregator.computeLinkMatrix(txos, allAgg, aggMatches, matInAggCmbn, maxDuration);
            nbCmbn = result.getNbCmbn();
            matLnk = result.getMatLnkCombinations();

            // Refresh deterministical links
            dtrmLnks = aggregator.findDtrmLinks(matLnk, nbCmbn);
        }

        if (!packs.isEmpty()) {
            // Unpacks the matrix
            UnpackLinkMatrixResult unpackResult = unpackLinkMatrix(matLnk, txos);
            txos = unpackResult.getTxos();
            matLnk = unpackResult.getMatLnk();

            // Refresh deterministical links // TODO reintegrate in unpackLinkMatrix?
            dtrmLnks = aggregator.findDtrmLinks(matLnk, nbCmbn);
        }

        return new TxosLinkerResult(nbCmbn, matLnk, dtrmLnks, txos);
    }

    /**
     * Packs input txos which are known as being controlled by a same entity
     * @param linkedTxos list of sets storing linked input txos. Each txo is identified by its "id"
     * @return Txos
     */
    protected Txos packLinkedTxos(Collection<Set<String>> linkedTxos, Txos txos) {
        int idx = packs.size();

        Txos packedTxos = new Txos(new LinkedHashMap<>(txos.getInputs()), new LinkedHashMap<>(txos.getOutputs()));

        // Merges packs sharing common elements
        List<Set<String>> newPacks = ListsUtils.mergeSets(linkedTxos);

        for (Set<String> pack : newPacks) {
            List<AbstractMap.SimpleEntry<String, Long>> ins = new ArrayList<>();
            long valIns = 0;

            for (String inPack : pack) {
                if (inPack.startsWith(TxProcessorConst.MARKER_INPUT)) {
                    long inPackValue = packedTxos.getInputs().get(inPack);
                    ins.add(new AbstractMap.SimpleEntry<>(inPack, inPackValue));
                    valIns += packedTxos.getInputs().get(inPack);
                    packedTxos.getInputs().remove(inPack);
                }
            }
            idx++;

            if (!ins.isEmpty()) {
                String lbl = MARKER_PACK+idx;
                packedTxos.getInputs().put(lbl, valIns);
                packs.add(new Pack(lbl, PackType.INPUTS, ins, new ArrayList<>()));
            }
        }
        return packedTxos;
    }

    /**
     * Unpacks linked txos in the linkability matrix.
     * @param matLnk linkability matrix to be unpacked
     * @param txos packed txos containing the pack
     * @return UnpackLinkMatrixResult
     */
    protected UnpackLinkMatrixResult unpackLinkMatrix(int[][] matLnk, Txos txos) {
        int[][] matRes = matLnk.clone();
        Txos newTxos = new Txos(new LinkedHashMap<>(txos.getInputs()), new LinkedHashMap<>(txos.getOutputs()));
        List<Pack> reversedPacks = new LinkedList<>(packs);
        Collections.reverse(reversedPacks);

        for (int i=packs.size()-1; i>=0; i--) { // packs in reverse order
            Pack pack = packs.get(i);
            UnpackLinkMatrixResult result = unpackLinkMatrix(matRes, newTxos, pack);
            matRes = result.getMatLnk();
            newTxos = result.getTxos();
        }
        return new UnpackLinkMatrixResult(newTxos, matRes);
    }

    /**
     * Unpacks txos in the linkability matrix for one pack.
     * @param matLnk linkability matrix to be unpacked
     * @param txos packed txos containing the pack
     * @param pack pack to unpack
     * @return UnpackLinkMatrixResult
     */
    protected UnpackLinkMatrixResult unpackLinkMatrix(int[][] matLnk, Txos txos, Pack pack) {

        int[][] newMatLnk = null;
        Txos newTxos = txos;

        if (matLnk != null) {
            if (PackType.INPUTS.equals(pack.getPackType())) {
                // unpack txos
                Map<String,Long> newInputs = new LinkedHashMap<>(txos.getInputs());
                int idx = unpackTxos(txos.getInputs(), pack, newInputs);
                newTxos = new Txos(newInputs, txos.getOutputs());

                // unpack matLnk
                int nbIns = txos.getInputs().size() + pack.getIns().size() - 1;
                int nbOuts = txos.getOutputs().size();
                final int[][] newMatLnkFinal = new int[nbOuts][nbIns];
                IntStream.range(0, nbOuts).parallel().forEach(i ->
                    IntStream.range(0, nbIns).parallel().forEach(j -> {
                        if (j < idx) {
                            // keep values before pack
                            newMatLnkFinal[i][j] = matLnk[i][j];
                        } else if (j >= (idx + pack.getIns().size())) {
                            // keep values after pack
                            newMatLnkFinal[i][j] = matLnk[i][j - pack.getIns().size() + 1];
                        } else {
                            // insert values for unpacked txos
                            newMatLnkFinal[i][j] = matLnk[i][idx];
                        }
                    })
                );
                newMatLnk = newMatLnkFinal;
            }
        }
        return new UnpackLinkMatrixResult(newTxos, newMatLnk);
    }

    /**
     * Unpack txos for one pack.
     * @param currentTxos packed txos containing the pack
     * @param unpackedTxos map to return unpacked txos
     * @param pack pack to unpack
     * @return idx pack indice in txos
     */
    protected int unpackTxos(Map<String,Long> currentTxos, Pack pack, Map<String,Long> unpackedTxos) {
        // find pack indice in txos
        String[] txoKeys = currentTxos.keySet().toArray(new String[]{});
        int idx = IntStream.range(0, txoKeys.length).filter(i -> pack.getLbl().equals(txoKeys[i])).findFirst().getAsInt();

        unpackedTxos.clear();
        Iterator<Map.Entry<String,Long>> currentTxosIterator = currentTxos.entrySet().iterator();
        // keep txos before pack
        IntStream.range(0, idx).parallel().forEach(i -> {
            Map.Entry<String,Long> entry = currentTxosIterator.next();
            unpackedTxos.put(entry.getKey(), entry.getValue());
        });
        // insert packed txos
        pack.getIns().parallelStream().forEach(entry -> {
            unpackedTxos.put(entry.getKey(), entry.getValue());
        });
        currentTxosIterator.next(); // skip packed txo
        // keep txos after pack
        while (currentTxosIterator.hasNext()) {
            Map.Entry<String,Long> entry = currentTxosIterator.next();
            unpackedTxos.put(entry.getKey(), entry.getValue());
        }
        return idx;
    }

    // LIMITS
    private boolean checkLimitOk(Txos txos) {
        int lenIn = txos.getInputs().size();
        int lenOut = txos.getOutputs().size();
        int maxCard = Math.max(lenIn, lenOut);
        return (maxCard <= maxTxos);
    }
}
