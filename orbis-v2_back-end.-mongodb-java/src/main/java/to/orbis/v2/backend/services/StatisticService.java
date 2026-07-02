package to.orbis.v2.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import to.orbis.v2.backend.models.StatisticSubscriptionType;
import to.orbis.v2.backend.models.dto.StatisticRequestDto;
import to.orbis.v2.backend.models.dto.StatisticDto;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class StatisticService {
    public Mono<List<StatisticDto>> countStatistic(
            String subscriptionKey,
            StatisticSubscriptionType type,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getCountForStatistic,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getAmountForStatistic
    ) {
        switch (type) {
            case MONTH:
                return countLastMonthDailyStatistic(subscriptionKey, getCountForStatistic, getAmountForStatistic);
            case TRIMESTER:
                return countLast3MonthWeeklyStatistic(subscriptionKey, getCountForStatistic, getAmountForStatistic);
            case SEMESTER:
                return countMonthStatistic(subscriptionKey, 6, getCountForStatistic, getAmountForStatistic);
            case YEAR:
                return countMonthStatistic(subscriptionKey, 12, getCountForStatistic, getAmountForStatistic);
            default:
                throw new IllegalStateException("Unexpected value statisticType: " + type);
        }
    }

    private Mono<List<StatisticDto>> countLastMonthDailyStatistic(
            String subscriptionKey,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getCountForStatistic,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getAmountForStatistic
    ) {
        var endDate = LocalDate.now().plus(Period.ofDays(1));
        var startDate = LocalDate.now().minus(Period.ofDays(30));

        long numOfDaysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        var datesList =
                IntStream.iterate(0, i -> i + 1)
                        .limit(numOfDaysBetween)
                        .mapToObj(i -> new StatisticDto(startDate.plusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE)))
                        .collect(Collectors.toList());

        return getStatistic(datesList, startDate, endDate, "%Y-%m-%d", subscriptionKey, getCountForStatistic, getAmountForStatistic);
    }

    private Mono<List<StatisticDto>> countLast3MonthWeeklyStatistic(
            String subscriptionKey,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getCountForStatistic,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getAmountForStatistic
    ) {
        var firstDayOfWeek = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        var lastDayOfWeek = firstDayOfWeek.plus(6);

        var endDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(lastDayOfWeek));
        var startDate = LocalDate.now().minusMonths(3).with(TemporalAdjusters.previousOrSame(firstDayOfWeek));

        long numOfDaysBetween = ChronoUnit.WEEKS.between(startDate, endDate) + 1;
        var datesMap = new HashMap<String, String>();
        var datesList =
                IntStream.iterate(0, i -> i + 1)
                        .limit(numOfDaysBetween)
                        .mapToObj(i -> {
                            var weekStart = startDate.plusWeeks(i);
                            var weekKey = getWeekNumber(weekStart);
                            datesMap.put(weekKey, String.format("from %s to %s", weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE), weekStart.plusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)));
                            return new StatisticDto(weekKey);
                        })
                        .collect(Collectors.toList());

        return getStatistic(datesList, startDate, endDate, "%U-%Y", subscriptionKey, getCountForStatistic, getAmountForStatistic)
                .flatMap(statisticResult -> Mono.just(
                        statisticResult.stream()
                                .peek(element -> element.setColumnName(datesMap.get(element.getColumnName())))
                                .collect(Collectors.toList())
                ));
    }

    private Mono<List<StatisticDto>> countMonthStatistic(
            String subscriptionKey,
            int numberOfMonth,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getCountForStatistic,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getAmountForStatistic
    ) {
        var endDate = YearMonth.now().plusMonths(1).atDay(1);
        var startDate = LocalDate.now().minusMonths(numberOfMonth).with(TemporalAdjusters.firstDayOfMonth());

        long numOfMonthBetween = ChronoUnit.MONTHS.between(startDate, endDate);
        var datesList =
                IntStream.iterate(0, i -> i + 1)
                        .limit(numOfMonthBetween)
                        .mapToObj(i -> new StatisticDto(startDate.plusMonths(i).format(DateTimeFormatter.ofPattern("uuuu-MM"))))
                        .collect(Collectors.toList());

        return getStatistic(datesList, startDate, endDate, "%Y-%m", subscriptionKey, getCountForStatistic, getAmountForStatistic);
    }

    private String getWeekNumber(LocalDate date) {
        var week = date.get(ChronoField.ALIGNED_WEEK_OF_YEAR);
        if (week < 10) {
            return "0" + week + "-" + date.getYear();
        } else {
            return week + "-" + date.getYear();
        }
    }

    private Mono<List<StatisticDto>> getStatistic(
            List<StatisticDto> datesList,
            LocalDate startDate,
            LocalDate endDate,
            String dataPattern,
            String subscriptionKey,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getCountForStatistic,
            Function<StatisticRequestDto, Mono<List<StatisticDto>>> getAmountForStatistic
    ) {
        var request = StatisticRequestDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .dataPattern(dataPattern)
                .key(subscriptionKey)
                .build();
        return Mono.just(datesList)
                .flatMap(dataList -> getCountForStatistic.apply(request)
                        .flatMap(resultList -> {
                            var resultMap = resultList
                                    .stream()
                                    .collect(Collectors.toMap(StatisticDto::getColumnName, StatisticDto::getNumber));
                            for (StatisticDto dto: dataList) {
                                if (resultMap.containsKey(dto.getColumnName())) {
                                    dto.setNumber(resultMap.get(dto.getColumnName()));
                                }
                            }
                            return Mono.just(dataList);
                        }))
                .flatMap(dataList -> getAmountForStatistic.apply(request)
                        .flatMap(resultList -> {
                            var resultMap = resultList
                                    .stream()
                                    .collect(Collectors.toMap(StatisticDto::getColumnName, StatisticDto::getAmount));
                            for (StatisticDto dto: dataList) {
                                if (resultMap.containsKey(dto.getColumnName())) {
                                    dto.setAmount(resultMap.get(dto.getColumnName()));
                                }
                            }
                            return Mono.just(dataList);
                        }));
    }
}
