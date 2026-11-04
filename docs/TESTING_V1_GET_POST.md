# Pengujian API Versi 1.0 (GET dan POST)

> **Proyek:** FILKOM Space — Prototipe Sistem Reservasi Ruangan Akademik Berbasis Microservices, FILKOM UB.  
> **Sumber pengujian:** kode dan hasil GitHub Actions branch `main`, run [41403862091](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091) (2 November 2026).  
> **Ruang lingkup laporan ini:** GET dan POST. Implementasi PUT/PATCH/DELETE dilaporkan terpisah.  
> **Catatan:** Semua akun, nama ruangan dan data merupakan simulasi. Bukti screenshot pengujian **lokal** belum dilampirkan dan tidak boleh diklaim telah dilakukan.

## 1. Tujuan dan pendekatan pengujian

Pengujian bertujuan memverifikasi bahwa tujuh operasi GET/POST API v1.0 memenuhi kontrak OpenAPI, aturan bisnis reservasi, dan kontrol akses. Dua layanan yang diuji adalah **Room Service** pada port 8081 serta **Reservation Service** pada port 8082, masing-masing memiliki database PostgreSQL sendiri.

Pengujian terdiri dari:

1. **Integration test** menggunakan JUnit 5, Spring Boot Test, MockMvc dan Spring Data JPA untuk controller, serialisasi JSON, validasi, security, dan penyimpanan.
2. **Unit test** menggunakan JUnit dan Mockito untuk kasus waktu WIB serta kegagalan komunikasi antarservice.
3. **End-to-end smoke test** menggunakan `scripts/smoke.py` terhadap dua aplikasi dan dua PostgreSQL yang dijalankan melalui Docker Compose pada GitHub Actions.

Tugas ini tidak hanya menguji status HTTP, tetapi juga hasil JSON, konsistensi data tersimpan, ownership, dan pemanggilan REST antarlayanan.

## 2. Kode sumber pengujian

| Kode sumber | Peran |
|---|---|
| [RoomIntegrationTest.java](../room-service/src/test/java/id/ac/ub/filkom/room/RoomIntegrationTest.java) | GET daftar/detail/filter ruangan; POST ruangan; validasi, duplikasi, role, OpenAPI |
| [ReservationIntegrationTest.java](../reservation-service/src/test/java/id/ac/ub/filkom/reservation/ReservationIntegrationTest.java) | GET ketersediaan/daftar/detail; POST reservasi; konflik jadwal, kapasitas, ownership, komunikasi HTTP |
| [ReservationServiceTest.java](../reservation-service/src/test/java/id/ac/ub/filkom/reservation/ReservationServiceTest.java) | Unit test logika waktu WIB dan error dependency |
| [scripts/smoke.py](../scripts/smoke.py) | Pengujian end-to-end tujuh operasi GET/POST melalui HTTP asli |
| [.github/workflows/test.yml](../.github/workflows/test.yml) | Otomasi Maven, PostgreSQL 17, Docker Compose, dan unggah laporan JUnit |

### 2.1. Uji POST dan GET pada Room Service

Kode berikut diambil dari `RoomIntegrationTest.java` (cuplikan, bukan seluruh file):

```java
String createRoom() throws Exception {
  return mvc.perform(
          post("/api/v1/rooms")
              .with(httpBasic("admin", "test-admin-password"))
              .contentType(MediaType.APPLICATION_JSON)
              .content(payload))
      .andExpect(status().isCreated())
      .andExpect(header().exists("Location"))
      .andExpect(jsonPath("$.isActive").value(true))
      .andReturn()
      .getResponse()
      .getContentAsString();
}

@Test
void createPersistsAndPublicGetReturnsContract() throws Exception {
  var response = json.readTree(createRoom());
  UUID.fromString(response.get("id").asText());
  Assertions.assertEquals(1, rooms.count());
  mvc.perform(get("/api/v1/rooms/" + response.get("id").asText()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("SIM-001"));
  mvc.perform(get("/api/v1/rooms"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(1));
}
```

