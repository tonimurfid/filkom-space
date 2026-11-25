# Demo Runbook — FILKOM Space (Rundown 7 Menit)

Panduan command siap *copy-paste* dari **setup awal**, **live demo API (sisi Student)**, **live demo automated testing (JUnit 5)**, hingga **reset & cleaning** untuk presentasi **Kamis, 26 November 2026**.

> **Target Waktu Demo:**
> - **Slide 7 — Demo API (Sisi Student):** `~1 menit 30 detik` (`3:35 – 5:20`)
> - **Slide 8 — Demo Automated Unit & Integration Test:** `~1 menit 15 detik` (`5:20 – 6:35`)

---

## Tahap 1 — Setup Awal & Pre-Warm (5 Menit Sebelum Maju)

Buka **2 terminal** di direktori root repositori `filkom-space`:
- **Terminal 1:** Khusus *Live Demo API* (Slide 7)
- **Terminal 2:** Khusus *Live Demo Automated Test* (Slide 8)

### A. PowerShell (Windows)

**Di Terminal 1 (Nyalakan Docker Compose & load `.env`):**
```powershell
# 1. Pastikan 4 container (2 service + 2 PostgreSQL 17) menyala & healthy
docker compose up -d
docker compose ps

# 2. Load variabel .env ke sesi PowerShell
Get-Content .env | ForEach-Object {
  if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim('"').Trim("'"), 'Process')
  }
}

# 3. Siapkan header Basic Auth untuk akun Student (235150200111001)
$AUTH = "$($env:DEMO_STUDENT_ID):$($env:DEMO_STUDENT_PASSWORD)"
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($AUTH))
$headers = @{ Authorization = "Basic $cred"; "Content-Type" = "application/json" }

# 4. Bersihkan layar terminal sebelum share screen
cls
```

**Di Terminal 2 (Pre-warm kompilasi Maven agar `mvn -B test` langsung cepat):**
```powershell
mvn -B test-compile
cls
```

---

### B. Bash (Linux / WSL)

**Di Terminal 1 (Nyalakan Docker Compose & load `.env`):**
```bash
docker compose up -d && docker compose ps
set -a && source .env && set +a
AUTH="${DEMO_STUDENT_ID}:${DEMO_STUDENT_PASSWORD}"
clear
```

**Di Terminal 2 (Pre-warm kompilasi Maven):**
```bash
mvn -B test-compile
clear
```

---

## Tahap 2 — Live Demo 1: Penggunaan API Sisi Student (Slide 7)

Jalankan urut **Langkah 1 → 5** di **Terminal 1**. Semua UUID (`ROOM_ID` dan `RES_ID`) tertangkap otomatis tanpa perlu *copy-paste* manual.

### A. PowerShell (Windows)

```powershell
# 1) Cari daftar ruangan aktif (Publik) & ambil ROOM_ID otomatis
$rooms = Invoke-RestMethod "http://localhost:8081/api/v2/rooms?minCapacity=4"
$rooms | ConvertTo-Json -Depth 4
$ROOM_ID = $rooms.items[0].id

# 2) Cek ketersediaan slot (Publik) — respons memakai objek schedule & available: true
Invoke-RestMethod "http://localhost:8082/api/v2/availability?roomId=$ROOM_ID&date=2026-12-10&startTime=13:00&endTime=15:00" | ConvertTo-Json -Depth 4

# 3) Ajukan reservasi baru sebagai Student (201 Created, status awal PENDING)
$bodyCreate = @{
  roomId = $ROOM_ID
  studentId = $env:DEMO_STUDENT_ID
  schedule = @{ date = "2026-12-10"; startTime = "13:00"; endTime = "15:00" }
  purpose = "Diskusi proyek akhir ABL"
  participantCount = 4
} | ConvertTo-Json -Depth 4
$res = Invoke-RestMethod -Method Post -Uri "http://localhost:8082/api/v2/reservations" -Headers $headers -Body $bodyCreate
$res | ConvertTo-Json -Depth 4
$RES_ID = $res.id

# 4) Ubah pengajuan PENDING milik sendiri (PUT -> 200 OK)
$bodyUpdate = @{
  roomId = $ROOM_ID
  schedule = @{ date = "2026-12-10"; startTime = "14:00"; endTime = "16:00" }
  purpose = "Diskusi & revisi proyek ABL"
  participantCount = 6
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Put -Uri "http://localhost:8082/api/v2/reservations/$RES_ID" -Headers $headers -Body $bodyUpdate | ConvertTo-Json -Depth 4

# 5) Batalkan reservasi milik sendiri (DELETE -> 204 No Content) & cek status CANCELLED
Invoke-WebRequest -Method Delete -Uri "http://localhost:8082/api/v2/reservations/$RES_ID" -Headers $headers | Select-Object StatusCode, StatusDescription
Invoke-RestMethod -Uri "http://localhost:8082/api/v2/reservations/$RES_ID" -Headers $headers | ConvertTo-Json -Depth 4
```

