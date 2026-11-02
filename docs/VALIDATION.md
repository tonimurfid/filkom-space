# Verifikasi implementasi GET/POST v1.0

Tanggal: 29 Oktober 2026 (WIB).

## Hasil lokal aktual

- JDK: Zulu OpenJDK 21.0.5; Maven 3.9.11; Spring Boot 3.5.16.
- `mvn -B clean verify`: **BUILD SUCCESS** untuk parent, room-service, dan reservation-service.
- RoomIntegrationTest: **7 test**, 0 failure, 0 error, 0 skipped.
- ReservationIntegrationTest: **10 test**, 0 failure, 0 error, 0 skipped.
- ReservationServiceTest: **2 test**, 0 failure, 0 error, 0 skipped.
- Total: **19 test lulus**. Integration test lokal menggunakan H2 mode PostgreSQL, bukan PostgreSQL sungguhan.
- Dua executable Spring Boot JAR berhasil dibuat pada `room-service/target/room-service-1.0.0.jar` dan `reservation-service/target/reservation-service-1.0.0.jar`.
- Pemeriksaan kontrak: tujuh operasi GET/POST dalam dua `/openapi.yaml` sama dengan operasi pada YAML master. File YAML master disimpan identik byte demi byte dengan lampiran asli.
- Compose dan workflow berhasil di-parse sebagai YAML. Ini pemeriksaan struktur file, bukan bukti container berhasil dijalankan.

Pengujian meliputi filter ruang, penyimpanan dan pembacaan data, duplikasi kode, validasi field, HTTP Basic, role, ownership, jadwal WIB, kapasitas, ruang tidak aktif, APPROVED overlap, batas interval berdempetan, PENDING yang tidak memblokir, upstream 404/500, format respons/error, serta akses Swagger. RoomClient diuji melalui HTTP ke stub JDK HttpServer, bukan hanya mock pemanggilan method.

Presisi `createdAt` dibatasi mikrodetik agar tidak berubah akibat pembulatan timestamp database. Respons dinormalisasi ke WIB; pengujian mengecek kesamaan timestamp hasil POST dengan GET berikutnya.

## Verifikasi dua proses aplikasi

**Lulus:** dua proses Spring Boot terpisah dijalankan pada port 8081/8082 dengan database H2 masing-masing. `scripts/smoke.py` berhasil melewati tujuh operasi GET/POST, termasuk HTTP RoomClient ke Room Service yang sebenarnya, HTTP Basic, ownership, kapasitas, PENDING availability, dan Swagger. Ini verifikasi dua aplikasi nyata melalui HTTP, dengan H2 lokal.

Untuk mengulang pada stack Compose:

```bash
set -a
source .env
set +a
python3 scripts/smoke.py
```

Script melakukan tujuh operasi GET/POST melalui HTTP, memeriksa auth, ownership, kapasitas, status PENDING, availability, dan akses Swagger.

## Batasan dan CI

Docker CLI/daemon dan PostgreSQL server tidak tersedia pada lingkungan lokal pengerjaan. Karena itu hasil lokal di atas **tidak mengklaim Docker Compose atau PostgreSQL telah berjalan**.

Workflow `.github/workflows/test.yml` menyiapkan:

1. Dua job unit/integration test dengan PostgreSQL 17 terisolasi (masing-masing service dalam job berbeda).
2. Job Compose yang membangun dua image, menjalankan dua service beserta dua PostgreSQL, lalu menjalankan `scripts/smoke.py`.

Status workflow adalah bukti terpisah pada GitHub Actions/PR; konfigurasi workflow saja tidak berarti CI sudah lulus. Laporan JUnit tiap job diunggah sebagai artifact.
