package io.github.steinbeck.seneca.golden;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The golden connection-table list and hash (golden/FORMAT.md "Conventions"):
 * {@code ct} = bonds {@code [i, j, order]} with {@code i < j}, order != 0, row-major from the upper
 * triangle; {@code hash} = lowercase hex SHA-1 of the ASCII string {@code "i,j,o;i,j,o;..."} over the
 * same bonds (empty structure: SHA-1 of ""). Identifies a labelled structure in dataset atom order.
 */
public final class Hashes {

    private Hashes() {
    }

    /** {@code ct} as JSON array. */
    public static Json ct(int[][] bm) {
        Json arr = Json.arr();
        for (int i = 0; i < bm.length; i++)
            for (int j = i + 1; j < bm.length; j++)
                if (bm[i][j] != 0) arr.add(Json.arr().add(i).add(j).add(bm[i][j]));
        return arr;
    }

    /** The string that is hashed. */
    public static String ctString(int[][] bm) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bm.length; i++)
            for (int j = i + 1; j < bm.length; j++)
                if (bm[i][j] != 0) {
                    if (sb.length() > 0) sb.append(';');
                    sb.append(i).append(',').append(j).append(',').append(bm[i][j]);
                }
        return sb.toString();
    }

    public static String hash(int[][] bm) {
        return sha1Hex(ctString(bm));
    }

    public static String sha1Hex(String ascii) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(ascii.getBytes(StandardCharsets.US_ASCII));
            StringBuilder h = new StringBuilder(40);
            for (byte b : d) h.append(String.format("%02x", b & 0xff));
            return h.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Symmetric matrix of size n from a ct list {@code [[i, j, o], ...]}. */
    public static int[][] matrixFromCt(int n, int[][] ct) {
        int[][] bm = new int[n][n];
        for (int[] b : ct) {
            bm[b[0]][b[1]] = b[2];
            bm[b[1]][b[0]] = b[2];
        }
        return bm;
    }
}
