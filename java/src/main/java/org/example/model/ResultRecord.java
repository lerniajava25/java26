package org.example.model;

public record ResultRecord(
        String username,
        String repoUrl,
        String compilationStatus,
        String runningStatus,
        String imageNameFound,
        String materialExists
) {
    public static final ResultRecord POISON_PILL = new ResultRecord("__POISON__", "", "", "", "", "");

    public String toCsvLine() {
        return String.join(",",
                escapeCsv(username),
                escapeCsv(repoUrl),
                escapeCsv(compilationStatus),
                escapeCsv(runningStatus),
                escapeCsv(imageNameFound),
                escapeCsv(materialExists)
        );
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