Metode `createRoom()` memanggil POST dengan autentikasi ADMIN, memeriksa HTTP 201, header `Location`, serta status aktif ruangan. Metode berikutnya memverifikasi bahwa data benar-benar tersimpan dan dapat diambil melalui dua operasi GET. Pengujian lain memeriksa filter `minCapacity`, `building`, `facility`, kode ruangan duplikat (409), input tidak valid (400), autentikasi (401), otorisasi (403), dan ruang tidak ditemukan (404).

### 2.2. Uji komunikasi antarmicroservices pada POST reservasi

Cuplikan aktual dari `ReservationIntegrationTest.java`:

```java
@Test
void createCallsRoomOverHttpAndPersistsPending() throws Exception {
  var result =
      postBody(payload())
          .andExpect(status().isCreated())
          .andExpect(header().exists("Location"))
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.startTime").value("13:00"))
          .andReturn();
  var body = json.readTree(result.getResponse().getContentAsString());
  UUID.fromString(body.get("id").asText());
  OffsetDateTime.parse(body.get("createdAt").asText());
  Assertions.assertEquals(1, calls.get());
  Assertions.assertEquals(1, reservations.count());
}
```

Dalam integration test, Room Service disimulasikan melalui **HTTP server lokal berbasis `HttpServer` JDK**, bukan sekadar mock pemanggilan method. Nilai `calls` membuktikan bahwa request POST reservasi memicu pemanggilan HTTP ke endpoint detail ruangan; data kemudian tersimpan dengan status awal `PENDING`.

### 2.3. Uji kapasitas dan status ruangan

Cuplikan dari `ReservationIntegrationTest.java`:

```java
@Test
void inactiveAndCapacityRejected() throws Exception {
  active = false;
  postBody(payload())
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
  active = true;
  postBody(payload().replace("\"participantCount\":6",
                             "\"participantCount\":9"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
  Assertions.assertEquals(0, reservations.count());
}
```

Pengujian memverifikasi bahwa ruangan nonaktif atau jumlah peserta melebihi kapasitas menghasilkan HTTP 400. Penegasan `reservations.count() == 0` menunjukkan tidak ada permohonan tidak valid yang tersimpan.

### 2.4. Unit test batas waktu dan kegagalan dependency

Dua metode dalam `ReservationServiceTest.java` ialah:

- `sameDayPastOrEqualStartUsesJakartaClock()`: menggunakan `Clock.fixed` pada zona `Asia/Jakarta`; jadwal yang sudah lewat atau sama dengan waktu sekarang ditolak dengan `PAST_SCHEDULE` sebelum repository/client dipanggil.
- `roomFailureNeverSaves()`: jika RoomClient mengembalikan `503 ROOM_SERVICE_UNAVAILABLE`, ReservationService meneruskan exception dan tidak menyimpan reservasi.

### 2.5. Pengujian end-to-end melalui HTTP

Bagian berikut berasal dari skrip `scripts/smoke.py`:

```python
room = request(ROOM, '/api/v1/rooms',
               method='POST', user='admin',
               expected=201, body={...})
assert request(ROOM, '/api/v1/rooms/' + room['id']) == room
assert request(RESERVATION, slot)['available'] is True
reservation = request(RESERVATION, '/api/v1/reservations',
                      method='POST', body=body,
                      user=STUDENT, expected=201)
assert reservation['status'] == 'PENDING'
```

Skrip memakai `urllib.request` dari Python standard library dan melakukan request ke layanan yang benar-benar berjalan pada port 8081/8082. Skrip juga memeriksa status 401/403, validasi kapasitas, ketersediaan `PENDING`, dan endpoint dokumentasi Swagger/OpenAPI.

## 3. Matriks skenario pengujian GET/POST

