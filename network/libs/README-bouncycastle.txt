Purpose
------
This module excludes Bouncy Castle from transitive dependencies to avoid release-time
ProGuard modifying signed BC jars, which causes runtime errors like:

  java.lang.SecurityException: SHA-256 digest error for org/bouncycastle/jce/provider/BouncyCastleProvider.class

How to add Bouncy Castle without signatures
-------------------------------------------
1) Download the BC jars that your build needs (commonly bcprov-jdk18on and bcpkix-jdk18on).
2) Strip their signature metadata so ProGuard/packaging cannot trigger digest checks:

   zip -d <file.jar> 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC'

3) Place the de-signed jars in this folder. The Gradle script includes any jar in here.

Notes
-----
- If you update jvm-native-trusted-roots, you may need to refresh the BC jar versions.
- Alternatively, you can automate stripping via a Gradle task, but this simple approach
  keeps the release build stable without changing upstream artifacts.

