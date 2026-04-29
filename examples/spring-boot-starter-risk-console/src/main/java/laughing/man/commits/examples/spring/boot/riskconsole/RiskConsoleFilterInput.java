package laughing.man.commits.examples.spring.boot.riskconsole;

record RiskConsoleFilterInput(String range,
                              String region,
                              String status,
                              String riskBand,
                              String segment,
                              String search) {
}