| ID | Skenario | Hasil yang diharapkan | Bukti kode |
|---|---|---|---|
| TC-01 | GET semua ruangan setelah POST | 200; struktur items/total | `createPersistsAndPublicGetReturnsContract` |
| TC-02 | GET detail ruangan valid | 200; UUID dan kode benar | `createPersistsAndPublicGetReturnsContract` |
| TC-03 | GET filter ruangan sesuai/tidak sesuai | Jumlah hasil sesuai filter | `filtersCombineAndExclude` |
| TC-04 | POST ruangan dengan ADMIN | 201; Location; data tersimpan | `createPersistsAndPublicGetReturnsContract` |
| TC-05 | POST kode ruangan duplikat | 409; `ROOM_CODE_CONFLICT` | `duplicateReturns409` |
| TC-06 | POST ruangan invalid | 400; error JSON | `validatesRequiredFieldsAndLimits` |
| TC-07 | POST tanpa login / STUDENT | 401 / 403 | `authenticationAndRoleAreEnforced` |
| TC-08 | GET detail ruangan tidak ada | 404 | `missingRoomReturns404` |
| TC-09 | POST reservasi valid | 201; PENDING; data tersimpan | `createCallsRoomOverHttpAndPersistsPending` |
| TC-10 | GET ketersediaan dengan PENDING | 200; available=true | `pendingDoesNotBlockMultipleRequests` |
| TC-11 | GET ketersediaan slot APPROVED bentrok | 200; available=false | `approvedOverlapsButTouchingBoundariesDoNot` |
| TC-12 | POST reservasi jadwal bentrok | 409; SCHEDULE_CONFLICT | `approvedOverlapsButTouchingBoundariesDoNot` |
| TC-13 | POST peserta melebihi kapasitas | 400; CAPACITY_EXCEEDED | `inactiveAndCapacityRejected` |
| TC-14 | POST ke ruangan nonaktif | 400; ROOM_INACTIVE | `inactiveAndCapacityRejected` |
| TC-15 | POST request invalid | 400; tak tersimpan | `invalidRequestsRejectedBeforePersistence` |
| TC-16 | Kegagalan Room Service | 404 / 503 dipetakan | `upstreamMissingAndFailureAreMapped` |
| TC-17 | GET reservasi tanpa login / milik orang lain | 401 / 403 | `protectedEndpointsRequireAuthentication`, `studentCannotImpersonateOrReadOthers` |
| TC-18 | GET daftar/detail ADMIN dan filter | 200; hasil terfilter | `adminListsAndFiltersAllAndDetailMissing404` |
| TC-19 | Swagger/OpenAPI tersedia | 200 | `swaggerAndRuntimeDocsAccessible`, `swaggerAccessible` |

Tabel ini merangkum skenario, **bukan menyatakan terdapat tepat 19 method test yang masing-masing berkorelasi satu-ke-satu dengan satu baris**. Beberapa metode JUnit menguji lebih dari satu skenario.

## 4. Hasil aktual pengujian pada GitHub Actions