*(Opsional bonus 5 detik — bukti RBAC Student tidak boleh membuat ruangan -> `403 Forbidden`)*:
```powershell
$bodyRoom = '{"code":"TEST-403","name":"Test","building":"G","floor":1,"capacity":5,"facilities":["WIFI"]}'
try { Invoke-WebRequest -Method Post -Uri "http://localhost:8081/api/v2/rooms" -Headers $headers -Body $bodyRoom } catch { $_.Exception.Response.StatusCode.value__ }
```

---

### B. Bash (Linux / WSL)

```bash
# 1) Cari daftar ruangan aktif (Publik) & ambil ROOM_ID otomatis
ROOMS=$(curl -s "http://localhost:8081/api/v2/rooms?minCapacity=4")
echo "$ROOMS" | python3 -m json.tool
ROOM_ID=$(echo "$ROOMS" | python3 -c "import sys,json; print(json.load(sys.stdin)['items'][0]['id'])")

# 2) Cek ketersediaan slot (Publik) — respons memakai objek schedule & available: true
curl -s "http://localhost:8082/api/v2/availability?roomId=${ROOM_ID}&date=2026-12-10&startTime=13:00&endTime=15:00" | python3 -m json.tool

# 3) Ajukan reservasi baru sebagai Student (201 Created, status awal PENDING)
RES=$(curl -s -u "$AUTH" -X POST "http://localhost:8082/api/v2/reservations" \
  -H "Content-Type: application/json" \
  -d "{\"roomId\":\"${ROOM_ID}\",\"studentId\":\"${DEMO_STUDENT_ID}\",\"schedule\":{\"date\":\"2026-12-10\",\"startTime\":\"13:00\",\"endTime\":\"15:00\"},\"purpose\":\"Diskusi proyek akhir ABL\",\"participantCount\":4}")
echo "$RES" | python3 -m json.tool
RES_ID=$(echo "$RES" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# 4) Ubah pengajuan PENDING milik sendiri (PUT -> 200 OK)
curl -s -u "$AUTH" -X PUT "http://localhost:8082/api/v2/reservations/${RES_ID}" \
  -H "Content-Type: application/json" \
  -d "{\"roomId\":\"${ROOM_ID}\",\"schedule\":{\"date\":\"2026-12-10\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"},\"purpose\":\"Diskusi & revisi proyek ABL\",\"participantCount\":6}" | python3 -m json.tool

# 5) Batalkan reservasi (DELETE -> 204 No Content) & verifikasi status CANCELLED
curl -i -s -u "$AUTH" -X DELETE "http://localhost:8082/api/v2/reservations/${RES_ID}" | head -n 1
curl -s -u "$AUTH" "http://localhost:8082/api/v2/reservations/${RES_ID}" | python3 -m json.tool
```

*(Opsional bonus 5 detik — bukti RBAC Student -> `403 Forbidden`)*:
```bash
curl -i -s -u "$AUTH" -X POST "http://localhost:8081/api/v2/rooms" \
  -H "Content-Type: application/json" \
  -d '{"code":"TEST-403","name":"Test","building":"G","floor":1,"capacity":5,"facilities":["WIFI"]}' | head -n 1
```

---

## Tahap 3 — Live Demo 2: Automated Unit & Integration Testing (Slide 8)

Jalankan di **Terminal 2** (berlaku sama di PowerShell maupun Bash):

