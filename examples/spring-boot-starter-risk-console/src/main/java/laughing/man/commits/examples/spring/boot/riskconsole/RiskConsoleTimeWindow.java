package laughing.man.commits.examples.spring.boot.riskconsole;

import java.time.LocalDateTime;

record RiskConsoleTimeWindow(LocalDateTime start,
                             LocalDateTime end,
                             LocalDateTime previousStart,
                             String label) {
}
