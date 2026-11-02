# Implementasi API FILKOM Space v1.0 — PUT, PATCH, DELETE

Dokumentasi tahap implementasi untuk tugas akhir Arsitektur Berbasis Layanan (ABL). Semua data dan akun adalah simulasi akademik, bukan layanan resmi FILKOM UB.

## Ruang lingkup

| Service | Method | Path | Hak akses | Sukses |
|---|---|---|---|---|
| Room | PUT | `/api/v1/rooms/{roomId}` | ADMIN | 200 |
| Room | PATCH | `/api/v1/rooms/{roomId}/status` | ADMIN | 200 |
| Room | DELETE | `/api/v1/rooms/{roomId}` | ADMIN | 204 |
| Reservation | PATCH | `/api/v1/reservations/{reservationId}` | ADMIN / pemilik | 200 |

GET dan POST dari PR #1 tetap tersedia. API v2 **belum** dikerjakan.

## Build properties

- Parent `pom.xml`: Java 21, Spring Boot 3.5.16, Maven multi-module, springdoc 2.10.0.
- `room-service/pom.xml` dan `reservation-service/pom.xml`: Spring MVC, Spring Data JPA, Validation, Security, PostgreSQL, Swagger UI, JUnit/MockMvc, dan H2 khusus unit/integration test.
- Tidak diperlukan dependency Maven baru untuk operasi PUT/PATCH/DELETE; `RestClient`, JPA, Spring Security, dan Validasi sudah digunakan oleh GET/POST.
- Build: `mvn -B clean verify` (JDK 21 + Maven 3.9+), atau `docker compose up --build -d` (Docker Desktop / Docker Compose v2).

## Konfigurasi deployment

- `docker-compose.yml`: Room Service pada localhost:8081, Reservation Service pada localhost:8082; dua PostgreSQL terpisah pada localhost:5433/5434.
- `room-service/src/main/resources/application.yml`: `RESERVATION_SERVICE_BASE_URL` untuk pengecekan reservasi sebelum arsip; `DEMO_ADMIN_PASSWORD` untuk Basic Auth antarlayanan pada prototipe lokal.
- `reservation-service/src/main/resources/application.yml`: `APPROVAL_PG_LOCKS=true` untuk mengaktifkan PostgreSQL transaction advisory lock; mode test H2 mematikannya, sedangkan CI PostgreSQL mengaktifkannya.
- `.env.example` disalin menjadi `.env` dan password diubah hanya untuk penggunaan lokal; jangan commit atau tampilkan password pada screenshot.
- Docker Compose tidak mengganti konfigurasi database yang sudah ada; volume data akan dipertahankan kecuali `docker compose down -v`.

## Kode sumber yang berkaitan langsung

### Room Service
- `room-service/.../controller/RoomController.java`: anotasi `@PutMapping`, `@PatchMapping`, dan `@DeleteMapping`.
- `room-service/.../service/RoomService.java`: mengganti semua field yang dapat diedit, validasi duplikasi kode, PATCH hanya `isActive`, dan soft-delete yang idempoten.
- `room-service/.../entity/Room.java`: field `archived` agar riwayat fisik data tidak hilang. GET list dan detail menyembunyikan ruangan arsip.
- `room-service/.../dto/ReplaceRoomRequest.java` serta `UpdateRoomStatusRequest.java`: validasi payload.
- `room-service/.../client/RoomReservationClient.java`: GET reservasi menurut `roomId` via HTTP ke Reservation Service. Arsip ditolak (409) jika ada reservasi PENDING/APPROVED mendatang; 503 jika layanan tidak tersedia.
- `room-service/.../config/SecurityConfig.java`: operasi perubahan data hanya boleh ADMIN.

