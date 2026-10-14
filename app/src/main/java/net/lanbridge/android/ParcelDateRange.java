package net.lanbridge.android;
import java.time.LocalDate;
final class ParcelDateRange {
    static LocalDate[] rolling(LocalDate today,int days){ParcelAutoPolicy.days(days);return new LocalDate[]{today.minusDays(days-1),today};}
}