```bash
# Opsi 1 (Super cepat ~3-5 detik): Sorot Unit Test murni terlebih dulu (zona WIB & Room Service 503)
mvn -B -pl reservation-service test -Dtest=ReservationServiceTest

# Opsi 2 (Utama ~15-25 detik): Jalankan SELURUH 37 Unit & Integration Test (15 Room + 22 Reservation)
mvn -B test
```

> **Yang ditunjukkan ke dosen:** `Tests run: 15` (Room Service) + `Tests run: 22` (Reservation Service) → `Failures: 0, Errors: 0, Skipped: 0` → **`BUILD SUCCESS`**.

---

## Tahap 4 — Reset Setelah Latihan & Cleaning Akhir

### A. Reset Bersih Setelah Latihan (Agar Besok Database 100% Fresh)

> **Catatan:** Jangan jalankan `mvn clean` sebelum presentasi agar hasil kompilasi di `target/` tetap siap dan `mvn -B test` jalan cepat.

```bash
# Hapus container + volume DB lama, lalu nyalakan ulang agar SEED_DATA=true mengisi data awal fresh
docker compose down -v
docker compose up -d
docker compose ps
```

Bersihkan variabel & layar terminal sebelum mulai presentasi:
- **PowerShell:** `Remove-Variable ROOM_ID, RES_ID -ErrorAction SilentlyContinue; cls`
- **Bash / WSL:** `unset ROOM_ID RES_ID && clear`

---

### B. Cleaning Penuh Setelah Presentasi Selesai

```bash
# Opsi 1: Matikan semua container (volume DB tetap tersimpan)
docker compose down

# Opsi 2: Matikan semua container + hapus volume PostgreSQL (bersih total)
docker compose down -v

# Opsional: bersihkan artefak build Maven lokal
mvn -B clean
```

---

## Opsi B — Versi Minikube + Istio Service Mesh (`http://127.0.0.1:18080`)

Gunakan opsi ini jika ingin mendemokan API lewat **satu pintu Istio Ingress Gateway** (`filkom-gateway` + `VirtualService filkom-api` + enkripsi antar-Pod **`STRICT mTLS`**) di atas Kubernetes Minikube.

> **Catatan Lingkungan (PDF Hal. 85–89):** Perintah deployment dijalankan di **terminal Ubuntu WSL2** (dengan Docker Desktop terintegrasi WSL2, minimal 2 CPU & RAM cluster 6000–8000 MB). Setelah `port-forward` aktif, request demo bisa dikirim dari **WSL2 (Bash)** maupun **PowerShell (Windows)**.

### B1. Setup Awal & Deploy Cluster (10–15 Menit Sebelum Maju, di Terminal WSL2)

```bash
# 1. Masuk ke root repo filkom-space & load .env
set -a && source .env && set +a

# 2. Deploy cluster Minikube + Istio + 2 PostgreSQL StatefulSet + 2 Spring Boot Deployment
export MINIKUBE_PROFILE=filkom-space
# export MINIKUBE_MEMORY=8000   # opsional jika RAM laptop mencukupi
bash scripts/deploy_minikube.sh

# 3. Pastikan semua Pod Ready (aplikasi 2/2 dengan istio-proxy, DB 1/1) & mTLS STRICT aktif
kubectl --context filkom-space -n istio-system get pods
kubectl --context filkom-space -n filkom-space get pods,svc,pvc
kubectl --context filkom-space -n filkom-space get peerauthentication default -o yaml
```

*(Opsional saat latihan — jalankan verifikasi otomatis gateway + uji negatif plaintext STRICT mTLS; pastikan tidak ada `port-forward` manual pada port `18080` yang sedang aktif)*:
```bash
bash scripts/verify_minikube.sh
```

### B2. Buka Pintu Istio Ingress Gateway (Terminal WSL2 Tersendiri — Biarkan Tetap Aktif)

```bash
kubectl --context filkom-space -n istio-system port-forward \
  --address=127.0.0.1 svc/istio-ingressgateway 18080:80
```

---

### B3. Live Demo 1: Penggunaan API Sisi Student via Istio Gateway (`:18080`)