### Reservation Service
- `reservation-service/.../controller/ReservationController.java`: `@PatchMapping("/reservations/{reservationId}")`, mengikuti **YAML**, bukan path `/status` yang tertulis di narasi DOCX lama.
- `reservation-service/.../dto/UpdateReservationStatusRequest.java`: `status` wajib, `reason` opsional maksimal 255 karakter.
- `reservation-service/.../service/ReservationService.java`: transisi status dan kontrol role; approval memvalidasi ulang kapasitas/status ruang serta bentrok jadwal.
- `reservation-service/.../service/ApprovalLock.java`: `pg_advisory_xact_lock` di transaksi PostgreSQL agar dua keputusan untuk ruangan/tanggal yang sama diserialisasi. Setelah lock didapat, entity di-refresh.
- `reservation-service/.../entity/Reservation.java`: `decisionReason` disimpan di database.
- `reservation-service/.../config/SecurityConfig.java`: PATCH hanya untuk aktor terautentikasi.

## Aturan bisnis

- PUT adalah **penggantian penuh** field `code,name,building,floor,capacity,facilities`; ID dan `isActive` tidak dapat diubah lewat PUT.
- PATCH room hanya mengubah `isActive` dan tidak membatalkan reservasi yang sudah APPROVED.
- DELETE melakukan **soft delete**: ruangan arsip tidak muncul dalam GET, tidak dapat dipesan, dan DELETE ulang mengembalikan 204.
- Admin dapat mengubah PENDING menjadi APPROVED atau REJECTED; mahasiswa hanya dapat membatalkan PENDING/APPROVED miliknya sebelum reservasi dimulai.
- REJECTED dan CANCELLED adalah final; APPROVED tidak bisa kembali ke PENDING. Penolakan perubahan tidak sah mengembalikan 400/403/409 sesuai kasus.
- Hanya APPROVED yang memblokir slot. Dua approval bentrok harus diproses bergantian dengan lock PostgreSQL.
- Risiko lintas layanan tetap ada: validasi pengarsipan dan reservasi melewati REST, bukan transaksi database terdistribusi. Ini keterbatasan prototipe dan perlu desain koordinasi lebih kuat untuk produksi.

## Demo Swagger (setelah Compose berjalan)

1. `GET http://localhost:8081/swagger-ui/index.html` untuk Room API.
2. Authorize ADMIN (username `admin` dan password dari `.env`).
3. Buat ruang POST atau gunakan GET yang sudah ada. Salin UUID.
4. `PUT /api/v1/rooms/{roomId}` dengan semua field wajib, lihat 200.
5. `PATCH /api/v1/rooms/{roomId}/status`: `{"isActive": false}`, lalu true.
6. Buat reservasi POST menggunakan mahasiswa pada tanggal mendatang. Salin UUID reservasi.
7. Sebagai ADMIN, `PATCH /api/v1/reservations/{reservationId}`: `{"status":"APPROVED","reason":"Sesuai"}`, lihat 200.
8. Sebagai pemilik, `PATCH /api/v1/reservations/{reservationId}`: `{"status":"CANCELLED"}`, lihat 200.
9. Uji DELETE ruang dengan PENDING/APPROVED (409). Setelah semua reservasi aktif dibatalkan, DELETE 204; GET kembali 404; DELETE ulang 204.

Lampirkan screenshot Swagger request/response, status HTTP, log Compose, dan hasil pengujian.

## Pengujian

```bash
cp .env.example .env
# Edit password lokal terlebih dahulu.
docker compose up --build -d
docker compose ps

# Bash/Linux/macOS (atau Git Bash Windows)
set -a
source .env
set +a

python3 scripts/smoke.py
python3 scripts/smoke_mutations.py

# Jika JDK21 dan Maven juga terpasang
mvn -B clean verify

docker compose down
```

Integration tests baru: `RoomMutationIntegrationTest` dan `ReservationMutationIntegrationTest`. GitHub Actions menguji unit/database dengan PostgreSQL 17 serta smoke Compose HTTP. Laporkan hasil aktual CI, jangan mengklaim lulus hanya berdasarkan keberadaan kode atau workflow.

Referensi kontrak: `docs/filkom-space-openapi-v1-complete.yaml`; Swagger Room dan Reservation menampilkan subset endpoint masing-masing.
