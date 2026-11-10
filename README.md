# SingularityGuard AntiCheat (Paper 1.20.4+, Java 17+)

## JAR nasıl alınır (3 yol)
1. **GitHub (kurulum gerektirmez):** Klasörü yeni bir GitHub reposuna yükle -> Actions sekmesi -> "Build SingularityGuard" -> Artifacts -> SingularityGuard-jar (içinde SingularityGuard.jar).
2. **IntelliJ IDEA:** Klasörü aç -> sağdaki Maven paneli -> Lifecycle -> package -> target/SingularityGuard.jar
3. **Terminal:** JDK 17 + Maven kur, `build.bat` (Windows) veya `./build.sh` çalıştır.

Jar'ı sunucunun `plugins/` klasörüne at.

Komutlar: /ac alerts | vl <oyuncu> | reset <oyuncu> | status | reload
İzinler: singularityguard.admin, singularityguard.alerts, singularityguard.bypass
Önce max-vl değerlerini yüksek tutup plugins/SingularityGuard/violations.log dosyasını izleyin.
