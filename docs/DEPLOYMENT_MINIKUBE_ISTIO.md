# Deployment FILKOM Space: Minikube dan Istio

Deployment demonstrasi ini menggunakan Minikube **v1.39.0**, Kubernetes **v1.35.0**, dan Istio **1.32.0** (sidecar mode). Docker Compose tetap tersedia sebagai alternatif. Ini adalah cluster pengembangan satu node, bukan rancangan produksi dengan high availability.

## Komponen

| Resource | Peran |
|---|---|
| Namespace `filkom-space` | Auto-injection Envoy untuk aplikasi |
| Deployment `room-service`, `reservation-service` | Aplikasi Spring Boot + sidecar; komunikasi melalui DNS Kubernetes |
| StatefulSet `room-db`, `reservation-db` | PostgreSQL 17 terpisah; masing-masing PVC 1 GiB |
| Secret `filkom-secrets` | Kredensial dari environment lokal, tidak disimpan di Git |
| ConfigMap `filkom-config` | URL service, seed simulasi, konfigurasi JVM dan advisory lock |
| Gateway `filkom-gateway` | Satu pintu masuk melalui `istio-ingressgateway` |
| VirtualService | Routing API v1/v2 serta host dokumentasi per layanan |
| PeerAuthentication + DestinationRule | STRICT mTLS untuk Pod aplikasi dan ISTIO_MUTUAL untuk tujuan kedua service |

Alur: klien → Istio ingress → Envoy aplikasi → Spring Boot. Reservation Service menghubungi Room Service melalui `http://room-service:8081`; Envoy mengenkripsi hubungan antarlayanan dengan mTLS. Room Service juga memanggil Reservation Service saat memeriksa kelayakan pengarsipan.

PostgreSQL tidak diinjeksi sidecar dan tidak termasuk cakupan mTLS ini. Database hanya memiliki Service internal; port DB tidak dipublikasikan ke host. HTTP Basic tetap diperiksa aplikasi. Koneksi dari browser/host ke ingress menggunakan HTTP pada loopback; demonstrasi ini **belum menyediakan HTTPS eksternal**.

## Prasyarat laptop Linux/WSL2

- Docker Engine berjalan dan dapat diakses tanpa `sudo` oleh pengguna.
- `minikube` v1.39.0, `kubectl` v1.35.x, `istioctl` 1.32.0, Python 3.
- Sediakan sekurangnya 6 GB RAM untuk cluster dan ruang disk sekitar 20 GB; 8 GB RAM cluster lebih nyaman pada laptop dengan RAM 16 GB. Default skrip adalah 2 CPU/6000 MB. Kapasitas ini untuk workload kecil ini, bukan seluruh contoh Bookinfo/add-on observabilitas.
- Jalankan dari checkout branch implementasi ini.

Unduh CLI dari halaman rilis resmi, sesuaikan arsitektur mesin, dan periksa checksum. Instalasi otomatis Linux amd64 dengan versi yang sama tersedia pada `.github/workflows/minikube-istio.yml`.

## Menjalankan

```bash
cp .env.example .env
# Edit .env: isi username/password database dan akun demonstrasi lokal.
# .env harus berisi assignment Bash biasa. Jangan gunakan kredensial produksi.
set -a
source .env
set +a
bash scripts/deploy_minikube.sh
bash scripts/verify_minikube.sh
```

Skrip memakai profile/context khusus `filkom-space`, membangun image dari Dockerfile yang sudah ada, memuatnya dengan `minikube image load`, memasang Istio, lalu menunggu DB dan aplikasi siap. Tidak memerlukan push image ke registry. Kredensial harus sama saat menjalankan verifikasi dan deployment.

Opsional, sebelum menjalankan deployment:

```bash
export MINIKUBE_MEMORY=8000
export MINIKUBE_CPUS=4
```

DB harus siap sebelum aplikasi dijalankan. Readiness/startup probe aplikasi menggunakan endpoint GET publik ruangan dan endpoint OpenAPI; startup probe memberi waktu untuk startup Java. Probe tersebut tidak menggantikan pengujian bisnis ke DB. Secret baru tidak mengubah password database yang sudah diinisialisasi pada PVC; gunakan password lama untuk profile yang sama atau lakukan migrasi password PostgreSQL secara terpisah.

## Akses API dan Swagger

Setelah verifikasi selesai, jalankan port-forward di terminal tersendiri:

```bash
kubectl --context filkom-space -n istio-system port-forward \
  --address=127.0.0.1 svc/istio-ingressgateway 18080:80
```

API menggunakan satu base URL: `http://127.0.0.1:18080`. Contoh:

```bash
curl -i http://127.0.0.1:18080/api/v2/rooms
curl -i -u "admin:$DEMO_ADMIN_PASSWORD" \
  http://127.0.0.1:18080/api/v2/reservations
```

Host dokumentasi mempertahankan URL absolut Swagger, sehingga tidak memerlukan rewrite path. Tambahkan baris berikut ke hosts file laptop:

```text
127.0.0.1 room.filkom.local reservation.filkom.local
```

- Room Swagger: `http://room.filkom.local:18080/swagger-ui/index.html`
- Reservation Swagger: `http://reservation.filkom.local:18080/swagger-ui/index.html`

Untuk Postman, isi `roomBase` dan `reservationBase` dengan `http://127.0.0.1:18080`; isi password sesuai `.env`. Kontrak dan path endpoint tetap sama.

## Verifikasi dan bukti asli

`bash scripts/verify_minikube.sh` melakukan:

1. Memeriksa aplikasi dan `istio-proxy` siap pada kedua Deployment (2/2).
2. Menjalankan `istioctl analyze` dan merekam `proxy-status`.
3. Mengirim request HTTP melalui ingress, termasuk GET/POST/PUT/PATCH/DELETE, v1/v2, input invalid, hak akses, approval, konflik, cancellation, archive, dan pengulangan DELETE/PUT.
4. Memeriksa routing host dokumentasi per service.
5. Mengirim GET dari Pod Python **tanpa sidecar** langsung ke kedua service dan memastikan koneksi plaintext ditolak pada lapisan transport oleh STRICT mTLS. Respons HTTP 401/403 tidak dianggap sebagai bukti penolakan mTLS.

Koleksi Postman asli tersedia di `tests/postman/filkom-space-v2.postman_collection.json`. Workflow juga menjalankannya melalui Newman 6.2.1 pada gateway yang sama. Untuk menambahkan pelaksanaan ini di laptop, gunakan `RUN_NEWMAN=true bash scripts/verify_minikube.sh` (memerlukan Node.js/npm). Koleksi tersebut menjalankan 31 request dengan 62 assertion; GET/POST membentuk fixture dan memverifikasi hasil perubahan.

Hasil aktual disimpan di `evidence/`: status Pod/PVC, konfigurasi mesh, output CLI, matriks respons HTTP JSON, serta hasil uji negatif mTLS. Tidak menyimpan password atau header Authorization dalam matriks hasil. Kubernetes Secret tidak diekspor.

Workflow **Minikube and Istio** menjalankan langkah yang sama pada runner GitHub Actions dengan Docker dan cluster Minikube nyata. Artifact `minikube-istio-evidence` serta job summary menjadi sumber bukti CI. Status PASS hanya berlaku apabila run selesai berhasil; keberadaan file YAML saja tidak membuktikan deployment berhasil.

Untuk screenshot laporan, ambil tampilan job summary/log run yang benar-benar selesai, `kubectl get pods`, hasil smoke dan uji mTLS. Sebutkan apakah sumbernya CI atau laptop. Jangan menyebut deployment laptop berhasil hanya berdasarkan hasil CI.

## Menghentikan dan menjalankan kembali

```bash
minikube stop -p filkom-space
minikube start -p filkom-space
# Jalankan ulang deployment bila image/config berubah.
```

PVC mempertahankan data selama profile tidak dihapus. Untuk menghapus cluster demonstrasi **beserta seluruh data DB di dalamnya**, jalankan secara sengaja:

```bash
minikube delete -p filkom-space
```

Tidak ada perintah penghapusan cluster otomatis pada skrip laptop. Workflow menghapus hanya cluster CI sementara setelah mengunggah bukti.

## Batasan dan rujukan

Tidak mencakup autoscaling, failover DB, backup, HTTPS publik, dashboard Kiali/Prometheus/Grafana, atau deployment produksi. Retry gateway dinonaktifkan agar request mutasi tidak diulang otomatis. STRICT mTLS melindungi koneksi ke Pod yang memiliki sidecar; tidak mengenkripsi segmen browser → ingress maupun aplikasi → PostgreSQL yang berada di luar mesh.

- [Istio: supported Kubernetes versions](https://istio.io/latest/docs/releases/supported-releases/)
- [Istio: installation with istioctl](https://istio.io/latest/docs/setup/install/istioctl/)
- [Istio: ingress routing](https://istio.io/latest/docs/tasks/traffic-management/ingress/ingress-control/)
- [Istio: STRICT mTLS](https://istio.io/latest/docs/tasks/security/authentication/mtls-migration/)
- [Minikube: loading local images](https://minikube.sigs.k8s.io/docs/handbook/pushing/)
