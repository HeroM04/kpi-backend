package com.trilong.kpibackend.core.utils;

public class HaversineUtils {

    // Bán kính Trái Đất (tính bằng km)
    private static final int EARTH_RADIUS_KM = 6371;

    /**
     * Hàm tính khoảng cách giữa 2 tọa độ GPS
     * @return Khoảng cách tính bằng mét (m)
     */
    public static double calculateDistanceInMeters(double startLat, double startLong, double endLat, double endLong) {

        double dLat  = Math.toRadians((endLat - startLat));
        double dLong = Math.toRadians((endLong - startLong));

        startLat = Math.toRadians(startLat);
        endLat   = Math.toRadians(endLat);

        double a = haversine(dLat) + Math.cos(startLat) * Math.cos(endLat) * haversine(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Nhân với 1000 để đổi từ km ra mét
        return EARTH_RADIUS_KM * c * 1000;
    }

    private static double haversine(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }
}