**Workflow yang diperiksa:** [Java tests — run #41403862091](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091), dijalankan 2 November 2026 pada branch `main`.

Ringkasan dari log Maven Surefire:

| Test class | Tests run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `RoomIntegrationTest` | 7 | 0 | 0 | 0 |
| `ReservationIntegrationTest` | 10 | 0 | 0 | 0 |
| `ReservationServiceTest` | 2 | 0 | 0 | 0 |
| **Total pengujian GET/POST** | **19** | **0** | **0** | **0** |

Karena workflow dijalankan setelah implementasi PUT/PATCH/DELETE turut digabungkan, job total saat ini juga menjalankan:

- `RoomMutationIntegrationTest`: 5 tes;
- `ReservationMutationIntegrationTest`: 4 tes.

Sehingga keseluruhan workflow Maven pada commit tersebut menguji **28 tes**, tetapi 9 tes tambahan merupakan cakupan PUT/PATCH/DELETE, **bukan** bagian pengujian GET/POST.

Job CI yang terverifikasi:

| Job | Hasil |
|---|---|
| [test (room-service)](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091/job/125141385890) | Success; 12 test total; BUILD SUCCESS |
| [test (reservation-service)](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091/job/125141385699) | Success; 16 test total; BUILD SUCCESS |
| [compose-smoke](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091/job/125141385841) | Success; HTTP GET/POST dan mutation smoke PASS |

**Cuplikan output log aktual pada job compose-smoke:**

```text
PASS: seven GET/POST operations, HTTP Basic,
ownership, capacity, PENDING availability, Swagger
```

Cuplikan ini berasal dari keluaran `scripts/smoke.py` dalam log workflow dan merupakan bukti bahwa tujuh operasi GET/POST berhasil diuji melalui dua service yang berjalan di Docker Compose.

## 5. Bukti screenshot pengujian

**Status bukti visual:** tautan dan log autentik sudah terverifikasi, tetapi file screenshot hasil GitHub Actions maupun tampilan Swagger lokal **belum disematkan** pada laporan ini. Jangan menampilkan visual buatan sebagai screenshot aktual.

Untuk memenuhi instruksi dosen, ambil tangkapan layar berikut dari halaman aslinya:

| Gambar | Sumber screenshot | Bagian yang perlu terlihat |
|---|---|---|
| 1 | [Job test room-service](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091/job/125141385890) | `RoomIntegrationTest`: Tests run 7, Failures 0, Errors 0; BUILD SUCCESS |
| 2 | [Job test reservation-service](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091/job/125141385699) | `ReservationIntegrationTest`: 10; `ReservationServiceTest`: 2; BUILD SUCCESS |
| 3 | [Job compose-smoke](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091/job/125141385841) | Baris `PASS: seven GET/POST operations...` |
| 4 | Swagger Room Service di `localhost:8081` | GET /rooms: HTTP 200 dan response `items`/`total` |
| 5 | Swagger Room Service di `localhost:8081` | POST /rooms: HTTP 201, header Location, response room baru |
| 6 | Swagger Reservation Service di `localhost:8082` | GET /availability: HTTP 200, `available` |
| 7 | Swagger Reservation Service di `localhost:8082` | POST /reservations: HTTP 201, `status=PENDING` |
| 8 | Swagger Reservation Service di `localhost:8082` | GET /reservations/{reservationId}: HTTP 200 |
| 9 | Swagger Reservation Service di `localhost:8082` | POST reservasi melebihi kapasitas: HTTP 400 |

**Catatan:** Screenshot nomor 4–9 hanya boleh dilampirkan setelah demonstrasi benar-benar dilakukan pada perangkat pengguna. Jika screenshot serupa sudah ada pada laporan Iterasi 5/6, dapat dirujuk kembali tanpa mengambil gambar identik, tetapi pastikan kode/status HTTP terlihat.

### Langkah mengambil screenshot GitHub Actions

1. Masuk ke akun GitHub yang mempunyai izin membaca repository privat.
2. Buka [run #41403862091](https://github.com/tonimurfid/filkom-space/actions/runs/41403862091).
3. Klik salah satu job: `test (room-service)`, `test (reservation-service)`, atau `compose-smoke`.
4. Buka step pengujian yang relevan; cari teks `Tests run:`, `BUILD SUCCESS`, atau `PASS: seven GET/POST`.
5. Ambil screenshot asli yang memperlihatkan nama job, status Success, dan keluaran log. Jangan crop sampai konteks sumbernya hilang.
6. Sisipkan sebagai Gambar Pengujian dan beri caption, misalnya: **Hasil pengujian integrasi Room Service pada GitHub Actions (7 pengujian, tanpa kegagalan)**.

## 6. Cara mengulang pengujian

Dari branch `main` dan lingkungan yang telah disiapkan:

```bash
mvn -B clean verify
```

Untuk menjalankan melalui Docker Compose dan smoke test:

```bash
cp .env.example .env
# Isi password lokal melalui .env
docker compose up --build -d
set -a
source .env
set +a
python3 scripts/smoke.py
```

Pada pengujian berbasis PostgreSQL di CI, `test.yml` mengaktifkan service `postgres:17-alpine` untuk job test kedua aplikasi. Jangan menjalankan integration test dengan profil `create-drop` pada database yang berisi data penting.

## 7. Kesimpulan

Pengujian implementasi GET dan POST API versi 1.0 pada FILKOM Space telah mencakup validasi kontrak request–response, pengelolaan dan pembacaan data, autentikasi, otorisasi, kepemilikan reservasi, pengecekan kapasitas dan jadwal, serta komunikasi HTTP antar-microservices. Pada GitHub Actions branch `main` tanggal 2 November 2026, 19 pengujian terkait GET/POST berhasil tanpa failure/error/skipped, serta smoke test tujuh operasi GET/POST melalui Docker Compose berstatus PASS. Screenshot GitHub Actions dapat diambil dari workflow yang dicantumkan; bukti lokal hanya dapat dinyatakan setelah demonstrasi aktual dilakukan.
