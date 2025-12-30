package cs451;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Lattice Agreement (multi-shot) following the provided proposer/acceptor pseudocode.
 * One slot is executed per proposal in the config; slots are run sequentially.
 */
public class LatticeAgreement {
    private enum LaMessageType {
        PROP,
        ACK,
        NACK,
        OTHER
    }

    private final UniformReliableBroadcast urb;
    private final LinkedBlockingQueue<String> logQueue;
    private final int myId;
    private final int hostCount;
    private final int f;
    private final List<List<Integer>> proposals;
    private final Runnable onComplete;

    private final Map<Integer, SlotState> slots = new ConcurrentHashMap<>();
    private int nextProposalSlot = 0;
    private int decidedCount = 0;

    public LatticeAgreement(UniformReliableBroadcast urb, int myId, int hostCount, List<List<Integer>> proposals, LinkedBlockingQueue<String> logQueue, Runnable onComplete) {
        this.urb = urb;
        this.myId = myId;
        this.hostCount = hostCount;
        this.proposals = proposals;
        this.logQueue = logQueue;
        this.f = (hostCount - 1) / 2;
        this.onComplete = onComplete;
    }

    public void start() {
        if (proposals != null && !proposals.isEmpty()) {
            proposeNextSlot();
        }
    }

    public void onDeliver(int senderId, String payload) {
        ParsedMessage msg = parse(payload);
        if (msg.type == LaMessageType.OTHER) {
            return;
        }

        if (msg.slot < 0 || msg.slot >= proposals.size()) {
            return;
        }

        SlotState slotState = slots.computeIfAbsent(msg.slot, s -> new SlotState(s));

        switch (msg.type) {
            case PROP:
                slotState.handleProposal(senderId, msg);
                break;
            case ACK:
                slotState.handleAck(senderId, msg);
                break;
            case NACK:
                slotState.handleNack(senderId, msg);
                break;
            default:
                break;
        }
    }