Semua endpoint Room Service dan Reservation Service kini diakses melalui **satu URL gateway `http://127.0.0.1:18080`**.

#### Versi Bash (Terminal WSL2 kedua):
```bash
# Persiapan variabel sesi (sebelum timer jalan)
set -a && source .env && set +a
BASE="http://127.0.0.1:18080"
AUTH="${DEMO_STUDENT_ID}:${DEMO_STUDENT_PASSWORD}"
clear

# 1) Cari daftar ruangan aktif via Istio Gateway (Publik) & ambil ROOM_ID otomatis
ROOMS=$(curl -s "${BASE}/api/v2/rooms?minCapacity=4")
echo "$ROOMS" | python3 -m json.tool
ROOM_ID=$(echo "$ROOMS" | python3 -c "import sys,json; print(json.load(sys.stdin)['items'][0]['id'])")

# 2) Cek ketersediaan slot via Istio Gateway (Publik)
curl -s "${BASE}/api/v2/availability?roomId=${ROOM_ID}&date=2026-12-10&startTime=13:00&endTime=15:00" | python3 -m json.tool

# 3) Ajukan reservasi baru sebagai Student (201 Created, status PENDING)
RES=$(curl -s -u "$AUTH" -X POST "${BASE}/api/v2/reservations" \
  -H "Content-Type: application/json" \
  -d "{\"roomId\":\"${ROOM_ID}\",\"studentId\":\"${DEMO_STUDENT_ID}\",\"schedule\":{\"date\":\"2026-12-10\",\"startTime\":\"13:00\",\"endTime\":\"15:00\"},\"purpose\":\"Diskusi proyek akhir ABL (via Istio)\",\"participantCount\":4}")
echo "$RES" | python3 -m json.tool
RES_ID=$(echo "$RES" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# 4) Ubah pengajuan PENDING milik sendiri (PUT -> 200 OK)
curl -s -u "$AUTH" -X PUT "${BASE}/api/v2/reservations/${RES_ID}" \
  -H "Content-Type: application/json" \
  -d "{\"roomId\":\"${ROOM_ID}\",\"schedule\":{\"date\":\"2026-12-10\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"},\"purpose\":\"Diskusi & revisi ABL (via Istio)\",\"participantCount\":6}" | python3 -m json.tool

# 5) Batalkan reservasi (DELETE -> 204 No Content) & verifikasi status CANCELLED
curl -i -s -u "$AUTH" -X DELETE "${BASE}/api/v2/reservations/${RES_ID}" | head -n 1
curl -s -u "$AUTH" "${BASE}/api/v2/reservations/${RES_ID}" | python3 -m json.tool
```

#### Versi PowerShell (Windows — tetap mengarah ke `http://127.0.0.1:18080` lewat WSL localhost):
```powershell
# Persiapan variabel (setelah load .env & $headers dari Tahap 1A)
$BASE = "http://127.0.0.1:18080"
cls

# 1) Cari daftar ruangan aktif via Istio Gateway
$rooms = Invoke-RestMethod "$BASE/api/v2/rooms?minCapacity=4"
$rooms | ConvertTo-Json -Depth 4
$ROOM_ID = $rooms.items[0].id

# 2) Cek ketersediaan slot via Istio Gateway
Invoke-RestMethod "$BASE/api/v2/availability?roomId=$ROOM_ID&date=2026-12-10&startTime=13:00&endTime=15:00" | ConvertTo-Json -Depth 4

# 3) Ajukan reservasi baru sebagai Student (201 Created, PENDING)
$bodyCreate = @{
  roomId = $ROOM_ID
  studentId = $env:DEMO_STUDENT_ID
  schedule = @{ date = "2026-12-10"; startTime = "13:00"; endTime = "15:00" }
  purpose = "Diskusi proyek akhir ABL (via Istio)"
  participantCount = 4
} | ConvertTo-Json -Depth 4
$res = Invoke-RestMethod -Method Post -Uri "$BASE/api/v2/reservations" -Headers $headers -Body $bodyCreate
$res | ConvertTo-Json -Depth 4
$RES_ID = $res.id

# 4) Ubah pengajuan PENDING milik sendiri (PUT -> 200 OK)
$bodyUpdate = @{
  roomId = $ROOM_ID
  schedule = @{ date = "2026-12-10"; startTime = "14:00"; endTime = "16:00" }
  purpose = "Diskusi & revisi ABL (via Istio)"
  participantCount = 6
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Put -Uri "$BASE/api/v2/reservations/$RES_ID" -Headers $headers -Body $bodyUpdate | ConvertTo-Json -Depth 4

# 5) Batalkan reservasi (DELETE -> 204 No Content) & cek status CANCELLED
Invoke-WebRequest -Method Delete -Uri "$BASE/api/v2/reservations/$RES_ID" -Headers $headers | Select-Object StatusCode, StatusDescription
Invoke-RestMethod -Uri "$BASE/api/v2/reservations/$RES_ID" -Headers $headers | ConvertTo-Json -Depth 4
```

