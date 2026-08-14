# Chạy & Deploy KPI bằng Docker

Toàn bộ hệ thống (Backend Spring Boot + Frontend React) chạy chỉ bằng **một lệnh Docker Compose**. Database dùng **Neon** (cloud) qua biến môi trường.

## Kiến trúc

```
Trình duyệt ──► http://localhost:8080 (nginx / Frontend)
                        │
                        ├── /            → file tĩnh React (SPA)
                        ├── /api/v1/**   ─┐
                        ├── /ws/**        ├─► proxy sang  backend:8088 (Spring Boot)
                        └── /uploads/**  ─┘
                                             │
                                             └──► Neon PostgreSQL (cloud)
```

- FE gọi API/WS bằng **đường tương đối** nên không dính CORS, không cần biết URL backend.
- Chỉ cổng **8080** (FE) là bắt buộc mở ra ngoài. Cổng 8088 (BE) mở thêm chỉ để debug/Swagger.

## Yêu cầu

- Docker Desktop (Windows) hoặc Docker Engine + Compose v2.
- **Hai repo phải nằm cạnh nhau** (compose build FE từ `../WebAdmin`):
  ```
  D:\kpi-backend   ← chứa docker-compose.yml
  D:\WebAdmin      ← Frontend
  ```

## Các bước

1. Tạo file cấu hình bí mật từ mẫu, rồi điền kết nối Neon + AWS:
   ```bash
   cp .env.example .env
   # mở .env, điền DATABASE_URL / DB_USERNAME / DB_PASSWORD (Neon)
   # và AWS_ACCESS_KEY / AWS_SECRET_KEY (giống trên Render)
   ```

2. Build và chạy toàn bộ:
   ```bash
   docker compose up -d --build
   ```

3. Mở trình duyệt: **http://localhost:8080** — đăng nhập `admin/admin123`.

4. Xem log / dừng:
   ```bash
   docker compose logs -f          # theo dõi log
   docker compose down             # dừng và xoá container
   docker compose up -d --build    # cập nhật khi có code mới
   ```

## Deploy lên server (VPS)

Y hệt local: clone cả hai repo cạnh nhau, tạo `.env`, chạy `docker compose up -d --build`. Nếu có domain, đặt một reverse proxy (Nginx/Caddy/Traefik) trước cổng 8080 để gắn HTTPS.

## Khóa API Gemini (AI chấm điểm bài đăng)

Khóa Gemini **chỉ nằm ở backend**, không đưa xuống trình duyệt. Web gọi
`POST /api/v1/ai/scan-post` của chính backend, backend mới gọi sang Google.

- **Chạy Docker / local**: điền `GEMINI_API_KEY` trong file `.env`.
- **Deploy Render**: thêm biến môi trường `GEMINI_API_KEY` trong tab *Environment*.
- Lấy khóa tại https://aistudio.google.com/apikey

> Trước đây khóa được ghi thẳng vào mã nguồn frontend và đã bị lộ lên GitHub →
> Google đình chỉ cả project. Cách làm hiện tại tránh hoàn toàn rủi ro đó.

## Ghi chú

- **Không cần Postgres trong Docker** — BE nối thẳng Neon qua `DATABASE_URL` (đã chọn phương án này).
- `ddl-auto: update` ở prod → khởi động không xoá dữ liệu, chỉ đồng bộ schema.
- Muốn chạy **không cần AWS thật**: xem phần tuỳ chọn cuối file `.env.example`
  (đổi sang lưu ảnh local + tắt chấm công khuôn mặt).
- File `.env` chứa secret đã được `.gitignore` — không bao giờ commit.