    private void proposeNextSlot() {
        if (nextProposalSlot >= proposals.size()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        SlotState state = slots.computeIfAbsent(nextProposalSlot, s -> new SlotState(s));
        state.startProposal(new HashSet<>(proposals.get(nextProposalSlot)));
    }

    private void onSlotDecided(int slot, Set<Integer> decidedValues) {
        if (slot != nextProposalSlot) {
            // Ignore out-of-order decides; we run sequentially.
            return;
        }

        decidedCount++;

        List<Integer> decided = new ArrayList<>(decidedValues);
        Collections.sort(decided);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < decided.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(decided.get(i));
        }
        try {
            logQueue.put(sb.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        nextProposalSlot++;
        if (decidedCount >= proposals.size()) {
            if (onComplete != null) {
                onComplete.run();
            }
        } else {
            proposeNextSlot();
        }
    }

    private ParsedMessage parse(String payload) {
        if (payload == null || payload.isEmpty()) {
            return ParsedMessage.other();
        }

        String[] tokens = payload.split(" ");
        if (tokens.length < 4) {
            return ParsedMessage.other();
        }

        if (!"LA".equals(tokens[0])) {
            return ParsedMessage.other();
        }

        LaMessageType type;
        switch (tokens[1]) {
            case "PROP":
                type = LaMessageType.PROP;
                break;
            case "ACK":
                type = LaMessageType.ACK;
                break;
            case "NACK":
                type = LaMessageType.NACK;
                break;
            default:
                return ParsedMessage.other();
        }

        try {
            int slot = Integer.parseInt(tokens[2]);
            int proposalNum = Integer.parseInt(tokens[3]);
            int target = tokens.length >= 5 ? Integer.parseInt(tokens[4]) : -1;

            Set<Integer> values = new HashSet<>();
            int startIdx;
            if (type == LaMessageType.PROP) {
                startIdx = 4;
            } else if (type == LaMessageType.NACK) {
                startIdx = 5;
            } else {
                startIdx = tokens.length; // ACK has no values
            }

            for (int i = startIdx; i < tokens.length; i++) {
                if (!tokens[i].isEmpty()) {
                    values.add(Integer.parseInt(tokens[i]));
                }
            }

            return new ParsedMessage(type, slot, proposalNum, target, values);
        } catch (NumberFormatException e) {
            return ParsedMessage.other();
        }
    }

    private String encode(LaMessageType type, int slot, int proposalNum, int target, Set<Integer> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("LA ").append(type.name()).append(' ').append(slot).append(' ').append(proposalNum);
        if (type != LaMessageType.PROP) {
            sb.append(' ').append(target);
        }
        if (type == LaMessageType.PROP || type == LaMessageType.NACK) {
            List<Integer> ordered = new ArrayList<>(values);
            Collections.sort(ordered);
            for (int v : ordered) {
                sb.append(' ').append(v);
            }
        }
        return sb.toString();
    }

    private void sendAck(int slot, int proposalNumber, int target) {
        String payload = encode(LaMessageType.ACK, slot, proposalNumber, target, Collections.emptySet());
        urb.broadcast(payload);
    }

    private void sendNack(int slot, int proposalNumber, int target, Set<Integer> values) {
        String payload = encode(LaMessageType.NACK, slot, proposalNumber, target, values);
        urb.broadcast(payload);
    }

    private class SlotState {
        private final int slot;
        private final Set<Integer> acceptedValue = new HashSet<>();

        private boolean active = false;
        private boolean decided = false;
        private int proposalNumber = 0;
        private Set<Integer> proposedValue = new HashSet<>();
        private int ackCount = 0;
        private int nackCount = 0;

        SlotState(int slot) {
            this.slot = slot;
        }

        synchronized void startProposal(Set<Integer> proposal) {
            if (decided) {
                return;
            }
            if (active) {
                return;
            }

            active = true;
            proposalNumber++;
            proposedValue = new HashSet<>(proposal);
            ackCount = 0;
            nackCount = 0;

            // Broadcast proposal
            broadcastProposal();
        }

        synchronized void handleProposal(int senderId, ParsedMessage msg) {
            if (msg.values.containsAll(acceptedValue)) {
                acceptedValue.clear();
                acceptedValue.addAll(msg.values);
                sendAck(msg.slot, msg.proposalNumber, senderId);
            } else {
                acceptedValue.addAll(msg.values);
                sendNack(msg.slot, msg.proposalNumber, senderId, new HashSet<>(acceptedValue));
            }
        }

        synchronized void handleAck(int senderId, ParsedMessage msg) {
            if (msg.target != myId) {
                return;
            }
            if (decided || !active) {
                return;
            }
            if (msg.slot != slot || msg.proposalNumber != proposalNumber) {
                return;
            }
            ackCount++;

            if (ackCount >= f + 1) {
                decide();
            }
        }

        synchronized void handleNack(int senderId, ParsedMessage msg) {
            if (msg.target != myId) {
                return;
            }
            if (decided || !active) {
                return;
            }
            if (msg.slot != slot || msg.proposalNumber != proposalNumber) {
                return;
            }

            proposedValue.addAll(msg.values);

            nackCount++;
            if (nackCount > 0 && ackCount + nackCount >= f + 1) {
                proposalNumber++;
                ackCount = 0;
                nackCount = 0;
                broadcastProposal();
            }
        }

        private void broadcastProposal() {
            String payload = encode(LaMessageType.PROP, slot, proposalNumber, myId, proposedValue);
            urb.broadcast(payload);
        }

        private void decide() {
            if (decided) {
                return;
            }
            decided = true;
            active = false;
            onSlotDecided(slot, proposedValue);
        }
    }

    private static class ParsedMessage {
        final LaMessageType type;
        final int slot;
        final int proposalNumber;
        final int target;
        final Set<Integer> values;

        ParsedMessage(LaMessageType type, int slot, int proposalNumber, int target, Set<Integer> values) {
            this.type = type;
            this.slot = slot;
            this.proposalNumber = proposalNumber;
            this.target = target;
            this.values = values;
        }

        static ParsedMessage other() {
            return new ParsedMessage(LaMessageType.OTHER, -1, -1, -1, Collections.emptySet());
        }
    }
}
