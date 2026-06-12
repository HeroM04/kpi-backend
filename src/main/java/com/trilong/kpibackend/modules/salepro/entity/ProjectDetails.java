package com.trilong.kpibackend.modules.salepro.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Metadata linh hoạt của dự án (lưu trong cột JSONB salepro.projects.details).
 * CHỈ THÊM trường mới, GIỮ NGUYÊN các trường cũ (overview, locationMap, trainingMaterials, images360, documents).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetails implements Serializable {

    // ===== Trường cũ (giữ nguyên) =====
    private String overview;
    private String locationMap;
    private List<String> trainingMaterials;
    private List<String> images360;
    private List<String> documents;

    // ===== Tổng quan / Thông tin tổng quan (dùng chung tab Tổng quan + Ảnh 360º) =====
    private String developer;          // Nhà phát triển, vd "Masterise Homes"
    private String address;            // Vị trí ngắn, vd "Nguyễn Trãi, Hà Nội"
    private String totalProjectArea;   // Tổng diện tích dự án, vd "82.820 m²"
    private String scaleDescription;   // Quy mô, vd "10 tòa | 35-46 tầng"
    private String constructionDensity;// Mật độ xây dựng, vd "28.8%"
    private String apartmentTypes;     // Loại hình căn hộ, vd "Studio, 1BR, 2BR, Duplex, Penthouse..."
    private String scale;              // Quy mô (tab Tổng quan), vd "1080 ha"
    private String capital;            // Vốn đầu tư, vd "2,3 tỷ USD"
    private String residents;          // Cư dân, vd "135.000 cư dân"
    private List<String> overviewBullets; // các gạch đầu dòng tổng quan
    private String bannerImageUrl;     // ảnh banner tab Tổng quan
    private String overviewImageUrl;   // ảnh minh hoạ tổng quan

    // ===== Vị trí =====
    private String locationDescription;          // mô tả vị trí (HTML)
    private List<ConnectionPoint> connectionPoints; // điểm kết nối: {time, label}
    private String mapImageUrl;                   // ảnh bản đồ vị trí
    private String mapEmbedUrl;                   // link nhúng Google Maps / vệ tinh
    private Double latitude;
    private Double longitude;

    // ===== Mặt bằng (Masterplan) =====
    private String masterplanImageUrl;           // ảnh nền mặt bằng cho bản đồ Leaflet

    // ===== Đào tạo =====
    private String trainingVideoUrl;             // link video đào tạo chính
    private String trainingThumbnail;            // ảnh thumbnail video

    // ===== Chính sách bán hàng (cấp dự án) =====
    private String salesPolicy;                  // nội dung CSBH dự án (HTML)

    // ===== Trang Tổng quan (landing) =====
    private Boolean isHot;                       // gắn nhãn 🔥 HOT ở danh sách dự án
    private List<String> heroImages;             // carousel ảnh lớn đầu trang
    private String productCount;                 // thẻ "Sản phẩm", vd "4500 căn"
    private String ownership;                    // thẻ "Sở hữu", vd "Lâu dài"
    private List<ProductType> products;          // mục "Sản phẩm": loại căn hộ + ảnh layout
    private List<Amenity> amenities;             // mục "Tiện ích"
    private String featureTitle;                 // banner video: tiêu đề
    private String featureDescription;           // banner video: mô tả
    private String featureVideoUrl;              // banner video: link video
    private String featureImage;                 // banner video: ảnh nền/poster
    private List<MasterplanTab> masterplanTabs;  // mục "Mặt bằng": các tab (Tổng thể / Tòa L1...)

    /**
     * Điểm kết nối hạ tầng (tab Vị trí), vd time="01'", label="Ga Cát Linh - Thượng Đình".
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionPoint implements Serializable {
        private String time;
        private String label;
    }

    /** Loại căn hộ trong mục "Sản phẩm" (vd "Căn hộ 1BR/1BR+1", diện tích, ảnh layout). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductType implements Serializable {
        private String name;
        private String areaRange;       // vd "50 - 102 m²"
        private List<String> images;    // ảnh layout
    }

    /** Tiện ích (mục "Tiện ích"): nhãn + ảnh. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Amenity implements Serializable {
        private String label;
        private String image;
        private String description;
    }

    /** Tab trong mục "Mặt bằng": tiêu đề + ảnh (vd Tổng thể, MB Điển hình Tòa L1). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MasterplanTab implements Serializable {
        private String label;
        private String image;
    }
}
