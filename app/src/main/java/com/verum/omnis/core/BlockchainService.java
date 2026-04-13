package com.verum.omnis.core;

public class BlockchainService {
    public static String anchor(String sha512) {
        // Placeholder: in production, this would create a deterministic on-chain anchor or Merkle inclusion proof.
        return "eth://anchor/" + HashUtil.truncate(sha512, 16);
    }
}