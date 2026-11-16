# FILKOM Space

Prototipe akademik tugas Arsitektur Berbasis Layanan, FILKOM Universitas Brawijaya. Semua nama ruangan, gedung, dan akun demonstrasi adalah **simulasi**, bukan inventaris atau layanan resmi FILKOM UB.

API v1.0 telah mengimplementasikan **GET, POST, PUT, PATCH, DELETE**. PUT/PATCH/DELETE berasal dari PR #2 (di atas fondasi PR #1). API v2 belum diimplementasikan.

## Arsitektur dan build

| Aplikasi | Port | Database | Tanggung jawab |
|---|---:|---|---|
| room-service | 8081 | room_db, PostgreSQL | Ruangan, fasilitas, kapasitas, status |
| reservation-service | 8082 | reservation_db, PostgreSQL | Reservasi, ownership, availability |

Reservation Service memanggil `GET /api/v1/rooms/{roomId}` melalui HTTP. Layanan ini tidak memiliki kredensial atau akses repository Room Service. GET detail ruangan memang publik menurut kontrak, sehingga tidak memerlukan service account pada tahap ini. DB tidak memiliki JOIN atau foreign key lintas service.

Java 21, Maven 3.9+, Spring Boot 3.5.16, Spring MVC/JPA/Validation/Security, springdoc 2.10.0, JUnit 5/Mockito/MockMvc. Versi dependency dipatok di POM agar build dapat direproduksi. Spring Boot 3.5 dipilih untuk stack JUnit 5/Spring MVC yang diminta pada tugas; lini ini sudah mengakhiri dukungan OSS, sehingga perlu upgrade sebelum dipakai di luar prototipe akademik. Rujukan: [Spring Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc compatibility](https://springdoc.org/v2/).

## Menjalankan dengan Docker Compose

Prasyarat: Docker Engine dan Docker Compose v2. Java/Maven host tidak diperlukan untuk cara ini.

```bash
cp .env.example .env
# Edit .env: ganti password demo dan database untuk lingkungan lokal Anda.
docker compose up --build -d
docker compose logs -f room-service reservation-service
```

Tunggu log `Started Application` dari kedua layanan. `depends_on` menunggu kesehatan DB; layanan reservasi dapat start sebelum Room Service selesai boot, dan akan mengembalikan 503 jika dipanggil saat dependensi belum siap.

- Room Swagger: http://localhost:8081/swagger-ui/index.html
- Reservation Swagger: http://localhost:8082/swagger-ui/index.html
- Kontrak OpenAPI v1.0 per service: `/openapi.yaml`
- Dokumentasi runtime hasil scanning: `/v3/api-docs`

Gunakan tombol **Authorize** untuk operasi terproteksi. Username admin: `admin`; username mahasiswa: nilai `DEMO_STUDENT_ID` (default simulasi `235150200111001`). Password diambil dari `.env`, tidak ditanam di kode. HTTP Basic dipakai untuk demo localhost.

```bash
docker compose down
# Opsional, hanya untuk menghapus SELURUH data demo lokal:
# docker compose down -v
```

Dua database tersedia pada `127.0.0.1:5433` dan `127.0.0.1:5434`. Volume menjaga data setelah container dihentikan. Mengganti password `.env` tidak mengganti password pada volume PostgreSQL yang sudah diinisialisasi. Semua port dipublikasikan hanya pada loopback host.

## Menjalankan dari source

Prasyarat: JDK 21, Maven 3.9+, PostgreSQL (atau jalankan hanya DB dari Compose).

```bash
cp .env.example .env
# Edit password lokal terlebih dahulu.
docker compose up -d room-db reservation-db
mvn -B clean verify
```

Terminal pertama (Bash, jalankan dari root repository; `.env` yang Anda buat harus berisi assignment shell biasa):

```bash
set -a
source .env
set +a
DB_URL=jdbc:postgresql://localhost:5433/room_db \
DB_USERNAME="$ROOM_DB_USERNAME" DB_PASSWORD="$ROOM_DB_PASSWORD" SEED_DATA=true \
mvn -pl room-service spring-boot:run
```

Terminal kedua:

```bash
set -a
source .env
set +a
DB_URL=jdbc:postgresql://localhost:5434/reservation_db \
DB_USERNAME="$RESERVATION_DB_USERNAME" DB_PASSWORD="$RESERVATION_DB_PASSWORD" \
ROOM_SERVICE_BASE_URL=http://localhost:8081 \
mvn -pl reservation-service spring-boot:run
```

`SEED_DATA=true` menambahkan dua ruangan simulasi apabila kode belum ada. UUID dibuat backend; ambil UUID aktual lewat GET, jangan memakai UUID contoh YAML. Hibernate `ddl-auto=update` dipilih untuk prototipe sederhana; migrasi skema produksi bukan scope tugas ini.

## Endpoint dan aturan

Implementasi tambahan PUT/PATCH/DELETE, detail build properties, konfigurasi deployment, dan pengujian terdokumentasi pada [docs/IMPLEMENTATION_V1_PUT_PATCH_DELETE.md](docs/IMPLEMENTATION_V1_PUT_PATCH_DELETE.md).

| Service | Method | Path | Akses |
|---|---|---|---|
| Room | GET | `/api/v1/rooms` | Publik |
| Room | GET | `/api/v1/rooms/{roomId}` | Publik |
| Room | POST | `/api/v1/rooms` | ADMIN |
| Room | PUT | `/api/v1/rooms/{roomId}` | ADMIN |
| Room | PATCH | `/api/v1/rooms/{roomId}/status` | ADMIN |
| Room | DELETE | `/api/v1/rooms/{roomId}` | ADMIN |
| Reservation | GET | `/api/v1/availability` | Publik |
| Reservation | GET | `/api/v1/reservations` | STUDENT/ADMIN |
| Reservation | GET | `/api/v1/reservations/{reservationId}` | Pemilik/ADMIN |
| Reservation | POST | `/api/v1/reservations` | Pemohon terautentikasi |
| Reservation | PATCH | `/api/v1/reservations/{reservationId}` | ADMIN / mahasiswa pemilik |

- Filter rooms: `minCapacity >= 1`, `building`, dan `facility`; filter gedung/fasilitas memakai nilai persis, dapat digabungkan. Respons daftar `{ "items": [...], "total": 2 }`.
- Room baru selalu aktif. `code` unik, termasuk ketika dua request membuat kode sama (constraint database).
- `studentId` POST harus sama dengan username autentikasi, termasuk jika login sebagai admin. Admin dapat membaca semua reservasi, tetapi tidak membuat permohonan atas nama mahasiswa lain.
- Mahasiswa hanya membaca reservasinya sendiri. Filter `studentId` orang lain dan filter `roomId` ditolak 403. Admin dapat memakai filter `studentId`, `status`, `roomId`.
- `date` dan jam `HH:mm` memakai Asia/Jakarta. Jadwal wajib di masa depan, start < end, peserta minimal 1 dan tidak melebihi kapasitas; ruangan wajib ada dan aktif.
- Reservasi baru **PENDING**. Hanya **APPROVED** memblokir slot; PENDING boleh menumpuk. Overlap: `existingStart < requestedEnd && existingEnd > requestedStart`. Slot 09:00–10:00 dan 10:00–11:00 tidak bentrok.
- Tahap PATCH menyediakan approval. ADMIN dapat mengubah PENDING menjadi APPROVED/REJECTED, sementara mahasiswa dapat membatalkan PENDING/APPROVED miliknya sebelum jadwal dimulai. Advisory transaction lock PostgreSQL menyerialisasi keputusan untuk roomId/tanggal yang sama.
- Timeout HTTP Room Service: connect 2 detik, read 3 detik. Room tidak ditemukan diterjemahkan menjadi 404; kegagalan koneksi/HTTP upstream menjadi 503. Reservasi gagal tidak disimpan.
- Sukses GET: 200; POST: 201 + `Location`. Error JSON: `status`, `code`, `message`, `path`. Input invalid 400, auth 401, role/ownership 403, ID tidak ada 404, konflik 409, dependensi gagal 503.

## Contoh curl

Jalankan Bash dari root repository setelah mengisi `.env`:

```bash
set -a
source .env
set +a

curl -sS http://localhost:8081/api/v1/rooms
curl -sS --get http://localhost:8081/api/v1/rooms \
  --data-urlencode 'minCapacity=8' --data-urlencode 'facility=PROYEKTOR'

curl -i -u "admin:$DEMO_ADMIN_PASSWORD" \
  -H 'Content-Type: application/json' http://localhost:8081/api/v1/rooms \
  -d '{"code":"SIM-NEW-01","name":"Ruang Proyek (simulasi)","building":"Gedung G (simulasi)","floor":2,"capacity":10,"facilities":["WIFI","PROYEKTOR"]}'
```

Salin UUID ruangan dari respons ke `ROOM_ID`. Gunakan tanggal mendatang pada `BOOKING_DATE` (ganti contoh jika sudah lewat).

```bash
ROOM_ID='GANTI-DENGAN-UUID-ROOM'
BOOKING_DATE='2027-11-02'
curl -sS "http://localhost:8081/api/v1/rooms/$ROOM_ID"
curl -sS --get http://localhost:8082/api/v1/availability \
  --data-urlencode "roomId=$ROOM_ID" --data-urlencode "date=$BOOKING_DATE" \
  --data-urlencode 'startTime=13:00' --data-urlencode 'endTime=15:00'

curl -i -u "$DEMO_STUDENT_ID:$DEMO_STUDENT_PASSWORD" \
  -H 'Content-Type: application/json' http://localhost:8082/api/v1/reservations \
  -d "{\"roomId\":\"$ROOM_ID\",\"studentId\":\"$DEMO_STUDENT_ID\",\"date\":\"$BOOKING_DATE\",\"startTime\":\"13:00\",\"endTime\":\"15:00\",\"purpose\":\"Diskusi penelitian\",\"participantCount\":6}"

curl -sS -u "$DEMO_STUDENT_ID:$DEMO_STUDENT_PASSWORD" http://localhost:8082/api/v1/reservations
curl -sS -u "admin:$DEMO_ADMIN_PASSWORD" 'http://localhost:8082/api/v1/reservations?status=PENDING'
# Isi dari id reservasi hasil POST:
RESERVATION_ID='GANTI-DENGAN-UUID-RESERVASI'
curl -sS -u "$DEMO_STUDENT_ID:$DEMO_STUDENT_PASSWORD" "http://localhost:8082/api/v1/reservations/$RESERVATION_ID"
```

## Testing

```bash
mvn -B clean verify
# Atau masing-masing modul:
mvn -B -pl room-service -am test
mvn -B -pl reservation-service -am test
```

Default test menggunakan H2 mode PostgreSQL, MockMvc, dan HTTP stub lokal berbasis JDK HttpServer untuk menguji RoomClient tanpa memerlukan service eksternal. Unit test Mockito memakai Clock tetap untuk batas waktu WIB. Integration test memeriksa controller, security, service, repository, dan serialisasi JSON.

Untuk mengulang integration test pada **PostgreSQL sungguhan**, gunakan database test kosong dan terpisah dari data demo. **Profil test memakai `create-drop`, sehingga seluruh tabel entity pada database target akan dibuat ulang/dihapus.** Contoh (siapkan database `filkom_test` terlebih dahulu):

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5432/filkom_test \
TEST_DB_USERNAME=test_user TEST_DB_PASSWORD=your-local-test-password \
TEST_DB_DRIVER=org.postgresql.Driver mvn -B clean verify
```

Untuk mengecek seluruh operasi melalui HTTP pada stack yang berjalan, muat `.env` lalu jalankan `python3 scripts/smoke.py` **dan** `python3 scripts/smoke_mutations.py` (Python 3 tanpa dependency tambahan).

GitHub Actions juga memiliki job Compose untuk build/start dua service dan dua PostgreSQL, lalu menjalankan smoke tersebut. GitHub Actions menjalankan dua job test terisolasi (satu per service), masing-masing PostgreSQL sendiri; laporan JUnit diunggah sebagai artifact. Hasil aktual verifikasi dan batasan lingkungan dicatat di [docs/VALIDATION.md](docs/VALIDATION.md). File laporan lokal berada di `*/target/surefire-reports/` dan tidak di-commit.

## Kontrak dan catatan sumber

- [YAML v1 lengkap asli](docs/filkom-space-openapi-v1-complete.yaml) disimpan tanpa perubahan; menjadi acuan seluruh 11 operasi GET/POST/PUT/PATCH/DELETE.
- [Rancangan OpenAPI v2](docs/filkom-space-openapi-v2.yaml) mendokumentasikan 12 operasi GET/POST/PUT/DELETE dengan jadwal reservasi dalam objek `schedule`. Tujuh operasi GET/POST tersedia pada kedua service dengan deployment Compose yang sama. PUT dan DELETE masih dalam tahap rancangan.
- Swagger `/openapi.yaml` per service adalah subset seluruh operasi masing-masing service dari YAML asli. Swagger memperlihatkan kontrak yang dituju; `/v3/api-docs` memperlihatkan scanning implementasi.
- DOCX menyebut PATCH reservasi `/{reservationId}/status`, sedangkan YAML memakai `/{reservationId}`. Implementasi mengikuti YAML: `PATCH /api/v1/reservations/{reservationId}`.
- Placeholder `{id}` pada instruksi endpoint dinyatakan sebagai `{roomId}`/`{reservationId}` mengikuti YAML; URL aktual tetap sama.
- YAML tidak mencantumkan 400 pada beberapa GET yang memiliki UUID/enum. Implementasi tetap mengembalikan 400 berformat standar untuk input malformed; tidak mengubah respons sukses atau kontrak field.

## File untuk laporan implementasi

| Kategori | File utama | Fungsi |
|---|---|---|
| Build properties | `pom.xml`, `room-service/pom.xml`, `reservation-service/pom.xml` | Java/dependency/plugin dan dua modul independen |
| Deployment | `docker-compose.yml`, `*/Dockerfile`, `*/src/main/resources/application.yml`, `.env.example` | Dua DB, dua proses, environment, port |
| HTTP/API | `*/controller/*Controller.java` | Mapping 11 operasi GET/POST/PUT/PATCH/DELETE |
| Aturan bisnis | `*/service/*Service.java` | Filter, ownership, kapasitas, waktu, konflik |
| Persistence | `*/entity/`, `*/repository/` | Tabel dan query masing-masing domain |
| DTO | `*/dto/` | Payload request/response sesuai v1 |
| Komunikasi HTTP | `reservation-service/.../client/RoomClient.java` | GET Room Service, timeout, pemetaan error |
| Security/error | `*/config/SecurityConfig.java`, `*/exception/` | HTTP Basic, role, JSON error |
| Demo | `room-service/.../config/DemoData.java` | Seed data simulasi, opt-in |
| Pengujian | `*/src/test/`, `.github/workflows/test.yml` | Unit, MockMvc, JPA, HTTP client, CI PostgreSQL |

API v2, frontend, gateway, dan broker belum termasuk implementasi tahap ini.
