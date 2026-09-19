# Panduan Setup Signing Keystore untuk GitHub Actions CI/CD

Panduan ini menjelaskan cara membuat release keystore dan mengonfigurasikannya ke **GitHub Secrets** agar GitHub Actions dapat menandatangani file APK rilis (*signed release APK*) secara otomatis saat tag versi dibuat atau push ke branch `main`.

---

## 1. Buat Release Keystore Lokal

Jalankan perintah berikut di terminal komputer Anda (pastikan JDK sudah terpasang):

```bash
keytool -genkey -v \
    -keystore mountify-release.jks \
    -alias mountify \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Noir, OU=MountX, O=Zy0x, L=ID, ST=ID, C=ID"
```

> [!IMPORTANT]
> Simpan password yang dimasukkan dengan aman. Berkas `mountx-release.jks` dilindungi pada `.gitignore` dan dilarang di-commit ke repositori publik.

---

## 2. Konversi Keystore ke Format Base64

Agar dapat disimpan sebagai GitHub Secret, konversikan file `.jks` ke teks Base64:

### Di PowerShell (Windows):
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("mountx-release.jks")) | Out-File -FilePath "keystore-base64.txt" -Encoding ASCII -NoNewline
```

### Di Linux / macOS:
```bash
base64 -w 0 mountx-release.jks > keystore-base64.txt
```

---

## 3. Tambahkan ke GitHub Secrets

1. Buka repositori GitHub:
   `https://github.com/Zy0x/Mountify/settings/secrets/actions`
2. Klik tombol **New repository secret**.
3. Tambahkan 4 secrets berikut satu per satu:

| Name | Secret Value |
|---|---|
| `KEYSTORE_BASE64` | Isi teks dari file `keystore-base64.txt` |
| `KEYSTORE_PASSWORD` | Password keystore yang dibuat di langkah 1 |
| `KEY_ALIAS` | `mountx` |
| `KEY_PASSWORD` | Password alias key yang dibuat di langkah 1 |

4. Hapus file `keystore-base64.txt` setelah selesai ditambahkan ke GitHub.

---

## 4. Cara Memicu Rilis Otomatis

Untuk membuat rilis baru beserta APK yang sudah di-sign:

```bash
# Buat tag versi baru (misal: v1.0.0)
git tag v1.0.0

# Push tag ke GitHub
git push origin v1.0.0
```

GitHub Actions akan secara otomatis:
1. Membangun Release APK.
2. Menandatangani APK menggunakan Keystore dari Secrets.
3. Mengemas modul Magisk ke dalam format `.zip`.
4. Membuat halaman **GitHub Release** baru dengan menyertakan file APK dan Magisk Module Zip untuk diunduh publik.