*(Catatan: Untuk **Live Demo 2 — Automated Unit & Integration Test** tetap jalankan `mvn -B test` persis seperti di **Tahap 3**, karena JUnit memakai profil test H2 in-memory dan independen dari cluster Minikube).*

---

### B4. Pause, Resume, & Cleaning Minikube + Istio

```bash
# 1. Hentikan port-forward di terminal WSL2 dengan Ctrl+C terlebih dahulu

# 2. Setelah latihan (hemat RAM, image & data PVC tetap tersimpan untuk presentasi besok):
minikube stop -p filkom-space

# 3. Melanjutkan lagi besok sebelum maju (jauh lebih cepat daripada deploy ulang dari awal):
minikube start -p filkom-space
kubectl --context filkom-space -n filkom-space get pods
# Lalu buka kembali port-forward pada langkah B2

# 4. Cleaning TOTAL setelah seluruh presentasi selesai (menghapus cluster, Istio, & seluruh PVC DB):
minikube delete -p filkom-space
```

---

## Opsi C — Versi Postman (GUI)

Gunakan panduan ini jika ingin mendemokan 5 langkah API v2 sisi Student lewat **Postman** (berlaku untuk **Docker Compose** maupun **Minikube + Istio** — cukup ubah variabel URL di Environment).

### C1. Buat Environment di Postman (`FILKOM Space Local`)

Buka tab **Environments** → **+** → beri nama `FILKOM Space Local`, lalu isi variabel berikut (jangan lupa pilih Environment ini di pojok kanan atas Postman):

| Variable | Docker Compose (Default) | Docker Compose (Override Port) | Minikube + Istio Gateway |
| :--- | :--- | :--- | :--- |
| `ROOM_BASE_URL` | `http://localhost:8081` | `http://127.0.0.1:18081` | `http://127.0.0.1:18080` |
| `RES_BASE_URL` | `http://localhost:8082` | `http://127.0.0.1:18082` | `http://127.0.0.1:18080` |
| `STUDENT_ID` | `235150200111001` | `235150200111001` | `235150200111001` |
| `STUDENT_PASSWORD` | *(isi sesuai `.env`)* | *(isi sesuai `.env`)* | *(isi sesuai `.env`)* |
| `ROOM_ID` | *(kosongkan — auto terisi)* | *(kosongkan — auto terisi)* | *(kosongkan — auto terisi)* |
| `RES_ID` | *(kosongkan — auto terisi)* | *(kosongkan — auto terisi)* | *(kosongkan — auto terisi)* |

> **Tips Auth:** Request **1** dan **2** bersifat publik → set **Authorization Type: `No Auth`**. Request **3–6** memakai **Authorization Type: `Basic Auth`** dengan Username `{{STUDENT_ID}}` dan Password `{{STUDENT_PASSWORD}}`.

---

### C2. Daftar Request Berurutan (Langkah 1 → 5 + Bonus RBAC)

#### 1) Cari Daftar Ruangan Aktif (Publik)
- **Method & URL:** `GET` `{{ROOM_BASE_URL}}/api/v2/rooms?minCapacity=4`
- **Authorization:** `No Auth`
- **Scripts → Post-response** *(opsional, agar `ROOM_ID` tersimpan otomatis)*:
  ```javascript
  const res = pm.response.json();
  if (res.items && res.items.length > 0) {
    pm.environment.set("ROOM_ID", res.items[0].id);
  }
  ```
