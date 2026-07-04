package com.simpchatcensor.detection;

public final class Levenshtein {
    private Levenshtein() {
    }

    public static int adaptiveThreshold(String word, int globalThreshold) {
        int length = word == null ? 0 : word.length();
        int adaptive = length >= 8 ? 2 : 1;
        return Math.max(0, Math.min(adaptive, globalThreshold));
    }

    public static boolean withinDistance(String left, String right, int threshold) {
        if (left == null || right == null) {
            return false;
        }
        if (Math.abs(left.length() - right.length()) > threshold) {
            return false;
        }
        return distance(left, right, threshold) <= threshold;
    }

    public static int distance(String left, String right, int maxDistance) {
        if (left.equals(right)) {
            return 0;
        }
        if (left.isEmpty()) {
            return right.length();
        }
        if (right.isEmpty()) {
            return left.length();
        }

        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int rowMinimum = current[0];
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
                rowMinimum = Math.min(rowMinimum, current[j]);
            }

            if (rowMinimum > maxDistance) {
                return maxDistance + 1;
            }

            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[right.length()];
    }
}