- **Ekspektasi:** `200 OK`, daftar ruangan seed muncul dan variabel `ROOM_ID` otomatis terisi.

#### 2) Cek Ketersediaan Slot (Publik)
- **Method & URL:** `GET` `{{RES_BASE_URL}}/api/v2/availability?roomId={{ROOM_ID}}&date=2026-12-10&startTime=13:00&endTime=15:00`
- **Authorization:** `No Auth`
- **Ekspektasi:** `200 OK`, respons berisi objek `schedule` dan `"available": true`.

#### 3) Ajukan Reservasi Baru sebagai Student
- **Method & URL:** `POST` `{{RES_BASE_URL}}/api/v2/reservations`
- **Authorization:** `Basic Auth` → Username: `{{STUDENT_ID}}`, Password: `{{STUDENT_PASSWORD}}`
- **Headers:** `Content-Type: application/json`
- **Body → `raw` → `JSON`:**
  ```json
  {
    "roomId": "{{ROOM_ID}}",
    "studentId": "{{STUDENT_ID}}",
    "schedule": {
      "date": "2026-12-10",
      "startTime": "13:00",
      "endTime": "15:00"
    },
    "purpose": "Diskusi proyek akhir ABL",
    "participantCount": 4
  }
  ```
- **Scripts → Post-response** *(opsional, agar `RES_ID` tersimpan otomatis)*:
  ```javascript
  const res = pm.response.json();
  if (res.id) {
    pm.environment.set("RES_ID", res.id);
  }
  ```
- **Ekspektasi:** `201 Created`, `"status": "PENDING"`, `createdAt` berformat WIB (`+07:00`).

#### 4) Ubah Pengajuan PENDING Milik Sendiri
- **Method & URL:** `PUT` `{{RES_BASE_URL}}/api/v2/reservations/{{RES_ID}}`
- **Authorization:** `Basic Auth` → Username: `{{STUDENT_ID}}`, Password: `{{STUDENT_PASSWORD}}`
- **Headers:** `Content-Type: application/json`
- **Body → `raw` → `JSON`:**
  ```json
  {
    "roomId": "{{ROOM_ID}}",
    "schedule": {
      "date": "2026-12-10",
      "startTime": "14:00",
      "endTime": "16:00"
    },
    "purpose": "Diskusi & revisi proyek ABL",
    "participantCount": 6
  }
  ```
- **Ekspektasi:** `200 OK`, jam berubah ke `14:00–16:00`, `participantCount: 6`, status tetap `PENDING`.

#### 5a) Batalkan Reservasi Milik Sendiri
- **Method & URL:** `DELETE` `{{RES_BASE_URL}}/api/v2/reservations/{{RES_ID}}`
- **Authorization:** `Basic Auth` → Username: `{{STUDENT_ID}}`, Password: `{{STUDENT_PASSWORD}}`
- **Ekspektasi:** `204 No Content`.

#### 5b) Verifikasi Status Berubah Menjadi CANCELLED
- **Method & URL:** `GET` `{{RES_BASE_URL}}/api/v2/reservations/{{RES_ID}}`
- **Authorization:** `Basic Auth` → Username: `{{STUDENT_ID}}`, Password: `{{STUDENT_PASSWORD}}`
- **Ekspektasi:** `200 OK`, `"status": "CANCELLED"`.

#### 6) Bonus Opsional (5 Detik) — Bukti RBAC Student Ditolak Membuat Ruangan
- **Method & URL:** `POST` `{{ROOM_BASE_URL}}/api/v2/rooms`
- **Authorization:** `Basic Auth` → Username: `{{STUDENT_ID}}`, Password: `{{STUDENT_PASSWORD}}`
- **Headers:** `Content-Type: application/json`
- **Body → `raw` → `JSON`:**
  ```json
  {
    "code": "TEST-403",
    "name": "Test",
    "building": "G",
    "floor": 1,
    "capacity": 5,
    "facilities": ["WIFI"]
  }
  ```
- **Ekspektasi:** `403 Forbidden`.


